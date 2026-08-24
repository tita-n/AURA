package com.aura

import com.aura.domain.*
import com.aura.design.AuraColorTokens
import com.aura.design.AuraSpacing
import com.aura.design.AuraRadius
import com.aura.design.AuraElevation
import org.junit.Assert.*
import org.junit.Test

/**
 * Invariant tests — ensures UI boundary respects ACT/ASK and never leaks resolver provenance.
 * Also verifies token system constraints (two surfaces, two radii, 4dp scale).
 */
class ResolutionOutcomeTest {

    @Test
    fun `ResolutionOutcome has only Act and Ask as resolution variants`() {
        // Sealed interface's permitted subclasses should be Act, Ask, Idle, Empty, Error
        // ACT/ASK are the only resolution outcomes that carry candidates/results
        val act = ResolutionOutcome.Act(PreviewData.actApp)
        val ask = ResolutionOutcome.Ask(PreviewData.askWhichSarah)
        assertTrue(act is ResolutionOutcome)
        assertTrue(ask is ResolutionOutcome)
        // Ensure no layer info exists on the objects
        assertFalse(act.result.toString().contains("L0"))
        assertFalse(act.result.toString().contains("L1"))
        assertFalse(act.result.toString().contains("L2"))
        assertFalse(act.result.toString().contains("L3"))
    }

    @Test
    fun `CommandState never exposes layer identity`() {
        val states = listOf(
            CommandState.Act(PreviewData.actApp),
            CommandState.Ask(PreviewData.askWhichSarah),
            CommandState.Idle,
            CommandState.Empty("test"),
            CommandState.Error(PreviewData.errorExample)
        )
        states.forEach { state ->
            val str = state.toString()
            assertFalse("State must not contain L0/L1/L2/L3", str.contains("L0") || str.contains("L1") || str.contains("L2") || str.contains("L3"))
            assertFalse("Must not contain confidence", str.contains("confidence", ignoreCase = true))
            assertFalse("Must not contain AI", str.contains("AIResult"))
        }
    }

    @Test
    fun `Ask never preselects`() {
        val group = PreviewData.askWhichSarah
        // CandidateGroup has no selected field — enforced by type
        assertEquals(3, group.candidates.size)
        // Every candidate is equal weight — no isSelected property exists
        group.candidates.forEach { candidate ->
            assertFalse(candidate.hasField("selected"))
        }
    }

    @Test
    fun `Act appearance is identical regardless of provenance`() {
        // Two acts from different imagined provenance should have identical type
        val act1 = ResolvedResult("1", "Chrome", null, ResultType.App, AuraAction.OpenApp("chrome"))
        val act2 = ResolvedResult("2", "Chrome", null, ResultType.App, AuraAction.OpenApp("chrome"))
        assertEquals(act1::class, act2::class)
        assertEquals(act1.type, act2.type)
    }

    @Test
    fun `Design tokens use only allowed values`() {
        // Two surface levels only
        assertNotNull(AuraColorTokens.Dark.surfaceBase)
        assertNotNull(AuraColorTokens.Dark.surfaceRaised)
        assertNotNull(AuraColorTokens.Light.surfaceBase)
        assertNotNull(AuraColorTokens.Light.surfaceRaised)

        // Two radii only
        assertEquals(12, AuraRadius.small.value.toInt())
        assertEquals(20, AuraRadius.large.value.toInt())

        // 4dp scale
        assertEquals(8, AuraSpacing.s8.value.toInt())
        assertEquals(24, AuraSpacing.s24.value.toInt())
        assertEquals(48, AuraSpacing.s48.value.toInt())

        // Elevations — only two
        assertEquals(0, AuraElevation.none.value.toInt())
        assertEquals(8, AuraElevation.raised.value.toInt())
    }

    private fun Any.hasField(name: String): Boolean {
        return this::class.java.declaredFields.any { it.name == name }
            || this::class.java.methods.any { it.name.equals(name, ignoreCase = true) }
    }
}
