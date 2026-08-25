package com.aura.platform.android

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
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
        val pm = context.packageManager
        val secs = action.durationSeconds.coerceIn(1, 24 * 3600)
        // TECNO/HiOS and some OEM desk clocks ignore EXTRA_SKIP_UI=true or require a message.
        // Try showing UI first (most compatible), then skip-ui variant.
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, secs)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AURA Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, secs)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AURA Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, secs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in candidates) {
            if (intent.resolveActivity(pm) == null) continue
            try {
                context.startActivity(intent)
                return ExecutionResult.Success
            } catch (_: Exception) { continue }
        }
        // Fallback: directly launch a known clock package so user at least lands in Clock
        val clockPkgs = listOf(
            "com.android.deskclock", "com.google.android.deskclock",
            "com.transsion.deskclock", "com.transsion.clock", "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock", "com.miui.deskclock"
        )
        for (pkg in clockPkgs) {
            val li = pm.getLaunchIntentForPackage(pkg) ?: continue
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(li); return ExecutionResult.Success } catch (_: Exception) { continue }
        }
        return ExecutionResult.Failure("No timer app found on this device")
    }

    private fun executeSetAlarm(action: AuraAction.SetAlarm): ExecutionResult {
        val pm = context.packageManager
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AURA Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in candidates) {
            if (intent.resolveActivity(pm) == null) continue
            try { context.startActivity(intent); return ExecutionResult.Success } catch (_: Exception) { continue }
        }
        return ExecutionResult.Failure("No alarm app found")
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
