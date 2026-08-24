package com.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing

/**
 * CandidateItem — one option in an ambiguity set (ASK pattern).
 * Equal weight, no pre-selection ever. Each row shows name + one disambiguating fact.
 * Never phone number alone. Full-row tap, 48dp min, non-color focus indicator via border on focus.
 */
@Composable
fun CandidateItem(
    title: String,
    disambiguation: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    val desc = if (disambiguation != null) "$title, $disambiguation" else title

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuraTheme.spacing.touchTargetMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .auraFocusRing(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .semantics { contentDescription = desc }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceBase),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = typography.caption,
                color = colors.textSecondary
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = typography.body,
                color = colors.textPrimary,
                maxLines = 1
            )
            if (disambiguation != null) {
                Text(
                    text = disambiguation,
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
