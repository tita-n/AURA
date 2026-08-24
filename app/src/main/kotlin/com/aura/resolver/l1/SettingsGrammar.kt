package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * Settings — wifi settings, bluetooth settings, display settings
 * Only deterministic mappings already in indexed settings catalog.
 * No speculative mappings, no Intent creation.
 */
class SettingsGrammar(
    private val index: L0Index
) : L1Grammar {
    override fun name() = "Settings"
    // Accepts: "wifi settings", "bluetooth", "display settings" etc. but we delegate to index.
    // For determinism, we match any query that ends with "settings" or is a known settings label.
    // However to avoid false positives (e.g., "settings" alone), we check via index lookup.

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmedNorm = normalized.trim()
        // First try direct lookup
        val lookup = index.lookup(trimmedNorm)
        val settingsCandidates = when (lookup) {
            is com.aura.resolver.L0LookupResult.Exact -> lookup.entities.filter { it.category == EntityCategory.Settings }
            is com.aura.resolver.L0LookupResult.Prefix -> lookup.entities.filter { it.category == EntityCategory.Settings }
            else -> emptyList()
        }
        if (settingsCandidates.isNotEmpty()) return resolveSettings(settingsCandidates, raw)

        // Hyphen-insensitive fallback: "wifi" vs "wi-fi", "wifi settings" vs "wi-fi settings"
        // Iterate all settings entities and compare after removing hyphens
        val queryNoHyphen = trimmedNorm.replace("-", "")
        val hyphenCandidates = index.allEntities().filter { it.category == EntityCategory.Settings }
            .filter { it.normalizedLabel.replace("-", "") == queryNoHyphen || it.normalizedLabel == trimmedNorm }
        if (hyphenCandidates.isNotEmpty()) return resolveSettings(hyphenCandidates, raw)

        return L1Result.Unrecognized
    }

    private fun resolveSettings(candidates: List<com.aura.resolver.IndexedEntity>, raw: String): L1Result {
        return when {
            candidates.size == 1 -> {
                val e = candidates.single()
                L1Result.Resolved(
                    ResolvedResult(
                        id = e.id,
                        title = e.displayLabel,
                        subtitle = e.subtitle,
                        type = ResultType.Settings,
                        action = e.action
                    )
                )
            }
            candidates.size > 1 -> {
                val items = candidates.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
                L1Result.Ambiguous(CandidateGroup("Which setting", items))
            }
            else -> L1Result.Unrecognized
        }
    }
}
