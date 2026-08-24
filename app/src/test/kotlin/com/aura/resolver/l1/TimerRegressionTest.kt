package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.CommandState
import com.aura.domain.ResolutionOutcome
import com.aura.domain.ResultType
import com.aura.domain.toCommandState
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0Resolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the physical-device timer bug:
 * "set a timer for 10 minutes" returned "Invalid timer format" instead of ACT.
 *
 * Root cause: L1 TimerGrammar returned Invalid (blocking L2) for filler-word
 * phrasings starting with "timer" that weren't the exact strict form.
 *
 * Semantics preserved:
 * - Valid supported syntax -> Resolved -> L3 -> ACT
 * - Recognized syntax + invalid value -> Invalid -> Error
 * - Unrecognized syntax -> Unrecognized -> L2/Empty
 */
class TimerRegressionTest {

    private val index = L0IndexFactory.demoIndex()
    private val router = IntentRouter(
        L0Resolver(index),
        L1Resolver(index),
        com.aura.resolver.l2.L2Resolver(index),
        com.aura.resolver.l3.L3Validator(index)
    )
    private val l1 = TimerGrammar()
    private val l2 = com.aura.resolver.l2.L2Resolver(index)

    private fun assertTimerSeconds(query: String, expectedSeconds: Int) {
        val out = router.route(query)
        assertTrue("Expected Act for '$query', got $out", out is ResolutionOutcome.Act)
        val action = (out as ResolutionOutcome.Act).result.action as AuraAction.SetTimer
        assertEquals("'$query' should be $expectedSeconds seconds", expectedSeconds, action.durationSeconds)
    }

    // ---- The exact failing physical-device query ----

    @Test fun `regression - set a timer for 10 minutes resolves to 600 seconds`() {
        assertTimerSeconds("set a timer for 10 minutes", 600)
    }

    // ---- L1 strict forms: every unit spelling ----

    @Test fun `timer 10 min is 600 seconds`() { assertTimerSeconds("timer 10 min", 600) }
    @Test fun `timer 10 mins is 600 seconds`() { assertTimerSeconds("timer 10 mins", 600) }
    @Test fun `timer 10 minute is 600 seconds`() { assertTimerSeconds("timer 10 minute", 600) }
    @Test fun `timer 10 minutes is 600 seconds`() { assertTimerSeconds("timer 10 minutes", 600) }
    @Test fun `timer 10 m is 600 seconds`() { assertTimerSeconds("timer 10 m", 600) }

    @Test fun `timer 10 sec is 10 seconds`() { assertTimerSeconds("timer 10 sec", 10) }
    @Test fun `timer 10 secs is 10 seconds`() { assertTimerSeconds("timer 10 secs", 10) }
    @Test fun `timer 10 second is 10 seconds`() { assertTimerSeconds("timer 10 second", 10) }
    @Test fun `timer 10 seconds is 10 seconds`() { assertTimerSeconds("timer 10 seconds", 10) }
    @Test fun `timer 10 s is 10 seconds`() { assertTimerSeconds("timer 10 s", 10) }

    @Test fun `timer 1 hour is 3600 seconds`() { assertTimerSeconds("timer 1 hour", 3600) }
    @Test fun `timer 1 hours is 3600 seconds`() { assertTimerSeconds("timer 1 hours", 3600) }
    @Test fun `timer 1 hr is 3600 seconds`() { assertTimerSeconds("timer 1 hr", 3600) }
    @Test fun `timer 1 hrs is 3600 seconds`() { assertTimerSeconds("timer 1 hrs", 3600) }
    @Test fun `timer 1 h is 3600 seconds`() { assertTimerSeconds("timer 1 h", 3600) }

    // ---- L2 semantic variants ----

    @Test fun `set a timer for 10 mins is 600 seconds`() { assertTimerSeconds("set a timer for 10 mins", 600) }
    @Test fun `set a timer for 10 minutes case variant`() { assertTimerSeconds("Set a Timer for 10 Minutes", 600) }
    @Test fun `timer for 10 mins is 600 seconds`() { assertTimerSeconds("timer for 10 mins", 600) }
    @Test fun `timer for 10 minutes is 600 seconds`() { assertTimerSeconds("timer for 10 minutes", 600) }
    @Test fun `remind me in 10 minutes is 600 seconds`() { assertTimerSeconds("remind me in 10 minutes", 600) }
    @Test fun `countdown 10 minutes is 600 seconds`() { assertTimerSeconds("countdown 10 minutes", 600) }
    @Test fun `remind me in 45 seconds is 45 seconds`() { assertTimerSeconds("remind me in 45 seconds", 45) }
    @Test fun `set a timer for 1 hour is 3600 seconds`() { assertTimerSeconds("set a timer for 1 hour", 3600) }

    // ---- Invalid values: recognized syntax, bad value -> Error (not Empty, not Act) ----

    private fun assertInvalidError(query: String) {
        val out = router.route(query)
        assertTrue("Expected Error for '$query', got $out", out is ResolutionOutcome.Error)
    }

    @Test fun `timer 0 minutes invalid`() { assertInvalidError("timer 0 minutes") }
    @Test fun `timer 25 hours invalid`() { assertInvalidError("timer 25 hours") }
    @Test fun `timer -1 minutes invalid`() { assertInvalidError("timer -1 minutes") }
    @Test fun `timer 10 lightyears invalid unit`() { assertInvalidError("timer 10 lightyears") }
    @Test fun `set a timer for 0 mins invalid`() { assertInvalidError("set a timer for 0 mins") }

    @Test fun `L1 direct - malformed missing unit is Invalid not Unrecognized`() {
        // "timer 10" — number present but no unit: recognized syntax, malformed value
        val r = l1.parse("timer 10".lowercase(), "timer 10")
        assertTrue(r is L1Result.Invalid)
    }

    @Test fun `malformed explicit timer text is Invalid not silently Empty`() {
        // "timer please" names the timer intent but has no parseable duration ->
        // recognized operation with invalid parameters -> Error (per Invalid semantics)
        val out = router.route("timer please")
        assertTrue("Expected Error for 'timer please', got $out", out is ResolutionOutcome.Error)
    }

    @Test fun `non-timer queries remain unrecognized by timer grammar`() {
        assertTrue(l1.parse("alarm 6:30".lowercase(), "alarm 6:30") is L1Result.Unrecognized)
    }

    // ---- UI state invariants unchanged ----

    @Test fun `timer queries produce only existing CommandStates`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        val queries = listOf(
            "set a timer for 10 minutes",
            "timer 10 min",
            "timer for 10 minutes",
            "timer 0 minutes",
            "timer please"
        )
        queries.forEach { q ->
            val cmd = router.route(q).toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }

    @Test fun `resolved timer carries undoable flag and SetTimer action`() {
        val out = router.route("set a timer for 10 minutes")
        val result = (out as ResolutionOutcome.Act).result
        assertEquals(ResultType.Timer, result.type)
        assertTrue(result.undoable)
        assertTrue(result.action is AuraAction.SetTimer)
    }
}
