package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import java.util.Locale

/**
 * Reminder — deterministic phrasings:
 *   "remind me to call Mum at 3pm"
 *   "remind me to call Mum at 3:30 pm"
 *   "remind me tomorrow at 9am to call Mum"
 *   "remind me at 3pm to call Mum"
 *   "remind me to call Mum tomorrow at 9am"
 *   "remind me tomorrow at 9am"            (no explicit text -> generic)
 *
 * Android has no public third-party reminder API. AURA does not invent a proprietary reminder
 * database or backend. The honest, platform-supported fallback is the system calendar editor
 * (ACTION_INSERT with a pre-filled event), which the user confirms. AURA therefore NEVER claims
 * a reminder was created — it opens the calendar with the details prefilled.
 *
 * A reminder with no time-of-day specification is not silently dropped or faked:
 *  - "remind me to X" (no time) -> Invalid with a helpful message
 *  - "remind me in 10 minutes" (duration, no time-of-day) -> Unrecognized, so the timer path handles it
 */
class ReminderMatcher {
    private val dayRegex = Regex("""\b(tomorrow|today)\b""", RegexOption.IGNORE_CASE)
    // A time-of-day: optional "at", then H, optional :MM, optional am/pm. We require a colon or
    // am/pm so a bare "10" (as in "in 10 minutes") is NOT mistaken for a time-of-day.
    private val timeRegex = Regex("""(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)

    fun match(normalized: String, raw: String): L2Result {
        val q = normalized.trim()
        if (!q.startsWith("remind me")) return L2Result.Unrecognized

        var rest = q
        var dayOffset = 0
        val dayM = dayRegex.find(rest)
        if (dayM != null) {
            if (dayM.value.equals("tomorrow", ignoreCase = true)) dayOffset = 1
            rest = rest.replaceFirst(dayM.value, " ")
        }

        val timeM = timeRegex.find(rest)
        val hasColon = timeM != null && timeM.groupValues[2].isNotBlank()
        val hasAmpm = timeM != null && timeM.groupValues[3].isNotBlank()
        if (timeM == null || (!hasColon && !hasAmpm)) {
            // No time-of-day. If this clearly looks like a "remind me to <text>" reminder, say so;
            // otherwise leave it for the timer path ("remind me in 10 minutes").
            if (rest.contains(" to ") || rest.startsWith("remind me to")) {
                return L2Result.Invalid("Add a time, e.g. \"remind me to call Mum at 3pm\"")
            }
            return L2Result.Unrecognized
        }

        val hRaw = timeM!!.groupValues[1].toIntOrNull() ?: return L2Result.Invalid("Invalid hour")
        val minStr = timeM.groupValues[2]
        val minute = if (minStr.isBlank()) 0 else (minStr.toIntOrNull() ?: return L2Result.Invalid("Invalid minute"))
        if (minute !in 0..59) return L2Result.Invalid("Minute must be 0–59")
        val ampm = timeM.groupValues[3].ifBlank { null }?.lowercase()

        val hour = when (ampm) {
            "am" -> { if (hRaw !in 1..12) return L2Result.Invalid("Hour must be 1–12 for am/pm"); if (hRaw == 12) 0 else hRaw }
            "pm" -> { if (hRaw !in 1..12) return L2Result.Invalid("Hour must be 1–12 for am/pm"); if (hRaw == 12) 12 else hRaw + 12 }
            else -> { if (hRaw !in 0..23) return L2Result.Invalid("Hour must be 0–23"); hRaw }
        }

        // Derive the reminder text by removing the time expression, "remind me", and the "to" connector.
        var text = rest.replace(timeM.value, " ")
        text = text.replace(Regex("""^\s*remind\s+me\s*"""), " ")
        text = text.replace(Regex("""\bto\b"""), " ")
        text = text.replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) text = "Reminder"

        val display = if (ampm != null) {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            String.format(Locale.ROOT, "%d:%02d %s", h12, minute, if (hour < 12) "AM" else "PM")
        } else {
            String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        }

        return L2Result.Resolved(
            ResolvedResult(
                id = "reminder:${dayOffset}:$hour:$minute:${text.hashCode()}",
                title = "Reminder: $text at $display",
                subtitle = if (dayOffset == 1) "Tomorrow · opens your calendar" else "Opens your calendar",
                type = ResultType.Reminder,
                action = AuraAction.SetReminder(text, hour, minute, dayOffset),
                undoable = false
            )
        )
    }
}
