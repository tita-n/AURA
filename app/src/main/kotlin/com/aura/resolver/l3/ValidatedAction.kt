package com.aura.resolver.l3

import com.aura.domain.ResolvedResult

/**
 * ValidatedAction — deterministic wrapper for a proposed AuraAction that has been
 * validated against Android/platform reality. Only produced by L3.
 * Contains no Android execution details, no confidence, no provenance.
 */
data class ValidatedAction(
    val result: ResolvedResult
)
