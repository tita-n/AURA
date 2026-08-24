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
 * Open — open Chrome, open WhatsApp, launch Spotify
 * Explicit verb where L0 alone cannot (L0 would need exact "chrome", not "open chrome").
 * L0 always gets first opportunity; L1 handles verb form.
 */
class OpenGrammar(
    private val index: L0Index
) : L1Grammar {
    override fun name() = "Open"
    private val pattern = Regex("""^\s*(?:open|launch|start)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        val m = pattern.matchEntire(raw.trim()) ?: pattern.matchEntire(normalized) ?: return L1Result.Unrecognized
        val nameRaw = m.groupValues[1].trim()
        if (nameRaw.isEmpty()) return L1Result.Invalid("Missing app name")
        val nameNorm = Normalizer.normalize(nameRaw)
        if (nameNorm.isEmpty()) return L1Result.Invalid("Invalid name")

        val lookup = index.lookup(nameNorm)
        val candidates = when (lookup) {
            is com.aura.resolver.L0LookupResult.Exact -> lookup.entities.filter { it.category == EntityCategory.App }
            is com.aura.resolver.L0LookupResult.Prefix -> lookup.entities.filter { it.category == EntityCategory.App }
            else -> emptyList()
        }
        return when {
            candidates.isEmpty() -> L1Result.Unrecognized // don't invent app
            candidates.size == 1 -> {
                val e = candidates.single()
                L1Result.Resolved(
                    ResolvedResult(
                        id = e.id,
                        title = e.displayLabel,
                        subtitle = null,
                        type = ResultType.App,
                        action = e.action
                    )
                )
            }
            else -> {
                // Ambiguous app name
                val items = candidates.map { e -> CandidateItemData(e.id, e.displayLabel, e.disambiguation, e.subtitle) }
                L1Result.Ambiguous(CandidateGroup("Which ${candidates.first().displayLabel}", items))
            }
        }
    }
}
