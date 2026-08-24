package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Timer — timer 10 min, timer 30 seconds, timer 1 hour
 * Parses constrained duration, produces SetTimer(seconds).
 */
class TimerGrammar : L1Grammar {
    override fun name() = "Timer"

    private val pattern = Regex("""^\s*timer\s+(\d+(?:\.\d+)?)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmed = raw.trim()
        // Must start with timer — otherwise Unrecognized (let other grammars try)
        if (!trimmed.lowercase().startsWith("timer")) return L1Result.Unrecognized

        // Distinguish recognized-but-invalid from unrecognized L2 phrasings:
        // - "timer 10" / "timer 10 lightyears" / "timer 0 min" -> number present -> Invalid (recognized, bad value)
        // - "timer for 10 minutes" / "timer of 5 secs"         -> filler word   -> Unrecognized (L2 semantic variant)
        val rest = trimmed.lowercase().removePrefix("timer").trim()
        if (rest.isEmpty()) return L1Result.Unrecognized
        val firstToken = rest.split(Regex("\\s+"))[0]
        val startsWithNumber = Regex("^\\d").containsMatchIn(firstToken)
        if (!startsWithNumber) return L1Result.Unrecognized

        val m = pattern.matchEntire(trimmed) ?: pattern.matchEntire(normalized) ?: return L1Result.Invalid("Invalid timer format. Use: timer 10 min")
        val numStr = m.groupValues[1]
        val unitStr = m.groupValues[2].lowercase()
        val num = numStr.toDoubleOrNull() ?: return L1Result.Invalid("Invalid number")
        if (num <= 0) return L1Result.Invalid("Duration must be positive")
        val seconds = when (unitStr) {
            "s", "sec", "secs", "second", "seconds" -> num
            "m", "min", "mins", "minute", "minutes" -> num * 60
            "h", "hr", "hrs", "hour", "hours" -> num * 3600
            else -> return L1Result.Invalid("Unsupported unit")
        }
        if (seconds > 24 * 3600) return L1Result.Invalid("Timer too long")
        val secsInt = seconds.toInt()
        val display = when {
            secsInt % 3600 == 0 -> "${secsInt / 3600} hour${if (secsInt/3600==1) "" else "s"}"
            secsInt % 60 == 0 -> "${secsInt / 60} min"
            else -> "${secsInt}s"
        }
        return L1Result.Resolved(
            ResolvedResult(
                id = "timer:$secsInt",
                title = "Timer set for $display",
                subtitle = null,
                type = ResultType.Timer,
                action = AuraAction.SetTimer(secsInt),
                undoable = true
            )
        )
    }
}
