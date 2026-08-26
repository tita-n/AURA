package com.aura.home

import androidx.compose.ui.graphics.Color

/**
 * Pure wallpaper treatment logic — no Android dependencies.
 *
 * Classifies wallpaper brightness and selects a scrim alpha that keeps
 * AURA's dark UI readable while preserving the user's wallpaper.
 *
 * Dark wallpaper   → low treatment (show mostly naturally)
 * Medium wallpaper → moderate treatment
 * Bright wallpaper → strong treatment
 * VeryBright      → strongest treatment
 *
 * Alphas are tuned so that textPrimary (#F5F3EF) over a *white* wallpaper still
 * hits WCAG 4.5:1 (body) / 3:1 (large). Darker wallpapers need far less because
 * the effective background is already dark. Contrast math lives in
 * WallpaperContrastTest / WallpaperTreatmentTest.
 *
 * The wallpaper is shown behind a transparent window (FLAG_SHOW_WALLPAPER in
 * MainActivity); AURA only controls the darkening scrim drawn in Compose.
 */
enum class WallpaperBrightness { Dark, Medium, Bright, VeryBright }

data class WallpaperTreatment(
    val brightness: WallpaperBrightness,
    val scrimAlpha: Float // 0..1, applied to surfaceBase for the top of the screen
)

object WallpaperTreatmentResolver {

    // sRGB relative-luminance thresholds (0..1).
    private const val DARK_MAX = 0.25f
    private const val MEDIUM_MAX = 0.50f
    private const val BRIGHT_MAX = 0.75f

    // Scrim alphas — each is the minimum that keeps primary text readable on a
    // wallpaper of that class. Bright/VeryBright must stay >= ~0.70 on white.
    const val ALPHA_DARK = 0.50f
    const val ALPHA_MEDIUM = 0.70f
    const val ALPHA_BRIGHT = 0.82f
    const val ALPHA_VERY_BRIGHT = 0.90f

    // Extra darkening added toward the bottom of the screen (Command Bar / dock
    // region) so those controls stay readable on bright wallpapers. Additive only
    // — never makes the top lighter than scrimAlpha alone.
    const val BOTTOM_BONUS = 0.12f

    fun classify(luminance: Float?): WallpaperTreatment {
        if (luminance == null || luminance.isNaN()) {
            // Unknown wallpaper — be conservative and treat it as bright.
            return WallpaperTreatment(WallpaperBrightness.Bright, ALPHA_BRIGHT)
        }
        val l = luminance.coerceIn(0f, 1f)
        return when {
            l < DARK_MAX -> WallpaperTreatment(WallpaperBrightness.Dark, ALPHA_DARK)
            l < MEDIUM_MAX -> WallpaperTreatment(WallpaperBrightness.Medium, ALPHA_MEDIUM)
            l < BRIGHT_MAX -> WallpaperTreatment(WallpaperBrightness.Bright, ALPHA_BRIGHT)
            else -> WallpaperTreatment(WallpaperBrightness.VeryBright, ALPHA_VERY_BRIGHT)
        }
    }

    /** Pure classification from a single representative wallpaper color. */
    fun fromColor(wallpaper: Color?): WallpaperTreatment {
        if (wallpaper == null) return classify(null)
        return classify(luminanceOf(wallpaper))
    }

    /** sRGB relative luminance (0..1) of a Compose color. Pure, no Android deps. */
    fun luminanceOf(c: Color): Float {
        fun linear(v: Float): Float =
            if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * linear(c.red) + 0.7152f * linear(c.green) + 0.0722f * linear(c.blue)
    }

    /** Bottom-of-screen scrim alpha: top alpha + bonus, capped at 1.0. */
    fun bottomAlpha(treatment: WallpaperTreatment): Float =
        (treatment.scrimAlpha + BOTTOM_BONUS).coerceAtMost(1f)
}
