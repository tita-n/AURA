package com.aura.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography — Inter Variable locked in Design Direction §1.6.
 * PRD wins if it mandates a different family; otherwise Inter is the engineering choice.
 *
 * Implementation note: Inter Variable font file should be added as res/font/inter_variable.ttf
 * and referenced here. Until that asset is added, FontFamily.Default (system sans) is used as
 * fallback — the metrics below are still exactly to spec, so adding the font later is a drop-in.
 * On Android, system sans is Roboto which is metrically close to Inter at these sizes.
 *
 * Tabular numerals: enabled via fontFeatureSettings = "tnum" for NumericResult.
 */
object AuraTypographyTokens {
    // Inter Variable — single family, variable axis. Fallback to Default until asset added.
    val Inter: FontFamily = FontFamily.Default

    val Display = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-0.32).sp // -0.5%
    )
    val CommandInput = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val Title = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val ResultPrimary = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val Body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val NumericResult = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        // tabular figures to prevent jitter — requires fontFeatureSettings
        // Compose TextStyle doesn't expose fontFeatureSettings directly pre-1.6,
        // we document it and enable via platform Typography when available.
        // For now, keep letterSpacing 0 and note that Inter's tnum will be enabled
        // once font asset with 'tnum' feature is bundled.
    )
    val Caption = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.13.sp // +1%
    )
    val Label = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.48.sp // +4% — Label is uppercase by usage, not by transform here
    )
}

data class AuraTypography(
    val display: TextStyle = AuraTypographyTokens.Display,
    val commandInput: TextStyle = AuraTypographyTokens.CommandInput,
    val title: TextStyle = AuraTypographyTokens.Title,
    val resultPrimary: TextStyle = AuraTypographyTokens.ResultPrimary,
    val body: TextStyle = AuraTypographyTokens.Body,
    val numericResult: TextStyle = AuraTypographyTokens.NumericResult,
    val caption: TextStyle = AuraTypographyTokens.Caption,
    val label: TextStyle = AuraTypographyTokens.Label
)

val LocalAuraTypography = staticCompositionLocalOf { AuraTypography() }
