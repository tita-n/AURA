package com.aura.library

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppLibraryAccessibilityTest {

    private fun screenText(): String {
        val f = File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui/library/AppLibraryScreen.kt")
        val alt = File("app/src/main/kotlin/com/aura/ui/library/AppLibraryScreen.kt")
        return (if (f.exists()) f else alt).readText()
    }

    @Test fun `section headers have heading semantics`() {
        val text = screenText()
        assertTrue("Section headers must have heading() for TalkBack", text.contains("heading()"))
    }

    @Test fun `rail has 48dp touch targets`() {
        val text = screenText()
        assertTrue(text.contains("size(48.dp)"))
        assertFalse("Rail must not use 36dp height (was 48x36)", text.contains("height = 36.dp"))
    }

    @Test fun `rail has contentDescription for TalkBack`() {
        val text = screenText()
        assertTrue(text.contains("contentDescription = \"Alphabetical index\"") || text.contains("contentDescription = \"Symbols\""))
        assertTrue(text.contains("Jump to"))
    }

    @Test fun `search field has contentDescription`() {
        val text = screenText()
        assertTrue(text.contains("contentDescription = \"Filter apps\""))
    }

    @Test fun `app rows have merged semantics and button role`() {
        val text = screenText()
        assertTrue(text.contains("mergeDescendants = true"))
        assertTrue(text.contains("onClickLabel = \"Open"))
        assertTrue(text.contains("Role.Button"))
    }

    @Test fun `hash section is accessible as Symbols`() {
        val text = screenText()
        assertTrue(text.contains("\"Symbols\"") || text.contains("Symbols"))
    }

    @Test fun `focus ring is applied`() {
        val text = screenText()
        assertTrue(text.contains("auraFocusRing()"))
    }

    @Test fun `rail is RTL-aware via CenterEnd`() {
        val text = screenText()
        assertTrue(text.contains("CenterEnd"))
    }
}
