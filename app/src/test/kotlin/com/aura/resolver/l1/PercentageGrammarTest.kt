package com.aura.resolver.l1

import org.junit.Assert.*
import org.junit.Test

class PercentageGrammarTest {
    private val grammar = PercentageGrammar()
    @Test fun `standard percentage`() { val r = grammar.parse("17% of 450000".lowercase(), "17% of 450000"); assertTrue(r is L1Result.Resolved); assertEquals("76500", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `another percentage`() { val r = grammar.parse("10% of 5000".lowercase(), "10% of 5000"); assertEquals("500", (r as L1Result.Resolved).result.inlineValue) }
    @Test fun `whitespace variation`() { val r = grammar.parse("  17%   of   450000  ".lowercase(), "  17%   of   450000  "); assertTrue(r is L1Result.Resolved) }
    @Test fun `malformed percentage is unrecognized`() { val r = grammar.parse("17% 450000".lowercase(), "17% 450000"); assertTrue(r is L1Result.Unrecognized) }
    @Test fun `not percentage is unrecognized`() { val r = grammar.parse("hello".lowercase(), "hello"); assertTrue(r is L1Result.Unrecognized) }
}
