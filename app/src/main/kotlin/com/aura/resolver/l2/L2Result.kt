package com.aura.resolver.l2

import com.aura.domain.CandidateGroup
import com.aura.domain.ResolvedResult

/**
 * L2 result — never exposes confidence, provenance, embeddings, scores.
 * UI only receives ResolutionOutcome.
 */
sealed interface L2Result {
    data class Resolved(val result: ResolvedResult) : L2Result
    data class Ambiguous(val group: CandidateGroup) : L2Result
    data class Invalid(val message: String) : L2Result
    data object Unrecognized : L2Result
}
