package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * Calendar relevance heuristics: AURA must not surface generic public holidays or
 * birthday noise as if they were personal meetings. Pure + deterministic.
 */
class CalendarRelevanceTest {

    @Test fun `holiday calendar name is noise`() {
        assertTrue(CalendarRelevance.isHolidayOrNoiseCalendar("US Holidays", "com.android.holidaycalendar"))
    }

    @Test fun `birthday calendar is noise`() {
        assertTrue(CalendarRelevance.isHolidayOrNoiseCalendar("Birthdays", null))
    }

    @Test fun `anniversary calendar is noise`() {
        assertTrue(CalendarRelevance.isHolidayOrNoiseCalendar("Anniversaries", "com.google"))
    }

    @Test fun `normal user calendar is not noise`() {
        assertFalse(CalendarRelevance.isHolidayOrNoiseCalendar("me@gmail.com", "com.google"))
        assertFalse(CalendarRelevance.isHolidayOrNoiseCalendar("My Calendar", null))
    }

    @Test fun `holiday event is excluded even if not all-day`() {
        assertFalse(CalendarRelevance.shouldInclude("US Holidays", "com.android.holidaycalendar", allDay = false))
    }

    @Test fun `all-day event on a normal calendar is excluded by default`() {
        assertFalse(CalendarRelevance.shouldInclude("me@gmail.com", "com.google", allDay = true))
    }

    @Test fun `normal timed user event is included`() {
        assertTrue(CalendarRelevance.shouldInclude("me@gmail.com", "com.google", allDay = false))
    }
}
