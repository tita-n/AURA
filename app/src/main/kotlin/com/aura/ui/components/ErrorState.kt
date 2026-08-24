package com.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme

/**
 * ErrorState — inline only. status.error Body + icon, 120ms cross-fade, no shake, no modal.
 * Always paired with non-color signal (icon + copy). Never used for EmptyState.
 */
@Composable
fun ErrorState(
    message: String,
    fallbackLabel: String? = null,
    onFallback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Error: $message" }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = colors.statusError,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = typography.body,
                color = colors.statusError
            )
            if (fallbackLabel != null && onFallback != null) {
                AuraChip(
                    variant = ChipVariant.Secondary(fallbackLabel),
                    onClick = onFallback
                )
            }
        }
    }
}
