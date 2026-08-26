package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * Contextual module relevance is the heart of AURA's "sparse unless something matters"
 * philosophy. These rules are deterministic and unit-tested so behavior never drifts.
 */
class ModuleRelevanceTest {

    private val now = 1_000_000L

    private fun ctx(
        event: NextEventInfo? = null,
        battery: BatteryUiModel? = null,
        playing: Boolean = false
    ) = ModuleRelevance.ModuleContext(now, event, battery, playing)

    @Test fun `constants match the product spec`() {
        assertEquals(60 * 60 * 1000L, ModuleRelevance.NEXT_EVENT_HORIZON_MS) // within 1 hour
        assertEquals(20, ModuleRelevance.BATTERY_LOW_THRESHOLD)              // <= 20% or charging
    }

    // ---- Next Event ----
    @Test fun `null event is irrelevant`() {
        assertFalse(ModuleRelevance.nextEventRelevant(null, now))
    }

    @Test fun `already-ended event is irrelevant`() {
        val ended = NextEventInfo("Past", now - 5000, now - 1000, false)
        assertFalse(ModuleRelevance.nextEventRelevant(ended, now))
    }

    @Test fun `event starting within the horizon is relevant`() {
        val soon = NextEventInfo("Soon", now + 30 * 60 * 1000, now + 90 * 60 * 1000, false)
        assertTrue(ModuleRelevance.nextEventRelevant(soon, now))
    }

    @Test fun `event starting beyond the horizon is irrelevant`() {
        val far = NextEventInfo("Far", now + 2 * 60 * 60 * 1000, now + 3 * 60 * 60 * 1000, false)
        assertFalse(ModuleRelevance.nextEventRelevant(far, now))
    }

    @Test fun `ongoing event is relevant`() {
        val ongoing = NextEventInfo("Now", now - 1000, now + 1000, false)
        assertTrue(ModuleRelevance.nextEventRelevant(ongoing, now))
    }

    // ---- Battery ----
    @Test fun `null battery is irrelevant`() {
        assertFalse(ModuleRelevance.batteryRelevant(null))
    }

    @Test fun `low battery is relevant`() {
        assertTrue(ModuleRelevance.batteryRelevant(BatteryUiModel(20, false)))
        assertTrue(ModuleRelevance.batteryRelevant(BatteryUiModel(5, false)))
    }

    @Test fun `healthy idle battery is irrelevant`() {
        assertFalse(ModuleRelevance.batteryRelevant(BatteryUiModel(21, false)))
        assertFalse(ModuleRelevance.batteryRelevant(BatteryUiModel(100, false)))
    }

    @Test fun `charging battery is relevant`() {
        assertTrue(ModuleRelevance.batteryRelevant(BatteryUiModel(80, true)))
    }

    // ---- Music ----
    @Test fun `music is relevant only while playing`() {
        assertTrue(ModuleRelevance.musicRelevant(true))
        assertFalse(ModuleRelevance.musicRelevant(false))
    }

    // ---- dispatch ----
    @Test fun `isRelevant dispatches by module type`() {
        val event = NextEventInfo("Soon", now + 30 * 60 * 1000, now + 90 * 60 * 1000, false)
        val lowBattery = BatteryUiModel(15, false)

        assertTrue(ModuleRelevance.isRelevant(HomeModuleType.NextEvent, ctx(event = event)))
        assertFalse(ModuleRelevance.isRelevant(HomeModuleType.NextEvent, ctx()))

        assertTrue(ModuleRelevance.isRelevant(HomeModuleType.Battery, ctx(battery = lowBattery)))
        assertFalse(ModuleRelevance.isRelevant(HomeModuleType.Battery, ctx(battery = BatteryUiModel(90, false))))

        assertTrue(ModuleRelevance.isRelevant(HomeModuleType.Music, ctx(playing = true)))
        assertFalse(ModuleRelevance.isRelevant(HomeModuleType.Music, ctx(playing = false)))
    }
}
