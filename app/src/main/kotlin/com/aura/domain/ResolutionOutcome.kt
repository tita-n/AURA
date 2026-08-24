package com.aura.domain

/**
 * ARCHITECTURAL INVARIANT — Design Direction §7.2 / Technical Spec Invariants
 *
 * The UI boundary has exactly two resolution outcomes: ACT and ASK.
 * Internal resolver layers (L0-L3), confidence scores, and model provenance
 * MUST NEVER leak into the presentation layer.
 *
 * Resolver → Validation → ACT or ASK → UI
 * Models propose. Deterministic code validates. Only Deterministic Action Layer executes Android APIs.
 *
 * Forbidden UI states (must never exist):
 * L0Result, L1Result, L2Result, L3Result, AIResult, High/Medium/LowConfidence
 */
sealed interface ResolutionOutcome {
    /**
     * ACT — a validated action exists and can safely be executed.
     * Appearance is identical regardless of whether resolution originated from L0, L1, L2, or validated L3.
     * One dominant result, immediately actionable, no competing candidate.
     */
    data class Act(val result: ResolvedResult) : ResolutionOutcome

    /**
     * ASK — system cannot safely determine a single action, user must choose.
     * A labeled candidate group, no pre-selection, no dominant guessed result.
     */
    data class Ask(val group: CandidateGroup) : ResolutionOutcome

    /** No query / idle — not an error */
    data object Idle : ResolutionOutcome

    /** No matches — calm EmptyState, not an error, never status.error */
    data class Empty(val query: String, val fallbackAction: String? = null) : ResolutionOutcome

    /** Validation or execution failure — inline ErrorState */
    data class Error(val error: CommandError) : ResolutionOutcome
}

/**
 * A validated, executable result — already passed through Deterministic Action Layer validation.
 * Contains no provenance field.
 */
data class ResolvedResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val type: ResultType,
    val action: AuraAction,
    // For inline results (math etc.) the display value is distinct from title
    val inlineValue: String? = null,
    val inlineQuery: String? = null,
    // For contact results with channel chips
    val actionChips: List<ActionChipData> = emptyList(),
    // For successful action with undo
    val undoable: Boolean = false
)

enum class ResultType {
    App,
    Contact,
    Settings,
    Math,
    Conversion,
    Alarm,
    Timer,
    Message,
    Call,
    DeepLink
}

/**
 * Candidate group — the only pattern for uncertain resolution.
 * Label vocabulary is closed: "Which Sarah", "Did you mean", "Choose an action", "Related to ..."
 * Group never has a pre-selected item.
 */
data class CandidateGroup(
    val label: String,
    val candidates: List<CandidateItemData>
) {
    init {
        require(candidates.isNotEmpty()) { "Candidate group must not be empty" }
    }
}

data class CandidateItemData(
    val id: String,
    val title: String,
    val disambiguation: String? = null, // one fact, never phone number alone
    val subtitle: String? = null
)

data class ActionChipData(
    val id: String,
    val label: String
)

/**
 * Pure data action — UI layer must not directly call Android APIs.
 * These actions are validated; execution will happen in Deterministic Action Layer (future).
 * For v0.1 shell, they are fake/sample.
 */
sealed interface AuraAction {
    data class OpenApp(val packageName: String) : AuraAction
    data class Dial(val phoneNumber: String) : AuraAction
    data class SendMessage(val contactId: String, val channel: String) : AuraAction
    data class Copy(val text: String) : AuraAction
    data class OpenCalculator(val expression: String) : AuraAction
    data class SetAlarm(val hour: Int, val minute: Int) : AuraAction
    data class SetTimer(val durationMinutes: Int) : AuraAction
    data class OpenSettings(val panel: String) : AuraAction
    data class SearchPlayStore(val query: String) : AuraAction
    data object NoOp : AuraAction
}

data class CommandError(
    val message: String,
    val fallback: AuraAction? = null
)

/**
 * Closed intent vocabulary for future L3 classifier+extractor — Design Direction §7.5
 * Not used in v0.1 UI, but documented here as the contract L3 will produce.
 */
enum class AuraIntent {
    OPEN_APP,
    CALL_CONTACT,
    SEND_MESSAGE,
    SEND_EMAIL,
    SET_ALARM,
    SET_TIMER,
    OPEN_SETTINGS,
    CALCULATE,
    CONVERT_UNIT,
    SEARCH
}
