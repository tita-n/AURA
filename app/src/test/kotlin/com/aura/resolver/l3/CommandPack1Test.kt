package com.aura.resolver.l3

import com.aura.domain.AuraAction
import com.aura.domain.ResolutionOutcome
import com.aura.domain.ResultType
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.L0Resolver
import com.aura.resolver.l1.L1Resolver
import com.aura.resolver.l2.L2Resolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 4A Command Pack 1 — end-to-end resolution through the real L0→L1→L2→L3 pipeline.
 * Verifies each new family resolves to the correct validated action, that unsupported
 * capabilities fail honestly, and that pre-existing commands are not regressed.
 */
class CommandPack1Test {
    private val index = L0IndexFactory.demoIndex()
    private val router = IntentRouter(
        L0Resolver(index),
        L1Resolver(index),
        L2Resolver(index),
        L3Validator(index)
    )

    private fun act(q: String): ResolutionOutcome.Act {
        val out = router.route(q)
        assertTrue("'$q' expected Act but got $out", out is ResolutionOutcome.Act)
        return out as ResolutionOutcome.Act
    }

    // ---- ALARM ----
    @Test fun `alarm 6 30 resolves to SetAlarm`() {
        assertTrue(act("alarm 6:30").result.action is AuraAction.SetAlarm)
    }

    // ---- CAMERA ----
    @Test fun `open camera resolves to OpenCamera`() {
        assertTrue(act("open camera").result.action is AuraAction.OpenCamera)
    }

    @Test fun `camera resolves via L0 app or OpenCamera`() {
        assertTrue(router.route("camera") is ResolutionOutcome.Act)
    }

    // ---- TIME / DATE ----
    @Test fun `what time is it resolves to Time inline`() {
        val r = act("what time is it")
        assertEquals(ResultType.Time, r.result.type)
        assertNotNull(r.result.inlineValue)
    }

    @Test fun `what is today's date resolves to Date inline`() {
        assertEquals(ResultType.Date, act("what is today's date").result.type)
    }

    @Test fun `what day is it resolves to Date inline`() {
        assertEquals(ResultType.Date, act("what day is it").result.type)
    }

    // ---- SCREENSHOT (unsupported, honest) ----
    @Test fun `screenshot resolves to Error with no false success`() {
        assertTrue(router.route("screenshot") is ResolutionOutcome.Error)
    }

    // ---- BRIGHTNESS (honest fallback) ----
    @Test fun `brightness 70 percent resolves to display settings`() {
        val a = act("brightness 70%").result.action
        assertTrue(a is AuraAction.OpenSettings && (a as AuraAction.OpenSettings).panel == "display")
    }

    @Test fun `brightness 150 percent is rejected honestly`() {
        // Out-of-range brightness is rejected, never silently applied.
        assertTrue(router.route("brightness 150%") is ResolutionOutcome.Error)
    }

    // ---- SYSTEM SETTINGS (expanded catalog) ----
    @Test fun `open wifi settings`() {
        assertTrue(act("open wifi settings").result.action is AuraAction.OpenSettings)
    }

    @Test fun `open accessibility settings`() {
        assertTrue(act("open accessibility settings").result.action is AuraAction.OpenSettings)
    }

    @Test fun `open location settings`() {
        assertTrue(act("open location settings").result.action is AuraAction.OpenSettings)
    }

    @Test fun `open date and time settings`() {
        assertTrue(act("open date and time settings").result.action is AuraAction.OpenSettings)
    }

    @Test fun `unknown setting is empty`() {
        assertTrue(router.route("open telepathy settings") is ResolutionOutcome.Empty)
    }

    // ---- REMINDER ----
    @Test fun `remind me to call mum at 3pm resolves to SetReminder`() {
        assertTrue(act("remind me to call mum at 3pm").result.action is AuraAction.SetReminder)
    }

    // ---- REGRESSIONS ----
    @Test fun `chrome still resolves`() {
        assertTrue(router.route("chrome") is ResolutionOutcome.Act)
    }

    @Test fun `call dad still resolves`() {
        assertTrue(router.route("call dad") is ResolutionOutcome.Act)
    }

    @Test fun `timer still resolves`() {
        assertTrue(router.route("timer 10 min") is ResolutionOutcome.Act)
    }

    @Test fun `calculator still resolves`() {
        assertTrue(router.route("500 * 27") is ResolutionOutcome.Act)
    }

    @Test fun `reminder still not confused with timer`() {
        // "remind me in 10 minutes" must resolve to a timer, not a reminder
        val out = router.route("remind me in 10 minutes")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue((out as ResolutionOutcome.Act).result.action is AuraAction.SetTimer)
    }
}
