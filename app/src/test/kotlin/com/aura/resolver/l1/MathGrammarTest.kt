package com.aura.resolver.l1

import com.aura.domain.ResultType
import org.junit.Assert.*
import org.junit.Test

class MathGrammarTest {
    private val grammar = MathGrammar()

    @Test fun `addition`() { val r = grammar.parse("17 + 4".lowercase(), "17 + 4"); assertTrue(r is L1Result.Resolved); assertEquals("21", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `subtraction`() { val r = grammar.parse("500 - 27".lowercase(), "500 - 27"); assertEquals("473", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `multiplication`() { val r = grammar.parse("500 * 27".lowercase(), "500 * 27"); assertEquals("13500", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `division`() { val r = grammar.parse("500 / 27".lowercase(), "500 / 27"); assertTrue(r is L1Result.Resolved) }
    @Test fun `precedence`() { val r = grammar.parse("2 + 3 * 4".lowercase(), "2 + 3 * 4"); assertEquals("14", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `parentheses`() { val r = grammar.parse("(500 + 27) * 2".lowercase(), "(500 + 27) * 2"); assertEquals("1054", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `whitespace variation`() { val r = grammar.parse("  500   *   27  ".lowercase(), "  500   *   27  "); assertEquals("13500", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `malformed expression is invalid`() { val r = grammar.parse("500 *".lowercase(), "500 *"); assertTrue(r is L1Result.Invalid) }
    @Test fun `divide by zero is invalid`() { val r = grammar.parse("500 / 0".lowercase(), "500 / 0"); assertTrue(r is L1Result.Invalid) }
    @Test fun `not math is unrecognized`() { val r = grammar.parse("hello world".lowercase(), "hello world"); assertTrue(r is L1Result.Unrecognized) }
    @Test fun `just number is unrecognized`() { val r = grammar.parse("500".lowercase(), "500"); assertTrue(r is L1Result.Unrecognized) }
    @Test fun `preserves original query as inlineQuery`() {
        val raw = "500 * 27"
        val r = grammar.parse(raw.lowercase(), raw) as L1Result.Resolved
        assertEquals(raw, r.result.inlineQuery)
        assertEquals(ResultType.Math, r.result.type)
    }
}
