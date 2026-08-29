package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.CandidateGroup
import com.aura.domain.CandidateItemData
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.FileSearchRequest
import com.aura.resolver.FileSearchResponse
import com.aura.resolver.FileSearchSource
import com.aura.resolver.locationFolder
import com.aura.resolver.rankFileResults

/**
 * File search — L2 capability. Understands "find my downloads", "find the budget document",
 * "search files for invoice", "find invoice.pdf", "find screenshots", "open my latest PDF", etc.
 *
 * Responsibilities (cleanly separated):
 *  - query interpretation: strip the file verb, detect a location hint, drop natural-language filler;
 *  - delegate discovery to the injected [FileSearchSource] (MediaStore / filesystem behind it);
 *  - result ranking via pure [rankFileResults];
 *  - UI presentation: ACT (single) / ASK (many) / calm Empty / honest Invalid.
 *
 * It does NOT know how Android storage works — that is the platform source's job.
 */
class FileSearchMatcher(private val source: FileSearchSource) {

    fun match(normalized: String, raw: String): L2Result {
        val term = raw.trim()
        if (term.length < 2) return L2Result.Unrecognized
        val verb = FILE_VERB.find(term) ?: return L2Result.Unrecognized
        var rest = term.removeRange(verb.range).trim()

        // Location hint (e.g., "downloads", "documents", "pictures", "screenshots")
        var location: String? = null
        for ((re, folder) in LOCATION_RE) {
            re.find(rest)?.let { m ->
                location = folder
                rest = rest.replace(m.value, " ", ignoreCase = true).trim()
            }
        }
        val query = cleanQuery(rest).lowercase()

        val request = FileSearchRequest(query, location)
        val response = source.search(request)
        return mapResponse(request, response)
    }

    private fun mapResponse(request: FileSearchRequest, response: FileSearchResponse): L2Result {
        return when {
            response.permissionDenied -> L2Result.Invalid("Storage access unavailable")
            response.results.isEmpty() && response.requiresManageStorage -> L2Result.Resolved(
                ResolvedResult(
                    id = "storage:grant",
                    title = "Grant file access",
                    subtitle = "Search all of storage, not just media",
                    type = ResultType.File,
                    action = AuraAction.RequestStorageAccess
                )
            )
            response.results.isEmpty() -> L2Result.Unrecognized
            response.results.size == 1 -> {
                val f = response.results.first()
                L2Result.Resolved(
                    ResolvedResult(
                        id = f.id,
                        title = f.displayName,
                        subtitle = "${f.locationLabel} · ${f.sizeLabel}",
                        type = ResultType.File,
                        action = AuraAction.OpenFile(f.contentUriString, f.displayName, f.mimeType)
                    )
                )
            }
            else -> {
                val ranked = rankFileResults(request, response.results)
                val candidates = ranked.map { f ->
                    CandidateItemData(
                        id = f.id,
                        title = f.displayName,
                        subtitle = "${f.locationLabel} · ${f.sizeLabel}"
                    )
                }
                L2Result.Ambiguous(CandidateGroup("Files", candidates))
            }
        }
    }

    private fun cleanQuery(text: String): String {
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.lowercase() !in FILLER }
            .joinToString(" ")
            .trim()
    }

    companion object {
        // File verbs. Deliberately NOT "show me"/"find the value of" collisions with calculator.
        private val FILE_VERB = Regex(
            """(?i)\b(find|search|locate|list|look for|get|open|my files?|files?|documents?)\b"""
        )
        private val LOCATION_RE = listOf(
            Regex("""(?i)\bdownload(?:s|ed|ing)?\b""") to "Downloads",
            Regex("""(?i)\bdocuments?\b""") to "Documents",
            Regex("""(?i)\bdcim\b""") to "DCIM",
            Regex("""(?i)\bpictures?\b|\bphotos?\b|\bimages?\b""") to "Pictures",
            Regex("""(?i)\bmovies?\b|\bvideos?\b""") to "Movies",
            Regex("""(?i)\bmusic\b|\baudio\b""") to "Music",
            Regex("""(?i)\bscreenshots?\b""") to "Screenshots",
            Regex("""(?i)\bwhatsapp\b""") to "WhatsApp"
        )
        private val FILLER = setOf(
            "my", "the", "a", "an", "file", "files", "document", "documents", "folder",
            "that", "this", "i", "it", "called", "named", "for", "some", "all", "any",
            "please", "want", "to", "on", "latest", "newest", "recent", "me"
        )
    }
}
