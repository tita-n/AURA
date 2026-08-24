package com.aura.resolver

import com.aura.domain.ResolutionOutcome
import com.aura.domain.toCommandState

/**
 * Minimal Intent Router for L0 — places L0 into existing architecture.
 * Normalizes, delegates to L0, maps to public ResolutionOutcome without exposing
 * unresolved internal state as a new CommandState.
 * Future L1/L2 will be chained here: L0 Unresolved -> L1 -> L2 -> Empty.
 */
class IntentRouter(
    private val l0Resolver: L0Resolver
) {
    fun route(rawQuery: String): ResolutionOutcome {
        return when (val l0 = l0Resolver.resolve(rawQuery)) {
            is L0Resolution.Idle -> ResolutionOutcome.Idle
            is L0Resolution.Resolved -> ResolutionOutcome.Act(l0.result)
            is L0Resolution.Ambiguous -> ResolutionOutcome.Ask(l0.group)
            is L0Resolution.Unresolved -> ResolutionOutcome.Empty(l0.query) // allows future L1 escalation; not Error
        }
    }

    fun routeToCommandState(rawQuery: String): com.aura.domain.CommandState {
        return route(rawQuery).toCommandState()
    }
}
