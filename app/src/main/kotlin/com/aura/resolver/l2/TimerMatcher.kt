package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Timer semantic matcher — handles variants like "set a timer for 10 mins", "remind me in 10 minutes", "countdown 10 mins"
 * Delegates to deterministic timer parsing after synonym normalization.
 */
class TimerMatcher {
    fun match(normalized: String, raw: String): L2Result {
        val trimmed = normalized.trim()
        // Normalize synonyms to "timer <duration>"
        var normalizedTimer = trimmed
        // "set a timer for 10 mins" -> "timer 10 mins"
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*set\s+a\s+timer\s+for\s+"""), "timer ")
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*set\s+timer\s+for\s+"""), "timer ")
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*timer\s+for\s+"""), "timer ")
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*remind\s+me\s+in\s+"""), "timer ")
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*countdown\s+"""), "timer ")
        normalizedTimer = normalizedTimer.replace(Regex("""^\s*remind\s+me\s+"""), "timer ")

        if (!normalizedTimer.startsWith("timer ")) return L2Result.Unrecognized

        // Now delegate to existing TimerGrammar logic via reusing its parsing
        // We do inline parsing to avoid instantiating TimerGrammar with index dependency
        val pattern = Regex("""^\s*timer\s+(\d+(?:\.\d+)?)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\s*$""", RegexOption.IGNORE_CASE)
        val m = pattern.matchEntire(normalizedTimer) ?: return L2Result.Invalid("Invalid timer format")
        val numStr = m.groupValues[1]
        val unitStr = m.groupValues[2].lowercase()
        val num = numStr.toDoubleOrNull() ?: return L2Result.Invalid("Invalid number")
        if (num <= 0) return L2Result.Invalid("Duration must be positive")
        val seconds = when (unitStr) {
            "s", "sec", "secs", "second", "seconds" -> num
            "m", "min", "mins", "minute", "minutes" -> num * 60
            "h", "hr", "hrs", "hour", "hours" -> num * 3600
            else -> return L2Result.Invalid("Unsupported unit")
        }
        if (seconds > 24 * 3600) return L2Result.Invalid("Timer too long")
        val secsInt = seconds.toInt()
        val display = when {
            secsInt % 3600 == 0 -> "${secsInt / 3600} hour${if (secsInt/3600==1) "" else "s"}"
            secsInt % 60 == 0 -> "${secsInt / 60} min"
            else -> "${secsInt}s"
        }
        return L2Result.Resolved(
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
