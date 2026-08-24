package com.aura.platform.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Platform validation — ONLY place where PackageManager/Intent are used for validation.
 * Resolver L3Validator may delegate to this for OpenApp existence checks at runtime.
 * For unit tests and deterministic validation, L0Index check is sufficient; this is for
 * execution-time validation against live Android reality.
 */
class AndroidActionValidator(
    private val context: Context
) {
    fun isPackageLaunchable(packageName: String): Boolean {
        if (packageName.isBlank() || !packageName.contains(".")) return false
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
        }
        // Use queryIntentActivities with package set — respects <queries> but also direct check
        val activities = pm.queryIntentActivities(intent, 0)
        if (activities.isNotEmpty()) return true
        // Fallback: check if package exists and has launch intent
        return try {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            launchIntent != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isSettingsPanelSupported(key: String): Boolean {
        // Closed vocabulary check — same as L3Validator, but platform may have additional runtime checks
        val allowed = setOf(
            "wifi", "wifi_settings",
            "bluetooth", "bluetooth_settings",
            "display", "display_settings",
            "sound", "sound_settings",
            "battery", "battery_settings",
            "apps", "apps_settings"
        )
        val normalized = key.lowercase().replace("-", "")
        return allowed.any { it.lowercase().replace("-", "") == normalized }
    }
}
