package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Percentage — 17% of 450000, 10% of 5000
 * Deterministic, no arbitrary % syntax.
 */
class PercentageGrammar : L1Grammar {
    override fun name() = "Percentage"
    // Case-insensitive, whitespace tolerant, allow decimal numbers
    private val regex = Regex("""^\s*(\d+(?:\.\d+)?)\s*%\s+of\s+(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val m = regex.matchEntire(raw.trim())
        if (m == null) return L1Result.Unrecognized
        val pStr = m.groupValues[1]
        val vStr = m.groupValues[2]
        val p = pStr.toDoubleOrNull() ?: return L1Result.Invalid("Invalid percentage")
        val v = vStr.toDoubleOrNull() ?: return L1Result.Invalid("Invalid value")
        val result = p / 100.0 * v
        val formatted = formatResult(result)
        return L1Result.Resolved(
            ResolvedResult(
                id = "percentage:${raw.trim()}",
                title = formatted,
                subtitle = null,
                type = ResultType.Math, // percentage uses Math inline pathway
                action = AuraAction.Copy(formatted),
                inlineValue = formatted,
                inlineQuery = raw.trim()
            )
        )
    }

    private fun formatResult(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else {
            var s = value.toString()
            if (s.contains("E")) return s
            s = s.trimEnd('0').trimEnd('.')
            s
        }
    }
}
