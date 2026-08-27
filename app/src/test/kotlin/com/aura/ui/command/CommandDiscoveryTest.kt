package com.aura.ui.command

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.CommandState
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 4B — Command Bar discoverability (pure, local-only logic).
 *
 * These tests deliberately avoid touching the resolver, Android, or Compose: the rotation
 * helpers and the failed-intent hint are product-level, deterministic functions.
 */
class CommandDiscoveryTest {

    // ---- Rotating placeholder: only real, supported examples ----

    @Test
    fun examples_are_non_empty_and_contain_no_duplicates() {
        val examples = SUPPORTED_COMMAND_EXAMPLES
        assert(examples.isNotEmpty())
        assertEquals("duplicate example detected", examples.size, examples.toSet().size)
        examples.forEach { assert(it.isNotBlank()) }
    }

    @Test
    fun examples_correspond_to_implemented_families() {
        // Every entry must map to a genuinely wired capability (call / message / timer /
        // percentage / open app / open settings / time). We assert the surface-level
        // keyword families are represented without inventing new ones.
        val joined = SUPPORTED_COMMAND_EXAMPLES.joinToString(" ").lowercase()
        listOf("call", "message", "timer", "calculate", "open", "what time").forEach { family ->
            assert(joined.contains(family)) { "missing example family: $family" }
        }
    }

    // ---- Rotation sequence is deterministic and stateless ----

    @Test
    fun next_example_index_cycles_deterministically() {
        val size = SUPPORTED_COMMAND_EXAMPLES.size
        val seq = mutableListOf<Int>()
        var i = 0
        repeat(size) { i = nextExampleIndex(i, size); seq.add(i) }
        assertEquals((1 until size).toList() + 0, seq)
    }

    @Test
    fun next_example_index_wraps_and_never_mutates_state() {
        val size = SUPPORTED_COMMAND_EXAMPLES.size
        val snapshot = SUPPORTED_COMMAND_EXAMPLES.toList()
        // Stepping a whole number of cycles must return to the start and stay in range.
        var i = 0
        repeat(size * 100) {
            i = nextExampleIndex(i, size)
            assert(i in 0 until size) { "index out of range: $i" }
        }
        assertEquals(0, i)
        assertEquals(snapshot, SUPPORTED_COMMAND_EXAMPLES.toList())
    }

    @Test
    fun next_example_index_handles_empty_list_safely() {
        assertEquals(0, nextExampleIndex(0, 0))
    }

    // ---- Lifecycle gating ----

    @Test
    fun rotation_pauses_when_home_not_in_foreground() {
        assertEquals(true, shouldPauseRotation(homeInForeground = false, query = "", focused = false))
    }

    @Test
    fun rotation_pauses_while_typing_or_focused() {
        assertEquals(true, shouldPauseRotation(homeInForeground = true, query = "cal", focused = false))
        assertEquals(true, shouldPauseRotation(homeInForeground = true, query = "", focused = true))
    }

    @Test
    fun rotation_proceeds_only_when_visible_empty_and_unfocused() {
        assertEquals(false, shouldPauseRotation(homeInForeground = true, query = "", focused = false))
    }

    // ---- Failed-intent hint ----

    private val emptyState: CommandState = CommandState.Empty("")
    private val actState: CommandState = CommandState.Act(
        ResolvedResult(id = "app:x", title = "X", subtitle = null, type = ResultType.App, action = AuraAction.OpenApp("x"))
    )
    private val askState: CommandState = CommandState.Ask(
        CandidateGroup("Which Sarah", listOf(CandidateItemData(id = "contact:sarah1", title = "Sarah")))
    )

    @Test
    fun normal_app_name_miss_produces_no_command_hint() {
        assertNull(CommandHint.suggest("spotify", emptyState))
        assertNull(CommandHint.suggest("xyzzz", emptyState))
    }

    @Test
    fun random_text_produces_no_hint() {
        assertNull(CommandHint.suggest("asdkjf qwer zxcv", emptyState))
    }

    @Test
    fun failed_call_like_input_produces_call_hint() {
        assertEquals("Try: Call Sarah", CommandHint.suggest("call Sarah tomorrow", emptyState))
        assertEquals("Try: Call Sarah", CommandHint.suggest("call sarah on", emptyState))
    }

    @Test
    fun failed_message_like_input_produces_message_hint() {
        assertEquals("Try: Message Sarah", CommandHint.suggest("message Sarah on", emptyState))
        assertEquals("Try: Message Sarah", CommandHint.suggest("text Sarah at", emptyState))
    }

    @Test
    fun timer_like_input_without_duration_produces_timer_hint() {
        assertEquals("Try: Set a timer for 10 min", CommandHint.suggest("set timer", emptyState))
        assertEquals("Try: Set a timer for 10 min", CommandHint.suggest("timer", emptyState))
    }

    @Test
    fun successful_resolution_removes_hint() {
        assertNull(CommandHint.suggest("call Mum", actState))
        assertNull(CommandHint.suggest("call sarah", askState))
    }

    @Test
    fun blank_query_produces_no_hint() {
        assertNull(CommandHint.suggest("", emptyState))
        assertNull(CommandHint.suggest("   ", emptyState))
    }

    @Test
    fun open_app_miss_is_not_misread_as_command_hint() {
        // "open face" is an ordinary app search miss; it must not produce a call/message/timer hint.
        assertNull(CommandHint.suggest("open face", emptyState))
    }
}
