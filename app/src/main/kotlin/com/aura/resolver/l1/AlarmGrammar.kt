package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import java.util.Locale

/**
 * Alarm — deterministic patterns:
 *   alarm 6:30, set alarm 6:30, alarm 6:30 am/pm, alarm 6am, wake me at 6:30,
 *   wake me at 7, wake me tomorrow at 6:30am, 18:30 (bare 24h), 6am (bare)
 * Extracts hour/minute, validates 0–23 / 0–59, optional am/pm.
 *
 * Note: day qualifiers (tomorrow/today) are accepted parse-wise but the platform AlarmClock
 * API only schedules the next matching time, so we set hour:minute only and document the
 * limitation (no public EXTRA_DAY on the AlarmClock contract across our supported versions).
 */
class AlarmGrammar : L1Grammar {
    override fun name() = "Alarm"

    // Verb form: alarm / wake me (+ optional set an / for / on / at / tomorrow / today)
    private val verbPattern = Regex(
        """^\s*(?:set\s+(?:an\s+)?)?(?:alarm|wake\s+me)(?:\s+(?:for|on|at|tomorrow|today))*?\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    // Bare 24h: 18:30 / 6:30pm
    private val barePattern = Regex("""^\s*(\d{1,2}):(\d{2})(?:\s*(am|pm))?\s*$""", RegexOption.IGNORE_CASE)
    // Bare hour+ampm: 6am / 9pm
    private val bareHourPattern = Regex("""^\s*(\d{1,2})\s*(am|pm)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmed = raw.trim()

        val verb = verbPattern.matchEntire(trimmed) ?: verbPattern.matchEntire(normalized)
        if (verb != null) {
            return resolve(
                verb.groupValues[1].toIntOrNull(),
                verb.groupValues[2].ifEmpty { null },
                verb.groupValues[3].ifEmpty { null }
            )
        }
        val bare = barePattern.matchEntire(trimmed) ?: barePattern.matchEntire(normalized)
        if (bare != null) {
            return resolve(
                bare.groupValues[1].toIntOrNull(),
                bare.groupValues[2].ifEmpty { null },
                bare.groupValues[3].ifEmpty { null }
            )
        }
        val bareH = bareHourPattern.matchEntire(trimmed) ?: bareHourPattern.matchEntire(normalized)
        if (bareH != null) {
            return resolve(bareH.groupValues[1].toIntOrNull(), null, bareH.groupValues[2].ifEmpty { null })
        }
        return L1Result.Unrecognized
    }

    private fun resolve(hourRaw: Int?, minuteStr: String?, ampmRaw: String?): L1Result {
        if (hourRaw == null) return L1Result.Unrecognized
        val minute = if (minuteStr.isNullOrBlank()) 0 else (minuteStr.toIntOrNull() ?: return L1Result.Invalid("Invalid minute"))
        if (minute !in 0..59) return L1Result.Invalid("Minute must be 0–59")

        val ampm = ampmRaw?.lowercase()?.takeIf { it.isNotBlank() }
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
                if (hourRaw !in 0..23) return L1Result.Invalid("Hour must be 0–23")
                hourRaw
            }
        }

        val display = String.format(Locale.ROOT, "%d:%02d", hour, minute)
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
