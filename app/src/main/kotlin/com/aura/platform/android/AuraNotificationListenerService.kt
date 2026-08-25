package com.aura.platform.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * AURA's notification ingestion boundary. Event-driven only:
 * onListenerConnected -> snapshot; onNotificationPosted/Removed -> upsert/remove.
 * Callbacks run on the main thread (API 24+), so work here is minimal mapping only.
 */
class AuraNotificationListenerService : NotificationListenerService() {

    private var appLabels: Map<String, String> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        NotificationRepository.attach(this)
    }

    override fun onDestroy() {
        NotificationRepository.detach()
        NotificationRepository.replaceAll(emptyList(), emptyArray())
        super.onDestroy()
    }

    override fun onListenerConnected() {
        NotificationRepository.connected = true
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: emptyArray()
        } catch (_: Exception) { emptyArray() }
        ingest(active)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = convert(sbn) ?: return
        NotificationRepository.upsert(item, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationRepository.remove(sbn.key)
    }

    private fun ingest(sbns: Array<StatusBarNotification>) {
        appLabels = resolveAppLabels(sbns)
        val items = sbns.mapNotNull { convert(it) }
        NotificationRepository.replaceAll(items, sbns)
    }

    private fun convert(sbn: StatusBarNotification): com.aura.domain.NotificationItem? {
        return try {
            NotificationConverter.appLabelFallback = appLabels[sbn.packageName]
                ?: appLabels.entries.firstOrNull { sbn.packageName.startsWith(it.key) }?.value
                ?: sbn.packageName
            NotificationConverter.fromSbn(sbn)
        } catch (_: Exception) {
            null // malformed notification — skip, never crash the listener
        }
    }

    private fun resolveAppLabels(sbns: Array<StatusBarNotification>): Map<String, String> {
        val pm = packageManager
        return sbns.map { it.packageName }.distinct().associateWith { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) { pkg }
        }
    }

    /** Explicit user viewed the panel — forward to system once, guarded. */
    fun markShown(keys: List<String>) {
        if (keys.isEmpty()) return
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) setNotificationsShown(keys.toTypedArray())
        } catch (_: Exception) { /* best-effort */ }
    }
}
