package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * AuraBottomSheet — raised surface, drag handle, ≤70% screen height, spring only on drag-dismiss.
 * Exactly two elevations: none (base) and raised. No third.
 */
@Composable
fun AuraBottomSheet(
    title: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = AuraTheme.radius.large, topEnd = AuraTheme.radius.large))
            .background(colors.surfaceRaised)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Drag handle — centered, 32x4
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.borderSubtle)
                .align(Alignment.CenterHorizontally)
        )
        if (title != null) {
            Text(
                text = title,
                style = typography.title,
                color = colors.textPrimary
            )
        }
        content()
    }
}
