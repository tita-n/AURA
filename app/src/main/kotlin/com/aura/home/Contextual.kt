package com.aura.home

/**
 * ONE contextual information surface.
 *
 * AURA philosophy: CALM, SPARSE, CONTEXTUAL. Next Event / Battery / Music are not three
 * separate Home boxes — they feed a single surface that appears only when something matters
 * and, if several things matter at once, shows ONE card that rotates between them.
 *
 * This is intentionally small: a priority-ordered list of [ContextualItem]s. No ML, no
 * behavioral prediction, no giant framework.
 */
enum class ContextualPriority {
    Calendar,   // immediate/ongoing event — highest
    Battery,    // important low-battery or charging warning
    Music       // active playback
}

/** A single piece of contextual information for the one surface. */
sealed interface ContextualItem {
    val id: String
    val priority: ContextualPriority
    /** Optional expiration — the surface stops showing the item past this time. */
    val expiresAtMillis: Long?
}

data class CalendarContextualItem(
    val event: NextEventInfo?,
    val denied: Boolean
) : ContextualItem {
    override val id = "ctx:calendar"
    override val priority = ContextualPriority.Calendar
    override val expiresAtMillis = event?.endMillis
}

data class BatteryContextualItem(
    val percent: Int,
    val charging: Boolean
) : ContextualItem {
    override val id = "ctx:battery"
    override val priority = ContextualPriority.Battery
    override val expiresAtMillis = null
}

data class MusicContextualItem(
    val state: MusicState
) : ContextualItem {
    override val id = "ctx:music"
    override val priority = ContextualPriority.Music
    override val expiresAtMillis = null
}

/**
 * Collects enabled contextual sources into a single, priority-ordered list.
 *
 * - Only *enabled* sources contribute (enabled = "allowed to generate contextual info",
 *   NOT "permanently occupies Home").
 * - Relevance is pure and deterministic (ModuleRelevance + CalendarRelevance).
 * - Output is sorted by [ContextualPriority] (Calendar > Battery > Music) — deterministic.
 * - Disabled sources, irrelevant states, holidays, and all-day noise are filtered out.
 */
object ContextualEngine {

    fun build(
        nowMillis: Long,
        nextEvent: NextEventInfo?,
        nextEventDenied: Boolean,
        nextEventEnabled: Boolean,
        battery: BatteryUiModel?,
        batteryEnabled: Boolean,
        musicState: MusicState,
        musicEnabled: Boolean,
        /** True only when the user has granted notification access to AURA's narrow
         *  media listener. Without it, the Music module is hidden (privacy-respecting). */
        musicAccess: Boolean = true
    ): List<ContextualItem> {
        val items = mutableListOf<ContextualItem>()

        if (nextEventEnabled) {
            when {
                nextEventDenied ->
                    items += CalendarContextualItem(event = null, denied = true)
                nextEvent != null &&
                    CalendarRelevance.shouldInclude(nextEvent.calendarName, nextEvent.accountType, nextEvent.allDay) &&
                    ModuleRelevance.nextEventRelevant(nextEvent, nowMillis) ->
                    items += CalendarContextualItem(event = nextEvent, denied = false)
            }
        }

        if (batteryEnabled && ModuleRelevance.batteryRelevant(battery)) {
            items += BatteryContextualItem(percent = battery!!.percent, charging = battery.charging)
        }

        // Music is relevant while playing OR paused (paused still shows real state), but
        // only when the module is enabled AND the user has granted the optional, narrow
        // media-access permission. Refusing it leaves Music hidden — nothing else is affected.
        if (musicEnabled && musicAccess &&
            (musicState is MusicState.Playing || musicState is MusicState.Paused)) {
            items += MusicContextualItem(state = musicState)
        }

        return items.sortedWith(compareBy { it.priority.ordinal })
    }
}
