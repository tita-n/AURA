package com.aura.resolver.l1

import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class OpenGrammarTest {
    private val index = L0IndexFactory.demoIndex()
    private val grammar = OpenGrammar(index)

    @Test fun `explicit app command`() {
        val r = grammar.parse("open chrome".lowercase(), "open Chrome")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `ambiguous app`() {
        // Create index with duplicate app names
        val dupIndex = com.aura.resolver.L0Index.build(listOf(
            L0IndexFactory.appEntity("com.a.one", "TestApp"),
            L0IndexFactory.appEntity("com.a.two", "TestApp")
        ))
        val dupGrammar = OpenGrammar(dupIndex)
        val r = dupGrammar.parse("open testapp".lowercase(), "open TestApp")
        assertTrue(r is L1Result.Ambiguous)
    }
    @Test fun `missing app`() {
        val r = grammar.parse("open unknownxyz".lowercase(), "open unknownxyz")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `not open is unrecognized`() {
        val r = grammar.parse("chrome".lowercase(), "chrome")
        assertTrue(r is L1Result.Unrecognized)
    }
}
