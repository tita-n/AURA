package com.aura.resolver.l1

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class UnitGrammarTest {
    private val grammar = UnitGrammar()
    @Test fun `km to miles`() {
        val r = grammar.parse("10 km in miles".lowercase(), "10 km in miles")
        assertTrue(r is L1Result.Resolved)
        val v = (r as L1Result.Resolved).result.inlineValue!!.toDouble()
        assertTrue(abs(v - 6.2137) < 0.01)
    }
    @Test fun `kg to pounds`() {
        val r = grammar.parse("5 kg in pounds".lowercase(), "5 kg in pounds")
        assertTrue(r is L1Result.Resolved)
        val v = (r as L1Result.Resolved).result.inlineValue!!.toDouble()
        assertTrue(abs(v - 11.0231) < 0.01)
    }
    @Test fun `c to f`() {
        val r = grammar.parse("100 c in f".lowercase(), "100 C in F")
        assertTrue(r is L1Result.Resolved)
        val v = (r as L1Result.Resolved).result.inlineValue!!.toDouble()
        assertTrue(abs(v - 212.0) < 0.01)
    }
    @Test fun `capitalization`() {
        val r = grammar.parse("10 KM IN MILES".lowercase(), "10 KM IN MILES")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `whitespace to variant`() {
        val r = grammar.parse("10 km to miles".lowercase(), "10 km to miles")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `unsupported unit is invalid`() {
        val r = grammar.parse("10 km in lightyears".lowercase(), "10 km in lightyears")
        assertTrue(r is L1Result.Invalid)
    }
    @Test fun `malformed conversion is unrecognized`() {
        val r = grammar.parse("10 km miles".lowercase(), "10 km miles")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `incompatible units invalid`() {
        val r = grammar.parse("10 km in pounds".lowercase(), "10 km in pounds")
        assertTrue(r is L1Result.Invalid)
    }
}
