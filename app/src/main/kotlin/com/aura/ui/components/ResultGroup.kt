package com.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aura.design.AuraTheme

/**
 * ResultGroup — labeled collection of ResultItems.
 * Gap within group = 8dp, between groups = 24dp (≥3× ratio, never dividers within kind).
 * Label vocabulary is closed: SUGGESTED, ACTIONS, ALSO, Did you mean, Which {name}, Choose an action, Related to {name}
 */
@Composable
fun ResultGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.gapWithinGroup)
    ) {
        Text(
            text = label.uppercase(),
            style = typography.label,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 4.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.gapWithinGroup),
            content = content
        )
    }
}
