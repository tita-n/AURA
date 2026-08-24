package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Alarm — deterministic patterns: alarm 6:30, wake me at 6:30
 * Extracts hour/minute, validates 0–23 / 0–59, optional am/pm.
 */
class AlarmGrammar : L1Grammar {
    override fun name() = "Alarm"

    // Supports:
    // alarm 6:30
    // alarm 06:30
    // alarm 6:30 am / pm
    // set alarm 6:30
    // wake me at 6:30
    // wake me at 6:30 am
    private val patterns = listOf(
        Regex("""^\s*(?:set\s+)?alarm\s+(\d{1,2}):(\d{2})(?:\s*(am|pm))?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*wake\s+me\s+at\s+(\d{1,2}):(\d{2})(?:\s*(am|pm))?\s*$""", RegexOption.IGNORE_CASE)
    )

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmed = raw.trim()
        // Use normalized for matching but keep raw for display; normalized is lowercased already
        val match = patterns.firstNotNullOfOrNull { it.matchEntire(trimmed) }
            ?: patterns.firstNotNullOfOrNull { it.matchEntire(normalized) }
            ?: return L1Result.Unrecognized

        val hourStr = match.groupValues[1]
        val minStr = match.groupValues[2]
        val ampm = match.groupValues.getOrNull(3)?.lowercase()?.takeIf { it.isNotBlank() }

        val hourRaw = hourStr.toIntOrNull() ?: return L1Result.Invalid("Invalid hour")
        val minute = minStr.toIntOrNull() ?: return L1Result.Invalid("Invalid minute")
        if (minute !in 0..59) return L1Result.Invalid("Minute must be 0–59")

        val hour = when (ampm) {
            "am" -> {
                if (hourRaw !in 1..12) return L1Result.Invalid("Hour must be 1–12 for am/pm")
                if (hourRaw == 12) 0 else hourRaw
            }
            "pm" -> {
                if (hourRaw !in 1..12) return L1Result.Invalid("Hour must be 1–12 for am/pm")
                if (hourRaw == 12) 12 else hourRaw + 12
            }
            else -> {
                // 24h mode: 0–23
                if (hourRaw !in 0..23) return L1Result.Invalid("Hour must be 0–23")
                hourRaw
            }
        }

        val display = String.format("%d:%02d", hour, minute)
        return L1Result.Resolved(
            ResolvedResult(
                id = "alarm:$hour:$minute",
                title = "Alarm set for $display",
                subtitle = null,
                type = ResultType.Alarm,
                action = AuraAction.SetAlarm(hour, minute),
                undoable = true
            )
        )
    }
}
