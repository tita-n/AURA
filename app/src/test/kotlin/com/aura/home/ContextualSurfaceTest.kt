package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * The contextual surface is ONE surface fed by many sources. These tests pin the
 * pure aggregation: zero/one/many relevant items, deterministic priority, and that
 * disabled or irrelevant sources never appear.
 */
class ContextualSurfaceTest {

    private val now = 1_000_000L
    private val soon = NextEventInfo("Meeting", now + 30 * 60_000, now + 90 * 60_000, false)
    private val far = NextEventInfo("Tomorrow", now + 20 * 60 * 60_000, now + 21 * 60 * 60_000, false)
    private val holiday = NextEventInfo(
        "Independence Day", now + 30 * 60_000, now + 90 * 60_000, true,
        calendarName = "US Holidays", accountType = "com.android.holidaycalendar"
    )
    private val lowBattery = BatteryUiModel(15, false)
    private val healthyBattery = BatteryUiModel(80, false)
    private val charging = BatteryUiModel(84, true)
    private val playing = MusicState.Playing("Rema", "Artist")
    private val paused = MusicState.Paused("Rema", "Artist")
    private val hidden = MusicState.Hidden

    private fun build(
        nextEvent: NextEventInfo? = null,
        nextEventDenied: Boolean = false,
        nextEventEnabled: Boolean = true,
        battery: BatteryUiModel? = null,
        batteryEnabled: Boolean = true,
        musicState: MusicState = hidden,
        musicEnabled: Boolean = true,
        musicAccess: Boolean = true
    ) = ContextualEngine.build(
        nowMillis = now,
        nextEvent = nextEvent,
        nextEventDenied = nextEventDenied,
        nextEventEnabled = nextEventEnabled,
        battery = battery,
        batteryEnabled = batteryEnabled,
        musicState = musicState,
        musicEnabled = musicEnabled,
        musicAccess = musicAccess
    )

    @Test fun `nothing relevant yields no surface`() {
        assertTrue(build(battery = healthyBattery, musicState = hidden).isEmpty())
    }

    @Test fun `single relevant source yields one item`() {
        val items = build(musicState = playing)
        assertEquals(1, items.size)
        assertTrue(items[0] is MusicContextualItem)
    }

    @Test fun `two relevant sources yield two items in priority order`() {
        val items = build(battery = lowBattery, musicState = playing)
        assertEquals(2, items.size)
        assertEquals(ContextualPriority.Battery, items[0].priority)
        assertEquals(ContextualPriority.Music, items[1].priority)
    }

    @Test fun `three relevant sources yield three items in priority order`() {
        val items = build(nextEvent = soon, battery = lowBattery, musicState = playing)
        assertEquals(3, items.size)
        assertEquals(listOf(ContextualPriority.Calendar, ContextualPriority.Battery, ContextualPriority.Music),
            items.map { it.priority })
    }

    @Test fun `calendar disabled excludes a relevant event`() {
        val items = build(nextEvent = soon, nextEventEnabled = false)
        assertTrue(items.none { it is CalendarContextualItem })
    }

    @Test fun `calendar event more than one hour away is hidden`() {
        val items = build(nextEvent = far)
        assertTrue(items.none { it is CalendarContextualItem })
    }

    @Test fun `public holiday is not surfaced as a personal event`() {
        val items = build(nextEvent = holiday)
        assertTrue(items.none { it is CalendarContextualItem })
    }

    @Test fun `healthy battery is hidden`() {
        val items = build(battery = healthyBattery)
        assertTrue(items.none { it is BatteryContextualItem })
    }

    @Test fun `low battery is relevant`() {
        val items = build(battery = lowBattery)
        assertEquals(1, items.size)
        assertTrue(items[0] is BatteryContextualItem)
    }

    @Test fun `charging is relevant`() {
        val items = build(battery = charging)
        assertEquals(1, items.size)
        assertTrue(items[0] is BatteryContextualItem)
    }

    @Test fun `music paused still shows (real state)`() {
        val items = build(musicState = paused)
        assertEquals(1, items.size)
        assertTrue(items[0] is MusicContextualItem)
    }

    @Test fun `music hidden yields no music item`() {
        val items = build(musicState = hidden)
        assertTrue(items.none { it is MusicContextualItem })
    }

    @Test fun `music shown when listener access granted and playing`() {
        val items = build(musicState = playing, musicAccess = true)
        assertEquals(1, items.count { it is MusicContextualItem })
    }

    @Test fun `music hidden when listener access not granted even if playing`() {
        // The optional media-access permission is the only gate; refusing it hides Music
        // entirely while everything else keeps working.
        val items = build(musicState = playing, musicEnabled = true, musicAccess = false)
        assertTrue(items.none { it is MusicContextualItem })
    }

    @Test fun `revoking listener access hides music gracefully`() {
        val withAccess = build(musicState = playing, musicEnabled = true, musicAccess = true)
        assertTrue(withAccess.any { it is MusicContextualItem })
        val without = build(musicState = playing, musicEnabled = true, musicAccess = false)
        assertTrue(without.none { it is MusicContextualItem })
    }

    @Test fun `Unavailable music state is not surfaced`() {
        val items = build(musicState = MusicState.Unavailable, musicEnabled = true, musicAccess = true)
        assertTrue(items.none { it is MusicContextualItem })
    }

    @Test fun `calendar item expires at event end`() {
        val items = build(nextEvent = soon)
        val cal = items.filterIsInstance<CalendarContextualItem>().first()
        assertEquals(soon.endMillis, cal.expiresAtMillis)
    }

    @Test fun `denied calendar shows the permission prompt item`() {
        val items = build(nextEventDenied = true)
        val cal = items.filterIsInstance<CalendarContextualItem>().firstOrNull()
        assertNotNull(cal)
        assertTrue(cal!!.denied)
    }
}
