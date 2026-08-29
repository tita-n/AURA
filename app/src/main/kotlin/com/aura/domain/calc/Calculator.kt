package com.aura.domain.calc

import java.util.Locale

/**
 * Pure natural-language calculator. Two layers:
 *
 *  1. [preprocess] turns ordinary English math into a strict infix expression:
 *       "what is 10% of 500"        → "500 * (10/100)"
 *       "500 increased by 10%"     → "500 * (1 + 10/100)"
 *       "500 reduced by 20%"       → "500 * (1 - 20/100)"
 *       "15% off 50"               → "50 * (1 - 15/100)"
 *       "10% + 20%"               → "(10/100) + (20/100)"
 *       "25 times 4"               → "25 * 4"
 *  2. [evaluate] runs the controlled [ExpressionParser] (no eval, no arbitrary code).
 *
 * Percentage semantics are explicit: "10% of 500" is 500·0.10, "10% off 50" is 50·0.85, and
 * "10% + 20%" is the sum of two fractions (0.30) — never silently merged into "10% of 20".
 *
 * Safety: malformed input, division/modulo by zero, absurd length, NaN/Infinity all map to
 * [CalculatorResult.Invalid] rather than crashing or returning a wrong number.
 */
sealed interface CalculatorResult {
    data class Ok(val value: Double, val expression: String) : CalculatorResult
    data object Invalid : CalculatorResult
}

object Calculator {
    // Calculator-intent prefixes only — never file verbs ("find", "show me"), so file search
    // keeps its own grammar intact when it runs after the calculator.
    private val LEADING_PREFIX = Regex(
        """^(what'?s|what is|calculate|compute|solve|evaluate|how much is|how many is|find the value of|work out)\b[\s:]*""",
        RegexOption.IGNORE_CASE
    )
    private val INCREASED = Regex(
        """(\d[\d\s.,+\-*/()]*?)\s+(?:increased|raised|up|more|higher|gained)\s+by\s+(\d+(?:\.\d+)?)\s*(?:percent|%)""",
        RegexOption.IGNORE_CASE
    )
    private val DECREASED = Regex(
        """(\d[\d\s.,+\-*/()]*?)\s+(?:decreased|reduced|down|lower|less|cut|dropped)\s+by\s+(\d+(?:\.\d+)?)\s*(?:percent|%)""",
        RegexOption.IGNORE_CASE
    )
    private val PERCENT_OFF = Regex("""(\d+(?:\.\d+)?)\s*%\s+off\s+(\d+(?:\.\d+)?)""")
    private val PERCENT_OF = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s+(\d+(?:\.\d+)?)""")
    private val PERCENT_WORD_OF = Regex(
        """(\d+(?:\.\d+)?)\s+percent\s+of\s+(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    // Standalone percentages with no base ("10% + 20%" -> "(10/100) + (20/100)"), but never when
    // followed by "of"/"off" (those are handled above as a base or a reduction).
    private val STANDALONE_PERCENT = Regex(
        """(\d+(?:\.\d+)?)\s*%(?!\s*(?:of|off))""",
        RegexOption.IGNORE_CASE
    )

    /** Convert an English math query into a strict infix expression, or null if not math. */
    fun preprocess(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        s = LEADING_PREFIX.replace(s, "")
        if (s.isBlank()) return null
        s = INCREASED.replace(s) { "${it.groupValues[1]} * (1 + ${it.groupValues[2]}/100)" }
        s = DECREASED.replace(s) { "${it.groupValues[1]} * (1 - ${it.groupValues[2]}/100)" }
        s = PERCENT_OFF.replace(s) { "(${it.groupValues[2]} * (1 - ${it.groupValues[1]}/100))" }
        s = PERCENT_OF.replace(s) { "(${it.groupValues[2]} * (${it.groupValues[1]}/100))" }
        s = PERCENT_WORD_OF.replace(s) { "(${it.groupValues[2]} * (${it.groupValues[1]}/100))" }
        s = STANDALONE_PERCENT.replace(s) { "(${it.groupValues[1]}/100)" }
        return NaturalMath.normalize(s)
    }

    fun evaluate(expression: String): CalculatorResult {
        return try {
            CalculatorResult.Ok(ExpressionParser.parse(expression), expression)
        } catch (_: Exception) {
            CalculatorResult.Invalid
        }
    }

    /** Native-feeling result formatting: integers without a decimal tail, trimmed decimals. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            val s = String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
            if (s.isEmpty() || s == "-") "0" else s
        }
    }
}
