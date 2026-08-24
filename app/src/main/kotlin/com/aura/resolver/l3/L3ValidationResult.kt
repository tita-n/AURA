package com.aura.resolver.l3

/**
 * L3 validation result — distinguishes Validated, Invalid, Unavailable.
 * Never exposes confidence, provenance, or Android details to UI.
 * UI only receives ResolutionOutcome.
 */
sealed interface L3ValidationResult {
    data class Validated(val action: ValidatedAction) : L3ValidationResult
    data class Invalid(val message: String) : L3ValidationResult
    data class Unavailable(val query: String) : L3ValidationResult
}
