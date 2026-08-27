package com.aura.ui.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.domain.*
import com.aura.platform.AppIcon
import com.aura.ui.components.*

/**
 * CommandStateHost — renders the resolution surface above the CommandBar.
 * Implements the ACT/ASK invariant: exactly two patterns, identical regardless of provenance.
 * No resolver layer identity is rendered.
 *
 * Layout: resolution surface expands upward above CommandBar, ≤70% screen height.
 * The CommandBar itself remains fixed 56dp (see HomeScreen).
 */
@Composable
fun CommandStateHost(
    state: CommandState,
    // L3 transient status — not a state, just a subtle overlay
    isResolving: Boolean = false,
    onActExecute: (ResolvedResult) -> Unit,
    onCandidateSelect: (CandidateItemData) -> Unit,
    onActionChipClick: (ActionChipData) -> Unit,
    onCopy: (String) -> Unit,
    onUndo: () -> Unit = {},
    onFallback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp) // ≈70% cap handled by parent; this is inner cap
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.large))
            .background(colors.surfaceRaised)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.gapBetweenGroups)
    ) {
        // L3 StatusIndicator — only visible when isResolving and state is Idle/Input
        if (isResolving) {
            StatusIndicator()
        }

        when (state) {
            is CommandState.Idle -> {
                // Silence is valid — render nothing (no carousel, no suggestions)
            }
            is CommandState.Input -> {
                // Typing but no resolution yet — show nothing or StatusIndicator handled above
            }
            is CommandState.Act -> {
                ActContent(
                    result = state.result,
                    onActExecute = onActExecute,
                    onActionChipClick = onActionChipClick,
                    onCopy = onCopy,
                    onUndo = onUndo
                )
            }
            is CommandState.Ask -> {
                AskContent(
                    group = state.group,
                    onCandidateSelect = onCandidateSelect
                )
            }
            is CommandState.Empty -> {
                EmptyState(
                    message = "No matches",
                    fallbackLabel = "Search Play Store for \"${state.query}\"",
                    onFallback = onFallback
                )
            }
            is CommandState.Error -> {
                ErrorState(
                    message = state.error.message,
                    fallbackLabel = state.error.fallback?.let { "Try alternative" },
                    onFallback = onFallback
                )
            }
        }
    }
}

@Composable
private fun ActContent(
    result: ResolvedResult,
    onActExecute: (ResolvedResult) -> Unit,
    onActionChipClick: (ActionChipData) -> Unit,
    onCopy: (String) -> Unit,
    onUndo: () -> Unit
) {
    // Inline results are the completed action, not a link
    if (result.type == ResultType.Math || result.type == ResultType.Conversion || result.type == ResultType.Time || result.type == ResultType.Date) {
        InlineResult(
            value = result.inlineValue ?: result.title,
            query = result.inlineQuery ?: result.title,
            onCopy = { onCopy(result.inlineValue ?: result.title) },
            onOpenCalculator = null
        )
        return
    }
    if (result.type == ResultType.Alarm || result.type == ResultType.Timer) {
        // Timer/Alarm: show tappable confirmation — tap the card or press IME Search to
        // actually create the system timer/alarm. This keeps execution explicit (user
        // must activate) while matching the expected "tap the result to set" UX.
        // Undo dismisses without claiming cancellation (we cannot identify the exact timer).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onActExecute(result) }
        ) {
            InlineConfirmation(
                phrase = result.title,
                onUndo = onUndo
            )
        }
        return
    }

    // Dominant result — ResultPrimary, accent tint, immediately actionable
    // For apps, show unmodified OS icon via platform helper; for contacts, keep initials/photo placeholder
    val isApp = result.type == ResultType.App && result.action is AuraAction.OpenApp
    ResultItem(
        title = result.title,
        subtitle = result.subtitle,
        selected = true,
        icon = {
            if (isApp) {
                val pkg = (result.action as AuraAction.OpenApp).packageName
                AppIcon(packageName = pkg, label = result.title, contentDescription = result.title)
            } else {
                ResultIconPlaceholder(result.title)
            }
        },
        onClick = { onActExecute(result) }
    )
    // Action chips for message/call — subordinate, never competing in weight
    if (result.actionChips.isNotEmpty()) {
        ResultGroup(label = "ACTIONS") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.actionChips.forEach { chip ->
                    AuraChip(
                        variant = ChipVariant.Action(chip.label),
                        onClick = { onActionChipClick(chip) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AskContent(
    group: CandidateGroup,
    onCandidateSelect: (CandidateItemData) -> Unit
) {
    // No pre-selection — user must choose. No dominant guessed result.
    ResultGroup(label = group.label) {
        group.candidates.forEach { candidate ->
            CandidateItem(
                title = candidate.title,
                disambiguation = candidate.disambiguation ?: candidate.subtitle,
                id = candidate.id,
                onClick = { onCandidateSelect(candidate) }
            )
        }
    }
}
