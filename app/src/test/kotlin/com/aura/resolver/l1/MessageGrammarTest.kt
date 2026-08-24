package com.aura.resolver.l1

import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class MessageGrammarTest {
    private val index = L0IndexFactory.demoIndex()
    private val grammar = MessageGrammar(index)

    @Test fun `unique recipient to Act`() {
        val r = grammar.parse("message dad".lowercase(), "message dad")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `duplicate recipient to Ask`() {
        val r = grammar.parse("message sarah".lowercase(), "message Sarah")
        assertTrue(r is L1Result.Ambiguous)
    }
    @Test fun `structured message extraction`() {
        // Use unique recipient Dad to avoid ASK ambiguity (Sarah has 2 contacts in demo index)
        val r = grammar.parse("tell dad i'll be there in 20".lowercase(), "tell Dad I'll be there in 20")
        assertTrue(r is L1Result.Resolved)
        val res = (r as L1Result.Resolved).result
        assertTrue(res.action.toString().contains("I'll be there in 20"))
    }
    @Test fun `missing recipient to Unrecognized`() {
        val r = grammar.parse("message unknownxyz".lowercase(), "message unknownxyz")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `malformed without recipient`() {
        val r = grammar.parse("message".lowercase(), "message")
        assertTrue(r is L1Result.Unrecognized || r is L1Result.Invalid)
    }
}
