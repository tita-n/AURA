package com.aura.platform.android

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Notification-listener access checks + Android-native settings deep link.
 * No fake AURA permission system — Android owns the grant.
 */
class AndroidNotificationAccessManager(private val context: Context) {

    private val componentName = ComponentName(context, AuraNotificationListenerService::class.java)

    fun isAccessGranted(): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationListenerAccessGranted(componentName)
        } else {
            // Pre-27: enabled listener packages contain our component
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            enabled.split(":").any { entry ->
                ComponentName.unflattenFromString(entry)?.packageName == context.packageName
            }
        }
    } catch (_: Exception) { false }

    /**
     * Detailed per-component settings where supported (API 26+), general listener
     * settings otherwise. Null-safe: returns null if neither activity resolves.
     */
    /** Launches the native settings UI directly — keeps startActivity out of the Activity. */
    fun launchSettings(): Boolean {
        val intent = openSettingsIntent() ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }

    fun openSettingsIntent(): Intent? {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val detailed = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
            if (detailed.resolveActivity(context.packageManager) != null) return detailed
        }
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        return if (general.resolveActivity(context.packageManager) != null) general else null
    }
}
