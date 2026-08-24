package com.aura.resolver

import com.aura.domain.CommandError
import com.aura.domain.ResolutionOutcome
import com.aura.domain.toCommandState
import com.aura.resolver.l1.L1Resolution
import com.aura.resolver.l1.L1Resolver
import com.aura.resolver.l2.L2Resolver
import com.aura.resolver.l2.L2Result
import com.aura.resolver.l3.L3ValidationResult
import com.aura.resolver.l3.L3Validator

/**
 * Intent Router — L0 (Exact) -> L1 (Deterministic Grammar) -> future L2/L3.
 * L0 always runs before L1; if L0 confidently resolves (Act/Ask), L1 is not run.
 * Maps internal L0/L1 resolutions to public ResolutionOutcome without exposing
 * provenance, confidence, or layer identity.
 */
class IntentRouter(
    private val l0Resolver: L0Resolver,
    private val l1Resolver: L1Resolver? = null,
    private val l2Resolver: L2Resolver? = null,
    private val l3Validator: L3Validator? = null
) {
    private fun validateOrAct(result: com.aura.domain.ResolvedResult, rawQuery: String): ResolutionOutcome {
        if (l3Validator == null) return ResolutionOutcome.Act(result)
        return when (val v = l3Validator.validate(result)) {
            is L3ValidationResult.Validated -> ResolutionOutcome.Act(v.action.result)
            is L3ValidationResult.Invalid -> ResolutionOutcome.Error(CommandError(v.message))
            is L3ValidationResult.Unavailable -> ResolutionOutcome.Empty(rawQuery)
        }
    }

    fun route(rawQuery: String): ResolutionOutcome {
        // L0 first — protects <10ms hot path
        when (val l0 = l0Resolver.resolve(rawQuery)) {
            is L0Resolution.Idle -> return ResolutionOutcome.Idle
            is L0Resolution.Resolved -> return validateOrAct(l0.result, rawQuery)
            is L0Resolution.Ambiguous -> return ResolutionOutcome.Ask(l0.group)
            is L0Resolution.Unresolved -> { /* fall through to L1 */ }
        }
        // L1 only if L0 unresolved
        if (l1Resolver != null) {
            when (val l1 = l1Resolver.resolve(rawQuery)) {
                is L1Resolution.Idle -> return ResolutionOutcome.Idle
                is L1Resolution.Resolved -> return validateOrAct(l1.result, rawQuery)
                is L1Resolution.Ambiguous -> return ResolutionOutcome.Ask(l1.group)
                is L1Resolution.Invalid -> return ResolutionOutcome.Error(CommandError(l1.message))
                is L1Resolution.Unrecognized -> { /* fall through to L2 */ }
            }
        } else {
            // No L1 and L0 unresolved -> try L2 if available, else Empty
            if (l2Resolver == null) return ResolutionOutcome.Empty(rawQuery)
        }
        // L2 only if L0 and L1 unresolved
        if (l2Resolver != null) {
            when (val l2 = l2Resolver.resolve(rawQuery)) {
                is L2Result.Resolved -> return validateOrAct(l2.result, rawQuery)
                is L2Result.Ambiguous -> return ResolutionOutcome.Ask(l2.group)
                is L2Result.Invalid -> return ResolutionOutcome.Error(CommandError(l2.message))
                is L2Result.Unrecognized -> { /* fall through to L3 unavailable or Empty */ }
            }
        }
        return ResolutionOutcome.Empty(rawQuery)
    }

    fun routeToCommandState(rawQuery: String): com.aura.domain.CommandState {
        return route(rawQuery).toCommandState()
    }
}
