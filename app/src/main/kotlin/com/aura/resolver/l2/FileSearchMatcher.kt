package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.FileSearchRequest
import com.aura.resolver.FileSearchSource

/**
 * File search — L2 semantic matcher (no new search architecture; plugs into the existing
 * L2 pipeline). Detects "find / search for / look for / locate / show me / list <term>"
 * phrasing, derives an optional location hint, and delegates the actual MediaStore query to
 * the injected [FileSearchSource] (platform-only). Results become ACT (single) or ASK
 * (multiple candidates). No resolution → Unrecognized (calm empty). Permission missing →
 * Invalid (honest, never a fake).
 */
class FileSearchMatcher(private val source: FileSearchSource) {

    fun match(normalized: String, raw: String): L2Result {
        val trimmed = raw.trim()
        val expr = stripVerb(trimmed) ?: stripVerb(normalized) ?: trimmed
        val inner = expr.trim()
        if (inner.length < 2) return L2Result.Unrecognized

        val (location, query) = clean(inner)
        val response = source.search(FileSearchRequest(query = query, locationHint = location))
        if (response.permissionDenied) {
            return L2Result.Invalid("Storage access is off — enable it to search files")
        }
        if (response.results.isEmpty()) return L2Result.Unrecognized
        if (response.results.size == 1) {
            return L2Result.Resolved(toResolved(response.results[0], query))
        }
        val candidates = response.results.map { f ->
            CandidateItemData(
                id = f.id,
                title = f.displayName,
                disambiguation = "${f.locationLabel} · ${f.sizeLabel}"
            )
        }
        return L2Result.Ambiguous(CandidateGroup(label = "Files", candidates = candidates))
    }

    private fun stripVerb(input: String): String? {
        val m = VERB.matchEntire(input) ?: return null
        return m.groupValues[2].trim()
    }

    /**
     * Extract an optional location hint and a cleaned filename query.
     * Location keywords are detected first (word-boundary, case-insensitive), then common
     * filler words (the/my/i/of/in/from…) are stripped so e.g. "find the PDF I downloaded"
     * resolves to location=Downloads, query="pdf".
     */
    private fun clean(term: String): Pair<String?, String> {
        var t = term.lowercase()
        var location: String? = null
        for ((re, loc) in LOCATION_PATTERNS) {
            if (re.containsMatchIn(t)) {
                t = t.replace(re, " ")
                location = loc
                break
            }
        }
        val query = t.replace(FILLER_RE, " ").replace(Regex("""\s+"""), " ").trim()
        return location to query
    }

    private fun toResolved(file: com.aura.resolver.FileSearchResult, query: String) = ResolvedResult(
        id = file.id,
        title = file.displayName,
        subtitle = "${file.locationLabel} · ${file.sizeLabel}",
        type = ResultType.File,
        action = AuraAction.OpenFile(
            uriString = file.contentUriString,
            displayName = file.displayName,
            mimeType = file.mimeType
        ),
        inlineValue = null,
        inlineQuery = query
    )

    private companion object {
        val VERB = Regex(
            """^\s*(find(?:\s+(?:my|the|all))?|search(?:\s+for)?|look(?:\s+for)?|locate(?:\s+(?:my|the|all))?|show(?:\s+me)?|list(?:\s+(?:my|the|all))?)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )

        val LOCATION_PATTERNS = listOf(
            Regex("""\b(downloads?|downloaded)\b""", RegexOption.IGNORE_CASE) to "Downloads",
            Regex("""\b(documents?|docs)\b""", RegexOption.IGNORE_CASE) to "Documents",
            Regex("""\b(pictures|photos|images)\b""", RegexOption.IGNORE_CASE) to "Pictures",
            Regex("""\b(dcim|camera)\b""", RegexOption.IGNORE_CASE) to "DCIM",
            Regex("""\b(movies|videos)\b""", RegexOption.IGNORE_CASE) to "Movies",
            Regex("""\b(music|audio|songs)\b""", RegexOption.IGNORE_CASE) to "Music",
            Regex("""\b(screenshots)\b""", RegexOption.IGNORE_CASE) to "Pictures"
        )

        val FILLER_RE = Regex(
            """\b(the|my|i|a|an|some|that|this|these|those|of|for|me|please|to|in|from|on|with|and|file|files|document|documents)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
