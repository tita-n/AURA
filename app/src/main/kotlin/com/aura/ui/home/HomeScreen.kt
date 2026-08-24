package com.aura.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.domain.*
import com.aura.ui.command.CommandBar
import com.aura.ui.command.CommandStateHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HomeScreen — shell per PRD 9.1 and Design Direction §4.2.
 * Vertically composed, generous whitespace, no idle animation.
 * Time (Display 64/68 500) + date + Presence (fixed strings, silence valid) + CommandBar (lower-middle third) + Dock.
 * Zero layout shift after first paint. Redraw only on data change.
 */
@Composable
fun HomeScreen(
    // For shell demo: controlled state is passed in; no resolver wiring yet.
    commandState: CommandState = CommandState.Idle,
    isResolving: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    query: String = "",
    focused: Boolean = false,
    onFocusedChange: (Boolean) -> Unit = {},
    presenceText: String? = null, // null = silence valid
    onActExecute: (ResolvedResult) -> Unit = {},
    onCandidateSelect: (CandidateItemData) -> Unit = {},
    onActionChipClick: (ActionChipData) -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    var internalQuery by remember { mutableStateOf(query) }
    var internalFocused by remember { mutableStateOf(focused) }

    // Sync external query if provided
    LaunchedEffect(query) { internalQuery = query }
    LaunchedEffect(focused) { internalFocused = focused }

    val timeText = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val dateText = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = AuraTheme.spacing.screenEdge)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Time — Display 64/68 500 — dominant typographic element
        Text(
            text = timeText,
            style = typography.display,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = dateText,
            style = typography.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Presence — Body, textSecondary, ONE fixed string or nothing
        if (presenceText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = presenceText,
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Resolution surface — expands upward above CommandBar, ≤70% cap
        // When idle/empty, this renders as silence (no content)
        if (commandState !is CommandState.Idle || isResolving) {
            CommandStateHost(
                state = commandState,
                isResolving = isResolving,
                onActExecute = onActExecute,
                onCandidateSelect = onCandidateSelect,
                onActionChipClick = onActionChipClick,
                onCopy = onCopy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // CommandBar — fixed 56dp, thumb-reachable lower-middle third
        CommandBar(
            query = internalQuery,
            onQueryChange = {
                internalQuery = it
                onQueryChange(it)
            },
            focused = internalFocused,
            onFocusedChange = {
                internalFocused = it
                onFocusedChange(it)
            },
            onClear = {
                internalQuery = ""
                onQueryChange("")
            },
            onSubmit = {},
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dock — 4 icons, no labels (labels only if ≤3, per PRD 11)
        DockPlaceholder()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DockPlaceholder() {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.surfaceRaised, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}
