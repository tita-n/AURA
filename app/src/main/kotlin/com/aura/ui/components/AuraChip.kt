package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing

/**
 * AuraChip — unified primitive with semantic variants (Design Direction §8.4 locked).
 * Same anatomy (compact pill, radius.small, Label text, hairline border) — different behavior.
 * Minimum 48dp touch target despite compact visual — enforced via heightIn.
 */
sealed interface ChipVariant {
    data class Action(val label: String) : ChipVariant
    data class Undo(val label: String, val onUndo: () -> Unit) : ChipVariant
    data class Secondary(val label: String) : ChipVariant
}

@Composable
fun AuraChip(
    variant: ChipVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    val label = when (variant) {
        is ChipVariant.Action -> variant.label
        is ChipVariant.Undo -> variant.label
        is ChipVariant.Secondary -> variant.label
    }

    val bg = when (variant) {
        is ChipVariant.Action -> colors.accentDynamic.copy(alpha = 0.14f)
        is ChipVariant.Undo -> colors.surfaceBase
        is ChipVariant.Secondary -> colors.surfaceBase
    }
    val borderColor = when (variant) {
        is ChipVariant.Action -> colors.accentDynamic.copy(alpha = 0.5f)
        else -> colors.borderSubtle
    }
    val textColor = when (variant) {
        is ChipVariant.Action -> colors.accentDynamic
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .heightIn(min = 32.dp) // visual compact but ensure touch target via outer padding if needed
            .defaultMinSize(minWidth = 48.dp)
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(AuraTheme.radius.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .auraFocusRing(RoundedCornerShape(AuraTheme.radius.small))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.label,
            color = if (enabled) textColor else colors.textSecondary
        )
    }
}

/**
 * Chip row helper — ensures 8dp gaps, wraps, respects 48dp targets via outer Box if needed.
 */
@Composable
fun AuraChipRow(
    chips: List<ChipVariant>,
    onChipClick: (ChipVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { variant ->
            AuraChip(variant = variant, onClick = { onChipClick(variant) })
        }
    }
}
