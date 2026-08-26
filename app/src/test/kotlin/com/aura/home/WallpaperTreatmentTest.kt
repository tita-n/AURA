package com.aura.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

/**
 * Pure wallpaper treatment logic — deterministic, no device needed.
 * Verifies classification, contrast guarantees, and determinism.
 *
 * WCAG formulas: relative luminance per sRGB, contrast = (L1+0.05)/(L2+0.05).
 */
class WallpaperTreatmentTest {

    private fun Color.toLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private fun Color.luminance(): Double {
        val r = toLinear(red).toDouble()
        val g = toLinear(green).toDouble()
        val b = toLinear(blue).toDouble()
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // scrim (surfaceBase) over wallpaper at alpha
    private fun blend(scrim: Color, wallpaper: Color, alpha: Float): Color {
        val a = alpha
        return Color(
            scrim.red * a + wallpaper.red * (1 - a),
            scrim.green * a + wallpaper.green * (1 - a),
            scrim.blue * a + wallpaper.blue * (1 - a),
            1f
        )
    }

    private val scrim = AuraColorTokensDark.surfaceBase // #0A0A0B
    private val primary = AuraColorTokensDark.textPrimary // #F5F3EF

    // Standalone reference to dark tokens (avoid coupling to design package surface getters)
    private object AuraColorTokensDark {
        val surfaceBase = Color(0xFF0A0A0B)
        val textPrimary = Color(0xFFF5F3EF)
    }

    // ---- Classification ----

    @Test fun `dark wallpaper classifies as Dark with 0_50 alpha`() {
        val t = WallpaperTreatmentResolver.classify(0.10f)
        assertEquals(WallpaperBrightness.Dark, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_DARK, t.scrimAlpha, 0f)
    }

    @Test fun `medium wallpaper classifies as Medium with 0_70 alpha`() {
        val t = WallpaperTreatmentResolver.classify(0.40f)
        assertEquals(WallpaperBrightness.Medium, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_MEDIUM, t.scrimAlpha, 0f)
    }

    @Test fun `bright wallpaper classifies as Bright with 0_82 alpha`() {
        val t = WallpaperTreatmentResolver.classify(0.60f)
        assertEquals(WallpaperBrightness.Bright, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_BRIGHT, t.scrimAlpha, 0f)
    }

    @Test fun `very bright wallpaper classifies as VeryBright with 0_90 alpha`() {
        val t = WallpaperTreatmentResolver.classify(0.95f)
        assertEquals(WallpaperBrightness.VeryBright, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_VERY_BRIGHT, t.scrimAlpha, 0f)
    }

    @Test fun `null wallpaper is treated conservatively as bright 0_82`() {
        val t = WallpaperTreatmentResolver.classify(null)
        assertEquals(WallpaperBrightness.Bright, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_BRIGHT, t.scrimAlpha, 0f)
    }

    @Test fun `NaN luminance falls back to bright`() {
        val t = WallpaperTreatmentResolver.classify(Float.NaN)
        assertEquals(WallpaperBrightness.Bright, t.brightness)
        assertEquals(WallpaperTreatmentResolver.ALPHA_BRIGHT, t.scrimAlpha, 0f)
    }

    @Test fun `classification boundaries are inclusive on the lower edge`() {
        assertEquals(WallpaperBrightness.Dark, WallpaperTreatmentResolver.classify(0.249f).brightness)
        assertEquals(WallpaperBrightness.Medium, WallpaperTreatmentResolver.classify(0.251f).brightness)
        assertEquals(WallpaperBrightness.Medium, WallpaperTreatmentResolver.classify(0.499f).brightness)
        assertEquals(WallpaperBrightness.Bright, WallpaperTreatmentResolver.classify(0.501f).brightness)
        assertEquals(WallpaperBrightness.Bright, WallpaperTreatmentResolver.classify(0.749f).brightness)
        assertEquals(WallpaperBrightness.VeryBright, WallpaperTreatmentResolver.classify(0.751f).brightness)
    }

    @Test fun `fromColor black is Dark, white is VeryBright`() {
        assertEquals(WallpaperBrightness.Dark, WallpaperTreatmentResolver.fromColor(Color.Black).brightness)
        assertEquals(WallpaperBrightness.VeryBright, WallpaperTreatmentResolver.fromColor(Color.White).brightness)
    }

    @Test fun `fromColor null is conservative bright`() {
        assertEquals(WallpaperBrightness.Bright, WallpaperTreatmentResolver.fromColor(null).brightness)
    }

    // ---- Contrast guarantees ----

    @Test fun `bright treatment keeps primary readable over white wallpaper`() {
        val effective = blend(scrim, Color.White, WallpaperTreatmentResolver.ALPHA_BRIGHT)
        assertTrue("primary vs white @0.82 should be >=4.5, was ${contrast(primary, effective)}",
            contrast(primary, effective) >= 4.5)
    }

    @Test fun `very bright treatment keeps primary readable over white wallpaper`() {
        val effective = blend(scrim, Color.White, WallpaperTreatmentResolver.ALPHA_VERY_BRIGHT)
        assertTrue("primary vs white @0.90 should be >=4.5, was ${contrast(primary, effective)}",
            contrast(primary, effective) >= 4.5)
    }

    @Test fun `each classified wallpaper meets 4_5_1 for primary text`() {
        // For a representative wallpaper of each class, the chosen alpha must keep primary text >=4.5:1.
        val samples = mapOf(
            "dark" to Color(0xFF101014),     // near-black
            "medium" to Color(0xFF9FA6B2),   // luminance ~0.32 -> Medium
            "bright" to Color(0xFFE6E9EF),   // luminance ~0.78 -> Bright
            "white" to Color.White
        )
        samples.forEach { (name, wp) ->
            val t = WallpaperTreatmentResolver.fromColor(wp)
            val effective = blend(scrim, wp, t.scrimAlpha)
            val c = contrast(primary, effective)
            assertTrue("$name (${t.brightness}, alpha ${t.scrimAlpha}) should meet 4.5:1, was $c", c >= 4.5)
        }
    }

    // ---- Gradient / determinism ----

    @Test fun `bottom alpha is never lighter than top and capped at 1`() {
        WallpaperBrightness.values().forEach { b ->
            val alpha = when (b) {
                WallpaperBrightness.Dark -> WallpaperTreatmentResolver.ALPHA_DARK
                WallpaperBrightness.Medium -> WallpaperTreatmentResolver.ALPHA_MEDIUM
                WallpaperBrightness.Bright -> WallpaperTreatmentResolver.ALPHA_BRIGHT
                WallpaperBrightness.VeryBright -> WallpaperTreatmentResolver.ALPHA_VERY_BRIGHT
            }
            val t = WallpaperTreatment(b, alpha)
            val bottom = WallpaperTreatmentResolver.bottomAlpha(t)
            assertTrue("bottom alpha must be >= top for $b", bottom >= alpha - 1e-6f)
            assertTrue("bottom alpha must be <= 1 for $b", bottom <= 1f)
        }
        // Saturated case caps at 1.0
        val capped = WallpaperTreatmentResolver.bottomAlpha(WallpaperTreatment(WallpaperBrightness.VeryBright, 0.95f))
        assertEquals(1f, capped, 0f)
    }

    @Test fun `classification is deterministic and pure`() {
        val a = WallpaperTreatmentResolver.classify(0.42f)
        val b = WallpaperTreatmentResolver.classify(0.42f)
        assertEquals(a, b)
        val c1 = WallpaperTreatmentResolver.fromColor(Color(0xFF808080))
        val c2 = WallpaperTreatmentResolver.fromColor(Color(0xFF808080))
        assertEquals(c1, c2)
    }

    @Test fun `luminanceOf matches sRGB reference for known colors`() {
        // White ~1.0, black ~0.0, mid-gray ~0.214
        assertEquals(1.0, WallpaperTreatmentResolver.luminanceOf(Color.White).toDouble(), 0.001)
        assertEquals(0.0, WallpaperTreatmentResolver.luminanceOf(Color.Black).toDouble(), 0.001)
        val gray = WallpaperTreatmentResolver.luminanceOf(Color(0xFF808080))
        assertTrue(gray in 0.18..0.24)
    }
}
