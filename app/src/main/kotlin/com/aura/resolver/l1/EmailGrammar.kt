package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer
import com.aura.resolver.TargetPatterns

/**
 * Email — email Sarah, send an email to Sarah, email Sarah hello
 */
class EmailGrammar(
    private val index: L0Index
) : L1Grammar {
    override fun name() = "Email"
    private val patterns = listOf(
        Regex("""^\s*email\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*send\s+(?:an\s+)?email\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
    )

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmedRaw = raw.trim()
        val trimmedNorm = normalized.trim()
        var remainder: String? = null
        for (p in patterns) {
            val m = p.matchEntire(trimmedRaw) ?: p.matchEntire(trimmedNorm)
            if (m != null) { remainder = m.groupValues[1].trim(); break }
        }
        if (remainder == null) return L1Result.Unrecognized
        if (remainder.isEmpty()) return L1Result.Invalid("Missing recipient")

        // Direct address email — "email someone@example.com [body]": first token email-shaped.
        val firstTokenE = remainder.trim().split(Regex("\\s+"))[0]
        if (TargetPatterns.isEmailLike(firstTokenE)) {
            val eBody = remainder.trim().removePrefix(firstTokenE).trim().takeIf { it.isNotBlank() }
            return L1Result.Resolved(
                ResolvedResult(
                    id = "email:${firstTokenE.lowercase()}",
                    title = firstTokenE,
                    subtitle = eBody?.let { "\"$it\"" },
                    type = ResultType.Email,
                    action = AuraAction.SendEmail(contactId = "", emailAddress = firstTokenE, body = eBody)
                )
            )
        }

        val (entity, body) = resolveContactWithBody(remainder) ?: return L1Result.Unrecognized

        val normRecipient = Normalizer.normalize(entity.displayLabel)
        val allForName = index.lookup(normRecipient).let { r ->
            when (r) {
                is com.aura.resolver.L0LookupResult.Exact -> r.entities.filter { it.category == EntityCategory.Contact }
                else -> emptyList()
            }
        }
        if (allForName.size > 1) {
            val items = allForName.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
            return L1Result.Ambiguous(CandidateGroup("Which ${entity.displayLabel}", items))
        }

        return L1Result.Resolved(
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
                    val leftover = if (len < tokens.size) tokens.drop(len).joinToString(" ") else null
                    return entity to leftover?.takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }
}
