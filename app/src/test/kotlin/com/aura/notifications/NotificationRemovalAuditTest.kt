package com.aura.notifications
import com.aura.TestPaths

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Architecture audit for the OPTIONAL, music-only media-access bridge.
 *
 * AURA does NOT have a notification panel / history / grouping / priority / feed. A
 * single narrow [AuraMediaNotificationListenerService] exists solely as a technical
 * bridge so AURA can discover other apps' active media sessions for the ONE Music
 * contextual surface. This test enforces that scope: the listener must be the only
 * listener, must never read notification content, and no broad notification subsystem
 * may exist.
 */
class NotificationRemovalAuditTest {

    private fun sourceRoot(): File {
        val candidates = listOf(
            File(System.getProperty("user.dir")),
            File(System.getProperty("user.dir"), ".."),
            TestPaths.find(""),
            File(".")
        )
        for (c in candidates) {
            val manifest = File(c, "app/src/main/AndroidManifest.xml")
            if (manifest.exists()) return File(c, "app/src/main")
            val kotlinRoot = File(c, "src/main/kotlin/com/aura")
            if (kotlinRoot.exists()) return kotlinRoot
        }
        return File(System.getProperty("user.dir"))
    }

    private fun allSourceFiles(): List<File> {
        val root = sourceRoot()
        if (root.path.endsWith("com/aura") || root.name == "aura") {
            return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
                listOfNotNull(File(root, "../../../../../../AndroidManifest.xml").takeIf { it.exists() })
        }
        val kotlinFiles = File(root, "kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val manifest = File(root, "AndroidManifest.xml").takeIf { it.exists() }?.let { listOf(it) } ?: emptyList()
        return kotlinFiles + manifest
    }

    private fun audit(vararg forbiddenSubstrings: String): List<String> {
        val violations = mutableListOf<String>()
        for (f in allSourceFiles()) {
            val text = try { f.readText() } catch (_: Exception) { continue }
            for (needle in forbiddenSubstrings) {
                if (text.contains(needle)) {
                    if (f.path.contains("NotificationRemovalAuditTest")) continue
                    if (f.path.contains("docs/PRODUCT")) continue
                    violations += "${f.path}: contains '$needle'"
                }
            }
        }
        return violations
    }

    @Test fun `no broad AuraNotificationListenerService (only the music-only one)`() {
        val v = audit("AuraNotificationListenerService")
        assertTrue("Old broad AuraNotificationListenerService must not exist: $v", v.isEmpty())
    }

    @Test fun `no notification repository converter or access manager`() {
        val v = audit("NotificationRepository", "NotificationConverter", "AndroidNotificationAccessManager")
        assertTrue("Broad notification infra must remain removed: $v", v.isEmpty())
    }

    @Test fun `no notification panel UI`() {
        val v = audit("NotificationPanelScreen")
        assertTrue("NotificationPanelScreen must not exist: $v", v.isEmpty())
    }

    @Test fun `no notification grouping or tiering logic`() {
        val v = audit("NotificationGrouping", "NotificationTier", "NotificationRules", "NotificationItem")
        assertTrue("Notification domain grouping/tiering must be removed: $v", v.isEmpty())
    }

    @Test fun `narrow music listener is the only notification listener`() {
        val files = allSourceFiles().map { it.readText() }
        assertTrue(
            "AuraMediaNotificationListenerService must exist",
            files.any { it.contains("class AuraMediaNotificationListenerService") }
        )
        // Exactly one service extends NotificationListenerService.
        val extending = files.count { it.contains(": NotificationListenerService()") }
        assertEquals("Exactly one notification listener service allowed", 1, extending)
    }

    @Test fun `music listener does not read notification content`() {
        val path = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AuraMediaNotificationListenerService.kt")
        assertTrue(path.exists())
        val text = path.readText()
        assertFalse(
            "Listener must not read notification title/text/body",
            text.contains("EXTRA_TITLE") || text.contains("EXTRA_TEXT") ||
                text.contains("EXTRA_BIG_TEXT") || text.contains("getCharSequence")
        )
        assertTrue("Listener must use the content-free media signal", text.contains("MediaNotificationSignal"))
    }

    @Test fun `music monitor uses media session not notification body`() {
        val path = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/MusicMonitor.kt")
        val text = path.readText()
        assertTrue(text.contains("MediaSessionManager") && text.contains("getActiveSessions"))
        assertTrue(text.contains("MediaController"))
        assertFalse(
            "MusicMonitor must not read notification body",
            text.contains("EXTRA_TITLE") || text.contains("EXTRA_TEXT") || text.contains("getCharSequence")
        )
    }

    @Test fun `manifest declares narrow listener with bind permission and no panel`() {
        val manifest = TestPaths.find("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.exists())
        val text = manifest.readText()
        assertTrue(text.contains("AuraMediaNotificationListenerService"))
        assertTrue(text.contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertFalse("No Notification Panel may be declared", text.contains("NotificationPanelScreen"))
    }

    @Test fun `no notification history or grouping files exist`() {
        val root = TestPaths.find("")
        val forbidden = listOf(
            "app/src/main/kotlin/com/aura/domain/Notifications.kt",
            "app/src/main/kotlin/com/aura/platform/android/AuraNotificationListenerService.kt",
            "app/src/main/kotlin/com/aura/platform/android/NotificationConverter.kt",
            "app/src/main/kotlin/com/aura/platform/android/NotificationRepository.kt",
            "app/src/main/kotlin/com/aura/platform/android/AndroidNotificationAccessManager.kt",
            "app/src/main/kotlin/com/aura/ui/notifications/NotificationPanelScreen.kt"
        )
        val existing = forbidden.filter { File(root, it).exists() }
        assertTrue("These notification files must have been deleted: $existing", existing.isEmpty())
    }

    @Test fun `documentation rejects notification panel and scopes music access as optional`() {
        val doc = TestPaths.find("docs/PRODUCT.md")
        assertTrue(doc.exists())
        val text = doc.readText()
        assertTrue(text.contains("REJECTED"))
        assertTrue(text.contains("NOTIFICATION PANEL"))
        assertTrue(text.contains("OPTIONAL"))
        assertTrue(text.contains("music-only") || text.contains("music only"))
    }
}
