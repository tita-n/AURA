package com.aura.resolver.l1

import org.junit.Assert.*
import org.junit.Test

class ScreenshotGrammarTest {
    private val g = ScreenshotGrammar()

    @Test fun `screenshot is recognized but unsupported (Invalid, honest)`() {
        val r = g.parse("screenshot", "screenshot")
        assertTrue(r is L1Result.Invalid)
        assertTrue((r as L1Result.Invalid).message.contains("Power"))
    }

    @Test fun `take a screenshot is unsupported`() {
        val r = g.parse("take a screenshot", "take a screenshot")
        assertTrue(r is L1Result.Invalid)
    }

    @Test fun `capture screen is unsupported`() {
        assertTrue(g.parse("capture screen", "capture screen") is L1Result.Invalid)
    }

    @Test fun `never resolves to a successful action (no false success)`() {
        // There must be no Resolved branch — AURA cannot take a screenshot.
        val r = g.parse("screenshot", "screenshot")
        assertFalse(r is L1Result.Resolved)
    }

    @Test fun `non screenshot is unrecognized`() {
        assertTrue(g.parse("open camera", "open camera") is L1Result.Unrecognized)
    }
}
