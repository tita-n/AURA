package com.aura.resolver.l2

/**
 * Deterministic natural-language → infix-math normalizer for the calculator.
 *
 * Strips word operators and percentage "of" phrasing into the strict arithmetic grammar that
 * [MathGrammar]/[com.aura.resolver.l1.MathParser] already understands:
 *   "500 plus 200"        → "500 + 200"
 *   "25 times 4"           → "25 * 4"
 *   "100 divided by 5"     → "100 / 5"
 *   "10% of 500"           → "(10/100)*500"
 *   "15 percent of 4000"   → "(15/100)*4000"
 *
 * Hard rules (Phase 4C):
 *  - No eval(), no functions, no arbitrary code. Only fixed regex replacements over a known map.
 *  - Returns null unless the result is genuinely an arithmetic expression (contains an operator
 *    and no letters remain), so non-math queries fall through to other resolvers.
 *  - Does NOT resolve the ambiguous lone-percentage case ("10 percent" with no base) — that is
 *    handled by the matcher, which surfaces it as Invalid rather than guessing.
 */
object NaturalMath {

    private val PERCENT_OF = Regex("""(\d+(?:\.\d+)?)\s*percent\s+of\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val PERCENT_SYM_OF = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s+(\d+(?:\.\d+)?)""")

    private val WORD_OPERATORS = listOf(
        Regex("""\bmultiplied\s+by\b""", RegexOption.IGNORE_CASE) to "*",
        Regex("""\bdivided\s+by\b""", RegexOption.IGNORE_CASE) to "/",
        Regex("""\btimes\b""", RegexOption.IGNORE_CASE) to "*",
        Regex("""\bover\b""", RegexOption.IGNORE_CASE) to "/",
        Regex("""\bplus\b""", RegexOption.IGNORE_CASE) to "+",
        Regex("""\bminus\b""", RegexOption.IGNORE_CASE) to "-"
    )

    /**
     * Normalize a natural-language arithmetic query into a strict infix expression, or return
     * null when the input is not arithmetic (so the caller can fall through).
     */
    fun normalize(input: String): String? {
        var s = input.lowercase()
        s = PERCENT_OF.replace(s) { "(${it.groupValues[1]}/100)*${it.groupValues[2]}" }
        s = PERCENT_SYM_OF.replace(s) { "(${it.groupValues[1]}/100)*${it.groupValues[2]}" }
        for ((re, sym) in WORD_OPERATORS) {
            s = re.replace(s, " $sym ")
        }
        s = s.replace(Regex("""\s+"""), " ").trim()
        // Must contain an arithmetic operator to be math.
        if (!s.any { it in "+-*/()" }) return null
        // Any leftover letters means it was not arithmetic — do not force it.
        if (s.any { it.isLetter() }) return null
        return s
    }

    /** True when the input is a percentage with no base value — ambiguous, not resolvable. */
    fun isLonePercentage(input: String): Boolean {
        val s = input.trim().lowercase()
        return LONE_PERCENT.matches(s)
    }

    private val LONE_PERCENT = Regex(
        """^\s*\d+(?:\.\d+)?\s*%?\s*$|^\s*\d+(?:\.\d+)?\s*percent\s*$|^\s*\d+(?:\.\d+)?\s*%\s*of\s*$""",
        RegexOption.IGNORE_CASE
    )
}
