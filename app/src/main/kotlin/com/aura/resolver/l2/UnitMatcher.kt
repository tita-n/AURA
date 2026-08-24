package com.aura.resolver.l2

/**
 * Unit conversion semantic matcher — handles "convert 10 kilometers to miles"
 * Normalizes to "10 km in miles" then delegates to UnitGrammar.
 */
class UnitMatcher {
    fun match(normalized: String, raw: String): L2Result {
        val trimmed = normalized.trim()
        // Patterns: "convert 10 kilometers to miles", "convert 10 km to miles"
        val convertPattern = Regex("""^\s*convert\s+(.+)$""", RegexOption.IGNORE_CASE)
        val m = convertPattern.matchEntire(trimmed) ?: convertPattern.matchEntire(raw.trim().lowercase()) ?: return L2Result.Unrecognized
        var inner = m.groupValues[1].trim()
        // Normalize unit aliases: kilometers -> km, pounds -> lb etc. will be handled by UnitGrammar aliases, but we need to ensure conversion
        // Map full words to short forms for UnitGrammar
        inner = inner.replace(Regex("""\bkilometers\b""", RegexOption.IGNORE_CASE), "km")
        inner = inner.replace(Regex("""\bkilometer\b""", RegexOption.IGNORE_CASE), "km")
        inner = inner.replace(Regex("""\bpounds\b""", RegexOption.IGNORE_CASE), "pounds") // already alias
        inner = inner.replace(Regex("""\bkilograms\b""", RegexOption.IGNORE_CASE), "kg")
        inner = inner.replace(Regex("""\bkilogram\b""", RegexOption.IGNORE_CASE), "kg")
        // Ensure it still contains "in" or "to" — if not, unrecognized
        if (!inner.contains(" in ") && !inner.contains(" to ")) return L2Result.Unrecognized

        val unitGrammar = com.aura.resolver.l1.UnitGrammar()
        return when (val r = unitGrammar.parse(inner, inner)) {
            is com.aura.resolver.l1.L1Result.Resolved -> L2Result.Resolved(r.result)
            is com.aura.resolver.l1.L1Result.Invalid -> L2Result.Invalid(r.message)
            else -> L2Result.Unrecognized
        }
    }
}
