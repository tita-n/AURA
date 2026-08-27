package com.aura.ui.command

import com.aura.domain.CommandState

/**
 * Deterministic, fully local hint generator for inputs that look like an attempted
 * command but could not be resolved.
 *
 * Design rules (Phase 4B):
 *  - No AI / cloud / network / analytics. Pattern matching only.
 *  - Does NOT call the resolver — it only inspects the already-computed UI state and
 *    the raw query string, so it is cheap and side-effect free.
 *  - Only fires for inputs that strongly resemble a known supported command family
 *    (call / message / timer). Random text and ordinary app-name misses produce no hint.
 *  - A successful or ambiguous resolution (Act / Ask) already has a surface, so no hint.
 *
 * The UI receives the returned [String]? as a clean, product-level signal — no resolver
 * internals, confidence scores, or Android types leak to Compose.
 */
object CommandHint {
    fun suggest(query: String, state: CommandState): String? {
        // A successful or ambiguous resolution already owns the surface.
        if (state is CommandState.Act || state is CommandState.Ask) return null
        val raw = query.trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase()

        callMatch(lower)?.let { name -> return name }
        messageMatch(lower)?.let { name -> return name }
        if (TIMER_RE.containsMatchIn(lower)) return "Try: Set a timer for 10 min"
        return null
    }

    private fun callMatch(lower: String): String? {
        val m = CALL_RE.find(lower) ?: return null
        val rest = m.groups[2]?.value ?: return null
        val name = extractName(rest)
        return if (name != null) "Try: Call $name" else "Try: Call a contact"
    }

    private fun messageMatch(lower: String): String? {
        val m = MSG_RE.find(lower) ?: return null
        val rest = m.groups[2]?.value ?: return null
        val name = extractName(rest)
        return if (name != null) "Try: Message $name" else "Try: Message a contact"
    }

    /**
     * Drop trailing time/qualifier words so "sarah tomorrow" and "sarah on" both reduce
     * to a clean "Sarah".
     */
    private fun extractName(rest: String): String? {
        val cleaned = rest.trim().replace(TRAILING_QUALIFIERS, "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private val CALL_RE = Regex("""(call|phone|dial|ring)\s+(.+)""")
    private val MSG_RE = Regex("""(message|msg|text|tell|whatsapp)\s+(.+)""")
    private val TIMER_RE = Regex("""(set\s+)?(a\s+)?(timer|countdown|stopwatch)""")
    private val TRAILING_QUALIFIERS = Regex("""\s+(at|on|tomorrow|today|in|after|this|next|morning|evening|afternoon|tonight|pm|am)\b.*$""")
}
