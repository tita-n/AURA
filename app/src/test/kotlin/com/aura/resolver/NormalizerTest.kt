package com.aura.resolver

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer as JavaNormalizer

class NormalizerTest {

    @Test
    fun `case folding`() {
        assertEquals("chrome", Normalizer.normalize("Chrome"))
        assertEquals("chrome", Normalizer.normalize("CHROME"))
        assertEquals("chrome", Normalizer.normalize("ChRoMe"))
    }

    @Test
    fun `trim leading trailing`() {
        assertEquals("chrome", Normalizer.normalize("  chrome  "))
        assertEquals("chrome", Normalizer.normalize("\tchrome\n"))
    }

    @Test
    fun `collapse repeated whitespace`() {
        assertEquals("hello world", Normalizer.normalize("hello   world"))
        assertEquals("hello world", Normalizer.normalize("hello\tworld"))
        assertEquals("hello world", Normalizer.normalize("hello  \n  world"))
        assertEquals("a b c", Normalizer.normalize("a    b    c"))
    }

    @Test
    fun `unicode NFC`() {
        val nfd = "e\u0301" // e + combining acute
        val nfc = "é"
        // Both should normalize to same
        assertEquals(Normalizer.normalize(nfc), Normalizer.normalize(nfd))
        // Verify it's NFC form
        val expected = JavaNormalizer.normalize(nfc, JavaNormalizer.Form.NFC).lowercase()
        assertEquals(expected, Normalizer.normalize(nfc))
    }

    @Test
    fun `empty and whitespace only`() {
        assertEquals("", Normalizer.normalize(""))
        assertEquals("", Normalizer.normalize("   "))
        assertEquals("", Normalizer.normalize("\t\n "))
        assertTrue(Normalizer.isEffectivelyEmpty("   "))
        assertTrue(Normalizer.isEffectivelyEmpty(""))
        assertFalse(Normalizer.isEffectivelyEmpty("a"))
    }

    @Test
    fun `does not remove meaningful characters`() {
        assertEquals("chrome!", Normalizer.normalize("Chrome!"))
        assertEquals("a-b", Normalizer.normalize("A-B"))
        assertEquals("wifi-settings", Normalizer.normalize("WiFi-Settings"))
        // Should preserve hyphens, numbers, punctuation that are part of label equality
        assertNotEquals("chrome", Normalizer.normalize("chrome!"))
    }

    @Test
    fun `deterministic`() {
        val inputs = listOf("  Chrome  ", "chrome", "CHROME", "  CHROME  ")
        val normalized = inputs.map { Normalizer.normalize(it) }
        assertTrue(normalized.all { it == "chrome" })
    }
}
