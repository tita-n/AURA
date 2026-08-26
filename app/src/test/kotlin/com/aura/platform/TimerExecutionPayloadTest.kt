package com.aura.platform
import com.aura.TestPaths

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.l3.L3Validator
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression around the final timer execution payload — not just parser output.
 * Verifies that AuraAction.SetTimer durations are passed to AlarmClock exactly
 * as seconds, with no double conversion, and that invalid durations never
 * reach the executor (L3 rejects them).
 */
class TimerExecutionPayloadTest {

    private fun timerResult(seconds: Int) = ResolvedResult(
        id = "timer:$seconds",
        title = "Timer set for $seconds",
        type = ResultType.Timer,
        action = AuraAction.SetTimer(seconds)
    )

    private fun emptyIndex() = L0IndexFactory.buildIndex(emptyList(), contacts = emptyList())

    @Test fun `SetTimer 10 seconds is valid and maps to 10`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(10))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Validated)
        val action = (v as com.aura.resolver.l3.L3ValidationResult.Validated).action.result.action as AuraAction.SetTimer
        assertEquals(10, action.durationSeconds)
        // Verify executor uses ACTION_SET_TIMER with EXTRA_LENGTH = seconds (not millis)
        val execText = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").takeIf { it.exists() }?.readText()
            ?: java.io.File("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        assertTrue(execText.contains("AlarmClock.ACTION_SET_TIMER"))
        assertTrue(execText.contains("AlarmClock.EXTRA_LENGTH"))
        assertFalse(execText.contains("EXTRA_LENGTH * 1000"))
        assertFalse(execText.contains("durationSeconds * 1000"))
    }

    @Test fun `SetTimer 60 seconds maps correctly`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(60))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Validated)
        assertEquals(60, ((v as com.aura.resolver.l3.L3ValidationResult.Validated).action.result.action as AuraAction.SetTimer).durationSeconds)
    }

    @Test fun `SetTimer 600 seconds (10 min) maps correctly — no double conversion to millis`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(600))
        val action = (v as com.aura.resolver.l3.L3ValidationResult.Validated).action.result.action as AuraAction.SetTimer
        assertEquals(600, action.durationSeconds)
        // Must be 600, not 60000 (millis) and not 10 (minutes) — check no *1000 or /60 confusion
        assertNotEquals(60000, action.durationSeconds)
        assertNotEquals(10, action.durationSeconds)
        val execText = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").takeIf { it.exists() }?.readText()
            ?: java.io.File("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        // Executor must put EXTRA_LENGTH as secs directly
        assertTrue(execText.contains("putExtra(AlarmClock.EXTRA_LENGTH, secs)"))
        assertFalse(execText.contains("* 1000"))
        assertFalse(execText.contains("*1000"))
    }

    @Test fun `SetTimer 3600 seconds (1 hour) maps correctly`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(3600))
        assertEquals(3600, ((v as com.aura.resolver.l3.L3ValidationResult.Validated).action.result.action as AuraAction.SetTimer).durationSeconds)
    }

    @Test fun `SetTimer 86400 seconds (24h) is max valid`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(86400))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `SetTimer 0 is invalid and never reaches executor`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(0))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `SetTimer negative is invalid`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(-5))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `SetTimer 86401 exceeds 24h and is invalid`() {
        val v = L3Validator(emptyIndex()).validate(timerResult(86401))
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `timer intent uses seconds extra, not millis, and correct action`() {
        val seconds = 600
        // Verify via executor source that correct extras are used
        val execText = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").takeIf { it.exists() }?.readText()
            ?: java.io.File("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        assertTrue(execText.contains("AlarmClock.ACTION_SET_TIMER"))
        assertTrue(execText.contains("AlarmClock.EXTRA_LENGTH"))
        assertTrue(execText.contains("AlarmClock.EXTRA_MESSAGE"))
        assertTrue(execText.contains("AlarmClock.EXTRA_SKIP_UI"))
        // Must NOT use alarm extras for timer
        // Check that SetTimer block does not put EXTRA_HOUR/MINUTES
        val timerBlock = execText.substringAfter("private fun executeSetTimer").substringBefore("private fun executeSetAlarm")
        assertFalse(timerBlock.contains("EXTRA_HOUR"))
        assertFalse(timerBlock.contains("EXTRA_MINUTES"))
        // Ensure duration is not converted to millis in the putExtra
        assertFalse(timerBlock.contains("* 1000"))
    }

    @Test fun `timer failure does not create new CommandState`() {
        // CommandState is sealed with exactly Idle/Act/Ask/Empty/Error — no TimerSuccess variant.
        val candidates = listOf(
            TestPaths.find("app/src/main/kotlin/com/aura/domain/CommandState.kt"),
            java.io.File("app/src/main/kotlin/com/aura/domain/CommandState.kt")
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("CommandState.kt not found")
        val text = file.readText()
        assertTrue(text.contains("data object Idle"))
        assertTrue(text.contains("data class Act"))
        assertFalse(text.contains("TimerSuccess"))
        assertFalse(text.contains("TimerCreated"))
    }
}
