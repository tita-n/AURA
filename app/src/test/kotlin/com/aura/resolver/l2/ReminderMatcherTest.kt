package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResultType
import com.aura.resolver.Normalizer
import org.junit.Assert.*
import org.junit.Test

class ReminderMatcherTest {
    private val m = ReminderMatcher()
    private fun match(q: String) = m.match(Normalizer.normalize(q), q)

    @Test fun `remind me to call mum at 3pm`() {
        val r = match("remind me to call mum at 3pm") as L2Result.Resolved
        val a = r.result.action as AuraAction.SetReminder
        assertEquals("call mum", a.title)
        assertEquals(15, a.hour)
        assertEquals(0, a.minute)
        assertEquals(ResultType.Reminder, r.result.type)
    }

    @Test fun `remind me tomorrow at 9am`() {
        val r = match("remind me tomorrow at 9am") as L2Result.Resolved
        val a = r.result.action as AuraAction.SetReminder
        assertEquals(9, a.hour)
        assertEquals(1, a.dayOffsetDays)
    }

    @Test fun `remind me at 3pm to call mum`() {
        val r = match("remind me at 3pm to call mum") as L2Result.Resolved
        val a = r.result.action as AuraAction.SetReminder
        assertEquals("call mum", a.title)
        assertEquals(15, a.hour)
    }

    @Test fun `remind me to call mum tomorrow at 9am`() {
        val r = match("remind me to call mum tomorrow at 9am") as L2Result.Resolved
        val a = r.result.action as AuraAction.SetReminder
        assertEquals("call mum", a.title)
        assertEquals(9, a.hour)
        assertEquals(1, a.dayOffsetDays)
    }

    @Test fun `reminder without time is invalid`() {
        assertTrue(match("remind me to call mum") is L2Result.Invalid)
    }

    @Test fun `remind me in 10 minutes is not a reminder (timer territory)`() {
        // Handled by TimerMatcher, not ReminderMatcher
        assertTrue(match("remind me in 10 minutes") is L2Result.Unrecognized)
    }

    @Test fun `invalid hour is rejected`() {
        assertTrue(match("remind me to call mum at 25pm") is L2Result.Invalid)
    }
}
