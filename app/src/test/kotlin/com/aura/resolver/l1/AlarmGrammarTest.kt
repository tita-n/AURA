package com.aura.resolver.l1

import com.aura.domain.ResultType
import org.junit.Assert.*
import org.junit.Test

class AlarmGrammarTest {
    private val grammar = AlarmGrammar()
    @Test fun `alarm 6 30`() { val r = grammar.parse("alarm 6:30".lowercase(), "alarm 6:30"); assertTrue(r is L1Result.Resolved); assertEquals(ResultType.Alarm, (r as L1Result.Resolved).result.type) }
    @Test fun `wake me at 6 30`() { val r = grammar.parse("wake me at 6:30".lowercase(), "wake me at 6:30"); assertTrue(r is L1Result.Resolved) }
    @Test fun `alarm with am`() { val r = grammar.parse("alarm 6:30 am".lowercase(), "alarm 6:30 am") as L1Result.Resolved; assertTrue(r.result.title.contains("6:30")) }
    @Test fun `alarm with pm`() { val r = grammar.parse("alarm 6:30 pm".lowercase(), "alarm 6:30 pm") as L1Result.Resolved; assertTrue(r.result.title.contains("18:30")) }
    @Test fun `invalid hour 25`() { val r = grammar.parse("alarm 25:00".lowercase(), "alarm 25:00"); assertTrue(r is L1Result.Invalid) }
    @Test fun `invalid minute 99`() { val r = grammar.parse("alarm 6:99".lowercase(), "alarm 6:99"); assertTrue(r is L1Result.Invalid) }
    @Test fun `malformed time missing colon`() { val r = grammar.parse("alarm 630".lowercase(), "alarm 630"); assertTrue(r is L1Result.Unrecognized) }
    @Test fun `not alarm is unrecognized`() { val r = grammar.parse("hello".lowercase(), "hello"); assertTrue(r is L1Result.Unrecognized) }
}
