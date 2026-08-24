package com.aura.launcher

import com.aura.resolver.L0IndexFactory
import com.aura.resolver.IndexedEntity
import com.aura.ui.library.AppLibraryLogic
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Launcher role + App Library phase — platform-independent contract tests.
 */
class LauncherLibraryTest {

    private fun app(label: String, pkg: String) = L0IndexFactory.appEntity(pkg, label)

    private val apps = listOf(
        app("Zebra", "com.z"), app("alpha", "com.a2"), app("Alpha Bank", "com.a1"),
        app("Calculator", "com.c"), app("42 Dialer", "com.n1"), app("#Notes", "com.h")
    )

    // ---- ROLE ----

    @Test fun `manifest exposes HOME and DEFAULT categories`() {
        val manifest = File("/home/titan/AURA/app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.intent.category.HOME"))
        assertTrue(manifest.contains("android.intent.category.DEFAULT"))
        // LAUNCHER retained for normal install/launch behavior
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
    }

    @Test fun `role request uses RoleManager with settings fallback`() {
        val helper = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/LauncherRoleHelper.kt").readText()
        assertTrue(helper.contains("ROLE_HOME"))
        assertTrue(helper.contains("createRequestRoleIntent"))
        assertTrue(helper.contains("ACTION_HOME_SETTINGS")) // pre-29 / unavailable fallback
        assertFalse(helper.contains("grantRole"))           // never silently self-grant
    }

    @Test fun `role banner is session-scoped - no persistent nagging`() {
        val main = File("/home/titan/AURA/app/src/main/kotlin/com/aura/MainActivity.kt").readText()
        assertTrue(main.contains("roleBannerDismissed"))
        val helper = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/LauncherRoleHelper.kt").readText()
        assertTrue(helper.contains("isRoleHeld")) // once granted, banner condition false forever
    }

    @Test fun `role decline does not affect resolver or executor paths`() {
        // Banner state is UI-only; router construction is independent of role state.
        val index = L0IndexFactory.demoIndex()
        val router = com.aura.resolver.IntentRouter(
            com.aura.resolver.L0Resolver(index),
            com.aura.resolver.l1.L1Resolver(index),
            com.aura.resolver.l2.L2Resolver(index),
            com.aura.resolver.l3.L3Validator(index)
        )
        assertTrue(router.route("chrome") is com.aura.domain.ResolutionOutcome.Act)
    }

    // ---- APP LIBRARY LOGIC ----

    @Test fun `apps filtered to launchable category and sorted alphabetically`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        assertEquals(listOf("#Notes", "42 Dialer", "alpha", "Alpha Bank", "Calculator", "Zebra"),
            sorted.map { it.displayLabel })
    }

    @Test fun `duplicate labels tiebreak by stable package id`() {
        val dup = listOf(app("Same", "com.b"), app("Same", "com.a"))
        val sorted = AppLibraryLogic.appsFromIndex(dup)
        assertEquals("app:com.a", sorted[0].id)
        assertEquals("app:com.b", sorted[1].id)
    }

    @Test fun `search filters loaded list case-insensitively`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        assertEquals(2, AppLibraryLogic.filter(sorted, "alp").size) // Alpha Bank, alpha
        assertEquals(listOf("Calculator"), AppLibraryLogic.filter(sorted, "calc").map { it.displayLabel })
    }

    @Test fun `search matches package name too`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        assertEquals(listOf("Zebra"), AppLibraryLogic.filter(sorted, "com.z").map { it.displayLabel })
    }

    @Test fun `unknown search returns empty`() {
        assertTrue(AppLibraryLogic.filter(AppLibraryLogic.appsFromIndex(apps), "zzzz").isEmpty())
    }

    @Test fun `app launch payload is AuraAction OpenApp through existing path`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        val chosen = sorted.first { it.displayLabel == "Calculator" }
        assertTrue(chosen.action is com.aura.domain.AuraAction.OpenApp)
        assertEquals("com.c", (chosen.action as com.aura.domain.AuraAction.OpenApp).packageName)
    }

    @Test fun `sections group digits and symbols under hash`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        val sections = AppLibraryLogic.sections(sorted)
        assertEquals("#", sections.first().letter)          // 42 Dialer, #Notes
        assertEquals("A", sections[1].letter)
        assertEquals("Z", sections.last().letter)
    }

    @Test fun `rail letters ordered hash first then A-Z`() {
        val rail = AppLibraryLogic.railLetters(AppLibraryLogic.sections(AppLibraryLogic.appsFromIndex(apps)))
        assertEquals(listOf("#", "A", "C", "Z"), rail.map { it.letter })
    }

    @Test fun `section start indices point into the same sorted list - no second catalog`() {
        val sorted = AppLibraryLogic.appsFromIndex(apps)
        val sections = AppLibraryLogic.sections(sorted)
        sections.forEach { s ->
            assertEquals(s.letter, AppLibraryLogic.sectionLetterFor(sorted[s.startIndex].displayLabel))
        }
    }

    // ---- ARCHITECTURE ----

    @Test fun `library logic contains no Android imports`() {
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui/library/AppLibraryLogic.kt").readText()
        assertFalse(src.contains("import android."))
    }

    @Test fun `package refresh is event-driven not polling`() {
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/PackageChangeMonitor.kt").readText()
        assertTrue(src.contains("PACKAGE_ADDED"))
        assertTrue(src.contains("PACKAGE_REMOVED"))
        assertTrue(src.contains("PACKAGE_REPLACED"))
        // Broadcast-driven: no scheduled/persistent work mechanisms
        assertFalse(src.contains("AlarmManager"))
        assertFalse(src.contains("WorkManager"))
        assertFalse(src.contains("scheduleAtFixedRate"))
    }

    @Test fun `no new CommandState introduced`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain/CommandState.kt").readText().let { src ->
            allowed.forEach { s -> assert(src.contains(s)) }
            assertFalse(src.contains("LibraryState") || src.contains("ExecutingState"))
        }
    }
}
