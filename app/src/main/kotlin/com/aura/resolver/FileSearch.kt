package com.aura.resolver

/**
 * Pure file-search domain types + ranking. NO Android imports — the actual storage access
 * (MediaStore, filesystem walk, FileProvider) lives behind [FileSearchSource] in the platform
 * layer. The Command Bar sends a [FileSearchRequest] and receives ranked [FileSearchResult]s.
 */
data class FileSearchRequest(
    val query: String,
    val locationHint: String? = null
)

data class FileSearchResult(
    /** Opaque id: "file:<contentUriString>". Domain never parses the Uri. */
    val id: String,
    val displayName: String,
    /** Relative path for display, e.g. "Download/invoice.pdf". */
    val pathLabel: String,
    /** Friendly folder name, e.g. "Downloads". */
    val locationLabel: String,
    /** Human-readable size, e.g. "2.4 MB". */
    val sizeLabel: String,
    val modifiedMillis: Long,
    val mimeType: String?,
    /** content:// Uri string the platform executor can open via ACTION_VIEW. */
    val contentUriString: String
)

data class FileSearchResponse(
    val results: List<FileSearchResult>,
    /** Scoped-storage read permission is missing — nothing can be searched. */
    val permissionDenied: Boolean = false,
    /** Broad (all-files) access is not granted; AURA can offer to request it when empty. */
    val requiresManageStorage: Boolean = false
)

interface FileSearchSource {
    fun search(request: FileSearchRequest): FileSearchResponse
}

object NoOpFileSearchSource : FileSearchSource {
    override fun search(request: FileSearchRequest): FileSearchResponse =
        FileSearchResponse(emptyList(), permissionDenied = false, requiresManageStorage = false)
}

/** Pure, deterministic ranking. Single source of truth for result order (used by matcher). */
fun rankFileResults(request: FileSearchRequest, results: List<FileSearchResult>): List<FileSearchResult> {
    if (results.isEmpty()) return emptyList()
    val q = request.query.lowercase().trim()
    val types = queryTypeExtensions(request.query)
    val location = request.locationHint?.lowercase()

    val scored = results.map { r ->
        var score = 0
        val name = r.displayName.lowercase()
        val nameNoExt = name.substringBeforeLast('.', name)
        val path = r.pathLabel.lowercase()
        val ext = r.displayName.substringAfterLast('.', "").lowercase()

        when {
            name == q -> score += 1000
            nameNoExt == q -> score += 900
            name.startsWith(q) -> score += 500
            name.contains(q) -> score += 300
            path.contains(q) -> score += 150
        }
        // Type relevance (e.g., "pdf", "image", "invoice" implied document)
        if (types.isNotEmpty() && ext in types) score += 250
        // Location relevance
        if (!location.isNullOrBlank() && path.contains(location)) score += 150
        r to score
    }

    return scored
        .distinctBy { it.first.contentUriString }
        .sortedWith(
            compareByDescending<Pair<FileSearchResult, Int>> { it.second }
                .thenByDescending { it.first.modifiedMillis }
        )
        .map { it.first }
        .take(MAX_RANKED)
}

/**
 * Map natural-language type words to file extensions for ranking/boosting.
 * Baseline examples only — NOT an exclusive allow-list; the repository still indexes every file.
 */
fun queryTypeExtensions(query: String): Set<String> {
    val q = query.lowercase()
    val out = mutableSetOf<String>()
    if ("pdf" in q) out += "pdf"
    if ("doc" in q || "document" in q) out += setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv")
    if ("sheet" in q || "excel" in q || "xls" in q) out += setOf("xls", "xlsx", "csv")
    if ("slide" in q || "ppt" in q) out += setOf("ppt", "pptx")
    if ("text" in q || "txt" in q || "note" in q) out += setOf("txt", "md", "rtf")
    if ("image" in q || "photo" in q || "picture" in q || "screenshot" in q || "img" in q) {
        out += setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    }
    if ("video" in q || "movie" in q || "clip" in q || "film" in q) out += setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")
    if ("music" in q || "audio" in q || "song" in q || "sound" in q) {
        out += setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus")
    }
    if ("zip" in q || "archive" in q) out += setOf("zip", "rar", "7z", "tar", "gz")
    if ("apk" in q) out += setOf("apk")
    return out
}

/** Map a location hint to the folder name used for path filtering/labeling. */
fun locationFolder(hint: String?): String? {
    val h = hint?.lowercase()?.trim() ?: return null
    return when {
        h.startsWith("download") -> "download"
        h.startsWith("document") -> "document"
        h == "dcim" -> "dcim"
        h.startsWith("picture") || h.startsWith("photo") || h.startsWith("image") -> "picture"
        h.startsWith("movie") || h.startsWith("video") -> "movie"
        h.startsWith("music") || h.startsWith("audio") -> "music"
        h.startsWith("screenshot") -> "screenshot"
        h.startsWith("whatsapp") -> "whatsapp"
        else -> null
    }
}

/** Max results surfaced to the UI after ranking. */
const val MAX_RANKED: Int = 40
