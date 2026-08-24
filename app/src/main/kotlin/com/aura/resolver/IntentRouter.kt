package com.aura.resolver

import com.aura.domain.CommandError
import com.aura.domain.ResolutionOutcome
import com.aura.domain.toCommandState
import com.aura.resolver.l1.L1Resolution
import com.aura.resolver.l1.L1Resolver

/**
 * Intent Router — L0 (Exact) -> L1 (Deterministic Grammar) -> future L2/L3.
 * L0 always runs before L1; if L0 confidently resolves (Act/Ask), L1 is not run.
 * Maps internal L0/L1 resolutions to public ResolutionOutcome without exposing
 * provenance, confidence, or layer identity.
 */
class IntentRouter(
    private val l0Resolver: L0Resolver,
    private val l1Resolver: L1Resolver? = null
) {
    fun route(rawQuery: String): ResolutionOutcome {
        // L0 first — protects <10ms hot path
        when (val l0 = l0Resolver.resolve(rawQuery)) {
            is L0Resolution.Idle -> return ResolutionOutcome.Idle
            is L0Resolution.Resolved -> return ResolutionOutcome.Act(l0.result)
            is L0Resolution.Ambiguous -> return ResolutionOutcome.Ask(l0.group)
            is L0Resolution.Unresolved -> { /* fall through to L1 */ }
        }
        // L1 only if L0 unresolved
        if (l1Resolver != null) {
            return when (val l1 = l1Resolver.resolve(rawQuery)) {
                is L1Resolution.Idle -> ResolutionOutcome.Idle
                is L1Resolution.Resolved -> ResolutionOutcome.Act(l1.result)
                is L1Resolution.Ambiguous -> ResolutionOutcome.Ask(l1.group)
                is L1Resolution.Invalid -> ResolutionOutcome.Error(CommandError(l1.message))
                is L1Resolution.Unrecognized -> ResolutionOutcome.Empty(l1.query)
            }
        }
        // No L1 or L1 unrecognized -> Empty for future L2 escalation; not Error
        return ResolutionOutcome.Empty(rawQuery)
    }

    fun routeToCommandState(rawQuery: String): com.aura.domain.CommandState {
        return route(rawQuery).toCommandState()
    }
}
