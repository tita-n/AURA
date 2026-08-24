package com.aura.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Design tokens — Design Language §6, §20 and Design Direction §7.1
 * All values from the 4dp scale. No arbitrary values outside this scale anywhere.
 */
object AuraSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s48 = 48.dp
    val s64 = 64.dp

    // Semantic aliases — per §6 table
    val screenEdge = s24
    val componentPadding = s12 // 12–16 range; default 12, raised to 16 where needed
    val componentPaddingLarge = s16
    val gapWithinGroup = s8
    val gapBetweenGroups = s24 // ≥3× within-group — ratio matters more than absolute
    val commandBarHorizontalCollapsed = s16
    val commandBarHorizontalFocused = 20.dp // scales to 20 on focus — the only non-scale value, explicitly allowed
    val commandBarVertical = s12
    val sectionSpacing = s32
    val sheetTopPadding = s24
    val touchTargetMin = 48.dp
}

object AuraRadius {
    val small = 12.dp // chips, icon containers
    val large = 20.dp // sheets, focused Command Bar field — exactly two values
}

object AuraElevation {
    // level 0 — base, no shadow
    // level 1 — sheets/overlays, single soft shadow 0 8px 24px rgba(0,0,0,0.35)
    val none = 0.dp
    val raised = 8.dp // shadow elevation; the 24px blur is handled via shadow properties
}

object AuraBorder {
    val hairline = 1.dp // border.width.hairline → color.border.subtle
}

object AuraIconSize {
    val action = 24.dp // bare control icons
    val entity = 40.dp // result-row icon containers
}

object AuraComponentHeight {
    val commandBarCollapsed = 56.dp
    val touchTargetMin = 48.dp
}

object AuraMotion {
    // Durations — DL §14 / PRD §13
    const val micro = 120 // result appearance, selection
    const val standard = 180 // Command Bar expand/collapse
    const val structuralMin = 220 // sheets, screen transitions
    const val structuralMax = 250
    const val successMin = 600 // success tint hold
    const val successMax = 800
    const val stagger = 20
    const val staggerMaxRows = 4

    // Easing names — actual easing is cubic bezier per Compose
    const val easingEnter = "ease-out" // entering — arrives promptly
    const val easingExit = "ease-in"
    const val easingDrag = "spring" // only for touch-driven dismissal

    // Hard ceiling — lint assertion: nothing >300ms
    const val ceiling = 300

    // Reduced-motion: system wins over AURA setting (§8.10 locked)
    // When reducedMotion == true, staggers and parallax are shortened/removed
    // but state-change feedback itself is preserved.
}

val LocalAuraSpacing = staticCompositionLocalOf { AuraSpacing }
val LocalAuraRadius = staticCompositionLocalOf { AuraRadius }
val LocalAuraElevation = staticCompositionLocalOf { AuraElevation }
