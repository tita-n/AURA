package com.aura.resolver.l1

import com.aura.domain.CandidateGroup
import com.aura.domain.ResolvedResult

/**
 * Deterministic L1 grammar — hand-maintained, no ML, no fuzzy.
 * Receives normalized input (via Normalizer.normalize) + access to L0Index for entity resolution.
 * Must be fast (<5ms), deterministic, auditable.
 */
interface L1Grammar {
    fun name(): String
    fun parse(normalized: String, raw: String): L1Result
}

sealed interface L1Result {
    data class Resolved(val result: ResolvedResult) : L1Result
    data class Ambiguous(val group: CandidateGroup) : L1Result
    data class Invalid(val message: String) : L1Result // recognized but invalid (e.g., 500/0, 25:99)
    data object Unrecognized : L1Result // not this grammar's responsibility
}
