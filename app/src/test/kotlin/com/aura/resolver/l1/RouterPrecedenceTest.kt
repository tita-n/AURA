package com.aura.resolver.l1

import com.aura.domain.CommandState
import com.aura.domain.ResolutionOutcome
import com.aura.domain.toCommandState
import com.aura.resolver.*
import org.junit.Assert.*
import org.junit.Test

class RouterPrecedenceTest {
    private val index = L0IndexFactory.demoIndex()
    private val routerWithL1 = IntentRouter(L0Resolver(index), L1Resolver(index))
    private val routerL0Only = IntentRouter(L0Resolver(index), null)

    @Test fun `L0 wins over L1`() {
        // "chrome" is L0 exact, should not be handled by L1 open grammar
        val out = routerWithL1.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `L1 only runs after L0 unresolved`() {
        // "500 * 27" is not an L0 entity, so L0 Empty, then L1 Math should Act
        val out = routerWithL1.route("500 * 27")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("13500", (out as ResolutionOutcome.Act).result.inlineValue)
        // L0 only router would give Empty
        assertTrue(routerL0Only.route("500 * 27") is ResolutionOutcome.Empty)
    }

    @Test fun `L1 Act maps to existing Act state`() {
        val out = routerWithL1.route("alarm 6:30")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue(out.toCommandState() is CommandState.Act)
    }

    @Test fun `L1 Ask maps to existing Ask state`() {
        val out = routerWithL1.route("call sarah")
        assertTrue(out is ResolutionOutcome.Ask)
        assertTrue(out.toCommandState() is CommandState.Ask)
    }

    @Test fun `unresolved does not become Error`() {
        val out = routerWithL1.route("what is the meaning of life bro")
        assertTrue(out is ResolutionOutcome.Empty)
        assertFalse(out is ResolutionOutcome.Error)
    }

    @Test fun `no provenance reaches UI`() {
        val act = routerWithL1.route("chrome")
        val ask = routerWithL1.route("call sarah")
        listOf(act, ask).forEach { o ->
            val s = o.toString().lowercase()
            assertFalse(s.contains("l0"))
            assertFalse(s.contains("l1"))
            assertFalse(s.contains("confidence"))
        }
    }

    @Test fun `no confidence reaches UI`() {
        val out = routerWithL1.route("500 * 27") as ResolutionOutcome.Act
        assertFalse(out.result.toString().lowercase().contains("confidence"))
        assertFalse(out.toCommandState().toString().lowercase().contains("confidence"))
    }

    @Test fun `open chrome via L1 when L0 would not exact match open chrome`() {
        // L0 exact for "open chrome" is none, so L1 Open should handle
        val out = routerWithL1.route("open chrome")
        assertTrue(out is ResolutionOutcome.Act)
    }

    @Test fun `settings via L1`() {
        val out = routerWithL1.route("bluetooth settings")
        assertTrue(out is ResolutionOutcome.Act)
    }
}
