package com.aura.domain

/**
 * UI State — Design Direction §7.2 locked.
 * Unidirectional flow. L3 internal Resolving never leaks — StatusIndicator is transient overlay, not a state.
 *
 * Forbidden explosion: Typing, LoadingAlmostDone, AIThinking, MediumConfidence etc. must never appear.
 */
sealed interface CommandState {
    data object Idle : CommandState
    data class Input(val query: String) : CommandState
    data class Act(val result: ResolvedResult) : CommandState
    data class Ask(val group: CandidateGroup) : CommandState
    data class Empty(val query: String) : CommandState
    data class Error(val error: CommandError) : CommandState
}

/**
 * Maps domain ResolutionOutcome to UI CommandState — the boundary where invariant is enforced.
 * No resolver-layer information passes through.
 */
fun ResolutionOutcome.toCommandState(): CommandState = when (this) {
    is ResolutionOutcome.Act -> CommandState.Act(result)
    is ResolutionOutcome.Ask -> CommandState.Ask(group)
    is ResolutionOutcome.Idle -> CommandState.Idle
    is ResolutionOutcome.Empty -> CommandState.Empty(query)
    is ResolutionOutcome.Error -> CommandState.Error(error)
}
