package com.aura.resolver.l3
import com.aura.TestPaths

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.domain.toCommandState
import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test

class L3ValidatorTest {
    private val index = L0IndexFactory.demoIndex()
    private val validator = L3Validator(index)

    private fun resultFor(action: AuraAction, type: ResultType = ResultType.App): ResolvedResult {
        return ResolvedResult(id = "test", title = "Test", type = type, action = action)
    }

    @Test fun `1 valid OpenApp to Validated`() {
        val app = L0IndexFactory.appEntity("com.android.chrome", "Chrome")
        val idx = com.aura.resolver.L0Index.build(listOf(app))
        val v = L3Validator(idx)
        val res = ResolvedResult("app:com.android.chrome", "Chrome", type = ResultType.App, action = AuraAction.OpenApp("com.android.chrome"))
        assertTrue(v.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `2 missing package to Invalid`() {
        val res = resultFor(AuraAction.OpenApp("com.nonexistent.app123"))
        val out = validator.validate(res)
        assertTrue(out is L3ValidationResult.Invalid)
    }

    @Test fun `2b blank package to Invalid`() {
        val res = resultFor(AuraAction.OpenApp("   "))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `3 valid Dial to Validated`() {
        val res = ResolvedResult("contact:4", "Dad", type = ResultType.Call, action = AuraAction.Dial("+2348010000004", contactId = "4"))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `4 unknown contact Dial to failure`() {
        // Contact-referencing dial to a nonexistent contact id -> Invalid
        val res = ResolvedResult("contact:999", "Unknown", type = ResultType.Call,
            action = AuraAction.Dial("+1000000999", contactId = "999"))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `5 ambiguous contact cannot validate as single is handled at resolution not validation — but validator with specific id still Validated`() {
        // L2 should produce ASK for "Sarah" duplicate, not Act; if somehow an Act with Sarah id 1 is proposed, it should validate because id is specific
        val res = ResolvedResult("contact:1", "Sarah", type = ResultType.Call, action = AuraAction.Dial("+2348010000001", contactId = "1"))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
        // However, we verify that router with L3 still produces ASK for "call sarah" (via L2), not Act
        val router = com.aura.resolver.IntentRouter(
            com.aura.resolver.L0Resolver(index),
            com.aura.resolver.l1.L1Resolver(index),
            com.aura.resolver.l2.L2Resolver(index),
            L3Validator(index)
        )
        val out = router.route("call sarah")
        assertTrue(out is com.aura.domain.ResolutionOutcome.Ask)
    }

    @Test fun `6 valid SendMessage to Validated`() {
        val res = ResolvedResult("contact:4", "Dad", type = ResultType.Message, action = AuraAction.SendMessage("4", "default", "hello", phone = "+2348010000004"))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `7 invalid SendMessage unknown contact to failure`() {
        val res = ResolvedResult("contact:999", "Unknown", type = ResultType.Message, action = AuraAction.SendMessage("999", "default", "hi", phone = "+2348000009999"))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `7b invalid channel to failure`() {
        val res = ResolvedResult("contact:4", "Dad", type = ResultType.Message, action = AuraAction.SendMessage("4", "unsupported_channel_xyz", "hi"))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `8 valid SendEmail to Validated`() {
        val res = ResolvedResult("contact:1", "Sarah", type = ResultType.Email, action = AuraAction.SendEmail("1", body = "hello", emailAddress = "sarah.okafor@email.com"))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `9 invalid email target to failure`() {
        val res = ResolvedResult("contact:999", "Unknown", type = ResultType.Email, action = AuraAction.SendEmail("999"))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `10 valid OpenSettings to Validated`() {
        val res = ResolvedResult("settings:wifi", "Wi-Fi", type = ResultType.Settings, action = AuraAction.OpenSettings("wifi"))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `11 unsupported setting to failure`() {
        val res = ResolvedResult("settings:unknown_xyz", "Unknown", type = ResultType.Settings, action = AuraAction.OpenSettings("unknown_xyz"))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `12 valid SetTimer to Validated`() {
        val res = ResolvedResult("timer:60", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(60))
        assertTrue(validator.validate(res) is L3ValidationResult.Validated)
    }

    @Test fun `13 zero timer to failure`() {
        val res = ResolvedResult("timer:0", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(0))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `14 negative timer to failure`() {
        val res = ResolvedResult("timer:-10", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(-10))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `15 excessive timer to failure`() {
        val res = ResolvedResult("timer:999999", "Timer", type = ResultType.Timer, action = AuraAction.SetTimer(25 * 3600))
        assertTrue(validator.validate(res) is L3ValidationResult.Invalid)
    }

    @Test fun `16 Android APIs never enter domain resolver`() {
        val dirDomain = TestPaths.find("app/src/main/kotlin/com/aura/domain")
        val dirResolver = TestPaths.find("app/src/main/kotlin/com/aura/resolver")
        (dirDomain.walkTopDown() + dirResolver.walkTopDown()).filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val t = f.readText()
            assertFalse("${f.name} must not import Intent", t.contains("import android.content.Intent"))
            assertFalse("${f.name} must not import PackageManager", t.contains("import android.content.pm.PackageManager"))
            assertFalse("${f.name} must not import AlarmClock", t.contains("import android.provider.AlarmClock"))
        }
    }

    @Test fun `17 Android APIs only exist inside platform boundary`() {
        val platformValidator = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AndroidActionValidator.kt")
        assertTrue(platformValidator.exists())
        assertTrue(platformValidator.readText().contains("PackageManager"))
        val executor = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt")
        assertTrue(executor.exists())
        assertTrue(executor.readText().contains("import android.content.Intent"))
    }

    @Test fun `18 validated L3 action produces same ACT semantics as earlier layers`() {
        val index2 = L0IndexFactory.demoIndex()
        val routerWithoutL3 = com.aura.resolver.IntentRouter(com.aura.resolver.L0Resolver(index2), com.aura.resolver.l1.L1Resolver(index2), com.aura.resolver.l2.L2Resolver(index2), null)
        val routerWithL3 = com.aura.resolver.IntentRouter(com.aura.resolver.L0Resolver(index2), com.aura.resolver.l1.L1Resolver(index2), com.aura.resolver.l2.L2Resolver(index2), L3Validator(index2))
        val outWithout = routerWithoutL3.route("chrome")
        val outWith = routerWithL3.route("chrome")
        assertTrue(outWithout is com.aura.domain.ResolutionOutcome.Act)
        assertTrue(outWith is com.aura.domain.ResolutionOutcome.Act)
        assertEquals((outWithout as com.aura.domain.ResolutionOutcome.Act).result.title, (outWith as com.aura.domain.ResolutionOutcome.Act).result.title)
        assertEquals(outWithout.result.type, outWith.result.type)
    }

    @Test fun `19 L3 does not expose provenance`() {
        val res = ResolvedResult("app:com.android.chrome", "Chrome", type = ResultType.App, action = AuraAction.OpenApp("com.android.chrome"))
        val out = validator.validate(res)
        assertFalse(out.toString().contains("L3"))
        assertFalse(out.toString().contains("provenance", ignoreCase = true))
    }

    @Test fun `20 L3 does not expose confidence`() {
        val res = ResolvedResult("app:com.android.chrome", "Chrome", type = ResultType.App, action = AuraAction.OpenApp("com.android.chrome"))
        val out = validator.validate(res)
        assertFalse(out.toString().lowercase().contains("confidence"))
    }

    @Test fun `21 L3 does not introduce new CommandState`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        val router = com.aura.resolver.IntentRouter(com.aura.resolver.L0Resolver(index), com.aura.resolver.l1.L1Resolver(index), com.aura.resolver.l2.L2Resolver(index), L3Validator(index))
        val cases = listOf("chrome", "call sarah", "unknownxyz", "500 / 0", "")
        cases.forEach { q ->
            val out = router.route(q)
            val cmd = out.toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }

    @Test fun `22 L0 L1 L2 tests remain green is verified via overall suite - here we sanity check router still handles L0 exact`() {
        val router = com.aura.resolver.IntentRouter(com.aura.resolver.L0Resolver(index), com.aura.resolver.l1.L1Resolver(index), com.aura.resolver.l2.L2Resolver(index), L3Validator(index))
        assertTrue(router.route("chrome") is com.aura.domain.ResolutionOutcome.Act)
        assertTrue(router.route("sarah") is com.aura.domain.ResolutionOutcome.Ask) // duplicate Sarah
        assertTrue(router.route("500 * 27") is com.aura.domain.ResolutionOutcome.Act) // L1 math
        assertTrue(router.route("chorme") is com.aura.domain.ResolutionOutcome.Act) // L2 typo
    }
}
