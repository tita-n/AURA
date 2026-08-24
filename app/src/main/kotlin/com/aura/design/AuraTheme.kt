package com.aura.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * AuraTheme — single source of truth. All composables must read tokens via AuraTheme.*
 * No literals in composables. Zero theme-specific component code — only token resolution.
 * Dark is default per PRD / Design Language.
 */
@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Allow forcing light for previews/tests
    accentOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val baseColors = if (darkTheme) AuraColorTokens.Dark else AuraColorTokens.Light
    val colors = if (accentOverride != null) {
        baseColors.copy(accentDynamic = AuraAccentResolver.resolve(accentOverride, baseColors))
    } else baseColors

    val typography = AuraTypography()
    // Bridge to Material3 — we use MaterialTheme only as a host for Compose infrastructure
    // (ripple, typography propagation, etc.) but our tokens are authoritative, not M3 defaults.
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = colors.accentDynamic,
            onPrimary = colors.surfaceBase,
            background = colors.surfaceBase,
            surface = colors.surfaceBase,
            surfaceVariant = colors.surfaceRaised,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderSubtle,
            error = colors.statusError,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = colors.accentDynamic,
            onPrimary = Color.White,
            background = colors.surfaceBase,
            surface = colors.surfaceBase,
            surfaceVariant = colors.surfaceRaised,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderSubtle,
            error = colors.statusError,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalAuraColors provides colors,
        LocalAuraTypography provides typography,
        LocalAuraSpacing provides AuraSpacing,
        LocalAuraRadius provides AuraRadius,
        LocalAuraElevation provides AuraElevation
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}

object AuraTheme {
    val colors: AuraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAuraColors.current

    val typography: AuraTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAuraTypography.current

    val spacing: AuraSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAuraSpacing.current

    val radius: AuraRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalAuraRadius.current

    val elevation: AuraElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalAuraElevation.current
}
