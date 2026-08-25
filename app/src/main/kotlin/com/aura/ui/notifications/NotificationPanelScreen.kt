package com.aura.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import com.aura.ui.components.AuraChip
import com.aura.ui.components.ChipVariant
import com.aura.domain.NotificationGrouping
import com.aura.domain.NotificationItem
import com.aura.domain.NotificationRules

/**
 * Notification Panel v1.1 (PRD §9.4): triage surface — PRIORITY items individually,
 * OTHER collapsed per app with count + expand. Access-denied shows the contextual
 * explanation; empty shows calm EmptyState. Tokens only, no new visual language.
 */
@Composable
fun NotificationPanelScreen(
    items: List<NotificationItem>,
    accessGranted: Boolean,
    onRequestAccess: () -> Unit,
    onOpenNotification: (NotificationItem) -> Unit,
    onDismissNotification: (NotificationItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AuraTheme.spacing.screenEdge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notifications", style = typography.title, color = colors.textPrimary)
            AuraChip(variant = ChipVariant.Secondary("Close"), onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))

        when {
            !accessGranted -> AccessExplanation(onRequestAccess = onRequestAccess)
            items.isEmpty() -> CalmEmpty()
            else -> NotificationList(
                items = items,
                onOpen = onOpenNotification,
                onDismiss = onDismissNotification
            )
        }
    }
}

@Composable
private fun AccessExplanation(onRequestAccess: () -> Unit) {
    val colors = AuraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "To show your notifications here,\nAURA needs Notification Access.",
            style = AuraTheme.typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        AuraChip(variant = ChipVariant.Action("Open Android Settings"), onClick = onRequestAccess)
    }
}

@Composable
private fun CalmEmpty() {
    val colors = AuraTheme.colors
    Text(
        "No notifications",
        style = AuraTheme.typography.caption,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
    )
}

@Composable
private fun NotificationList(
    items: List<NotificationItem>,
    onOpen: (NotificationItem) -> Unit,
    onDismiss: (NotificationItem) -> Unit
) {
    val model = remember(items) { NotificationGrouping.build(items) }
    var expandedPackage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.gapWithinGroup)
    ) {
        if (model.priority.isNotEmpty()) {
            item(key = "hdr-priority") {
                SectionLabel("PRIORITY")
            }
            model.priority.forEach { item ->
                item(key = item.key) {
                    NotificationRow(item = item, priority = true, onOpen = onOpen, onDismiss = onDismiss)
                }
            }
        }
        if (model.otherGroups.isNotEmpty()) {
            item(key = "hdr-other") {
                SectionLabel("OTHER")
            }
            model.otherGroups.forEach { group ->
                val expanded = expandedPackage == group.packageName
                if (!expanded) {
                    item(key = "grp:${group.packageName}") {
                        GroupRow(group) { expandedPackage = group.packageName }
                    }
                } else {
                    item(key = "grphdr:${group.packageName}") {
                        GroupHeaderExpanded(group.appLabel) { expandedPackage = null }
                    }
                    group.items.forEach { item ->
                        item(key = item.key) {
                            NotificationRow(item = item, priority = false, onOpen = onOpen, onDismiss = onDismiss)
                        }
                    }
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = AuraTheme.typography.label,
        color = AuraTheme.colors.textSecondary,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun GroupRow(group: NotificationGrouping.Group, onExpand: () -> Unit) {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuraTheme.spacing.touchTargetMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .clickable(role = Role.Button, onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${group.appLabel} · ${group.items.size}",
            style = AuraTheme.typography.body,
            color = colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupHeaderExpanded(appLabel: String, onCollapse: () -> Unit) {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(appLabel, style = AuraTheme.typography.label, color = colors.textSecondary)
        Text(
            "Collapse",
            style = AuraTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onCollapse)
                .padding(8.dp)
        )
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    priority: Boolean,
    onOpen: (NotificationItem) -> Unit,
    onDismiss: (NotificationItem) -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    // Priority is communicated by structure (PRIORITY section + weight), not color alone.
    val titleStyle = if (priority) typography.resultPrimary else typography.body

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuraTheme.spacing.touchTargetMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .clickable(role = Role.Button) { onOpen(item) }
            .auraFocusRing()
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            Text(item.appLabel.take(1).uppercase(), style = typography.caption, color = colors.textSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title ?: item.appLabel, style = titleStyle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!item.body.isNullOrBlank() && item.body != item.title) {
                Text(item.body, style = typography.caption, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(item.appLabel, style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onDismiss(item) }
                .semantics { contentDescription = "Dismiss notification from ${item.appLabel}" },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", style = typography.caption, color = colors.textSecondary)
        }
    }
}
