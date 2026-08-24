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
 * Message semantic matcher — handles "send sarah a message", "whatsapp sarah", etc.
 * Synonyms: message, text, chat, send ... message, tell, sms, whatsapp
 */
class MessageMatcher(private val index: L0Index) {
    private val patterns = listOf(
        Regex("""^\s*send\s+(.+?)\s+a\s+message\s*$""", RegexOption.IGNORE_CASE), // send sarah a message
        Regex("""^\s*send\s+a\s+message\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*message\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*text\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*chat\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*tell\s+(.+)$""", RegexOption.IGNORE_CASE), // tell Sarah ...
        Regex("""^\s*whatsapp\s+(.+)$""", RegexOption.IGNORE_CASE)
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

        // Resolve contact with optional message body
        val (entity, message) = resolveContactWithMessage(remainder) ?: return L2Result.Unrecognized

        // Check ambiguity
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
                subtitle = message?.let { "\"$it\"" } ?: entity.disambiguation,
                type = ResultType.Message,
                action = AuraAction.SendMessage(entity.id.removePrefix("contact:"), channel = "default", message = message),
                actionChips = entity.actionChips
            )
        )
    }

    private fun resolveContactWithMessage(remainder: String): Pair<com.aura.resolver.IndexedEntity, String?>? {
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
                    // For "send sarah a message", leftover is "a message" -> not a real message body, treat as no body
                    val message = leftover?.takeIf { it.isNotBlank() && it.lowercase() !in setOf("a message", "message") }
                    return entity to message
                }
            }
        }
        // Try fuzzy for typo like "sarahh" — threshold based on first token, not whole remainder
        val firstTokenNorm = Normalizer.normalize(remainder.trim().split(Regex("\\s+"))[0])
        val threshold = if (firstTokenNorm.length <= 4) 1 else 2
        val contacts = index.allEntities().filter { it.category == EntityCategory.Contact }
        val fuzzy = contacts.filter { EditDistance.isFuzzyMatch(firstTokenNorm, it.normalizedLabel, threshold) }
        if (fuzzy.size == 1) {
            val entity = fuzzy.first()
            // Check if remainder has extra words beyond first token
            val firstToken = remainder.trim().split(Regex("\\s+")).first()
            val leftover = remainder.trim().removePrefix(firstToken).trim().takeIf { it.isNotBlank() }
            val message = leftover?.takeIf { it.lowercase() !in setOf("a message", "message") }
            return entity to message
        }
        if (fuzzy.size > 1) {
            // Multiple fuzzy -> still need to return one for ambiguity check; caller will handle multi
            // Return first to trigger ambiguous path
            return fuzzy.first() to null
        }
        return null
    }
}
