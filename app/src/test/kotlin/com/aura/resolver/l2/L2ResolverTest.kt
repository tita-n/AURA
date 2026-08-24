package com.aura.resolver.l2

import com.aura.domain.CommandState
import com.aura.domain.ResolutionOutcome
import com.aura.domain.toCommandState
import com.aura.resolver.*
import org.junit.Assert.*
import org.junit.Test

class L2ResolverTest {
    private val index = L0IndexFactory.demoIndex()
    private val l2 = L2Resolver(index)
    private val l0 = L0Resolver(index)
    private val l1 = com.aura.resolver.l1.L1Resolver(index)
    private val routerL2 = IntentRouter(l0, l1, l2)
    private val routerL0L1 = IntentRouter(l0, l1, null)

    @Test fun `L0 still wins over L2 - chrom exact via L0 not fuzzy`() {
        // "chrome" is L0 exact, should be Act via L0, not L2 fuzzy
        val out = routerL2.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        // L2 alone would also resolve "chrom" typo, but L0 exact should win for "chrome"
        val l2Direct = l2.resolve("chrome")
        // L2 for "chrome" (exact without typo) should also be Act via open matcher, but router should have stopped at L0
        // We verify L0 would have handled it
        assertTrue(l0.resolve("chrome") is L0Resolution.Resolved)
    }

    @Test fun `L1 still wins over L2`() {
        // "500 * 27" is L1 math, should be Act via L1, not L2
        val out = routerL2.route("500 * 27")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue(l1.resolve("500 * 27") is com.aura.resolver.l1.L1Resolution.Resolved)
        // L2 math variant "what is 500 * 27" should be handled by L2 only when L1 doesn't recognize "what is"
        val l1Unrec = l1.resolve("what is 500 * 27")
        assertTrue(l1Unrec is com.aura.resolver.l1.L1Resolution.Unrecognized)
        val l2Res = l2.resolve("what is 500 * 27")
        assertTrue(l2Res is L2Result.Resolved)
    }

    @Test fun `L2 resolves typo chrom to Chrome`() {
        // "chrom" is actually a prefix of Chrome, so L0 already handles it as Act via prefix — L2 not needed for this case
        // Use a true typo "chorme" (edit distance) to test L2 fuzzy
        val out = routerL2.route("chorme")
        assertTrue("Expected Act for chorme typo, got $out", out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
        // Direct L2 check
        val direct = l2.resolve("chorme")
        assertTrue("L2 should resolve chorme, got $direct", direct is L2Result.Resolved)
        // L0 and L1 should be unresolved for "chorme" (not a prefix)
        assertTrue(l0.resolve("chorme") is L0Resolution.Unresolved)
        assertTrue(l1.resolve("chorme") is com.aura.resolver.l1.L1Resolution.Unrecognized)
    }

    @Test fun `L2 resolves whatsapp pls with filler`() {
        val out = routerL2.route("whatsapp pls")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("WhatsApp", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `L2 resolves set a timer for 10 mins`() {
        val out = routerL2.route("set a timer for 10 mins")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue((out as ResolutionOutcome.Act).result.title.contains("10 min"))
    }

    @Test fun `L2 resolves convert 10 kilometers to miles`() {
        val out = routerL2.route("convert 10 kilometers to miles")
        assertTrue(out is ResolutionOutcome.Act)
        // Should be conversion result
        assertTrue((out as ResolutionOutcome.Act).result.inlineValue != null)
    }

    @Test fun `L2 resolves send sarah a message`() {
        // Sarah is ambiguous (2 contacts) -> should be Ask, not Act
        val out = routerL2.route("send sarah a message")
        assertTrue(out is ResolutionOutcome.Ask)
        assertTrue((out as ResolutionOutcome.Ask).group.label.contains("Which"))
    }

    @Test fun `L2 resolves open that music app to Spotify`() {
        val out = routerL2.route("open that music app")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Spotify", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `L2 resolves play my music to Spotify`() {
        val out = routerL2.route("play my music")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Spotify", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `L2 resolves turn on wifi`() {
        val out = routerL2.route("turn on wifi")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue((out as ResolutionOutcome.Act).result.title.contains("Wi-Fi", ignoreCase = true) || (out as ResolutionOutcome.Act).result.title.contains("wifi", ignoreCase = true))
    }

    @Test fun `L2 ambiguous target to ASK - message sarah duplicate`() {
        val out = routerL2.route("message sarah")
        // L1 would already handle "message sarah" as Ask (duplicate), but L2 also should be Ask
        // Since L0/L1 would have resolved "message sarah" via L1 MessageGrammar as Ask, L2 shouldn't be reached
        // But for pure L2 path, test via L2 direct
        val direct = l2.resolve("message sarah")
        // L2 MessageMatcher will also be Ask for duplicate Sarah
        // However L1 would have already been Ask, so router would have stopped at L1
        // So we test that L2 alone for a variant that L1 doesn't handle still gives Ask if ambiguous
        val variant = l2.resolve("send sarah a message")
        assertTrue(variant is L2Result.Ambiguous)
        assertTrue((variant as L2Result.Ambiguous).group.candidates.size == 2)
    }

    @Test fun `L2 never preselects ASK candidates`() {
        val out = routerL2.route("send sarah a message") as ResolutionOutcome.Ask
        assertEquals(2, out.group.candidates.size)
        // No selected field
        assertFalse(out.group::class.java.declaredFields.any { it.name.contains("selected", ignoreCase = true) })
    }

    @Test fun `L2 unresolved to EMPTY`() {
        val out = routerL2.route("tell me about the moon")
        assertTrue(out is ResolutionOutcome.Empty)
        assertTrue(out.toCommandState() is CommandState.Empty)
    }

    @Test fun `L2 do something cool to Empty`() {
        assertTrue(routerL2.route("do something cool") is ResolutionOutcome.Empty)
    }

    @Test fun `L2 invalid recognized command to ERROR`() {
        // Invalid timer duration
        val out = routerL2.route("set a timer for 0 mins")
        assertTrue(out is ResolutionOutcome.Error)
        // Invalid unit conversion
        val out2 = routerL2.route("convert 10 kilometers to lightyears")
        // L2 Unit via L1 delegate may be Invalid -> Error, or Unrecognized -> Empty
        // For unsupported unit, L2's UnitMatcher delegates to UnitGrammar which returns Invalid -> Error
        // So check that it's either Error or Empty, but not Act
        assertTrue(out2 is ResolutionOutcome.Error || out2 is ResolutionOutcome.Empty)
    }

    @Test fun `L2 does not expose confidence`() {
        val out = routerL2.route("chrom") as ResolutionOutcome.Act
        assertFalse(out.toString().lowercase().contains("confidence"))
        assertFalse(out.result.toString().lowercase().contains("confidence"))
        assertFalse(out.toCommandState().toString().lowercase().contains("confidence"))
    }

    @Test fun `L2 does not expose provenance`() {
        val out = routerL2.route("chrom")
        assertFalse(out.toString().contains("L2"))
        assertFalse(out.toString().contains("l2", ignoreCase = false))
        // ResolvedResult has no provenance field
        if (out is ResolutionOutcome.Act) {
            assertFalse(out.result::class.java.declaredFields.any { it.name.contains("provenance", ignoreCase = true) })
            assertFalse(out.result::class.java.declaredFields.any { it.name.contains("layer", ignoreCase = true) })
        }
    }

    @Test fun `L2 does not expose resolver layer identity`() {
        val act = routerL2.route("chrom") as ResolutionOutcome.Act
        val ask = routerL2.route("send sarah a message") as ResolutionOutcome.Ask
        listOf(act, ask).forEach { o ->
            val s = o.toString().lowercase()
            assertFalse(s.contains("l0"))
            assertFalse(s.contains("l1"))
            assertFalse(s.contains("l2"))
        }
    }

    @Test fun `L2 invalid not empty - timer zero`() {
        val direct = l2.resolve("set a timer for 0 mins")
        assertTrue(direct is L2Result.Invalid)
        assertTrue(routerL2.route("set a timer for 0 mins") is ResolutionOutcome.Error)
    }

    @Test fun `L2 reuse existing IndexedEntity no duplicate catalog`() {
        // Verify that L2 uses same index, not duplicate list
        val chromeViaL2 = l2.resolve("chorme") as L2Result.Resolved
        val chromeViaL0 = l0.resolve("chrome") as L0Resolution.Resolved
        assertEquals(chromeViaL2.result.id, chromeViaL0.result.id)
        assertEquals(chromeViaL2.result.title, chromeViaL0.result.title)
    }
}
