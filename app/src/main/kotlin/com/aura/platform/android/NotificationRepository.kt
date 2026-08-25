package com.aura.platform.android

import android.service.notification.StatusBarNotification
import com.aura.domain.NotificationItem

/**
 * Platform-side notification store + action boundary.
 * Holds the live in-memory snapshot (Android SBN references stay here, never in domain/UI)
 * and executes open/cancel/mark-shown on behalf of explicit user interactions.
 */
object NotificationRepository {

    private val state = kotlinx.coroutines.flow.MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: kotlinx.coroutines.flow.StateFlow<List<NotificationItem>> = state

    @Volatile internal var connected: Boolean = false
    private val live = java.util.concurrent.ConcurrentHashMap<String, StatusBarNotification>()

    internal fun replaceAll(items: List<NotificationItem>, sbns: Array<StatusBarNotification>) {
        synchronized(live) {
            live.clear()
            sbns.forEach { live[it.key] = it }
        }
        state.value = items
    }

    internal fun upsert(item: NotificationItem, sbn: StatusBarNotification) {
        synchronized(live) { live[sbn.key] = sbn }
        state.value = state.value.filterNot { it.key == item.key } + item
    }

    internal fun remove(key: String) {
        synchronized(live) { live.remove(key) }
        state.value = state.value.filterNot { it.key == key }
    }

    fun clearForTest() {
        synchronized(live) { live.clear() }
        state.value = emptyList()
        connected = false
    }

    /** Open the source app/notification via its Android PendingIntent. Returns false if unsafe/absent. */
    fun open(key: String): Boolean {
        val sbn = synchronized(live) { live[key] } ?: return false
        return try {
            val pi = sbn.notification.contentIntent ?: return false
            pi.send()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Cancel the underlying system notification (real dismissal, listener removal follows). */
    fun cancel(key: String): Boolean {
        val svc = serviceRef ?: return false
        return try {
            svc.cancelNotification(key)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Mark keys as seen — only called when the user actually views the panel. */
    fun markShown(keys: List<String>): Boolean {
        val svc = serviceRef ?: return false
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 23) svc.setNotificationsShown(keys.toTypedArray())
            true
        } catch (_: Exception) {
            false
        }
    }

    @Volatile private var serviceRef: AuraNotificationListenerService? = null
    internal fun attach(service: AuraNotificationListenerService) { serviceRef = service }
    internal fun detach() { serviceRef = null }
}
