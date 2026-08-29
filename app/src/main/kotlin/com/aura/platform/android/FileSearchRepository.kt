package com.aura.platform.android

import android.Manifest
import android.annotation.TargetApi
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.aura.resolver.FileSearchRequest
import com.aura.resolver.FileSearchResponse
import com.aura.resolver.FileSearchResult
import com.aura.resolver.FileSearchSource
import com.aura.resolver.locationFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale

/**
 * Platform file-search source. Implements [FileSearchSource] so the resolver never sees Android
 * storage details.
 *
 * Storage model (Android 14 / API 34, personal launcher — not Play-constrained):
 *  - Without all-files access: scoped MediaStore query (media + Downloads/documents the OS allows).
 *    Fast and safe; results may be partial, which is why the matcher can offer to request broader
 *    access when a search comes back empty.
 *  - With [Manifest.permission.MANAGE_EXTERNAL_STORAGE] (granted on demand, never at install): a
 *    bounded in-memory index of shared storage built on a background dispatcher. Live queries hit
 *    the index (main-thread safe, capped). This covers Downloads/Documents/DCIM/Pictures/Movies/
 *    Music and arbitrary user folders — broad local discovery.
 *
 * Opening any result goes through [androidx.core.content.FileProvider] for filesystem files (never
 * a raw file:// Uri) or a MediaStore content:// Uri; the executor grants read permission to the
 * chosen viewer.
 */
class FileSearchRepository(private val context: Context) : FileSearchSource {

    private val authority = "${context.packageName}.fileprovider"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var index: List<FileSystemWalker.Entry>? = null
    @Volatile private var building = false

    override fun search(request: FileSearchRequest): FileSearchResponse {
        return if (canManageStorage()) {
            ensureIndexBuilt()
            val idx = index
            if (idx != null) {
                FileSearchResponse(filesystemQuery(request, idx))
            } else {
                // Index still building in the background — return scoped MediaStore results now.
                FileSearchResponse(mediaStoreSearch(request))
            }
        } else {
            val media = mediaStoreSearch(request)
            // Offer to request broader access only when scoped search found nothing.
            FileSearchResponse(media, requiresManageStorage = media.isEmpty())
        }
    }

    @TargetApi(30)
    private fun canManageStorage(): Boolean =
        Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    @Synchronized
    private fun ensureIndexBuilt() {
        if (building || index != null) return
        building = true
        scope.launch {
            try {
                val root = Environment.getExternalStorageDirectory()
                val entries = FileSystemWalker.collect(
                    roots = listOf(root),
                    maxFiles = MAX_INDEX_FILES,
                    shouldContinue = { building }
                )
                mutex.withLock { index = entries }
            } finally {
                building = false
            }
        }
    }

    /** Drop the cached index (e.g., after the user revokes all-files access). */
    fun clearCache() {
        building = false
        index = null
    }

    private fun filesystemQuery(request: FileSearchRequest, idx: List<FileSystemWalker.Entry>): List<FileSearchResult> {
        val q = request.query.lowercase().trim()
        val folder = locationFolder(request.locationHint)
        val matches = mutableListOf<FileSystemWalker.Entry>()
        for (e in idx) {
            if (matches.size >= MAX_QUERY_MATCHES) break
            val rel = e.relativePath.lowercase()
            val name = e.name.lowercase()
            if (folder != null && !rel.contains(folder)) continue
            if (q.isNotEmpty() && !(name.contains(q) || rel.contains(q))) continue
            matches.add(e)
        }
        return matches.mapNotNull { toResult(it) }
    }

    private fun toResult(e: FileSystemWalker.Entry): FileSearchResult? {
        val uri = runCatching { FileProvider.getUriForFile(context, authority, e.file) }.getOrNull()
            ?: return null
        return FileSearchResult(
            id = "file:$uri",
            displayName = e.name,
            pathLabel = e.relativePath,
            locationLabel = friendlyLocation(e.relativePath),
            sizeLabel = humanSize(e.sizeBytes),
            modifiedMillis = e.modifiedMillis,
            mimeType = guessMime(e.name),
            contentUriString = uri.toString()
        )
    }

    // ---- Scoped MediaStore fallback ----

    @TargetApi(33)
    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun mediaStoreSearch(request: FileSearchRequest): List<FileSearchResult> {
        if (!hasMediaPermission()) return emptyList()
        val q = request.query.lowercase().trim()
        val out = mutableListOf<FileSearchResult>()
        for (collection in collectionsFor(request.locationHint)) {
            if (out.size >= MAX_QUERY_MATCHES) break
            queryCollection(collection, q, out)
        }
        return out
    }

    @TargetApi(29)
    private fun collectionsFor(location: String?): List<Uri> {
        val folder = locationFolder(location)
        val list = mutableListOf<Uri>()
        if (folder == null || folder == "picture" || folder == "screenshot" || folder == "dcim") {
            list += MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        if (folder == null || folder == "movie") list += MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        if (folder == null || folder == "music") list += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        if (folder == null || folder == "download") {
            if (Build.VERSION.SDK_INT >= 29) list += MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        if (folder == null || folder == "document") {
            if (Build.VERSION.SDK_INT >= 29) list += MediaStore.Files.getContentUri("external")
        }
        return list
    }

    @TargetApi(29)
    private fun queryCollection(collection: Uri, q: String, out: MutableList<FileSearchResult>) {
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val sel = if (q.isNotEmpty()) "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" else null
        val selArgs = if (q.isNotEmpty()) arrayOf("%$q%") else null
        runCatching {
            context.contentResolver.query(
                collection, proj, sel, selArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (c.moveToNext() && out.size < MAX_QUERY_MATCHES) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: continue
                    val size = c.getLong(sizeIdx)
                    val mod = c.getLong(modIdx) * 1000L
                    val mime = c.getString(mimeIdx)
                    val uri = ContentUris.withAppendedId(collection, id)
                    out += FileSearchResult(
                        id = "file:$uri",
                        displayName = name,
                        pathLabel = friendlyDataPath(c, name),
                        locationLabel = friendlyLocationFromUri(collection),
                        sizeLabel = humanSize(size),
                        modifiedMillis = mod,
                        mimeType = mime,
                        contentUriString = uri.toString()
                    )
                }
            }
        }
    }

    @TargetApi(29)
    private fun friendlyLocationFromUri(collection: Uri): String = when (collection) {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "Pictures"
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "Movies"
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> "Music"
        MediaStore.Downloads.EXTERNAL_CONTENT_URI -> "Downloads"
        else -> "Documents"
    }

    private fun friendlyDataPath(c: Cursor, name: String): String {
        val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
        val data = if (dataIdx >= 0) runCatching { c.getString(dataIdx) }.getOrNull() else null
        if (data.isNullOrBlank()) return name
        val parent = File(data).parentFile?.name ?: return name
        return "$parent/$name"
    }

    private fun friendlyLocation(relativePath: String): String {
        val seg = relativePath.substringBefore('/')
        if (seg.isBlank() || seg == relativePath) return "Storage"
        return seg.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var u = 0
        while (size >= 1024 && u < units.size - 1) {
            size /= 1024.0
            u++
        }
        return if (u == 0) "$bytes ${units[0]}" else String.format(Locale.US, "%.1f %s", size, units[u])
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    companion object {
        const val MAX_INDEX_FILES = 20_000
        const val MAX_QUERY_MATCHES = 300
    }
}
