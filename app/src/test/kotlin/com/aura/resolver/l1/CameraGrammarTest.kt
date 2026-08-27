package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResultType
import org.junit.Assert.*
import org.junit.Test

class CameraGrammarTest {
    private val g = CameraGrammar()

    @Test fun `open camera`() {
        val r = g.parse("open camera", "open camera") as L1Result.Resolved
        assertEquals(ResultType.Camera, r.result.type)
        assertTrue(r.result.action is AuraAction.OpenCamera)
    }

    @Test fun `launch camera`() {
        val r = g.parse("launch camera", "launch camera") as L1Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenCamera)
    }

    @Test fun `bare camera`() {
        val r = g.parse("camera", "camera") as L1Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenCamera)
    }

    @Test fun `take a photo`() {
        val r = g.parse("take a photo", "take a photo") as L1Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenCamera)
    }

    @Test fun `selfie`() {
        val r = g.parse("selfie", "selfie") as L1Result.Resolved
        assertTrue(r.result.action is AuraAction.OpenCamera)
    }

    @Test fun `unknown camera action is unrecognized`() {
        assertTrue(g.parse("camera zoom", "camera zoom") is L1Result.Unrecognized)
        assertTrue(g.parse("camera settings", "camera settings") is L1Result.Unrecognized)
    }

    @Test fun `does not hijack non camera commands`() {
        assertTrue(g.parse("open chrome", "open chrome") is L1Result.Unrecognized)
    }
}
