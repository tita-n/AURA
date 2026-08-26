package com.aura.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.L0IndexFactory
import com.aura.platform.AppIcon
import com.aura.resolver.IndexedEntity
import com.aura.ui.components.AuraChip
import com.aura.ui.components.ChipVariant

/**
 * App Library (PRD §9.3 MVP): single scrollable alphabetical list, live search over the
 * loaded index, fast-scroll rail, strict-alphabetical by construction. Visually subordinate
 * to the Command Bar; same tokens, no new visual language.
 */
@Composable
fun AppLibraryScreen(
    apps: List<IndexedEntity>,
    onLaunch: (ResolvedResult) -> Unit,
    onClose: () -> Unit,
    wallpaperEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    var searchQuery by remember { mutableStateOf("") }
    var pendingScrollLetter by remember { mutableStateOf<String?>(null) }

    val sorted = remember(apps) { AppLibraryLogic.appsFromIndex(apps) }
    val filtered = remember(sorted, searchQuery) { AppLibraryLogic.filter(sorted, searchQuery) }
    val sections = remember(filtered) { AppLibraryLogic.sections(filtered) }
    val rail = remember(sections) { AppLibraryLogic.railLetters(sections) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Active rail letter, coupled to the actual scroll position (not faked).
    // firstVisibleItemIndex includes the search header, so offset by HEADER_OFFSET.
    val activeIndex by remember {
        derivedStateOf {
            AppLibraryRail.activeSectionIndex(
                sections,
                (listState.firstVisibleItemIndex - AppLibraryRail.HEADER_OFFSET).coerceAtLeast(0)
            )
        }
    }
    val activeLetter = rail.getOrNull(activeIndex)?.letter

    // Jump to section when a rail letter is picked (nearest available if missing).
    LaunchedEffect(pendingScrollLetter, sections) {
        pendingScrollLetter?.let { letter ->
            val target = AppLibraryRail.targetIndexForLetter(sections, letter)
            if (target != null) listState.scrollToItem(AppLibraryRail.listScrollIndex(target))
            pendingScrollLetter = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Dark, calm, translucent AURA surface over the wallpaper — not a separate
            // bright application screen. Opaque only when wallpaper is off.
            .background(colors.surfaceBase.copy(alpha = if (wallpaperEnabled) 0.92f else 1f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AuraTheme.spacing.screenEdge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Apps", style = typography.title, color = colors.textPrimary)
            AuraChip(variant = ChipVariant.Secondary("Close"), onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            })
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "search", contentType = "search") {
                    Column {
                        LibrarySearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            "No matches",
                            style = typography.caption,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        )
                    }
                } else {
                    var lastLetter: String? = null
                    filtered.forEachIndexed { idx, app ->
                        val letter = AppLibraryLogic.sectionLetterFor(app.displayLabel)
                        if (letter != lastLetter) {
                            item(key = "hdr:$letter:$idx", contentType = "header") {
                                Text(
                                    letter,
                                    style = typography.label,
                                    color = colors.textSecondary,
                                    modifier = Modifier
                                        .padding(top = 16.dp, bottom = 4.dp)
                                        .semantics { heading() }
                                )
                            }
                            lastLetter = letter
                        }
                        item(key = app.id, contentType = "app") {
                            LibraryAppRow(
                                app = app,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onLaunch(
                                        ResolvedResult(
                                            id = app.id,
                                            title = app.displayLabel,
                                            subtitle = null,
                                            type = ResultType.App,
                                            action = app.action
                                        )
                                    )
                                }
                            )
                        }
                    }
                    item(key = "bottomPad", contentType = "pad") { Spacer(Modifier.height(24.dp)) }
                }
            }

            // Fast-scroll rail. RTL-aware (CenterEnd mirrors), 48dp targets, keyboard accessible.
            // It lists ALL present letters and is itself independently scrollable, while also
            // auto-following the active app-list section so every letter — including those
            // far down the alphabet — stays reachable. (The previous static Column centered
            // and clipped to ~A–O, hiding later letters behind the screen edge.)
            if (!rail.isNullOrEmpty() && filtered.size >= 8) {
                val railState = rememberLazyListState()
                // Keep the active letter visible in the rail as the app list scrolls.
                LaunchedEffect(activeIndex) {
                    if (rail.isNotEmpty()) railState.scrollToItem(activeIndex.coerceIn(0, rail.lastIndex))
                }
                LazyColumn(
                    state = railState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(min = 48.dp)
                        .semantics { contentDescription = "Alphabetical index" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(rail.size) { i ->
                        val section = rail[i]
                        val isHash = section.letter == "#"
                        val isActive = section.letter == activeLetter
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) colors.textPrimary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (isHash) "Jump to symbols" else "Jump to ${section.letter}"
                                ) { pendingScrollLetter = section.letter }
                                .auraFocusRing()
                                .semantics {
                                    contentDescription = if (isHash) "Symbols" else section.letter
                                    // TalkBack announces the currently visible section.
                                    selected = isActive
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                section.letter,
                                style = typography.caption,
                                color = if (isActive) colors.textPrimary else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = AuraTheme.colors
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = typography().body.copy(color = colors.textPrimary),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AuraTheme.spacing.touchTargetMin)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
                    .background(colors.surfaceRaised)
                    .semantics { contentDescription = "Filter apps" }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("Filter apps", style = typography().body, color = colors.textSecondary)
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Filter apps" }
    )
}

// small local accessor to avoid importing theme object into lambda capture confusion
@Composable private fun typography() = AuraTheme.typography

@Composable
private fun LibraryAppRow(app: IndexedEntity, onClick: () -> Unit) {
    val colors = AuraTheme.colors
    val pkg = app.id.removePrefix("app:")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuraTheme.spacing.touchTargetMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .clickable(role = Role.Button, onClickLabel = "Open ${app.displayLabel}", onClick = onClick)
            .auraFocusRing()
            .semantics(mergeDescendants = true) { contentDescription = app.displayLabel }
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            AppIcon(packageName = pkg, label = app.displayLabel, contentDescription = null, iconSize = 32.dp, modifier = Modifier.clip(CircleShape))
        }
        Text(app.displayLabel, style = AuraTheme.typography.body, color = colors.textPrimary, maxLines = 1)
    }
}
