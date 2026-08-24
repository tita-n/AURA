package com.aura.resolver

import com.aura.domain.*

/**
 * L0 Exact Index resolver — fastest deterministic path.
 * Never calls Android APIs. Produces candidates/data only.
 * Maps directly to ResolutionOutcome without provenance.
 *
 * Behavior:
 * - normalize query
 * - empty -> Idle
 * - exact single -> Act
 * - exact multiple -> Ask (Which <label>)
 * - no exact but single prefix -> Act (only if unambiguous)
 * - no exact but multiple prefix -> Ask (Did you mean)
 * - none -> Unresolved (caller maps to Empty for UI, allowing future L1 escalation)
 */
class L0Resolver(
    private val index: L0Index
) {
    fun resolve(rawQuery: String): L0Resolution {
        val normalized = Normalizer.normalize(rawQuery)
        if (normalized.isEmpty()) return L0Resolution.Idle
        return when (val lookup = index.lookup(normalized)) {
            is L0LookupResult.Exact -> handleExact(lookup.entities, rawQuery)
            is L0LookupResult.Prefix -> handlePrefix(lookup.entities, rawQuery)
            is L0LookupResult.None -> L0Resolution.Unresolved(rawQuery)
        }
    }

    private fun handleExact(entities: List<IndexedEntity>, rawQuery: String): L0Resolution {
        return when {
            entities.size == 1 -> {
                val e = entities.single()
                L0Resolution.Resolved(
                    ResolvedResult(
                        id = e.id,
                        title = e.displayLabel,
                        subtitle = e.subtitle,
                        type = e.resultType,
                        action = e.action,
                        actionChips = e.actionChips
                    )
                )
            }
            entities.size > 1 -> {
                // Multiple exact — must Ask, no preselection
                val candidates = entities.map { e ->
                    CandidateItemData(
                        id = e.id,
                        title = e.displayLabel,
                        disambiguation = e.disambiguation ?: e.subtitle,
                        subtitle = e.subtitle
                    )
                }
                // Use original display label of first entity for "Which X" label — deterministic
                val label = "Which ${entities.first().displayLabel}"
                L0Resolution.Ambiguous(CandidateGroup(label, candidates))
            }
            else -> L0Resolution.Unresolved(rawQuery) // should not happen
        }
    }

    private fun handlePrefix(entities: List<IndexedEntity>, rawQuery: String): L0Resolution {
        return when {
            entities.size == 1 -> {
                val e = entities.single()
                // Single strong prefix — safe to resolve
                L0Resolution.Resolved(
                    ResolvedResult(
                        id = e.id,
                        title = e.displayLabel,
                        subtitle = e.subtitle,
                        type = e.resultType,
                        action = e.action,
                        actionChips = e.actionChips
                    )
                )
            }
            entities.size > 1 -> {
                // Multiple prefix — Ask with "Did you mean" (closed vocabulary)
                val candidates = entities.take(5).map { e ->
                    CandidateItemData(
                        id = e.id,
                        title = e.displayLabel,
                        disambiguation = e.disambiguation ?: e.subtitle,
                        subtitle = e.subtitle
                    )
                }
                L0Resolution.Ambiguous(CandidateGroup("Did you mean", candidates))
            }
            else -> L0Resolution.Unresolved(rawQuery)
        }
    }
}

/**
 * Internal L0 resolution — not exposed to UI.
 * IntentRouter maps this to public ResolutionOutcome without leaking provenance.
 */
sealed interface L0Resolution {
    data object Idle : L0Resolution
    data class Resolved(val result: ResolvedResult) : L0Resolution
    data class Ambiguous(val group: CandidateGroup) : L0Resolution
    data class Unresolved(val query: String) : L0Resolution
}
