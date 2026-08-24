package com.aura.resolver.l1

import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class EmailGrammarTest {
    private val index = L0IndexFactory.demoIndex()
    private val grammar = EmailGrammar(index)

    @Test fun `unique recipient`() {
        val r = grammar.parse("email dad".lowercase(), "email dad")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `duplicate recipient`() {
        val r = grammar.parse("email sarah".lowercase(), "email Sarah")
        assertTrue(r is L1Result.Ambiguous)
    }
    @Test fun `missing recipient`() {
        val r = grammar.parse("email unknownxyz".lowercase(), "email unknownxyz")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `send email to`() {
        val r = grammar.parse("send an email to dad".lowercase(), "send an email to dad")
        assertTrue(r is L1Result.Resolved)
    }
}
