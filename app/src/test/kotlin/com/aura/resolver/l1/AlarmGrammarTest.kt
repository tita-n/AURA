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

    // --- Phase 4A: additional deterministic alarm forms ---
    @Test fun `alarm 6am is hour 6`() {
        val r = grammar.parse("alarm 6am".lowercase(), "alarm 6am") as L1Result.Resolved
        val a = r.result.action as com.aura.domain.AuraAction.SetAlarm
        assertEquals(6, a.hour); assertEquals(0, a.minute)
    }
    @Test fun `alarm 6 30pm is 18 30`() {
        val r = grammar.parse("alarm 6:30pm".lowercase(), "alarm 6:30pm") as L1Result.Resolved
        val a = r.result.action as com.aura.domain.AuraAction.SetAlarm
        assertEquals(18, a.hour); assertEquals(30, a.minute)
    }
    @Test fun `24h 18 30`() {
        val r = grammar.parse("18:30".lowercase(), "18:30") as L1Result.Resolved
        val a = r.result.action as com.aura.domain.AuraAction.SetAlarm
        assertEquals(18, a.hour); assertEquals(30, a.minute)
    }
    @Test fun `wake me at 7 is hour 7`() {
        val r = grammar.parse("wake me at 7".lowercase(), "wake me at 7") as L1Result.Resolved
        val a = r.result.action as com.aura.domain.AuraAction.SetAlarm
        assertEquals(7, a.hour); assertEquals(0, a.minute)
    }
    @Test fun `wake me tomorrow at 6 30am`() {
        val r = grammar.parse("wake me tomorrow at 6:30am".lowercase(), "wake me tomorrow at 6:30am") as L1Result.Resolved
        val a = r.result.action as com.aura.domain.AuraAction.SetAlarm
        assertEquals(6, a.hour); assertEquals(30, a.minute)
    }
    @Test fun `set an alarm for 6 30`() {
        val r = grammar.parse("set an alarm for 6:30".lowercase(), "set an alarm for 6:30") as L1Result.Resolved
        assertTrue(r.result.action is com.aura.domain.AuraAction.SetAlarm)
    }
    @Test fun `invalid hour 25 still invalid`() { assertTrue(grammar.parse("alarm 25:00".lowercase(), "alarm 25:00") is L1Result.Invalid) }
    @Test fun `invalid minute 99 still invalid`() { assertTrue(grammar.parse("alarm 6:99".lowercase(), "alarm 6:99") is L1Result.Invalid) }
    @Test fun `ambiguous space separated time is unrecognized`() {
        // "6 30" (space, no colon) is ambiguous -> not silently resolved
        assertTrue(grammar.parse("alarm 6 30".lowercase(), "alarm 6 30") is L1Result.Unrecognized)
    }
}
