package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * Call contact semantic matcher — synonyms: call, dial, phone, ring, make a call to
 * Also handles typos via edit distance.
 */
class CallMatcher(private val index: L0Index) {
    private val verbs = listOf(
        Regex("""^\s*call\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*dial\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*phone\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*ring\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*make\s+a\s+call\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
    )

    fun match(normalized: String, raw: String): L2Result {
        var remainder: String? = null
        val trimmed = raw.trim()
        for (p in verbs) {
            val m = p.matchEntire(trimmed) ?: p.matchEntire(normalized)
            if (m != null) { remainder = m.groupValues[1].trim(); break }
        }
        if (remainder == null) return L2Result.Unrecognized
        if (remainder.isEmpty()) return L2Result.Invalid("Missing contact")

        val norm = Normalizer.normalize(remainder)
        // Try exact, then fuzzy
        val lookup = index.lookup(norm)
        val contacts = when (lookup) {
            is com.aura.resolver.L0LookupResult.Exact -> lookup.entities.filter { it.category == EntityCategory.Contact }
            is com.aura.resolver.L0LookupResult.Prefix -> lookup.entities.filter { it.category == EntityCategory.Contact }
            else -> emptyList()
        }
        if (contacts.size == 1) {
            val e = contacts.first()
            return L2Result.Resolved(ResolvedResult(id = e.id, title = e.displayLabel, subtitle = e.disambiguation, type = ResultType.Call, action = AuraAction.Dial(e.id.removePrefix("contact:"))))
        }
        if (contacts.size > 1) {
            val items = contacts.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
            return L2Result.Ambiguous(CandidateGroup("Which ${contacts.first().displayLabel}", items))
        }
        // Fuzzy fallback for typo like "sarahh"
        val fuzzy = findContactFuzzy(norm)
        return when (fuzzy) {
            is FuzzyResult.Single -> L2Result.Resolved(ResolvedResult(id = fuzzy.entity.id, title = fuzzy.entity.displayLabel, subtitle = fuzzy.entity.disambiguation, type = ResultType.Call, action = AuraAction.Dial(fuzzy.entity.id.removePrefix("contact:"))))
            is FuzzyResult.Multiple -> {
                val items = fuzzy.entities.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
                L2Result.Ambiguous(CandidateGroup("Which ${fuzzy.entities.first().displayLabel}", items))
            }
            null -> L2Result.Unrecognized
        }
    }

    private sealed interface FuzzyResult {
        data class Single(val entity: com.aura.resolver.IndexedEntity) : FuzzyResult
        data class Multiple(val entities: List<com.aura.resolver.IndexedEntity>) : FuzzyResult
    }
    private fun findContactFuzzy(query: String): FuzzyResult? {
        val contacts = index.allEntities().filter { it.category == EntityCategory.Contact }
        val threshold = if (query.length <= 4) 1 else 2
        val matches = contacts.filter { EditDistance.isFuzzyMatch(query, it.normalizedLabel, threshold) }
        return when {
            matches.isEmpty() -> null
            matches.size == 1 -> FuzzyResult.Single(matches.first())
            else -> FuzzyResult.Multiple(matches.take(5))
        }
    }
}
