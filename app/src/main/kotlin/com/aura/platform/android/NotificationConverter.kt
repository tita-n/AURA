package com.aura.platform.android

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.aura.domain.NotificationItem

/**
 * Android -> pure domain conversion. Lives at the platform boundary so no framework
 * type escapes; kept trivial so listener callbacks stay cheap (main-thread delivery).
 */
object NotificationConverter {

    fun fromSbn(sbn: StatusBarNotification): NotificationItem? {
        if (sbn.isOngoing) { /* still ingested — ongoing affects tiering, not ingestion */ }
        val n: Notification = sbn.notification ?: return null

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val content = body ?: bigText

        // App label resolved once per package by the service; passed in to keep this pure.
        return build(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabelFallback,
            title = title,
            body = content,
            timestamp = sbn.postTime,
            category = n.category,
            isOngoing = sbn.isOngoing,
            isConversation = n.shortcutId != null, // conversation notifications carry a shortcut id
            importanceRank = n.priority
        )
    }

    // Set by the service before conversion — avoids repeated PackageManager lookups per notification.
    @Volatile internal var appLabelFallback: String = ""

    /** Pure field-level builder — unit-testable without Android objects. */
    fun build(
        key: String,
        packageName: String,
        appLabel: String,
        title: String?,
        body: String?,
        timestamp: Long,
        category: String?,
        isOngoing: Boolean,
        isConversation: Boolean,
        importanceRank: Int
    ): NotificationItem =
        NotificationItem(
            key = key,
            packageName = packageName,
            appLabel = appLabel.ifBlank { packageName },
            title = title,
            body = body,
            timestamp = timestamp,
            category = category,
            isOngoing = isOngoing,
            isConversation = isConversation,
            importance = importanceRank
        )
}
