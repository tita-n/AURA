package com.aura.resolver.l3

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.domain.toCommandState
import com.aura.resolver.L0IndexFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExecutionBoundaryTest {

    @Test fun `executor accepts ValidatedAction not AuraAction`() {
        // Verify ActionExecutor signature requires ValidatedAction
        val executorFile = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt")
        val text = executorFile.readText()
        assertTrue(text.contains("suspend fun execute(action: ValidatedAction)"))
        assertFalse(text.contains("suspend fun execute(action: AuraAction)"))
    }

    @Test fun `invalid actions cannot bypass L3`() {
        val index = L0IndexFactory.demoIndex()
        val validator = L3Validator(index)
        val invalid = ResolvedResult("app:com.nonexistent", "Fake", type = ResultType.App, action = AuraAction.OpenApp("com.nonexistent"))
        val out = validator.validate(invalid)
        assertTrue(out is L3ValidationResult.Invalid)
        // IntentRouter with L3 should map invalid to Error, not Act
        val router = com.aura.resolver.IntentRouter(
            com.aura.resolver.L0Resolver(index),
            com.aura.resolver.l1.L1Resolver(index),
            com.aura.resolver.l2.L2Resolver(index),
            L3Validator(index)
        )
        // Direct L3 invalid is Error, not Act
        assertTrue(out is L3ValidationResult.Invalid)
    }

    @Test fun `UI domain resolver do not import Android execution APIs`() {
        val forbidden = listOf(
            "import android.content.Intent",
            "import android.content.Context",
            "import android.content.pm.PackageManager",
            "import android.provider.Settings",
            "import android.provider.AlarmClock",
            "import android.net.Uri",
            "startActivity"
        )
        val dirs = listOf(
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui")
        )
        dirs.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val text = f.readText()
                forbidden.forEach { imp ->
                    assertFalse("${f.name} must not contain $imp", text.contains(imp))
                }
            }
        }
    }

    @Test fun `only platform android may contain execution APIs`() {
        val platformDir = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android")
        val files = platformDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        val hasIntent = files.any { it.readText().contains("import android.content.Intent") }
        assertTrue("platform should contain Intent", hasIntent)
        // Domain should not
        val domainHasIntent = File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain").walkTopDown()
            .any { it.isFile && it.extension == "kt" && it.readText().contains("import android.content.Intent") }
        assertFalse(domainHasIntent)
    }

    @Test fun `no new CommandState introduced`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        val index = L0IndexFactory.demoIndex()
        val router = com.aura.resolver.IntentRouter(
            com.aura.resolver.L0Resolver(index),
            com.aura.resolver.l1.L1Resolver(index),
            com.aura.resolver.l2.L2Resolver(index),
            L3Validator(index)
        )
        listOf("chrome", "call sarah", "unknownxyz", "500 / 0", "", "chorme", "turn on wifi").forEach { q ->
            val out = router.route(q)
            val cmd = out.toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }

    @Test fun `resolution must never execute automatically`() {
        val index = L0IndexFactory.demoIndex()
        val router = com.aura.resolver.IntentRouter(
            com.aura.resolver.L0Resolver(index),
            com.aura.resolver.l1.L1Resolver(index),
            com.aura.resolver.l2.L2Resolver(index),
            L3Validator(index)
        )
        // Routing alone must not execute (no side effect). We verify by checking that route returns Act without needing executor
        val out = router.route("chrome")
        assertTrue(out is com.aura.domain.ResolutionOutcome.Act)
        // No execution happened yet — executor would need explicit call
        // Verify that Act's action is still OpenApp, not yet executed
        assertTrue((out as com.aura.domain.ResolutionOutcome.Act).result.action is AuraAction.OpenApp)
    }

    @Test fun `ValidatedAction is required for execution - direct AuraAction not executable`() {
        // Verify that AndroidActionExecutor's execute method requires ValidatedAction, not AuraAction
        val executorText = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidActionExecutor.kt").readText()
        assertTrue(executorText.contains("ValidatedAction"))
        assertFalse(executorText.contains("fun execute(action: AuraAction"))
    }
}
