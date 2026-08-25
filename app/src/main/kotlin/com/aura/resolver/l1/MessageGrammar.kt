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
 * Message — message Sarah, text Sarah, tell Sarah I'll be there in 20
 * Deterministic, no NLU. Only explicitly defined patterns.
 * Reuses L0Index for contact resolution.
 */
class MessageGrammar(
    private val index: L0Index
) : L1Grammar {
    override fun name() = "Message"

    // Order matters: more specific with message body first
    private val patterns = listOf(
        Regex("""^\s*tell\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*message\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*text\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*send\s+(?:a\s+)?message\s+to\s+(.+)$""", RegexOption.IGNORE_CASE)
    )

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmedRaw = raw.trim()
        val trimmedNorm = normalized.trim()
        // Try raw first, then normalized
        var remainder: String? = null
        for (p in patterns) {
            val m = p.matchEntire(trimmedRaw) ?: p.matchEntire(trimmedNorm)
            if (m != null) {
                remainder = m.groupValues[1].trim()
                break
            }
        }
        if (remainder == null) return L1Result.Unrecognized
        if (remainder.isEmpty()) return L1Result.Invalid("Missing recipient")

        // Direct number message — "message +15551234567 hello": first token phone-shaped
        // -> composer to that number; remaining words become the body.
        val firstToken = remainder.trim().split(Regex("\\s+"))[0]
        if (TargetPatterns.isPhoneLike(firstToken)) {
            val body = remainder.trim().removePrefix(firstToken).trim().takeIf { it.isNotBlank() }
            return L1Result.Resolved(
                ResolvedResult(
                    id = "sms:${firstToken.replace(" ", "")}",
                    title = firstToken,
                    subtitle = body?.let { "\"$it\"" },
                    type = ResultType.Message,
                    action = AuraAction.SendMessage(
                        contactId = "",
                        channel = "default",
                        message = body,
                        phone = firstToken
                    )
                )
            )
        }

        // Resolve contact + optional message via longest prefix match
        val (entity, message) = resolveContactWithMessage(remainder) ?: return L1Result.Unrecognized

        // Check for ambiguity: multiple contacts with same normalized name
        val normalizedRecipient = Normalizer.normalize(entity.displayLabel)
        val allForName = index.lookup(normalizedRecipient).let { r ->
            when (r) {
                is com.aura.resolver.L0LookupResult.Exact -> r.entities.filter { it.category == EntityCategory.Contact }
                else -> emptyList()
            }
        }
        if (allForName.size > 1) {
            // Ambiguous recipient
            val items = allForName.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
            return L1Result.Ambiguous(CandidateGroup("Which ${entity.displayLabel}", items))
        }

        // Single contact -> Act
        return L1Result.Resolved(
            ResolvedResult(
                id = entity.id,
                title = entity.displayLabel,
                subtitle = if (message != null) "\"$message\"" else entity.disambiguation,
                type = ResultType.Message,
                action = AuraAction.SendMessage(contactId = entity.id.removePrefix("contact:"), channel = "default", message = message, phone = entity.phones.firstOrNull()),
                actionChips = entity.actionChips
            )
        )
    }

    /**
     * Try to find longest prefix of remainder that matches a contact exactly.
     * Returns (entity, leftoverMessage) or null if no contact found.
     */
    private fun resolveContactWithMessage(remainder: String): Pair<com.aura.resolver.IndexedEntity, String?>? {
        val tokens = remainder.trim().split(Regex("\\s+"))
        // Try longest prefix first (to support multi-word names like "Sarah M.")
        for (len in tokens.size downTo 1) {
            val candidateName = tokens.take(len).joinToString(" ")
            val norm = Normalizer.normalize(candidateName)
            val lookup = index.lookup(norm)
            if (lookup is com.aura.resolver.L0LookupResult.Exact) {
                val contacts = lookup.entities.filter { it.category == EntityCategory.Contact }
                if (contacts.isNotEmpty()) {
                    // Found contact(s) for this prefix; pick first for now, ambiguity handled by caller
                    val entity = contacts.first()
                    val leftover = if (len < tokens.size) tokens.drop(len).joinToString(" ") else null
                    val message = leftover?.takeIf { it.isNotBlank() }
                    return entity to message
                }
            }
        }
        return null
    }
}
