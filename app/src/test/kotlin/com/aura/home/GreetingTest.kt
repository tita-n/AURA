package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * Local-time greeting must be deterministic (same clock -> same words) so it never feels
 * random or model-driven. Pure, no system clock read.
 */
class GreetingTest {

    @Test fun `night for 0 through 4`() {
        assertEquals("Good night", Presence.greetingFor(0))
        assertEquals("Good night", Presence.greetingFor(4))
    }

    @Test fun `morning for 5 through 11`() {
        assertEquals("Good morning", Presence.greetingFor(5))
        assertEquals("Good morning", Presence.greetingFor(11))
    }

    @Test fun `afternoon for 12 through 16`() {
        assertEquals("Good afternoon", Presence.greetingFor(12))
        assertEquals("Good afternoon", Presence.greetingFor(16))
    }

    @Test fun `evening for 17 through 23`() {
        assertEquals("Good evening", Presence.greetingFor(17))
        assertEquals("Good evening", Presence.greetingFor(23))
    }

    @Test fun `out-of-range hours fall back to evening`() {
        assertEquals("Good evening", Presence.greetingFor(24))
        assertEquals("Good evening", Presence.greetingFor(-1))
    }

    @Test fun `boundaries are exclusive on the lower edge, inclusive on upper`() {
        assertEquals("Good night", Presence.greetingFor(4))   // 4 -> night
        assertEquals("Good morning", Presence.greetingFor(5)) // 5 -> morning
        assertEquals("Good afternoon", Presence.greetingFor(16)) // 16 -> afternoon
        assertEquals("Good evening", Presence.greetingFor(17))   // 17 -> evening
    }
}
