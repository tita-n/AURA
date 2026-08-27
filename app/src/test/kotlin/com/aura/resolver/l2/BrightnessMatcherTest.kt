package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.resolver.Normalizer
import org.junit.Assert.*
import org.junit.Test

class BrightnessMatcherTest {
    private val m = BrightnessMatcher()
    private fun match(q: String) = m.match(Normalizer.normalize(q), q)

    @Test fun `brightness 0 percent opens display settings`() {
        val r = match("brightness 0%") as L2Result.Resolved
        val a = r.result.action
        assertTrue(a is AuraAction.OpenSettings && (a as AuraAction.OpenSettings).panel == "display")
    }

    @Test fun `brightness 50 opens display settings`() {
        val r = match("brightness 50") as L2Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenSettings)
    }

    @Test fun `brightness 100 percent opens display settings`() {
        val r = match("brightness 100%") as L2Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenSettings)
    }

    @Test fun `increase brightness opens display settings`() {
        val r = match("increase brightness") as L2Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenSettings)
    }

    @Test fun `brightness settings opens display settings`() {
        val r = match("brightness settings") as L2Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenSettings)
    }

    @Test fun `invalid 150 percent is rejected`() {
        assertTrue(match("brightness 150%") is L2Result.Invalid)
    }

    @Test fun `non brightness is unrecognized`() {
        assertTrue(match("open wifi") is L2Result.Unrecognized)
    }
}
