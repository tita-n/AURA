package com.aura.design

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Accessibility foundations — Design Language §18 / PRD §14.
 * - 48dp min touch targets enforced via modifiers in components
 * - Semantics, screen-reader grouped results, heading roles
 * - Visible focus indicators (non-color) for keyboard/switch access
 * - Dynamic font scaling via sp units (automatic)
 * - RTL-safe layouts (Compose Row/Column handle LayoutDirection automatically)
 * - Reduced-motion: system animator scale 0 shortens durations
 * - Color-independent status: every status color paired with icon/text
 */

@Composable
fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    val scale = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
    }
    // Also check transition scale
    val transitionScale = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
    }
    return scale == 0f || transitionScale == 0f
}

object AuraAccessibility {
    const val minimumTouchTarget = 48 // dp
    const val bodyContrastMin = 4.5
    const val largeContrastMin = 3.0
}
