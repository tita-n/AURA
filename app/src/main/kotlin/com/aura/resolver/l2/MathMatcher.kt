package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Math semantic matcher — handles "what is 500 * 27", "calculate 500 * 27", "compute ..."
 * Delegates to MathGrammar after stripping prefix.
 */
class MathMatcher {
    private val prefixes = Regex(
        """^\s*(what\s*is|whats|calculate|compute|solve|how\s+much\s+is|how\s+many\s+is|how\s+many|tell\s+me)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun match(normalized: String, raw: String): L2Result {
        val trimmed = raw.trim()
        // Strip a natural-language prefix ("what is", "calculate", …) if present.
        var expr: String? = null
        val m = prefixes.matchEntire(trimmed) ?: prefixes.matchEntire(normalized)
        if (m != null) expr = m.groupValues[2]
        // No prefix: treat the whole query as a potential natural-math expression
        // (e.g. "500 plus 200"); non-math queries fall through to other resolvers.
        if (expr == null) expr = trimmed
        val inner = expr.trim()
        if (inner.isEmpty()) return L2Result.Unrecognized
        // Ambiguous lone percentage (e.g. "10 percent") — do not guess a result.
        if (NaturalMath.isLonePercentage(inner)) {
            return L2Result.Invalid("10% of what? Try e.g. '10% of 500'")
        }
        val mathExpr = NaturalMath.normalize(inner) ?: return L2Result.Unrecognized
        // Delegate to MathGrammar (strict arithmetic parser — no eval).
        val mathGrammar = com.aura.resolver.l1.MathGrammar()
        return when (val r = mathGrammar.parse(mathExpr, mathExpr)) {
            is com.aura.resolver.l1.L1Result.Resolved -> L2Result.Resolved(r.result)
            is com.aura.resolver.l1.L1Result.Invalid -> L2Result.Invalid(r.message)
            else -> L2Result.Unrecognized
        }
    }
}
