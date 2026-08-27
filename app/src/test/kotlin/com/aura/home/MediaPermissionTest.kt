package com.aura.home

import com.aura.TestPaths
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Permission + privacy boundary tests for the OPTIONAL Music feature.
 *
 * The Music module must be hidden unless BOTH the module is enabled AND the user has
 * granted the narrow, music-only media access. Refusing it leaves Music unavailable
 * while everything else works. No notification access is requested at install, and no
 * notification-panel state exists anywhere.
 */
class MediaPermissionTest {

    private fun build(musicState: MusicState, musicAccess: Boolean) = ContextualEngine.build(
        nowMillis = 0,
        nextEvent = null,
        nextEventDenied = false,
        nextEventEnabled = false,
        battery = null,
        batteryEnabled = false,
        musicState = musicState,
        musicEnabled = true,
        musicAccess = musicAccess
    )

    @Test fun `listener disabled hides Music even while playing`() {
        val items = build(MusicState.Playing("Calm Down", "Rema"), musicAccess = false)
        assertTrue("Music must be hidden without access", items.none { it is MusicContextualItem })
    }

    @Test fun `listener enabled surfaces Music while playing`() {
        val items = build(MusicState.Playing("Calm Down", "Rema"), musicAccess = true)
        assertEquals(1, items.count { it is MusicContextualItem })
    }

    @Test fun `disabling listener hides Music gracefully`() {
        val withAccess = build(MusicState.Playing("Calm Down", "Rema"), musicAccess = true)
        assertTrue(withAccess.any { it is MusicContextualItem })
        val without = build(MusicState.Playing("Calm Down", "Rema"), musicAccess = false)
        assertTrue(without.none { it is MusicContextualItem })
    }

    @Test fun `Unavailable state is never surfaced`() {
        val items = build(MusicState.Unavailable, musicAccess = true)
        assertTrue(items.none { it is MusicContextualItem })
    }

    @Test fun `non-music modules remain unaffected when Music access denied`() {
        // Battery + Calendar survive a denied Music access; only Music is gated.
        val items = ContextualEngine.build(
            nowMillis = 0,
            nextEvent = NextEventInfo("Meeting", 0, 100, false),
            nextEventDenied = false,
            nextEventEnabled = true,
            battery = BatteryUiModel(15, false),
            batteryEnabled = true,
            musicState = MusicState.Playing("x", "y"),
            musicEnabled = true,
            musicAccess = false
        )
        assertTrue(items.any { it is CalendarContextualItem })
        assertTrue(items.any { it is BatteryContextualItem })
        assertTrue(items.none { it is MusicContextualItem })
    }

    @Test fun `no notification access requested at install`() {
        val manifest = TestPaths.find("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.exists())
        val lines = manifest.readText().lines()
        assertFalse(
            "A <uses-permission> must not request notification access",
            lines.any { it.contains("<uses-permission") && it.contains("NOTIFICATION") }
        )
        assertFalse(
            "No request-notification permission",
            manifest.readText().contains("REQUEST_NOTIFICATION")
        )
    }

    @Test fun `no notification-panel UI or state exists`() {
        val root = TestPaths.find("")
        val notificationsUi = File(root, "app/src/main/kotlin/com/aura/ui/notifications")
        assertFalse("No notification UI package", notificationsUi.exists())
        val engine = TestPaths.find("app/src/main/kotlin/com/aura/home/Contextual.kt")
        assertTrue(engine.exists())
        assertFalse("Engine must not reference a notification panel", engine.readText().contains("NotificationPanel"))
    }
}
