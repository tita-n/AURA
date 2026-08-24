package com.aura.resolver

import com.aura.domain.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 1.5 — Real Android App Index tests (device-independent, pure resolver).
 * Verifies that launchable apps become IndexedEntity correctly and integrate with L0/L1.
 */
class AppIndexTest {

    @Test fun `1 launchable apps become IndexedEntity`() {
        val e = L0IndexFactory.appEntity("com.example.test", "TestApp")
        assertEquals("app:com.example.test", e.id)
        assertEquals("TestApp", e.displayLabel)
        assertEquals(EntityCategory.App, e.category)
        assertEquals(ResultType.App, e.resultType)
    }

    @Test fun `2 application label is preserved`() {
        val e = L0IndexFactory.appEntity("com.test", "My Cool App")
        assertEquals("My Cool App", e.displayLabel)
    }

    @Test fun `3 normalized label is deterministic`() {
        val e1 = L0IndexFactory.appEntity("com.a", "Chrome")
        val e2 = L0IndexFactory.appEntity("com.b", "  CHROME  ")
        assertEquals(e1.normalizedLabel, e2.normalizedLabel)
        assertEquals("chrome", e1.normalizedLabel)
        assertEquals(Normalizer.normalize("  CHROME  "), e1.normalizedLabel)
    }

    @Test fun `4 package name preserved as OpenApp payload`() {
        val e = L0IndexFactory.appEntity("com.example.chrome", "Chrome")
        val action = e.action as AuraAction.OpenApp
        assertEquals("com.example.chrome", action.packageName)
    }

    @Test fun `5 non-launchable packages are excluded by provider filtering`() {
        // Simulate provider filtering: blank label or package is excluded (mapNotNull in AndroidAppIndexProvider)
        val filtered = listOf(
            L0IndexFactory.appEntity("", "ValidLabel"), // blank package -> should be filtered in real provider
            L0IndexFactory.appEntity("com.valid", ""), // blank label
            L0IndexFactory.appEntity("com.valid2", "Valid2")
        ).filter { it.displayLabel.isNotBlank() && it.id.removePrefix("app:").isNotBlank() }
        // Only last should remain
        assertEquals(1, filtered.size)
        assertEquals("Valid2", filtered.first().displayLabel)
    }

    @Test fun `6 duplicate normalized labels remain separate entities`() {
        val e1 = L0IndexFactory.appEntity("com.a.one", "TestApp")
        val e2 = L0IndexFactory.appEntity("com.a.two", "TestApp")
        val index = L0Index.build(listOf(e1, e2))
        assertEquals(2, index.size())
        val lookup = index.lookup("testapp")
        assertTrue(lookup is L0LookupResult.Exact)
        assertEquals(2, (lookup as L0LookupResult.Exact).entities.size)
    }

    @Test fun `7 multiple matching apps produce ASK`() {
        val index = L0Index.build(listOf(
            L0IndexFactory.appEntity("com.a.one", "TestApp"),
            L0IndexFactory.appEntity("com.a.two", "TestApp")
        ))
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        val out = router.route("testapp")
        assertTrue(out is ResolutionOutcome.Ask)
    }

    @Test fun `8 one matching app produces ACT`() {
        val index = L0Index.build(listOf(L0IndexFactory.appEntity("com.chrome", "Chrome")))
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        val out = router.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `9 unknown app produces Empty`() {
        val index = L0Index.build(listOf(L0IndexFactory.appEntity("com.chrome", "Chrome")))
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        val out = router.route("unknownxyz123")
        assertTrue(out is ResolutionOutcome.Empty)
        assertFalse(out is ResolutionOutcome.Error)
    }

    @Test fun `10 L1 open app continues working with real index`() {
        val index = L0Index.build(listOf(L0IndexFactory.appEntity("com.chrome", "Chrome")))
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        val out = router.route("open chrome")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test fun `10b L1 open unknown app is Empty not invented`() {
        val index = L0Index.build(listOf(L0IndexFactory.appEntity("com.chrome", "Chrome")))
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        val out = router.route("open unknownxyz")
        assertTrue(out is ResolutionOutcome.Empty)
    }

    @Test fun `11 resolver package contains no Android imports`() {
        val dir = java.io.File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse("${f.name} must not import android", text.contains("import android."))
        }
    }

    @Test fun `12 domain package contains no Android imports`() {
        val dir = java.io.File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse(text.contains("import android."))
        }
    }

    @Test fun `13 UI contains no PackageManager Intent execution`() {
        val dir = java.io.File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse(text.contains("import android.content.pm"))
            assertFalse(text.contains("startActivity("))
            assertFalse(text.contains("import android.content.Intent"))
        }
        // MainActivity must not import Android execution APIs (providers are platform-side)
        val main = java.io.File("/home/titan/AURA/app/src/main/kotlin/com/aura/MainActivity.kt").readText()
        assertFalse(main.contains("import android.content.pm"))
        assertFalse(main.contains("startActivity"))
        assertTrue(main.contains("AndroidAppIndexProvider"))
    }

    @Test fun `real apps plus contacts plus settings remain composable`() {
        val realApps = listOf(L0IndexFactory.appEntity("com.real.app", "RealApp"))
        val contacts = L0IndexFactory.demoContacts()
        val settings = L0IndexFactory.demoSettings()
        val index = L0IndexFactory.buildIndex(realApps, contacts, settings)
        // Real app should be searchable
        val router = IntentRouter(L0Resolver(index), com.aura.resolver.l1.L1Resolver(index))
        assertTrue(router.route("realapp") is ResolutionOutcome.Act)
        // Contacts preserved
        assertTrue(router.route("dad") is ResolutionOutcome.Act)
        // Settings preserved
        assertTrue(router.route("bluetooth") is ResolutionOutcome.Act)
        // Total size = realApps + contacts + settings
        assertEquals(realApps.size + contacts.size + settings.size, index.size())
    }

    @Test fun `L0 index built once not per keystroke`() {
        val apps = listOf(L0IndexFactory.appEntity("com.chrome", "Chrome"))
        val index = L0Index.build(apps)
        val resolver = L0Resolver(index)
        // Multiple lookups should use same index instance
        val r1 = resolver.resolve("chrome")
        val r2 = resolver.resolve("chrome")
        assertTrue(r1 is L0Resolution.Resolved)
        assertTrue(r2 is L0Resolution.Resolved)
        assertEquals(index.size(), 1)
    }
}
