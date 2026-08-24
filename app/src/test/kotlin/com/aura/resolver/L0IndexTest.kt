package com.aura.resolver

import com.aura.domain.ResultType
import com.aura.domain.AuraAction
import org.junit.Assert.*
import org.junit.Test

class L0IndexTest {

    private fun app(label: String, pkg: String = "com.test.${label.lowercase()}") =
        L0IndexFactory.appEntity(pkg, label)

    private fun contact(name: String, id: String, dis: String = "test") =
        L0IndexFactory.contactEntity(id, name, dis)

    @Test
    fun `exact app match`() {
        val index = L0Index.build(listOf(app("Chrome"), app("WhatsApp")))
        val result = index.lookup(Normalizer.normalize("chrome"))
        assertTrue(result is L0LookupResult.Exact)
        assertEquals(1, (result as L0LookupResult.Exact).entities.size)
        assertEquals("Chrome", result.entities.first().displayLabel)
    }

    @Test
    fun `case-insensitive exact`() {
        val index = L0Index.build(listOf(app("Chrome")))
        assertTrue(index.lookup(Normalizer.normalize("CHROME")) is L0LookupResult.Exact)
        assertTrue(index.lookup(Normalizer.normalize("ChRoMe")) is L0LookupResult.Exact)
    }

    @Test
    fun `whitespace normalization for index`() {
        val index = L0Index.build(listOf(app("Chrome")))
        assertTrue(index.lookup(Normalizer.normalize("  chrome  ")) is L0LookupResult.Exact)
        assertTrue(index.lookup(Normalizer.normalize("  CHROME  ")) is L0LookupResult.Exact)
    }

    @Test
    fun `exact beats prefix`() {
        val index = L0Index.build(listOf(
            app("Chrome"),
            app("Chrome Beta")
        ))
        // Query "chrome" exact matches Chrome, should not return prefix Chrome Beta
        val result = index.lookup(Normalizer.normalize("chrome"))
        assertTrue(result is L0LookupResult.Exact)
        assertEquals(1, (result as L0LookupResult.Exact).entities.size)
    }

    @Test
    fun `prefix match single`() {
        val index = L0Index.build(listOf(app("Chrome"), app("WhatsApp")))
        val result = index.lookup(Normalizer.normalize("chr"))
        assertTrue(result is L0LookupResult.Prefix)
        assertEquals(1, (result as L0LookupResult.Prefix).entities.size)
    }

    @Test
    fun `prefix multiple`() {
        val index = L0Index.build(listOf(app("Chrome"), app("Chrome Beta"), app("Calculator")))
        val result = index.lookup(Normalizer.normalize("ch"))
        // Note: "Calculator" doesn't start with "ch", so only 2
        assertTrue(result is L0LookupResult.Prefix)
        assertEquals(2, (result as L0LookupResult.Prefix).entities.size)
    }

    @Test
    fun `multiple exact same normalized`() {
        val index = L0Index.build(listOf(
            contact("Sarah", "1", "a"),
            contact("Sarah", "2", "b")
        ))
        val result = index.lookup(Normalizer.normalize("sarah"))
        assertTrue(result is L0LookupResult.Exact)
        assertEquals(2, (result as L0LookupResult.Exact).entities.size)
    }

    @Test
    fun `no match`() {
        val index = L0Index.build(listOf(app("Chrome")))
        assertTrue(index.lookup(Normalizer.normalize("unknownxyz")) is L0LookupResult.None)
    }

    @Test
    fun `empty query none`() {
        val index = L0Index.build(listOf(app("Chrome")))
        assertTrue(index.lookup("") is L0LookupResult.None)
    }

    @Test
    fun `index size and entities`() {
        val entities = listOf(app("Chrome"), app("WhatsApp"))
        val index = L0Index.build(entities)
        assertEquals(2, index.size())
        assertEquals(2, index.allEntities().size)
    }
}
