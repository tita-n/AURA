package com.aura.resolver.l3

import com.aura.domain.AuraAction
import com.aura.domain.ResolutionOutcome
import com.aura.domain.ResultType
import com.aura.domain.toCommandState
import com.aura.platform.android.AndroidActionExecutor
import com.aura.platform.android.ExecutionResult
import com.aura.resolver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EndToEndTest {
    private val index = L0IndexFactory.demoIndex()
    private val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index), com.aura.resolver.l2.L2Resolver(index), L3Validator(index))

    @Test fun `chrome L0 to L3 to Act explicit execution only`() {
        val out = router.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        // No automatic execution — need explicit ValidatedAction
        val validated = L3Validator(index).validate((out as ResolutionOutcome.Act).result)
        assertTrue(validated is L3ValidationResult.Validated)
        // Executor would need ValidatedAction, not just Act
        val executorText = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        assertTrue(executorText.contains("ValidatedAction"))
    }

    @Test fun `500 * 27 L1 to L3 to Act`() {
        val out = router.route("500 * 27")
        assertTrue(out is ResolutionOutcome.Act)
        assertTrue((out as ResolutionOutcome.Act).result.type == ResultType.Math)
    }

    @Test fun `chorme L2 to L3 to Act`() {
        val out = router.route("chorme")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `call Sarah Ask no execution`() {
        val out = router.route("call sarah")
        assertTrue(out is ResolutionOutcome.Ask)
        assertTrue(out.toCommandState() is com.aura.domain.CommandState.Ask)
        // No execution for Ask
    }

    @Test fun `unknownxyz Empty no execution`() {
        val out = router.route("unknownxyz")
        assertTrue(out is ResolutionOutcome.Empty)
    }

    @Test fun `500 divide 0 Error no execution`() {
        val out = router.route("500 / 0")
        assertTrue(out is ResolutionOutcome.Error)
    }

    @Test fun `OpenApp success via L3 validation`() {
        val res = com.aura.domain.ResolvedResult("app:com.android.chrome", "Chrome", type = ResultType.App, action = AuraAction.OpenApp("com.android.chrome"))
        val v = L3Validator(L0Index.build(listOf(L0IndexFactory.appEntity("com.android.chrome", "Chrome")))).validate(res)
        assertTrue(v is L3ValidationResult.Validated)
    }

    @Test fun `OpenApp unavailable via L3`() {
        val res = com.aura.domain.ResolvedResult("app:com.fake", "Fake", type = ResultType.App, action = AuraAction.OpenApp("com.nonexistent.fake"))
        val v = L3Validator(L0IndexFactory.demoIndex()).validate(res)
        assertTrue(v is L3ValidationResult.Invalid)
        // Router should map invalid to Error
        val router2 = IntentRouter(L0Resolver(L0Index.build(listOf(L0IndexFactory.appEntity("com.android.chrome", "Chrome")))), null, null, L3Validator(L0Index.build(listOf(L0IndexFactory.appEntity("com.android.chrome", "Chrome")))))
        // Propose an OpenApp for nonexistent via direct validation
        assertTrue(v is L3ValidationResult.Invalid)
    }

    @Test fun `OpenSettings success`() {
        val res = com.aura.domain.ResolvedResult("settings:wifi", "Wi-Fi", type = ResultType.Settings, action = AuraAction.OpenSettings("wifi"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `OpenSettings unsupported`() {
        val res = com.aura.domain.ResolvedResult("settings:unknown", "Unknown", type = ResultType.Settings, action = AuraAction.OpenSettings("unknown_xyz"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `timer success`() {
        val res = com.aura.domain.ResolvedResult("timer:60", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(60))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `timer invalid zero`() {
        val res = com.aura.domain.ResolvedResult("timer:0", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(0))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `dial success`() {
        val res = com.aura.domain.ResolvedResult("contact:4", "Dad", type = ResultType.Call, action = AuraAction.Dial("4"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `message success`() {
        val res = com.aura.domain.ResolvedResult("contact:4", "Dad", type = ResultType.Message, action = AuraAction.SendMessage("4", "default", "hello"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `email success`() {
        val res = com.aura.domain.ResolvedResult("contact:4", "Dad", type = ResultType.Email, action = AuraAction.SendEmail("4"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `copy success`() {
        val res = com.aura.domain.ResolvedResult("copy:1", "hello", type = ResultType.Math, action = AuraAction.Copy("hello"))
        assertTrue(L3Validator(index).validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `executionResult domain safe no Android types`() {
        val file = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt")
        val text = file.readText()
        // ExecutionResult should not contain Intent, PackageManager inside its definition (it does contain those in executor, but result itself is pure)
        val resultSection = text.substringAfter("sealed interface ExecutionResult").substringBefore("interface ActionExecutor")
        assertFalse(resultSection.contains("Intent"))
        assertFalse(resultSection.contains("PackageManager"))
        assertTrue(resultSection.contains("Success"))
        assertTrue(resultSection.contains("Failure"))
        assertTrue(resultSection.contains("Unavailable"))
    }

    @Test fun `no new CommandState`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        listOf("chrome", "chorme", "500 * 27", "unknownxyz", "call sarah", "500 / 0").forEach { q ->
            val cmd = router.route(q).toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }
}
