package com.aura.resolver

/**
 * Local device file search — pure, platform-agnostic contract.
 *
 * The resolver (L2) produces a [FileSearchRequest] and consumes a [FileSearchResponse].
 * The actual MediaStore/ContentResolver access lives behind [FileSearchSource] in
 * platform/android — this file must never import Context, Uri, ContentResolver,
 * DocumentsContract, or MediaStore. That boundary is enforced by L0ArchitectureTest.
 *
 * Location hints are a closed, safe vocabulary mapped to MediaStore collections by the
 * platform implementation. AURA never scans the raw filesystem and never becomes a file
 * manager (no navigation, edit, delete, move, copy, cloud).
 */
data class FileSearchRequest(
    val query: String,
    /** One of: Downloads, Documents, Pictures, DCIM, Movies, Music, All, or null. */
    val locationHint: String?
)

data class FileSearchResult(
    /** Opaque id; the platform source sets it to "file:<contentUriString>" so the UI
     *  can reconstruct the open action without the domain layer knowing about Uris. */
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    val mimeType: String?,
    val locationLabel: String,
    val contentUriString: String
)

data class FileSearchResponse(
    val results: List<FileSearchResult>,
    val permissionDenied: Boolean
)

interface FileSearchSource {
    fun search(request: FileSearchRequest): FileSearchResponse
}

/** Default no-op used in tests and as the L2Resolver default (no Android dependency). */
object NoOpFileSearchSource : FileSearchSource {
    override fun search(request: FileSearchRequest) = FileSearchResponse(emptyList(), permissionDenied = false)
}
