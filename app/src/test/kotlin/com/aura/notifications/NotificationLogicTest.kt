package com.aura.notifications

import com.aura.domain.NotificationGrouping
import com.aura.domain.NotificationItem
import com.aura.domain.NotificationRules
import com.aura.domain.NotificationTier
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Notification Panel v1.1 — pure domain logic, boundaries, and manifest contract.
 * No device, no Android framework objects in domain/UI assertions.
 */
class NotificationLogicTest {

    private fun item(
        key: String, pkg: String, label: String = pkg,
        title: String? = "T", body: String? = "B", ts: Long = 100L,
        category: String? = null, ongoing: Boolean = false,
        conversation: Boolean = false, importance: Int = 3 // DEFAULT
    ) = NotificationItem(key, pkg, label, title, body, ts, category, ongoing, conversation, importance)

    // ---- PRIORITY RULES ----

    @Test fun `conversation is priority`() {
        assertEquals(NotificationTier.PRIORITY, NotificationRules.tier(item("k","com.w", conversation = true)))
    }

    @Test fun `call category is priority`() {
        assertEquals(NotificationTier.PRIORITY, NotificationRules.tier(item("k","com.p", category = "CALL")))
    }

    @Test fun `alarm calendar category is priority`() {
        assertEquals(NotificationTier.PRIORITY, NotificationRules.tier(item("k","com.cal", category = "ALARM")))
    }

    @Test fun `high importance is priority`() {
        assertEquals(NotificationTier.PRIORITY, NotificationRules.tier(item("k","com.x", importance = 4)))
    }

    @Test fun `silent low importance is low`() {
        assertEquals(NotificationTier.LOW, NotificationRules.tier(item("k","com.x", importance = 1)))
    }

    @Test fun `ongoing is low even at default importance`() {
        assertEquals(NotificationTier.LOW, NotificationRules.tier(item("k","com.x", ongoing = true)))
    }

    @Test fun `default alerting notification is standard`() {
        assertEquals(NotificationTier.STANDARD, NotificationRules.tier(item("k","com.x")))
    }

    @Test fun `recency sort newest first with stable tiebreak`() {
        val a = item("a","p", ts = 100)
        val b = item("b","p", ts = 300)
        val c = item("c","p", ts = 200)
        val sorted = NotificationRules.sortedByRecency(listOf(a, b, c))
        assertEquals(listOf("b","c","a"), sorted.map { it.key })
    }

    // ---- GROUPING ----

    private val whatsapp = listOf(
        item("w1","com.whatsapp","WhatsApp", ts = 10),
        item("w2","com.whatsapp","WhatsApp", ts = 20),
        item("w3","com.whatsapp","WhatsApp", ts = 30),
        item("w4","com.whatsapp","WhatsApp", ts = 40),
        item("w5","com.whatsapp","WhatsApp", ts = 50),
        item("w6","com.whatsapp","WhatsApp", ts = 60)
    )

    @Test fun `same app groups with correct count`() {
        val model = NotificationGrouping.build(whatsapp)
        assertEquals(0, model.priority.size)
        assertEquals(1, model.otherGroups.size)
        assertEquals(6, model.otherGroups.first().items.size)
        assertEquals("WhatsApp", model.otherGroups.first().appLabel)
    }

    @Test fun `different apps never mix in one group`() {
        val items = whatsapp + listOf(
            item("i1","com.instagram","Instagram"),
            item("i2","com.instagram","Instagram"),
            item("g1","com.gmail","Gmail")
        )
        val groups = NotificationGrouping.build(items).otherGroups
        assertEquals(3, groups.size)
        assertTrue(groups.all { g -> g.items.all { it.packageName == g.packageName } })
        // Sorted by count desc: WhatsApp 6, Instagram 2, Gmail 1
        assertEquals(listOf("com.whatsapp","com.instagram","com.gmail"), groups.map { it.packageName })
    }

    @Test fun `priority items stay individual - not collapsed into app group`() {
        val items = whatsapp + listOf(
            item("mum","com.messages","Messages","Mum","Can you call me?", conversation = true)
        )
        val model = NotificationGrouping.build(items)
        assertEquals(1, model.priority.size)
        assertEquals("Can you call me?", model.priority.first().body)
        assertFalse(model.otherGroups.any { it.packageName == "com.messages" })
    }

    @Test fun `removed notification disappears from grouping`() {
        val withoutW3 = whatsapp.filterNot { it.key == "w3" }
        assertEquals(5, NotificationGrouping.build(withoutW3).otherGroups.first().items.size)
    }

    @Test fun `update replaces by key - no duplicates`() {
        val updated = listOf(item("w6","com.whatsapp","WhatsApp", ts = 999))
        val merged = (whatsapp.filterNot { it.key == "w6" } + updated)
        assertEquals(6, NotificationGrouping.build(merged).otherGroups.first().items.size)
        assertTrue(merged.count { it.key == "w6" } == 1)
    }

    @Test fun `empty input yields empty model`() {
        val m = NotificationGrouping.build(emptyList())
        assertTrue(m.priority.isEmpty())
        assertTrue(m.otherGroups.isEmpty())
    }

    // ---- BOUNDARIES ----

    @Test fun `domain has no Android imports`() {
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain/Notifications.kt").readText()
        assertFalse(src.contains("import android"))
        assertFalse(src.contains("StatusBarNotification") || src.contains("PendingIntent"))
    }

    @Test fun `UI has no listener service or Android notification objects`() {
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui/notifications/NotificationPanelScreen.kt").readText()
        assertFalse(src.contains("NotificationListenerService"))
        assertFalse(src.contains("StatusBarNotification"))
        assertFalse(src.contains("import android."))
    }

    @Test fun `platform is the only Android notification boundary`() {
        val svc = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AuraNotificationListenerService.kt").readText()
        assertTrue(svc.contains("NotificationListenerService()"))
        val repo = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/NotificationRepository.kt").readText()
        assertTrue(repo.contains("StatusBarNotification"))
        assertTrue(repo.contains("cancelNotification"))
        assertTrue(repo.contains("setNotificationsShown"))
    }

    @Test fun `manifest declares service correctly`() {
        val m = File("/home/titan/AURA/app/src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(m.contains("android.service.notification.NotificationListenerService"))
        assertTrue(m.contains("AuraNotificationListenerService"))
        // Not exported — only the system binds it
        val svcBlock = m.substringAfter("AuraNotificationListenerService")
        assertTrue(svcBlock.contains("android:exported=\"false\""))
    }

    @Test fun `no notification content logging in platform`() {
        val files = listOf(
            "/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AuraNotificationListenerService.kt",
            "/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/NotificationRepository.kt",
            "/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/NotificationConverter.kt"
        )
        files.forEach { f ->
            val t = File(f).readText()
            assertFalse("$f must not use Log", t.contains("Log."))
            assertFalse("$f must not println", t.contains("println"))
            assertFalse("$f must not open network", t.contains("http"))
        }
    }

    @Test fun `no AI summarization anywhere in notification path`() {
        listOf(
            "domain/Notifications.kt",
            "platform/android/NotificationConverter.kt"
        ).forEach { rel ->
            val t = File("/home/titan/AURA/app/src/main/kotlin/com/aura/$rel").readText().lowercase()
            assertFalse("$rel mentions summarize", t.contains("summar"))
            assertFalse("$rel mentions llm/model inference", t.contains("llm"))
        }
    }

    @Test fun `access manager uses native settings not fake dialogs`() {
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidNotificationAccessManager.kt").readText()
        assertTrue(src.contains("isNotificationListenerAccessGranted"))
        assertTrue(src.contains("ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS") || src.contains("ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }
}
