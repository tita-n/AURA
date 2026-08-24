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
 * Open app semantic matcher — handles synonyms and typos for opening apps.
 * Synonyms: open, launch, start, run, show, go to
 * Also handles "play my music" -> Spotify, "open that music app" -> Spotify
 * Typo tolerance via EditDistance (threshold 2).
 */
class OpenAppMatcher(private val index: L0Index) {
    private val verbs = setOf("open", "launch", "start", "run", "show")
    private val musicAliases = mapOf(
        "music" to "Spotify",
        "songs" to "Spotify",
        "spotify" to "Spotify"
    )

    fun match(normalized: String, raw: String): L2Result {
        val trimmed = normalized.trim()
        // Handle "play my music" special case
        if (trimmed == "play my music" || trimmed == "play music" || trimmed == "open that music app" || trimmed == "music app") {
            val spotify = findAppByNormalized("spotify")
            if (spotify != null) {
                return L2Result.Resolved(
                    ResolvedResult(
                        id = spotify.id,
                        title = spotify.displayLabel,
                        subtitle = null,
                        type = ResultType.App,
                        action = spotify.action
                    )
                )
            }
        }

        // Strip filler words for "whatsapp pls" -> "whatsapp"
        val cleaned = trimmed
            .replace(Regex("""\b(pls|please|app|that|my)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Extract verb + app name: "open chrome", "launch chrome", "start whatsapp pls" etc.
        var appName: String? = null
        for (verb in verbs) {
            if (cleaned.startsWith("$verb ")) {
                appName = cleaned.removePrefix("$verb ").trim()
                break
            }
        }
        // Also handle "go to chrome" (two-word verb)
        if (appName == null && cleaned.startsWith("go to ")) {
            appName = cleaned.removePrefix("go to ").trim()
        }
        // If no verb but query looks like app name with typo, try direct fuzzy
        if (appName == null) {
            // Check if entire query fuzzy matches an app (e.g., "chrom", "whatsapp pls" already cleaned to "whatsapp")
            val direct = findAppFuzzy(cleaned)
            if (direct != null) {
                return when (direct) {
                    is FuzzyResult.Single -> L2Result.Resolved(
                        ResolvedResult(
                            id = direct.entity.id,
                            title = direct.entity.displayLabel,
                            subtitle = null,
                            type = ResultType.App,
                            action = direct.entity.action
                        )
                    )
                    is FuzzyResult.Multiple -> {
                        val items = direct.entities.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
                        L2Result.Ambiguous(CandidateGroup("Which ${direct.entities.first().displayLabel}", items))
                    }
                }
            }
            return L2Result.Unrecognized
        }

        // Check music alias
        val aliasTarget = musicAliases[appName]
        if (aliasTarget != null) {
            val target = findAppByNormalized(Normalizer.normalize(aliasTarget))
            if (target != null) {
                return L2Result.Resolved(
                    ResolvedResult(
                        id = target.id,
                        title = target.displayLabel,
                        subtitle = null,
                        type = ResultType.App,
                        action = target.action
                    )
                )
            }
        }

        // Try exact, then prefix, then fuzzy
        val exact = findAppByNormalized(appName)
        if (exact != null) {
            return L2Result.Resolved(
                ResolvedResult(id = exact.id, title = exact.displayLabel, subtitle = null, type = ResultType.App, action = exact.action)
            )
        }
        val fuzzy = findAppFuzzy(appName)
        return when (fuzzy) {
            is FuzzyResult.Single -> L2Result.Resolved(ResolvedResult(id = fuzzy.entity.id, title = fuzzy.entity.displayLabel, subtitle = null, type = ResultType.App, action = fuzzy.entity.action))
            is FuzzyResult.Multiple -> {
                val items = fuzzy.entities.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
                L2Result.Ambiguous(CandidateGroup("Which ${fuzzy.entities.first().displayLabel}", items))
            }
            null -> L2Result.Unrecognized
        }
    }

    private fun findAppByNormalized(normalized: String): com.aura.resolver.IndexedEntity? {
        val lookup = index.lookup(normalized)
        val apps = when (lookup) {
            is com.aura.resolver.L0LookupResult.Exact -> lookup.entities.filter { it.category == EntityCategory.App }
            is com.aura.resolver.L0LookupResult.Prefix -> lookup.entities.filter { it.category == EntityCategory.App }
            else -> emptyList()
        }
        return if (apps.size == 1) apps.first() else null
    }

    private sealed interface FuzzyResult {
        data class Single(val entity: com.aura.resolver.IndexedEntity) : FuzzyResult
        data class Multiple(val entities: List<com.aura.resolver.IndexedEntity>) : FuzzyResult
    }

    private fun findAppFuzzy(query: String): FuzzyResult? {
        val apps = index.allEntities().filter { it.category == EntityCategory.App }
        // First try prefix (for partial like "chr" -> chrome already handled by L0, but L2 also handles)
        val prefixMatches = apps.filter { it.normalizedLabel.startsWith(query) }
        if (prefixMatches.size == 1) return FuzzyResult.Single(prefixMatches.first())
        if (prefixMatches.size > 1) return FuzzyResult.Multiple(prefixMatches.take(5))

        // Then edit distance
        val threshold = if (query.length <= 4) 1 else 2
        val fuzzy = apps.filter { EditDistance.isFuzzyMatch(query, it.normalizedLabel, threshold) }
        return when {
            fuzzy.isEmpty() -> null
            fuzzy.size == 1 -> FuzzyResult.Single(fuzzy.first())
            else -> FuzzyResult.Multiple(fuzzy.take(5))
        }
    }
}
