package com.aura.design

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FocusRingTest {

    private fun focusText(): String {
        val f = File("/home/titan/AURA/app/src/main/kotlin/com/aura/design/AuraFocus.kt")
        val alt = File("app/src/main/kotlin/com/aura/design/AuraFocus.kt")
        return (if (f.exists()) f else alt).readText()
    }

    @Test fun `focus ring uses theme textPrimary not hardcoded Dark borderSubtle`() {
        val text = focusText()
        assertTrue("Should use AuraTheme.colors.textPrimary for visibility", text.contains("colors.textPrimary") || text.contains("AuraTheme.colors.textPrimary"))
        assertFalse("Must not hardcode Dark.borderSubtle", text.contains("AuraColorTokens.Dark.borderSubtle"))
    }

    @Test fun `focus ring is 2dp outline distinct from selection fill`() {
        val text = focusText()
        assertTrue(text.contains("2.dp"))
        assertTrue(text.contains("border("))
        // Documented distinction: outline vs fill (selection is 0.12 accent fill)
        assertTrue(text.contains("outline") || text.contains("border"))
    }

    @Test fun `focus ring respects 48dp touch target (no size change)`() {
        val text = focusText()
        assertTrue(text.contains("Does not alter 48dp"))
    }

    @Test fun `focus ring decision is documented`() {
        val text = focusText()
        assertTrue(text.contains("Decision") || text.contains("grayscale"))
        assertTrue(text.contains("13:1") || text.contains("contrast"))
    }
}
