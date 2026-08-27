package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Brightness — deterministic phrasings: "brightness 70%", "brightness 50", "increase brightness",
 * "decrease brightness", "brightness settings".
 *
 * Platform reality: a normal third-party app cannot set system brightness without the
 * WRITE_SETTINGS permission, which Android requires the user to grant via a dedicated system
 * screen (Settings.ACTION_MANAGE_WRITE_SETTINGS). AURA must NOT request that permission
 * automatically and must NOT silently manipulate system settings. So every brightness command
 * honestly resolves to "Open brightness settings" (the display settings screen, where the
 * brightness slider lives). The requested percentage is acknowledged but never applied, and the
 * subtitle explains why. Invalid percentages (outside 0–100) are rejected.
 */
class BrightnessMatcher {
    // brightness 70% / brightness 70 / set brightness to 70%
    private val valuePattern = Regex(
        """^\s*(?:set\s+)?brightness\s+(?:to\s+)?(\d{1,3})\s*%?\s*$""",
        RegexOption.IGNORE_CASE
    )
    // relative: increase / decrease / raise / lower / turn up / turn down brightness
    private val relativePattern = Regex(
        """^\s*(?:increase|raise|turn\s+up|boost|brighten|decrease|lower|turn\s+down|dim|reduce)\s+brightness\s*$""",
        RegexOption.IGNORE_CASE
    )
    // bare "brightness" / "brightness settings"
    private val barePattern = Regex(
        """^\s*brightness(?:\s+settings)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun match(normalized: String, raw: String): L2Result {
        val trimmed = normalized.trim()

        val valueMatch = valuePattern.matchEntire(trimmed) ?: valuePattern.matchEntire(raw.trim().lowercase())
        if (valueMatch != null) {
            val pct = valueMatch.groupValues[1].toIntOrNull()
            if (pct == null || pct < 0 || pct > 100) {
                return L2Result.Invalid("Brightness must be between 0% and 100%")
            }
            return openBrightnessSettings("Open brightness settings ($pct%)")
        }

        if (relativePattern.containsMatchIn(trimmed) || relativePattern.containsMatchIn(raw.trim().lowercase())) {
            return openBrightnessSettings("Open brightness settings")
        }

        if (barePattern.containsMatchIn(trimmed) || barePattern.containsMatchIn(raw.trim().lowercase())) {
            return openBrightnessSettings("Open brightness settings")
        }

        return L2Result.Unrecognized
    }

    private fun openBrightnessSettings(title: String): L2Result {
        return L2Result.Resolved(
            ResolvedResult(
                id = "brightness:settings",
                title = title,
                subtitle = "Android requires a special permission to set brightness — opening display settings",
                type = ResultType.Settings,
                action = AuraAction.OpenSettings("display")
            )
        )
    }
}
