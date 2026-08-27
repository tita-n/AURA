package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * Settings semantic matcher — handles "turn on wifi", "enable wifi", "open wifi settings", etc.
 * Synonyms: turn on/off, enable/disable, open, show
 */
class SettingsMatcher(private val index: L0Index) {
    private val settingsKeywords = mapOf(
        "wifi" to listOf("wifi", "wi-fi", "wireless"),
        "bluetooth" to listOf("bluetooth"),
        "display" to listOf("display", "screen", "brightness"),
        "sound" to listOf("sound", "volume", "audio"),
        "accessibility" to listOf("accessibility", "accessibility settings"),
        "location" to listOf("location", "gps", "location services"),
        "date_and_time" to listOf("date and time", "date & time", "date", "time", "date settings", "time settings")
    )

    // Patterns like "turn on wifi", "turn off bluetooth", "enable wifi", "open wifi settings"
    private val patterns = listOf(
        Regex("""^\s*(?:turn\s+on|turn\s+off|enable|disable|open|show)\s+(.+?)(?:\s+settings?)?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(.+?)\s+settings?\s*$""", RegexOption.IGNORE_CASE)
    )

    fun match(normalized: String, raw: String): L2Result {
        val trimmed = normalized.trim()
        // Must look like settings intent
        var keyword: String? = null
        for (p in patterns) {
            val m = p.matchEntire(trimmed) ?: p.matchEntire(raw.trim().lowercase())
            if (m != null) {
                keyword = m.groupValues[1].trim()
                break
            }
        }
        if (keyword == null) return L2Result.Unrecognized
        // Check if keyword maps to known settings via synonym table
        val normalizedKeyword = Normalizer.normalize(keyword)
        var targetKey: String? = null
        for ((key, aliases) in settingsKeywords) {
            if (aliases.any { Normalizer.normalize(it) == normalizedKeyword }) {
                targetKey = key
                break
            }
            // Also check if keyword contains alias (e.g., "wifi settings" -> contains "wifi")
            if (aliases.any { keyword.contains(it) } || keyword.contains(key)) {
                targetKey = key
                if (normalizedKeyword == key) break
            }
        }
        if (targetKey == null) return L2Result.Unrecognized

        // Find settings entity for targetKey
        val candidates = index.allEntities().filter { it.category == EntityCategory.Settings }
            .filter { it.id.contains(targetKey, ignoreCase = true) || it.normalizedLabel.contains(targetKey) }
        if (candidates.isEmpty()) return L2Result.Unrecognized
        // Prefer exact "wifi settings" over "wifi"
        val exact = candidates.find { it.normalizedLabel == "$targetKey settings" || it.normalizedLabel == targetKey }
            ?: candidates.first()
        // If multiple with same normalized, ask
        val matching = candidates.filter { it.normalizedLabel.replace("-", "") == exact.normalizedLabel.replace("-", "") }
        return if (matching.size == 1) {
            L2Result.Resolved(ResolvedResult(id = exact.id, title = exact.displayLabel, subtitle = exact.subtitle, type = ResultType.Settings, action = exact.action))
        } else if (matching.size > 1) {
            val items = matching.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
            L2Result.Ambiguous(CandidateGroup("Which setting", items))
        } else {
            L2Result.Resolved(ResolvedResult(id = exact.id, title = exact.displayLabel, subtitle = exact.subtitle, type = ResultType.Settings, action = exact.action))
        }
    }
}
