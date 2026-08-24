package com.aura.resolver.l1

import org.junit.Assert.*
import org.junit.Test

class TimerGrammarTest {
    private val grammar = TimerGrammar()
    @Test fun `seconds`() { val r = grammar.parse("timer 30 seconds".lowercase(), "timer 30 seconds"); assertTrue(r is L1Result.Resolved) }
    @Test fun `minutes`() { val r = grammar.parse("timer 10 min".lowercase(), "timer 10 min"); assertTrue(r is L1Result.Resolved) }
    @Test fun `hours`() { val r = grammar.parse("timer 1 hour".lowercase(), "timer 1 hour"); assertTrue(r is L1Result.Resolved) }
    @Test fun `malformed duration missing unit`() { val r = grammar.parse("timer 10".lowercase(), "timer 10"); assertTrue(r is L1Result.Invalid) }
    @Test fun `not timer is unrecognized`() { val r = grammar.parse("alarm 10 min".lowercase(), "alarm 10 min"); assertTrue(r is L1Result.Unrecognized) }
    @Test fun `zero duration invalid`() { val r = grammar.parse("timer 0 min".lowercase(), "timer 0 min"); assertTrue(r is L1Result.Invalid) }
}
