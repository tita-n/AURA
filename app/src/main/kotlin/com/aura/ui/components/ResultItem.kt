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

/**
 * ResultItem — single actionable row. Shared anatomy for all result types:
 * 40dp icon container + primary label + optional secondary.
 * Row height is consistent across types — never varies by result type.
 * No divider between rows of same kind — spacing does grouping (8dp).
 * Minimum 48dp touch target — enforced via modifier.
 */
@Composable
fun ResultItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable () -> Unit = { ResultIconPlaceholder(title) },
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    val bg = if (selected) colors.accentDynamic.copy(alpha = 0.12f) else colors.surfaceRaised
    val contentDesc = if (subtitle != null) "$title, $subtitle" else title

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuraTheme.spacing.touchTargetMin)
            .clip(AuraTheme.radius.small.let { androidx.compose.foundation.shape.RoundedCornerShape(it) })
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDesc }
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
            icon()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = if (selected) typography.resultPrimary else typography.body,
                color = colors.textPrimary,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ResultIconPlaceholder(label: String, modifier: Modifier = Modifier) {
    val colors = AuraTheme.colors
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = label.take(1).uppercase(),
            style = AuraTheme.typography.caption,
            color = colors.textSecondary
        )
    }
}

/**
 * AppResult — unmodified OS icon variant (Design Language §16)
 * ContactResult — photo variant; for v0.1 shell, placeholder with same anatomy.
 * They are semantic wrappers around ResultItem to keep one system.
 */
@Composable
fun AppResult(
    appName: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResultItem(
        title = appName,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ContactResult(
    name: String,
    disambiguation: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResultItem(
        title = name,
        subtitle = disambiguation,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    )
}
