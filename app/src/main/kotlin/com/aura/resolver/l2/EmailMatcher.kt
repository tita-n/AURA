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
 * Email semantic matcher — email Sarah, send email to Sarah, mail Sarah
 */
class EmailMatcher(private val index: L0Index) {
    private val patterns = listOf(
        Regex("""^\s*email\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*mail\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*send\s+(?:an\s+)?email\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*send\s+(?:an\s+)?mail\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
    )

    fun match(normalized: String, raw: String): L2Result {
        var remainder: String? = null
        val trimmed = raw.trim()
        for (p in patterns) {
            val m = p.matchEntire(trimmed) ?: p.matchEntire(normalized)
            if (m != null) { remainder = m.groupValues[1].trim(); break }
        }
        if (remainder == null) return L2Result.Unrecognized
        if (remainder.isEmpty()) return L2Result.Invalid("Missing recipient")
        val (entity, body) = resolveContactWithBody(remainder) ?: return L2Result.Unrecognized
        val normRecipient = Normalizer.normalize(entity.displayLabel)
        val allForName = index.lookup(normRecipient).let { r ->
            when (r) {
                is com.aura.resolver.L0LookupResult.Exact -> r.entities.filter { it.category == EntityCategory.Contact }
                else -> emptyList()
            }
        }
        if (allForName.size > 1) {
            val items = allForName.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
            return L2Result.Ambiguous(CandidateGroup("Which ${entity.displayLabel}", items))
        }
        return L2Result.Resolved(
            ResolvedResult(
                id = entity.id,
                title = entity.displayLabel,
                subtitle = body?.let { "\"$it\"" } ?: entity.disambiguation,
                type = ResultType.Email,
                action = AuraAction.SendEmail(contactId = entity.id.removePrefix("contact:"), body = body, emailAddress = entity.emails.firstOrNull())
            )
        )
    }

    private fun resolveContactWithBody(remainder: String): Pair<com.aura.resolver.IndexedEntity, String?>? {
        val tokens = remainder.trim().split(Regex("\\s+"))
        for (len in tokens.size downTo 1) {
            val candidateName = tokens.take(len).joinToString(" ")
            val norm = Normalizer.normalize(candidateName)
            val lookup = index.lookup(norm)
            if (lookup is com.aura.resolver.L0LookupResult.Exact) {
                val contacts = lookup.entities.filter { it.category == EntityCategory.Contact }
                if (contacts.isNotEmpty()) {
                    val entity = contacts.first()
                    val leftover = if (len < tokens.size) tokens.drop(len).joinToString(" ").trim() else null
                    return entity to leftover?.takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }
}
