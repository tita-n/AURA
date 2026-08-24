package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Math semantic matcher — handles "what is 500 * 27", "calculate 500 * 27", "compute ..."
 * Delegates to MathGrammar after stripping prefix.
 */
class MathMatcher {
    private val prefixes = listOf(
        Regex("""^\s*what\s+is\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*calculate\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*compute\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*solve\s+(.+)$""", RegexOption.IGNORE_CASE)
    )

    fun match(normalized: String, raw: String): L2Result {
        var expr: String? = null
        val trimmed = raw.trim()
        for (p in prefixes) {
            val m = p.matchEntire(trimmed) ?: p.matchEntire(normalized)
            if (m != null) { expr = m.groupValues[1]; break }
        }
        if (expr == null) return L2Result.Unrecognized
        // Delegate to MathGrammar
        val mathGrammar = com.aura.resolver.l1.MathGrammar()
        return when (val r = mathGrammar.parse(expr.lowercase(), expr)) {
            is com.aura.resolver.l1.L1Result.Resolved -> L2Result.Resolved(r.result)
            is com.aura.resolver.l1.L1Result.Invalid -> L2Result.Invalid(r.message)
            else -> L2Result.Unrecognized
        }
    }
}
