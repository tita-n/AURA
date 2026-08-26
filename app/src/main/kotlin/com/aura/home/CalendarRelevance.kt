package com.aura.home

/**
 * Pure calendar-relevance heuristics — no Android APIs, fully unit-testable.
 *
 * AURA must not surface generic public holidays or birthday noise as if they were
 * personal meetings. Android does not expose a universal "user-created" flag, so we use
 * deterministic calendar metadata (display name / account type) plus an all-day rule.
 *
 * This is a heuristic, not a guarantee — documented as such in PRODUCT.md.
 */
object CalendarRelevance {

    /**
     * Substrings that mark a calendar as holiday/birthday/anniversary noise.
     * Matched case-insensitively against the calendar display name and account type.
     */
    private val NOISE_KEYWORDS = listOf("holiday", "birthday", "anniversar")

    fun isHolidayOrNoiseCalendar(displayName: String?, accountType: String?): Boolean {
        val name = displayName?.lowercase().orEmpty()
        val account = accountType?.lowercase().orEmpty()
        return NOISE_KEYWORDS.any { name.contains(it) || account.contains(it) }
    }

    /**
     * Whether a calendar event should be considered for the contextual surface.
     *
     * - Holiday/birthday/anniversary calendars are always excluded.
     * - All-day events are excluded by default (generic all-day noise — e.g. public
     *   holidays are typically all-day on a "Holidays" calendar). Hide rather than clutter.
     * - Otherwise (a normal user calendar event) it is eligible.
     */
    fun shouldInclude(calendarName: String?, accountType: String?, allDay: Boolean): Boolean {
        if (isHolidayOrNoiseCalendar(calendarName, accountType)) return false
        if (allDay) return false
        return true
    }
}
