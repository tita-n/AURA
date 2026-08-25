package com.aura.notifications

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Audit: the AURA notification subsystem is rejected.
 * This test walks the repository source tree asserting that no
 * notification-specific AURA implementation remains.
 *
 * Legitimate Android notification behavior (e.g., OS-generated toasts,
 * system notification shade via PendingIntent launch, copy-to-clipboard
 * success feedback) is permitted — only listener/interception/panel/history
 * code is forbidden.
 */
class NotificationRemovalAuditTest {

    private fun sourceRoot(): File {
        // Tests run with working dir = app module; walk up to find the repository root
        val candidates = listOf(
            File(System.getProperty("user.dir")),
            File(System.getProperty("user.dir"), ".."),
            File("/home/titan/AURA"),
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
        // If root is already the kotlin/com/aura directory, walk it
        if (root.path.endsWith("com/aura") || root.name == "aura") {
            return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
                listOfNotNull(File(root, "../../../../../../AndroidManifest.xml").takeIf { it.exists() })
        }
        // Otherwise root is app/src/main — collect kotlin + manifest
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
                    // Allow the rejection doc itself and this audit file to mention the terms
                    if (f.path.contains("NotificationRemovalAuditTest")) continue
                    if (f.path.contains("docs/PRODUCT")) continue
                    violations += "${f.path}: contains '$needle'"
                }
            }
        }
        return violations
    }

    @Test fun `no NotificationListenerService in source`() {
        val v = audit("NotificationListenerService")
        assertTrue("Forbidden NotificationListenerService reference remains: $v", v.isEmpty())
    }

    @Test fun `no AuraNotificationListenerService`() {
        val v = audit("AuraNotificationListenerService")
        assertTrue("AuraNotificationListenerService must be removed: $v", v.isEmpty())
    }

    @Test fun `no notification access manager in source`() {
        val v = audit("AndroidNotificationAccessManager")
        assertTrue("AndroidNotificationAccessManager must be removed: $v", v.isEmpty())
    }

    @Test fun `no NotificationRepository`() {
        val v = audit("NotificationRepository")
        assertTrue("NotificationRepository must be removed: $v", v.isEmpty())
    }

    @Test fun `no NotificationConverter`() {
        val v = audit("NotificationConverter")
        assertTrue("NotificationConverter must be removed: $v", v.isEmpty())
    }

    @Test fun `no StatusBarNotification in source`() {
        val v = audit("StatusBarNotification")
        assertTrue("StatusBarNotification import remains (listener interception): $v", v.isEmpty())
    }

    @Test fun `no notification panel UI`() {
        val v = audit("NotificationPanelScreen")
        assertTrue("NotificationPanelScreen must be removed: $v", v.isEmpty())
    }

    @Test fun `no notification grouping or tiering logic`() {
        // These domain symbols existed only for the rejected subsystem
        val v = audit("NotificationGrouping", "NotificationTier", "NotificationRules", "NotificationItem")
        assertTrue("Notification domain grouping/tiering must be removed: $v", v.isEmpty())
    }

    @Test fun `no BIND_NOTIFICATION_LISTENER_SERVICE in manifest`() {
        // Check manifest directly
        val manifest = File("/home/titan/AURA/app/src/main/AndroidManifest.xml")
        if (!manifest.exists()) {
            // Fallback: locate via sourceRoot
            val root = sourceRoot()
            val cand = File(root, "AndroidManifest.xml")
            if (cand.exists()) {
                assertFalse(cand.readText().contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
            }
            return
        }
        assertFalse("Manifest must not declare BIND_NOTIFICATION_LISTENER_SERVICE", manifest.readText().contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
    }

    @Test fun `no notification history or grouping files exist`() {
        val root = File("/home/titan/AURA")
        val forbiddenFiles = listOf(
            "app/src/main/kotlin/com/aura/domain/Notifications.kt",
            "app/src/main/kotlin/com/aura/platform/android/AuraNotificationListenerService.kt",
            "app/src/main/kotlin/com/aura/platform/android/NotificationConverter.kt",
            "app/src/main/kotlin/com/aura/platform/android/NotificationRepository.kt",
            "app/src/main/kotlin/com/aura/platform/android/AndroidNotificationAccessManager.kt",
            "app/src/main/kotlin/com/aura/ui/notifications/NotificationPanelScreen.kt",
            "app/src/test/kotlin/com/aura/notifications/NotificationLogicTest.kt"
        )
        val existing = forbiddenFiles.filter { File(root, it).exists() }
        assertTrue("These notification files must have been deleted: $existing", existing.isEmpty())
    }

    @Test fun `notification interception docs only describe rejection`() {
        val doc = File("/home/titan/AURA/docs/PRODUCT.md")
        assertTrue("docs/PRODUCT.md must exist and describe rejection", doc.exists())
        val text = doc.readText()
        assertTrue(text.contains("REJECTED"))
        assertTrue(text.contains("NOTIFICATION PANEL"))
        assertTrue(text.contains("Android already handles notifications"))
        assertFalse("Active roadmap must not list Notification Phase 1.1 as upcoming", text.contains("Phase 1.1") && !text.contains("removed from the active roadmap"))
    }
}
