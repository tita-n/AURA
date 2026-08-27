package com.aura.platform.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.aura.domain.AuraAction
import com.aura.resolver.l3.ValidatedAction

/**
 * Execution boundary — ONLY place where Intent, AlarmClock, Settings, etc. are executed.
 * Domain/resolver/UI must never import these.
 * For Phase L3, execution is established but not automatically wired to every ACT typing.
 * AURA must never execute merely because a user typed a partial query.
 */

sealed interface ExecutionResult {
    data object Success : ExecutionResult
    data class Failure(val message: String) : ExecutionResult
    data object Unavailable : ExecutionResult
}

interface ActionExecutor {
    suspend fun execute(action: ValidatedAction): ExecutionResult
}

class AndroidActionExecutor(
    private val context: Context
) : ActionExecutor {

    override suspend fun execute(action: ValidatedAction): ExecutionResult {
        return when (val a = action.result.action) {
            is AuraAction.OpenApp -> executeOpenApp(a)
            is AuraAction.OpenSettings -> executeOpenSettings(a)
            is AuraAction.SetTimer -> executeSetTimer(a)
            is AuraAction.SetAlarm -> executeSetAlarm(a)
            is AuraAction.OpenCamera -> executeOpenCamera(a)
            is AuraAction.SetReminder -> executeSetReminder(a)
            is AuraAction.OpenFile -> executeOpenFile(a)
            is AuraAction.Dial -> executeDial(a)
            is AuraAction.SendMessage -> executeSendMessage(a)
            is AuraAction.SendEmail -> executeSendEmail(a)
            is AuraAction.Copy -> executeCopy(a)
            is AuraAction.OpenCalculator -> executeOpenCalculator(a)
            is AuraAction.SearchPlayStore -> executeSearchPlayStore(a)
            is AuraAction.NoOp -> ExecutionResult.Success
        }
    }

    private fun executeOpenApp(action: AuraAction.OpenApp): ExecutionResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(action.packageName)
            ?: return ExecutionResult.Failure("App not found")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Failed to launch")
        }
    }

    private fun executeOpenSettings(action: AuraAction.OpenSettings): ExecutionResult {
        val intent = when (action.panel.lowercase().replace("-", "")) {
            "wifi", "wifisettings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth", "bluetoothsettings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display", "displaysettings" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound", "soundsettings" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery", "batterysettings" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "apps", "appssettings" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "accessibility", "accessibilitysettings" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "location", "locationsettings" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "date_and_time", "date_and_timesettings" -> Intent(Settings.ACTION_DATE_SETTINGS)
            else -> return ExecutionResult.Failure("Unsupported settings")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Failed to open settings")
        }
    }

    private fun executeSetTimer(action: AuraAction.SetTimer): ExecutionResult {
        val secs = action.durationSeconds
        // Defensive: L3 already validated 1..86400, but guard against stale callers.
        if (secs <= 0 || secs > 24 * 3600) {
            Log.d("AURA_TIMER", "SetTimer rejected: durationSeconds=$secs out of range")
            return ExecutionResult.Failure("Invalid timer duration")
        }
        // Build the correct timer intent — EXTRA_LENGTH is seconds, not millis.
        // See https://developer.android.com/reference/android/provider/AlarmClock#ACTION_SET_TIMER
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_MESSAGE, "AURA Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d("AURA_TIMER", "SetTimer duration=$secs intent=${intent.action} length=${intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1)} skipUi=${intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)}")
        // Package visibility: resolveActivity() returns null on API 30+ if <queries> missing,
        // even when a handler exists. We therefore do NOT treat null as definitive Unavailable —
        // we still try startActivity and let ActivityNotFoundException decide.
        val handler = try { intent.resolveActivity(context.packageManager) } catch (_: Exception) { null }
        Log.d("AURA_TIMER", "SetTimer handler=$handler")
        return try {
            context.startActivity(intent)
            Log.d("AURA_TIMER", "SetTimer launch attempted — handler=$handler result=Success")
            ExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            Log.d("AURA_TIMER", "SetTimer ActivityNotFound: ${e.message}")
            ExecutionResult.Unavailable
        } catch (e: Exception) {
            Log.d("AURA_TIMER", "SetTimer failed: ${e.javaClass.simpleName} ${e.message}")
            ExecutionResult.Failure(e.message ?: "Failed to set timer")
        }
    }

    private fun executeOpenCamera(action: AuraAction.OpenCamera): ExecutionResult {
        val pm = context.packageManager
        // If a specific camera package is known and launchable, open it directly.
        if (!action.packageName.isNullOrBlank()) {
            val direct = pm.getLaunchIntentForPackage(action.packageName)
            if (direct != null) {
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(direct)
                    ExecutionResult.Success
                } catch (e: Exception) {
                    ExecutionResult.Failure(e.message ?: "Failed to open camera")
                }
            }
        }
        // Otherwise use the standard still-image camera intent. AURA never requests the CAMERA
        // permission; the camera app owns its own permission. No custom camera is implemented.
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Unavailable
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Camera unavailable")
        }
    }

    private fun executeOpenFile(action: AuraAction.OpenFile): ExecutionResult {
        // SEARCH ONLY: hand the content Uri to Android's normal file-opening chooser. AURA
        // never becomes a file manager. Grant read permission to the chosen app; the platform
        // source only ever produced content:// Uris for files the storage permission can read.
        return try {
            val uri = android.net.Uri.parse(action.uriString)
            val mime = action.mimeType?.takeIf { it.isNotBlank() }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (mime != null) setDataAndType(uri, mime) else data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Open ${action.displayName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Unavailable
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Cannot open file")
        }
    }

    private fun executeSetReminder(action: AuraAction.SetReminder): ExecutionResult {
        // Android has no public third-party reminder API. AURA honestly hands off to the system
        // calendar editor (pre-filled event). It never claims a reminder was created.
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, action.hour)
        cal.set(java.util.Calendar.MINUTE, action.minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (action.dayOffsetDays > 0) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, action.dayOffsetDays)
        }
        val begin = cal.timeInMillis
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Unavailable
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Calendar unavailable")
        }
    }

    private fun executeSetAlarm(action: AuraAction.SetAlarm): ExecutionResult {
        if (action.hour !in 0..23 || action.minute !in 0..59) {
            return ExecutionResult.Failure("Invalid alarm time")
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "AURA Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val handler = try { intent.resolveActivity(context.packageManager) } catch (_: Exception) { null }
        Log.d("AURA_TIMER", "SetAlarm hour=${action.hour} minute=${action.minute} handler=$handler")
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Unavailable
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Failed to set alarm")
        }
    }

    private fun executeDial(action: AuraAction.Dial): ExecutionResult {
        // PRD §9.2: ACTION_DIAL hand-off — no CALL_PHONE; user confirms in dialer.
        // Number comes only from validated indexed contact data, never raw query text.
        return try {
            val pm = context.packageManager
            val intent = if (action.phoneNumber.isNotBlank()) {
                Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${android.net.Uri.encode(action.phoneNumber)}"))
            } else {
                Intent(Intent.ACTION_DIAL)
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent.resolveActivity(pm) == null) return ExecutionResult.Unavailable
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure("Dialer unavailable")
        }
    }

    private fun executeSendMessage(action: AuraAction.SendMessage): ExecutionResult {
        // Composer hand-off only — AURA never claims delivery. Channel contract:
        // default/message/sms -> smsto: composer (body preserved); whatsapp -> WhatsApp send flow;
        // unknown channel cannot execute safely -> Failure.
        return try {
            val pm = context.packageManager
            when (action.channel.lowercase()) {
                "default", "message", "sms" -> {
                    val uri = if (!action.phone.isNullOrBlank()) "smsto:${android.net.Uri.encode(action.phone)}" else "smsto:"
                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(uri)).apply {
                        action.message?.let { putExtra("sms_body", it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(pm) == null) return ExecutionResult.Unavailable
                    context.startActivity(intent)
                    ExecutionResult.Success
                }
                "whatsapp" -> {
                    val launch = pm.getLaunchIntentForPackage("com.whatsapp")
                        ?: return ExecutionResult.Unavailable
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    ExecutionResult.Success
                }
                else -> ExecutionResult.Failure("Unsupported channel")
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("Messaging unavailable")
        }
    }

    private fun executeSendEmail(action: AuraAction.SendEmail): ExecutionResult {
        // mailto composer hand-off with validated address — never claims delivery.
        return try {
            val pm = context.packageManager
            val uri = if (!action.emailAddress.isNullOrBlank()) "mailto:${android.net.Uri.encode(action.emailAddress)}" else "mailto:"
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse(uri)
                action.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                action.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) == null) return ExecutionResult.Unavailable
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure("Email unavailable")
        }
    }

    private fun executeCopy(action: AuraAction.Copy): ExecutionResult {
        return try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AURA", action.text)
            clipboard.setPrimaryClip(clip)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure("Copy failed")
        }
    }

    private fun executeOpenCalculator(action: AuraAction.OpenCalculator): ExecutionResult {
        // Deterministic calculator lookup — known calculator packages only.
        // No fabricated support: if none is launchable -> Unavailable.
        val knownPackages = listOf(
            "com.android.calculator2",
            "com.google.android.calculator",
            "com.oneplus.calculator",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator",
            "com.transsion.calculator"
        )
        val pm = context.packageManager
        for (pkg in knownPackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launch)
                    ExecutionResult.Success
                } catch (e: Exception) {
                    continue
                }
            }
        }
        return ExecutionResult.Unavailable
    }

    private fun executeSearchPlayStore(action: AuraAction.SearchPlayStore): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/search?q=${android.net.Uri.encode(action.query)}&c=apps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure("Cannot open Play Store")
        }
    }
}
