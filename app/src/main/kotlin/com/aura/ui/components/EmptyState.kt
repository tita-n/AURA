package com.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme

/**
 * EmptyState — calm, not an error. Never status.error, never apologetic.
 * One sentence + optional one fallback action (e.g. "Search Play Store for ...").
 * Never blank screen.
 */
@Composable
fun EmptyState(
    message: String = "No matches",
    fallbackLabel: String? = null,
    onFallback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = typography.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (fallbackLabel != null && onFallback != null) {
            AuraChip(
                variant = ChipVariant.Secondary(fallbackLabel),
                onClick = onFallback
            )
        }
    }
}
