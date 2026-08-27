package com.aura.platform.android

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aura.home.CAT_PROGRESS
import com.aura.home.CAT_TRANSPORT
import com.aura.home.MediaNotificationFilter
import com.aura.home.MediaNotificationSignal

/**
 * AURA Media Notification Listener — music-only, narrow, privacy-preserving.
 *
 * THIS IS NOT A NOTIFICATION SUBSYSTEM. There is no notification list, no history,
 * no grouping, no priority, no storage, and no rendering. The service exists solely as
 * an optional technical bridge so AURA can discover other apps' active media sessions
 * and their structured metadata for the ONE Music contextual surface.
 *
 * What it does:
 *   1. On a posted/removed notification, extract ONLY a content-free [MediaNotificationSignal]
 *      (category + whether it carries a media session + whether it is media-style/ongoing).
 *   2. If that signal is demonstrably media-related (MediaNotificationFilter), ask
 *      [MusicMonitor] to refresh the media-session query.
 *
 * What it NEVER does:
 *   - read notification title/text/body;
 *   - inspect WhatsApp/SMS/email/social content;
 *   - store, log, or transmit any notification data;
 *   - pass the original [StatusBarNotification] past this class.
 *
 * Actual track metadata (title/artist/album/artwork) comes from the MediaSession /
 * MediaController that [MusicMonitor] queries once the listener is enabled — never from
 * the notification body.
 */
class AuraMediaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Listener just became active: prime a session query so an already-playing track
        // shows promptly on next Home resume.
        MusicMonitor.instance?.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val notification = sbn.notification ?: return
        // Strip to a content-free signal; reject everything non-media immediately.
        val signal = MediaNotificationSignal(
            packageName = sbn.packageName,
            category = notification.category,
            hasMediaSession = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            isMediaStyle = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                notification.category == CAT_TRANSPORT || notification.category == CAT_PROGRESS,
            isOngoing = sbn.isOngoing
        )
        if (!MediaNotificationFilter.accept(signal)) return
        // Narrow, media-only: trigger a fresh media-session query. No content is read.
        MusicMonitor.instance?.onMediaNotificationPosted()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Re-evaluate so a stopped/removed media session reflects promptly (hidden surface).
        MusicMonitor.instance?.onMediaNotificationPosted()
    }
}
