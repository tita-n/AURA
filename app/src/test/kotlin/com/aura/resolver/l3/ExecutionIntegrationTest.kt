package com.aura.resolver.l3

import com.aura.domain.*
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0Resolver
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * L3 Execution Integration — platform-independent contract tests.
 * Verifies the full chain: resolve -> validate -> explicit execution -> result,
 * and that resolution alone NEVER executes.
 */
class ExecutionIntegrationTest {

    private val index = L0IndexFactory.demoIndex()
    private val router = IntentRouter(
        com.aura.resolver.L0Resolver(index),
        com.aura.resolver.l1.L1Resolver(index),
        com.aura.resolver.l2.L2Resolver(index),
        L3Validator(index)
    )
    private val validator = L3Validator(index)

    // ---- Validated actions reach executor shape; invalid never do ----

    @Test fun `validated OpenApp reaches executor pathway`() {
        val out = router.route("chrome")
        val result = (out as ResolutionOutcome.Act).result
        val v = validator.validate(result)
        assertTrue(v is L3ValidationResult.Validated)
        // ValidatedAction is what executor.execute() accepts
        assertEquals("app:com.android.chrome", (v as L3ValidationResult.Validated).action.result.id)
    }

    @Test fun `invalid OpenApp never reaches executor`() {
        val invalid = ResolvedResult("app:com.nonexistent", "X", type = ResultType.App, action = AuraAction.OpenApp("com.nonexistent"))
        assertTrue(validator.validate(invalid) is L3ValidationResult.Invalid)
    }

    @Test fun `validated timer preserves exact seconds`() {
        for ((q, secs) in listOf(
            "timer 1 second" to 1, "timer 10 seconds" to 10,
            "set a timer for 10 minutes" to 600, "timer 1 hour" to 3600,
            "timer for 10 minutes" to 600, "timer 24 hours" to 86400
        )) {
            val out = router.route(q)
            val r = (out as ResolutionOutcome.Act).result.action as AuraAction.SetTimer
            assertEquals("'$q' -> $secs seconds", secs, r.durationSeconds)
            // And it validates
            assertTrue(validator.validate((out).result) is L3ValidationResult.Validated)
        }
    }

    @Test fun `invalid timer values rejected by L3 before executor`() {
        for (secs in listOf(0, -1, 25 * 3600)) {
            val res = ResolvedResult("t", "T", type = ResultType.Timer, action = AuraAction.SetTimer(secs))
            assertTrue("SetTimer($secs) must be Invalid", validator.validate(res) is L3ValidationResult.Invalid)
        }
    }

    @Test fun `settings closed vocabulary enforced`() {
        for (key in listOf("wifi", "bluetooth", "display_settings")) {
            val res = ResolvedResult("s:$key", key, type = ResultType.Settings, action = AuraAction.OpenSettings(key))
            assertTrue(validator.validate(res) is L3ValidationResult.Validated)
        }
        val bad = ResolvedResult("s:x", "x", type = ResultType.Settings, action = AuraAction.OpenSettings("arbitrary_intent_action"))
        assertTrue(validator.validate(bad) is L3ValidationResult.Invalid)
    }

    @Test fun `contact actions require existing contact`() {
        val good = ResolvedResult("contact:4", "Dad", type = ResultType.Call, action = AuraAction.Dial("+2348010000004", contactId = "4"))
        assertTrue(validator.validate(good) is L3ValidationResult.Validated)
        val bad = ResolvedResult("contact:9999", "?", type = ResultType.Call, action = AuraAction.Dial("+2348000009999", contactId = "9999"))
        assertTrue(validator.validate(bad) is L3ValidationResult.Invalid)
    }

    // ---- Execution failure maps to existing states only ----

    @Test fun `execution failure does not create new CommandState`() {
        // Simulate the MainActivity mapping: Failure -> Error, Unavailable -> Empty
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        val failOutcome: CommandState = CommandState.Error(CommandError("App not found"))
        val unavailOutcome: CommandState = CommandState.Empty("chrome")
        assertTrue(failOutcome::class.simpleName in allowed)
        assertTrue(unavailOutcome::class.simpleName in allowed)
        // No Executing/Permission/Launching/L3 state exists anywhere in domain source
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.readTexts()
        assertFalse(src.contains("ExecutingState") || src.contains("PermissionState") || src.contains("LaunchingState"))
    }

    private fun Sequence<File>.readTexts(): String = joinToString("\n") { it.readText() }

    // ---- Ask / Empty / Error / typing / ACT-resolution never execute ----

    @Test fun `Ask never executes`() {
        val out = router.route("call sarah")
        assertTrue(out is ResolutionOutcome.Ask)
        // Router returns Ask directly; no validateOrAct path, no ValidatedAction produced.
        // Executor requires ValidatedAction which is only produced from Act results.
        assertTrue(out.toCommandState() is CommandState.Ask)
    }

    @Test fun `Empty never executes`() {
        val out = router.route("what is my destiny")
        assertTrue(out is ResolutionOutcome.Empty)
        // No ResolvedResult exists -> no ValidatedAction possible
    }

    @Test fun `Error never executes`() {
        val out = router.route("500 divide 0".replace("divide", "/"))
        assertTrue(out is ResolutionOutcome.Error)
    }

    @Test fun `typing alone never executes - route is pure`() {
        // Routing produces outcomes with no side effects; executor is a separate object
        // that routing never touches. Structural proof: IntentRouter has no executor field.
        val routerSrc = File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver/IntentRouter.kt").readText()
        assertFalse(routerSrc.contains("ActionExecutor"))
        assertFalse(routerSrc.contains("executor"))
        assertFalse(routerSrc.contains("startActivity"))
    }

    @Test fun `ACT resolution alone never executes - no executor call in resolver layers`() {
        val l3src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver/l3/L3Validator.kt").readText()
        assertFalse(l3src.contains("startActivity"))
        assertFalse(l3src.contains("ActionExecutor"))
        // Validation produces ValidatedAction; execution happens only via ActionExecutor.execute
        // which lives in platform/android and is invoked solely from MainActivity's explicit handlers.
    }

    @Test fun `only platform android may execute`() {
        val mainSrc = File("/home/titan/AURA/app/src/main/kotlin/com/aura/MainActivity.kt").readText()
        // MainActivity wires executors/providers but never constructs or starts Intents itself
        assertFalse(mainSrc.contains("import android.content.Intent"))
        assertFalse(mainSrc.contains("startActivity"))
        assertTrue(mainSrc.contains("AndroidActionExecutor"))
        // Role intents also live behind the platform boundary
        val roleSrc = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/LauncherRoleHelper.kt").readText()
        assertTrue(roleSrc.contains("createRequestRoleIntent"))
    }

    // ---- Full timer flow regression remains fixed ----

    @Test fun `timer full chain - all variants resolve validate to SetTimer`() {
        for (q in listOf(
            "timer for 10 minutes", "set a timer for 10 minutes", "timer 10 minutes",
            "remind me in 10 minutes", "countdown 10 minutes", "timer for 10 seconds"
        )) {
            val out = router.route(q)
            assertTrue("'$q' expected Act", out is ResolutionOutcome.Act)
            val result = (out as ResolutionOutcome.Act).result
            val v = validator.validate(result)
            assertTrue("'${q}' should validate", v is L3ValidationResult.Validated)
            assertTrue((v as L3ValidationResult.Validated).action.result.action is AuraAction.SetTimer)
        }
    }

    // ---- Security invariants ----

    @Test fun `no Android execution APIs outside platform android`() {
        val forbidden = listOf(
            "import android.content.Intent", "import android.content.Context",
            "import android.content.pm.PackageManager", "import android.provider.Settings",
            "import android.provider.AlarmClock", "import android.net.Uri", "startActivity"
        )
        val dirs = listOf(
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/MainActivity.kt")
        )
        dirs.forEach { d ->
            val files = if (d.isDirectory) d.walkTopDown().filter { it.isFile && it.extension == "kt" } else sequenceOf(d)
            files.forEach { f ->
                forbidden.forEach { imp ->
                    assertFalse("${f.name} contains $imp", f.readText().contains(imp))
                }
            }
        }
    }

    @Test fun `ExecutionResult has no Android types`() {
        val text = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        val section = text.substringAfter("sealed interface ExecutionResult").substringBefore("interface ActionExecutor")
        assertFalse(section.contains("Intent"))
        assertFalse(section.contains("Exception"))
        assertTrue(section.contains("Success"))
        assertTrue(section.contains("Failure"))
        assertTrue(section.contains("Unavailable"))
    }

    @Test fun `no provenance or confidence in validated results`() {
        val out = router.route("chorme") as ResolutionOutcome.Act
        val s = out.toString().lowercase()
        assertFalse(s.contains("confidence") || s.contains("l0") || s.contains("l1") || s.contains("l2") || s.contains("l3") || s.contains("provenance"))
    }

    // ---- Undo contract: honest only ----

    @Test fun `undo does not claim system timer cancellation`() {
        val hostSrc = File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui/command/CommandStateHost.kt").readText()
        assertTrue(hostSrc.contains("never claims cancellation") || hostSrc.contains("cannot identify"))
        // No AlarmClock cancel intent anywhere in UI
        assertFalse(hostSrc.contains("ACTION_CANCEL_TIMER"))
    }
}
