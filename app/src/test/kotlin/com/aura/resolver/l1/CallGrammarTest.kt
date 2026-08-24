package com.aura.resolver.l1

import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class CallGrammarTest {
    private val index = L0IndexFactory.demoIndex()
    private val grammar = CallGrammar(index)

    @Test fun `unique contact to Act`() {
        val r = grammar.parse("call dad".lowercase(), "call dad")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `duplicate contact to Ask`() {
        val r = grammar.parse("call sarah".lowercase(), "call Sarah")
        assertTrue(r is L1Result.Ambiguous)
        assertTrue((r as L1Result.Ambiguous).group.label.contains("Which"))
    }
    @Test fun `missing contact to Unrecognized`() {
        val r = grammar.parse("call unknownxyz".lowercase(), "call unknownxyz")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `case normalization`() {
        val r = grammar.parse("call DAD".lowercase(), "call DAD")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `not call is unrecognized`() {
        val r = grammar.parse("message sarah".lowercase(), "message sarah")
        assertTrue(r is L1Result.Unrecognized)
    }
}
