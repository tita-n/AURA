package com.aura.library

import com.aura.resolver.IndexedEntity
import com.aura.resolver.EntityCategory
import com.aura.domain.ResultType
import com.aura.domain.AuraAction
import com.aura.resolver.Normalizer
import com.aura.ui.library.AppLibraryLogic
import org.junit.Assert.*
import org.junit.Test

class AppLibraryNormalizationTest {

    private fun app(label: String, pkg: String = "com.test.app"): IndexedEntity = IndexedEntity(
        id = "app:$pkg",
        displayLabel = label,
        normalizedLabel = Normalizer.normalize(label),
        category = EntityCategory.App,
        resultType = ResultType.App,
        action = AuraAction.OpenApp(pkg)
    )

    @Test fun `filter trims and lowercases`() {
        val apps = listOf(app("Chrome", "com.chrome"), app("Firefox", "com.firefox"))
        assertEquals(1, AppLibraryLogic.filter(apps, " chrome ").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "CHROME").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "Chrome").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "  CHROME  ").size)
    }

    @Test fun `filter collapses whitespace`() {
        val apps = listOf(app("My App", "com.myapp"))
        // Normalizer collapses multiple spaces, so query with extra spaces must still match
        assertEquals(1, AppLibraryLogic.filter(apps, "my  app").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "my\tapp").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "  my   app  ").size)
    }

    @Test fun `filter uses NFC - composed and decomposed e-acute match`() {
        // café with composed é (U+00E9) vs decomposed e + combining acute (U+0065 U+0301)
        val appComposed = app("café", "com.cafe1") // NFC will keep as café
        val apps = listOf(appComposed)
        val queryDecomposed = "cafe\u0301" // e + combining
        // Both normalize to NFC café, so should match
        assertEquals(1, AppLibraryLogic.filter(apps, queryDecomposed).size)
        assertEquals(1, AppLibraryLogic.filter(apps, "café").size)
    }

    @Test fun `filter does not strip diacritics - cafe vs café are distinct`() {
        val apps = listOf(app("café", "com.example.app"))
        // Normalizer does NOT strip diacritics, so "cafe" should NOT match "café"
        assertEquals(0, AppLibraryLogic.filter(apps, "cafe").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "café").size)
        // But composed vs decomposed same via NFC should match
        assertEquals(1, AppLibraryLogic.filter(apps, "cafe\u0301").size)
    }

    @Test fun `filter is case-insensitive via Normalizer ROOT`() {
        val apps = listOf(app("Chrome", "com.chrome"), app("Firefox", "com.firefox"))
        assertEquals(1, AppLibraryLogic.filter(apps, "chrome").size)
        assertTrue(AppLibraryLogic.filter(apps, "chrome").any { it.displayLabel == "Chrome" })
        assertEquals(1, AppLibraryLogic.filter(apps, "CHROME").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "  ChRoMe  ").size)
    }

    @Test fun `filter matches package id case-insensitively`() {
        val apps = listOf(app("MyApp", "com.Example.MyApp"))
        assertEquals(1, AppLibraryLogic.filter(apps, "com.example.myapp").size)
        assertEquals(1, AppLibraryLogic.filter(apps, "EXAMPLE").size)
    }

    @Test fun `filter empty query returns all`() {
        val apps = listOf(app("A"), app("B"))
        assertEquals(2, AppLibraryLogic.filter(apps, "").size)
        assertEquals(2, AppLibraryLogic.filter(apps, "   ").size)
        assertEquals(2, AppLibraryLogic.filter(apps, "\t\n ").size)
    }

    @Test fun `filter deterministic - same query same result`() {
        val apps = listOf(app("Chrome"), app("Firefox"), app("Settings"))
        val q = " chroMe "
        val r1 = AppLibraryLogic.filter(apps, q)
        val r2 = AppLibraryLogic.filter(apps, q)
        assertEquals(r1, r2)
    }

    @Test fun `appsFromIndex sorts by normalizedLabel not raw displayLabel`() {
        // " chrome " (with spaces) and "Chrome" should sort identically after normalization
        val a1 = app(" chrome ", "com.a")
        val a2 = app("Chrome", "com.b")
        val sorted = AppLibraryLogic.appsFromIndex(listOf(a1, a2))
        // Both have normalized "chrome", tiebreak by id
        assertEquals("com.a", sorted[0].id.removePrefix("app:"))
        assertEquals("com.b", sorted[1].id.removePrefix("app:"))
    }
}
