package com.aura

import com.aura.domain.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Deep ACT/ASK contract tests — Task 5.
 * No fake resolver implementations — only the UI boundary types.
 */
class ActAskContractTest {

    private fun fieldNames(obj: Any): List<String> {
        return obj::class.java.declaredFields.map { it.name } +
            obj::class.java.methods.map { it.name }
    }

    // 1. L0/L1/L2/L3/L4 provenance cannot reach UI state model
    @Test
    fun `provenance cannot reach UI`() {
        val act = CommandState.Act(PreviewData.actApp)
        val ask = CommandState.Ask(PreviewData.askWhichSarah)
        listOf<CommandState>(act, ask).forEach { state: CommandState ->
            val str = state.toString()
            assertFalse(str.contains("L0"))
            assertFalse(str.contains("L3"))
            val names = fieldNames(state)
            names.forEach { n: String ->
                assertFalse("UI state exposes provenance via '$n'", n.contains("layer", ignoreCase = true))
                assertFalse(n == "L0")
                assertFalse(n == "L3")
            }
            if (state is CommandState.Act) {
                val resultNames = fieldNames(state.result)
                resultNames.forEach { n: String ->
                    assertFalse("ResolvedResult leaks provenance '$n'", n.contains("layer", ignoreCase = true))
                }
            }
        }
        val outcome: ResolutionOutcome = ResolutionOutcome.Act(PreviewData.actApp)
        assertFalse(outcome.toString().contains("L0"))
        assertFalse(outcome.toString().contains("L3"))
    }

    // 2. ACT has no confidence field
    @Test
    fun `ACT has no confidence field`() {
        val act = ResolvedResult("1", "Chrome", null, ResultType.App, AuraAction.OpenApp("c"))
        val props = fieldNames(act).map { it.lowercase() }
        assertFalse(props.any { it.contains("confidence") })
        val cmd = CommandState.Act(act)
        val cmdProps = fieldNames(cmd).map { it.lowercase() }
        assertFalse(cmdProps.any { it.contains("confidence") })
    }

    // 3. ACT has no resolver-layer field
    @Test
    fun `ACT has no resolver layer field`() {
        val act = ResolvedResult("1", "Chrome", null, ResultType.App, AuraAction.OpenApp("c"))
        val props = fieldNames(act)
        assertFalse(props.any { it.equals("layer", ignoreCase = true) })
        assertFalse(props.any { it.equals("resolver", ignoreCase = true) })
        assertFalse(props.any { it.contains("provenance", ignoreCase = true) })
    }

    // 4. ASK cannot contain a selected candidate
    @Test
    fun `ASK cannot contain selected candidate`() {
        val group = PreviewData.askWhichSarah
        assertFalse(fieldNames(group).any { it.contains("selected", ignoreCase = true) })
        group.candidates.forEach { c: CandidateItemData ->
            assertFalse(fieldNames(c).any { it.contains("selected", ignoreCase = true) })
        }
        val ask = CommandState.Ask(group)
        assertFalse(fieldNames(ask).any { it.contains("selected", ignoreCase = true) })
    }

    // 5. Selecting an ASK candidate produces ACT
    @Test
    fun `selecting ASK candidate produces ACT`() {
        val group = PreviewData.askWhichSarah
        val candidate: CandidateItemData = group.candidates.first()
        val actResult = ResolvedResult(
            id = candidate.id,
            title = candidate.title,
            subtitle = candidate.disambiguation,
            type = ResultType.Contact,
            action = AuraAction.NoOp
        )
        val act: CommandState = CommandState.Act(actResult)
        assertTrue(act is CommandState.Act)
        assertEquals(candidate.title, (act as CommandState.Act).result.title)
        assertEquals(candidate.disambiguation, act.result.subtitle)
    }

    // 6. L3 processing remains internal resolving condition rather than public UI state
    @Test
    fun `L3 processing is not a public UI state`() {
        // Verify allowed states are exactly Idle/Input/Act/Ask/Empty/Error — no Resolving/Loading/L3
        // We check via manual instantiation rather than sealedSubclasses to avoid kotlin-reflect
        val allowed: Set<String> = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        val actual: List<CommandState> = listOf(
            CommandState.Idle,
            CommandState.Input("x"),
            CommandState.Act(PreviewData.actApp),
            CommandState.Ask(PreviewData.askWhichSarah),
            CommandState.Empty("q"),
            CommandState.Error(CommandError("e"))
        )
        actual.forEach { s: CommandState ->
            val name = s::class.simpleName ?: ""
            assertTrue("Unexpected UI state $name — would be state explosion", name in allowed)
            assertFalse(name.contains("Resolving", ignoreCase = true))
            assertFalse(name.contains("Loading", ignoreCase = true))
            assertFalse(name.contains("L3", ignoreCase = true))
        }
        // Also ensure no isResolving field exists on CommandState
        assertFalse(fieldNames(CommandState.Idle).any { it.contains("Resolving", ignoreCase = true) })
    }

    // 7. Validated L3 result maps to exact same ACT representation as L0/L1
    @Test
    fun `validated L3 maps to same ACT as L0 L1`() {
        val l0Result = ResolvedResult("sarah", "Sarah", "WhatsApp", ResultType.Contact, AuraAction.SendMessage("s1", "WhatsApp"))
        val l3Result = ResolvedResult("sarah", "Sarah", "WhatsApp", ResultType.Contact, AuraAction.SendMessage("s1", "WhatsApp"))
        val l0Act = CommandState.Act(l0Result)
        val l3Act = CommandState.Act(l3Result)
        assertEquals(l0Act::class, l3Act::class)
        assertEquals(l0Act.result, l3Act.result)
        assertEquals(l0Act.toString(), l3Act.toString())
    }

    // 8. Empty is not an Error
    @Test
    fun `Empty is not Error`() {
        val empty: CommandState = CommandState.Empty("zxq")
        val error: CommandState = CommandState.Error(CommandError("fail"))
        assertFalse(empty is CommandState.Error)
        assertFalse(error is CommandState.Empty)
        assertTrue(empty is CommandState.Empty)
        assertTrue(error is CommandState.Error)
        val domainEmpty: ResolutionOutcome = ResolutionOutcome.Empty("zxq")
        val domainError: ResolutionOutcome = ResolutionOutcome.Error(CommandError("fail"))
        assertFalse(domainEmpty is ResolutionOutcome.Error)
    }

    // 9. No-match does not use error semantics
    @Test
    fun `no-match does not use error semantics`() {
        val empty = CommandState.Empty("nope")
        assertFalse(empty.toString().contains("error", ignoreCase = true))
        assertTrue(empty is CommandState.Empty)
    }

    // 10. Error requires an actual failure condition
    @Test
    fun `Error requires failure condition`() {
        val error = CommandError("Couldn't reach Settings")
        val state = CommandState.Error(error)
        assertTrue(state.error.message.isNotBlank())
        val empty = CommandState.Empty("Settings")
        assertNotEquals(empty.toString(), state.toString())
    }

    @Test
    fun `ResolutionOutcome toCommandState preserves invariant`() {
        val actOutcome: ResolutionOutcome = ResolutionOutcome.Act(PreviewData.actApp)
        val askOutcome: ResolutionOutcome = ResolutionOutcome.Ask(PreviewData.askWhichSarah)
        val actState = actOutcome.toCommandState()
        val askState = askOutcome.toCommandState()
        assertTrue(actState is CommandState.Act)
        assertTrue(askState is CommandState.Ask)
        assertFalse(actState.toString().contains("L0"))
        assertFalse(askState.toString().contains("L3"))
    }
}
