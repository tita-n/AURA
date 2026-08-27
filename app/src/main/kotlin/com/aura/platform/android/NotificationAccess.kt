package com.aura.platform.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Narrow media-access gate.
 *
 * AURA's [AuraMediaNotificationListenerService] is the ONLY notification-listener it
 * declares, and it is music-only. This helper answers the single question the rest of
 * the app needs: "has the user enabled AURA's media listener in Android settings?"
 *
 * - We NEVER request this at install, nor because AURA became the default launcher, nor
 *   merely because the Music module was enabled. The user opts in explicitly (see the
 *   Music enable flow), Android shows its own settings, and we re-check here.
 * - No <uses-permission> for this is declared; the system grants it via Settings.
 */
object NotificationAccess {

    fun listenerComponent(context: Context): ComponentName =
        // The system registers the listener under the app NAMESPACE package (com.aura)
        // with the full class name as the component class — NOT under the class's own
        // sub-package (com.aura.platform.android). Build it that way so getActiveSessions
        // and the enabled-listener check match exactly what Android stored.
        ComponentName(context.packageName, AuraMediaNotificationListenerService::class.java.name)

    /** True only if AURA's media listener is enabled in Android's notification-access settings. */
    fun isListenerEnabled(context: Context): Boolean {
        val flat = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        } catch (_: Exception) {
            null
        } ?: return false
        val self = listenerComponent(context)
        // Compare by (packageName == app, className == full class name); the stored value
        // uses a shorthand that can differ from ComponentName.flattenToString().
        return flat.split(":").any { entry ->
            try {
                val c = ComponentName.unflattenFromString(entry)
                c != null && c.packageName == context.packageName && c.className == self.className
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Open Android's notification-listener settings so the user can grant AURA music access.
     *  Lives in the platform layer (not MainActivity) to keep UI free of startActivity(). */
    fun openSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}
