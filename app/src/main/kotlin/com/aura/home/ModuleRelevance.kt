package com.aura.home

/**
 * Contextual module relevance — pure, Android-free, fully unit-testable.
 *
 * AURA Home stays empty most of the time. A module is *enabled* by the user (it is
 * allowed to appear) but only *relevant* when context makes it useful right now.
 * Enabled != permanently visible. This is the core of AURA's "sparse when nothing
 * matters, useful when something matters" philosophy.
 *
 * Relevance is intentionally deterministic and time/state-based. No behavioral
 * inference, no tracking. For the first implementation we prioritize clear,
 * predictable rules over complicated "has the user seen this" heuristics.
 */
object ModuleRelevance {

    /** A module is relevant only within 1 hour of its start, or while it is ongoing. */
    const val NEXT_EVENT_HORIZON_MS = 60 * 60 * 1000L

    /** Battery appears low or when charging becomes relevant. 80% idle → hidden. */
    const val BATTERY_LOW_THRESHOLD = 20

    /** Snapshot of the signals a module's relevance depends on. Pure data. */
    data class ModuleContext(
        val nowMillis: Long,
        val nextEvent: NextEventInfo?,
        val battery: BatteryUiModel?,
        val musicPlaying: Boolean
    )

    fun isRelevant(type: HomeModuleType, ctx: ModuleContext): Boolean = when (type) {
        HomeModuleType.NextEvent -> nextEventRelevant(ctx.nextEvent, ctx.nowMillis)
        HomeModuleType.Battery -> batteryRelevant(ctx.battery)
        HomeModuleType.Music -> musicRelevant(ctx.musicPlaying)
    }

    /** Relevant when an event starts within the horizon, or is currently ongoing. */
    fun nextEventRelevant(event: NextEventInfo?, nowMillis: Long): Boolean {
        if (event == null) return false
        if (event.endMillis <= nowMillis) return false            // already ended
        if (event.beginMillis > nowMillis + NEXT_EVENT_HORIZON_MS) return false // too far out
        return true                                              // starts soon or ongoing
    }

    /** Relevant when low (<= threshold) or actively charging. */
    fun batteryRelevant(battery: BatteryUiModel?): Boolean {
        if (battery == null) return false
        return battery.percent <= BATTERY_LOW_THRESHOLD || battery.charging
    }

    /** Relevant only while music is actively playing. */
    fun musicRelevant(playing: Boolean): Boolean = playing
}
