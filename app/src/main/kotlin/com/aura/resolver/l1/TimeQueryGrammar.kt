package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Time / Date queries — deterministic, device-local, no network, no cloud.
 * Examples: "what time is it", "what is today's date", "what day is it".
 * Optional timezone: "what time is it in Lagos" — resolved from a small, closed,
 * offline table of major cities mapped to the device's TimeZone database. Unknown
 * cities are rejected honestly; no guessing, no cloud lookup.
 *
 * Results are inline (no app switch, no execution). Rendered as InlineResult by the UI.
 */
class TimeQueryGrammar : L1Grammar {
    override fun name() = "TimeQuery"

    // Closed, offline city -> TimeZone id table. Deterministic; not a cloud dependency.
    private val cityZones = mapOf(
        "lagos" to "Africa/Lagos",
        "abuja" to "Africa/Lagos",
        "london" to "Europe/London",
        "paris" to "Europe/Paris",
        "berlin" to "Europe/Berlin",
        "madrid" to "Europe/Madrid",
        "cairo" to "Africa/Cairo",
        "nairobi" to "Africa/Nairobi",
        "johannesburg" to "Africa/Johannesburg",
        "dubai" to "Asia/Dubai",
        "mumbai" to "Asia/Kolkata",
        "new delhi" to "Asia/Kolkata",
        "tokyo" to "Asia/Tokyo",
        "beijing" to "Asia/Shanghai",
        "singapore" to "Asia/Singapore",
        "sydney" to "Australia/Sydney",
        "new york" to "America/New_York",
        "los angeles" to "America/Los_Angeles",
        "chicago" to "America/Chicago",
        "toronto" to "America/Toronto",
        "saopaulo" to "America/Sao_Paulo",
        "sao paulo" to "America/Sao_Paulo"
    )

    private val timeWord = Regex("""\btime\b""", RegexOption.IGNORE_CASE)
    private val dateWord = Regex("""\bdate\b""", RegexOption.IGNORE_CASE)
    private val dayWord = Regex("""\bday\b""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val q = normalized.trim()
        if (q.isEmpty()) return L1Result.Unrecognized

        val hasTime = timeWord.containsMatchIn(q)
        val hasDate = dateWord.containsMatchIn(q)
        val hasDay = dayWord.containsMatchIn(q)

        // Must look like a question/query about time/date/day — not a command like "set a timer".
        val isQuery = q.startsWith("what") ||
            q.startsWith("current") ||
            q.startsWith("tell me") ||
            q == "time" || q == "date" || q == "day" ||
            q.contains("now") || q.contains("today") ||
            q.endsWith("time") || q.endsWith("date") || q.endsWith("day")

        if (!isQuery || (!hasTime && !hasDate && !hasDay)) return L1Result.Unrecognized

        // Optional timezone: "... in <city>"
        var timeZone: TimeZone = TimeZone.getDefault()
        val cityMatch = Regex("""\bin\s+([a-z][a-z\s]*)$""").find(q)
        if (cityMatch != null) {
            val city = cityMatch.groupValues[1].trim().replace(Regex("""\s+"""), " ")
            val zoneId = cityZones[city]
            if (zoneId == null) {
                return L1Result.Invalid("Unknown city: $city")
            }
            timeZone = TimeZone.getTimeZone(zoneId)
        }

        // Decide which value to produce. Preference: TIME > DATE > DAY.
        val (type, pattern) = when {
            hasTime -> ResultType.Time to "h:mm a"
            hasDate -> ResultType.Date to "EEEE, d MMMM yyyy"
            else -> ResultType.Date to "EEEE, d MMMM yyyy"
        }

        val now = Calendar.getInstance(timeZone)
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        fmt.timeZone = timeZone
        val value = fmt.format(now.time)

        return L1Result.Resolved(
            ResolvedResult(
                id = "timequery:${type.name.lowercase()}:${timeZone.id}",
                title = value,
                subtitle = if (timeZone == TimeZone.getDefault()) null else "in ${timeZone.getDisplayName(Locale.getDefault())}",
                type = type,
                action = AuraAction.NoOp,
                inlineValue = value,
                inlineQuery = raw.trim(),
                undoable = false
            )
        )
    }
}
