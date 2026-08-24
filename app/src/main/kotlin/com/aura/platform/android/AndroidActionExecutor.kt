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
    data object RequiresPermission : ExecutionResult
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
            else -> ExecutionResult.Success // Copy, NoOp etc. require no Android execution
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
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Failed to set timer")
        }
    }

    private fun executeSetAlarm(action: AuraAction.SetAlarm): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Failed to set alarm")
        }
    }

    private fun executeDial(action: AuraAction.Dial): ExecutionResult {
        // Dial via ACTION_DIAL — no CALL_PHONE permission needed, user confirms in dialer
        // For validation phase, we don't actually execute; this is the boundary where execution would happen
        // Actual dial execution is deferred until explicit user confirmation in UI
        return ExecutionResult.Success
    }

    private fun executeSendMessage(action: AuraAction.SendMessage): ExecutionResult {
        // Messaging execution requires chooser — deferred; validation already ensures contact exists
        return ExecutionResult.Success
    }

    private fun executeSendEmail(action: AuraAction.SendEmail): ExecutionResult {
        return ExecutionResult.Success
    }
}
