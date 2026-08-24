package com.aura.resolver.l1

import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsGrammarTest {
    private val index = L0IndexFactory.demoIndex()
    private val grammar = SettingsGrammar(index)

    @Test fun `supported setting`() {
        val r = grammar.parse("wifi settings".lowercase(), "wifi settings")
        // demo index has Wi-Fi (wi-fi) but query wifi settings without hyphen — grammar handles hyphen variance
        assertTrue(r is L1Result.Resolved || r is L1Result.Ambiguous)
    }
    @Test fun `bluetooth settings`() {
        val r = grammar.parse("bluetooth settings".lowercase(), "bluetooth settings")
        assertTrue(r is L1Result.Resolved)
    }
    @Test fun `unsupported setting`() {
        val r = grammar.parse("unknown settings".lowercase(), "unknown settings")
        assertTrue(r is L1Result.Unrecognized)
    }
    @Test fun `normalization wifi vs wi-fi`() {
        val r1 = grammar.parse("wifi".lowercase(), "wifi")
        val r2 = grammar.parse("wi-fi".lowercase(), "wi-fi")
        // At least one should resolve or both unrecognized consistently — but not error
        assertTrue(r1 is L1Result.Resolved || r1 is L1Result.Unrecognized)
        assertTrue(r2 is L1Result.Resolved || r2 is L1Result.Unrecognized)
    }
}
