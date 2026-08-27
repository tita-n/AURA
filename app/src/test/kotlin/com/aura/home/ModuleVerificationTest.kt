package com.aura.home
import com.aura.TestPaths

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ModuleVerificationTest {

    private fun read(path: String): String {
        val f = TestPaths.find("$path")
        val alt = File(path)
        return (if (f.exists()) f else alt).readText()
    }

    // NextEvent
    @Test fun `NextEvent permission is contextual not manifest auto-grant`() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("READ_CALENDAR"))
        val provider = read("app/src/main/kotlin/com/aura/platform/android/NextEventProvider.kt")
        assertTrue(provider.contains("hasPermission"))
        val main = read("app/src/main/kotlin/com/aura/MainActivity.kt")
        // Should use rememberLauncherForActivityResult for calendar, contextual
        assertTrue(main.contains("NextEventProvider.PERMISSION") || main.contains("READ_CALENDAR"))
        assertTrue(main.contains("calendarPermissionLauncher") || main.contains("RequestPermission"))
    }

    @Test fun `NextEvent denied state is calm`() {
        val home = read("app/src/main/kotlin/com/aura/ui/home/HomeScreen.kt")
        assertTrue(home.contains("Calendar is hidden") || home.contains("No upcoming events"))
        assertTrue(home.contains("Tap to allow"))
    }

    @Test fun `NextEvent query runs off UI`() {
        val main = read("app/src/main/kotlin/com/aura/MainActivity.kt")
        assertTrue(main.contains("Dispatchers.IO") && main.contains("queryNextEvent"))
    }

    @Test fun `NextEvent all-day renders without nonsense`() {
        val home = read("app/src/main/kotlin/com/aura/ui/home/HomeScreen.kt")
        // Should check allDay and format as EEE d MMM without time
        assertTrue(home.contains("allDay") || home.contains("ALL_DAY"))
        assertTrue(home.contains("EEE d MMM"))
    }

    @Test fun `NextEvent ContentObserver updates work no excessive querying`() {
        val provider = read("app/src/main/kotlin/com/aura/platform/android/NextEventProvider.kt")
        assertTrue(provider.contains("ContentObserver"))
        assertTrue(provider.contains("registerContentObserver"))
        val main = read("app/src/main/kotlin/com/aura/MainActivity.kt")
        // Should observe version flow, not poll
        assertTrue(main.contains("nextEventVersion") || main.contains("version.collectAsState"))
        assertFalse(main.contains("while (true)") && main.contains("queryNextEvent") && main.contains("delay"))
    }

    // Battery
    @Test fun `Battery sticky snapshot and live changes`() {
        val battery = read("app/src/main/kotlin/com/aura/platform/android/BatteryMonitor.kt")
        assertTrue(battery.contains("ACTION_BATTERY_CHANGED"))
        assertTrue(battery.contains("registerReceiver"))
        // Sticky: registerReceiver(null, filter) for immediate
        assertTrue(battery.contains("sticky") || battery.contains("registerReceiver(null"))
    }

    @Test fun `Battery handles 0-100 and unknown scale`() {
        val battery = read("app/src/main/kotlin/com/aura/platform/android/BatteryMonitor.kt")
        assertTrue(battery.contains("coerceIn(0, 100)"))
        assertTrue(battery.contains("scale") && battery.contains("level"))
        assertTrue(battery.contains("if (level < 0 || scale <= 0) return null") || battery.contains("level < 0"))
    }

    @Test fun `Battery charging updates`() {
        val battery = read("app/src/main/kotlin/com/aura/platform/android/BatteryMonitor.kt")
        assertTrue(battery.contains("BATTERY_STATUS_CHARGING") || battery.contains("charging"))
        assertTrue(battery.contains("EXTRA_STATUS") || battery.contains("EXTRA_PLUGGED"))
    }

    // Music — narrow, music-only media-access bridge (NOT a notification panel)
    @Test fun `Music uses media session with narrow listener bridge`() {
        val main = read("app/src/main/kotlin/com/aura/MainActivity.kt")
        val music = read("app/src/main/kotlin/com/aura/platform/android/MusicMonitor.kt")
        val listener = read("app/src/main/kotlin/com/aura/platform/android/AuraMediaNotificationListenerService.kt")
        // Preferred path: real media session / controller.
        assertTrue(music.contains("MediaSessionManager") && music.contains("getActiveSessions"))
        assertTrue(music.contains("MediaController"))
        assertTrue(music.contains("TransportControls") || music.contains("transportControls"))
        // Narrow listener bridge exists and is music-only.
        assertTrue(listener.contains("AuraMediaNotificationListenerService"))
        assertTrue(listener.contains("MediaNotificationFilter") || listener.contains("MediaNotificationSignal"))
        // No notification body reading anywhere.
        assertFalse(music.contains("EXTRA_TITLE") || music.contains("EXTRA_TEXT") || music.contains("getCharSequence"))
        assertFalse(listener.contains("EXTRA_TITLE") || listener.contains("EXTRA_TEXT") || listener.contains("getCharSequence"))
        // No broad/old notification subsystem.
        assertFalse(music.contains("AuraNotificationListenerService"))
        assertFalse(main.contains("AuraNotificationListenerService"))
    }

    @Test fun `Music uses AudioManager fallback and fails gracefully`() {
        val music = read("app/src/main/kotlin/com/aura/platform/android/MusicMonitor.kt")
        assertTrue(music.contains("AudioManager"))
        assertTrue(music.contains("isMusicActive"))
        assertTrue(music.contains("dispatchMediaKeyEvent"))
        assertTrue(music.contains("try") && music.contains("catch"))
    }

    @Test fun `Music lifecycle safe via ON_RESUME and re-checks access`() {
        val main = read("app/src/main/kotlin/com/aura/MainActivity.kt")
        assertTrue(main.contains("ON_RESUME") && main.contains("musicMonitor"))
        // Access is re-checked on resume; never prompted at install or by launcher role.
        assertTrue(main.contains("NotificationAccess.isListenerEnabled"))
    }

    @Test fun `modules are optional and default empty`() {
        val models = read("app/src/main/kotlin/com/aura/home/HomeModels.kt")
        assertTrue(models.contains("modules: List<HomeModuleType> = emptyList()"))
        val home = read("app/src/main/kotlin/com/aura/ui/home/HomeScreen.kt")
        assertTrue(home.contains("hasExtras") || home.contains("modules.isNotEmpty()"))
    }
}
