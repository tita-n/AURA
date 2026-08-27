package com.aura.resolver.l1

import com.aura.domain.ResultType
import com.aura.resolver.Normalizer
import org.junit.Assert.*
import org.junit.Test

class TimeQueryGrammarTest {
    private val g = TimeQueryGrammar()
    private fun parse(q: String) = g.parse(Normalizer.normalize(q), q)

    @Test fun `what time is it resolves to Time`() {
        val r = parse("what time is it") as L1Result.Resolved
        assertEquals(ResultType.Time, r.result.type)
        assertNotNull(r.result.inlineValue)
        assertTrue(r.result.inlineValue!!.matches(Regex(""".*\d{1,2}:\d{2}.*""")))
    }

    @Test fun `what is today's date resolves to Date`() {
        val r = parse("what is today's date") as L1Result.Resolved
        assertEquals(ResultType.Date, r.result.type)
        assertFalse(r.result.inlineValue.isNullOrBlank())
    }

    @Test fun `what day is it resolves to Date`() {
        val r = parse("what day is it") as L1Result.Resolved
        assertEquals(ResultType.Date, r.result.type)
        assertFalse(r.result.inlineValue.isNullOrBlank())
    }

    @Test fun `timezone lagos resolves to Time`() {
        val r = parse("what time is it in lagos") as L1Result.Resolved
        assertEquals(ResultType.Time, r.result.type)
    }

    @Test fun `unknown city is invalid`() {
        assertTrue(parse("what time is it in springfield") is L1Result.Invalid)
    }

    @Test fun `not a time query is unrecognized`() {
        assertTrue(parse("set an alarm for 6") is L1Result.Unrecognized)
        assertTrue(parse("open camera") is L1Result.Unrecognized)
    }

    @Test fun `deterministic output is stable for same instant`() {
        val a = parse("what time is it")
        val b = parse("what time is it")
        assertEquals(a.toString(), b.toString())
    }
}
