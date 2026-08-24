package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aura.design.AuraTheme

/**
 * AuraDialog — rare, high-cost-confirmation only (Design Language §13).
 * Short reviewed list; adding one requires scrutiny equal to adding a permission.
 * Title + message + ≤2 actions.
 * No decorative animation, no third elevation.
 */
@Composable
fun AuraDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuraTheme.radius.large))
                .background(colors.surfaceRaised)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = typography.title,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = message,
                style = typography.body,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissLabel, color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text(text = confirmLabel, color = colors.accentDynamic)
                }
            }
        }
    }
}
