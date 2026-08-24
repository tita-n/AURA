package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import kotlinx.coroutines.delay

/**
 * InlineResult — deterministic computed result (math, conversion).
 * Visually distinct from search results: no icon, NumericResult 28/32 500 tabular,
 * original query as Caption receipt. Tap copies (+ success tint).
 * No gradients, no elevation beyond base.
 */
@Composable
fun InlineResult(
    value: String,
    query: String,
    onCopy: () -> Unit,
    onOpenCalculator: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(700)
            copied = false
        }
    }

    val bg = if (copied) colors.statusSuccess.copy(alpha = 0.12f) else colors.surfaceRaised

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(bg)
            .clickable {
                onCopy()
                copied = true
            }
            .auraFocusRing(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .semantics { contentDescription = "$value, result for $query" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = typography.numericResult,
            color = colors.textPrimary
        )
        Text(
            text = query,
            style = typography.caption,
            color = colors.textSecondary
        )
        if (onOpenCalculator != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AuraChip(
                variant = ChipVariant.Secondary("Open Calculator"),
                onClick = onOpenCalculator
            )
        }
        if (copied) {
            Text(
                text = "Copied",
                style = typography.caption,
                color = colors.statusSuccess
            )
        }
    }
}

/**
 * Confirmation inline — alarm/timer phrase at ResultPrimary + Undo.
 * No navigation occurs, Command Bar returns to idle after window.
 */
@Composable
fun InlineConfirmation(
    phrase: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = phrase,
            style = typography.resultPrimary,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        AuraChip(
            variant = ChipVariant.Undo("Undo", onUndo = onUndo),
            onClick = onUndo
        )
    }
}
