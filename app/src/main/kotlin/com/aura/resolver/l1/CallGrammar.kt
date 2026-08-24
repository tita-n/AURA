package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * Call — call dad, call Sarah
 * Reuses L0Index for contact lookup (no duplicate logic, no ContactsContract).
 */
class CallGrammar(
    private val index: L0Index
) : L1Grammar {
    override fun name() = "Call"
    private val pattern = Regex("""^\s*call\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val m = pattern.matchEntire(raw.trim()) ?: pattern.matchEntire(normalized) ?: return L1Result.Unrecognized
        val nameRaw = m.groupValues[1].trim()
        if (nameRaw.isEmpty()) return L1Result.Invalid("Missing contact name")
        val nameNorm = Normalizer.normalize(nameRaw)
        if (nameNorm.isEmpty()) return L1Result.Invalid("Invalid name")

        // Reuse L0 index — filter to contacts only
        val candidates = index.lookup(nameNorm).let { result ->
            when (result) {
                is com.aura.resolver.L0LookupResult.Exact -> result.entities.filter { it.category == EntityCategory.Contact }
                is com.aura.resolver.L0LookupResult.Prefix -> result.entities.filter { it.category == EntityCategory.Contact }
                else -> emptyList()
            }
        }

        return when {
            candidates.isEmpty() -> L1Result.Unrecognized // no contact -> let router escalate, don't invent
            candidates.size == 1 -> {
                val e = candidates.single()
                L1Result.Resolved(
                    ResolvedResult(
                        id = e.id,
                        title = e.displayLabel,
                        subtitle = e.disambiguation,
                        type = ResultType.Call,
                        action = AuraAction.Dial(e.id.removePrefix("contact:"))
                    )
                )
            }
            else -> {
                val items = candidates.map { e ->
                    CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle)
                }
                L1Result.Ambiguous(CandidateGroup("Which ${candidates.first().displayLabel}", items))
            }
        }
    }
}
