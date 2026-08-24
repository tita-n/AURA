package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme

/**
 * StatusIndicator — the ONLY processing acknowledgment permitted in AURA.
 * Exists solely because L3 genuinely takes measurable time (<150ms). Forbidden everywhere else.
 * Subtle, low-amplitude presence at the leading edge of where the result will appear.
 * Must disappear immediately once resolution completes.
 * Never a spinner, never skeleton, never "AI thinking" copy.
 */
@Composable
fun StatusIndicator(
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    Row(
        modifier = modifier
            .semantics { contentDescription = "Resolving" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.accentDynamic.copy(alpha = 0.6f))
        )
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(8.dp)
                .clip(RoundedCornerShape@androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(colors.borderSubtle)
        )
    }
}
