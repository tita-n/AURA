package com.aura.platform.android

import android.content.Context
import android.content.pm.PackageManager
import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.aura.resolver.FileSearchRequest
import com.aura.resolver.FileSearchResponse
import com.aura.resolver.FileSearchResult
import com.aura.resolver.FileSearchSource

/**
 * Platform implementation of [FileSearchSource] — the ONLY place that touches MediaStore /
 * ContentResolver. Domain and resolver layers never see Uri, Context, or ContentResolver.
 *
 * Scope (Phase 4C, Android 14 scoped storage):
 *  - Uses MediaStore collections (Images / Video / Audio / Downloads / Files) — never a raw
 *    recursive filesystem walk and never MANAGE_EXTERNAL_STORAGE.
 *  - Reads only DISPLAY_NAME + SIZE + MIME_TYPE; opens via a content Uri handed to a chooser.
 *  - Permission is checked; if unavailable the source reports permissionDenied (honest), and
 *    inaccessible collections are skipped rather than crashing.
 * This is SEARCH ONLY: no navigation, edit, delete, move, copy, or cloud.
 */
class AndroidFileSearchSource(private val context: Context) : FileSearchSource {

    companion object {
        private const val MAX = 20
    }

    override fun search(request: FileSearchRequest): FileSearchResponse {
        if (!hasStoragePermission()) return FileSearchResponse(emptyList(), permissionDenied = true)

        val collections = collectionsFor(request.locationHint)
        val results = mutableListOf<FileSearchResult>()
        val query = request.query.trim()
        val selection = if (query.isBlank()) null else "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selArgs = if (query.isBlank()) null else arrayOf("%$query%")

        for (collection in collections) {
            if (results.size >= MAX) break
            val label = request.locationHint ?: labelFor(collection)
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
            )
            try {
                context.contentResolver.query(
                    collection, projection, selection, selArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    while (cursor.moveToNext() && results.size < MAX) {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                        val uri = ContentUrisCompat.withAppendedId(collection, id)
                        results.add(
                            FileSearchResult(
                                id = "file:$uri",
                                displayName = name,
                                sizeLabel = formatSize(size),
                                mimeType = mime,
                                locationLabel = label,
                                contentUriString = uri.toString()
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Collection inaccessible (e.g. documents without the right permission on some
                // builds) — skip it rather than failing the whole search.
                continue
            }
        }
        return FileSearchResponse(results.distinctBy { it.contentUriString }.take(MAX), permissionDenied = false)
    }

    @TargetApi(29)
    private fun collectionsFor(location: String?): List<Uri> {
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val video = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val downloads = if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI else null
        val files = if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.getContentUri("external") else null
        return when (location) {
            "Downloads" -> listOfNotNull(downloads)
            "Documents" -> listOfNotNull(files, downloads)
            "Pictures" -> listOf(images)
            "DCIM" -> listOf(images)
            "Movies" -> listOf(video)
            "Music" -> listOf(audio)
            else -> listOfNotNull(images, video, audio, downloads, files)
        }
    }

    @TargetApi(29)
    private fun labelFor(uri: Uri): String = when (uri) {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "Pictures"
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "Movies"
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> "Music"
        MediaStore.Downloads.EXTERNAL_CONTENT_URI -> "Downloads"
        else -> "Files"
    }

    private fun hasStoragePermission(): Boolean {
        val granted = PackageManager.PERMISSION_GRANTED
        val check: (String) -> Boolean = { context.checkSelfPermission(it) == granted }
        return if (Build.VERSION.SDK_INT >= 33) {
            check(android.Manifest.permission.READ_MEDIA_IMAGES)
                || check(android.Manifest.permission.READ_MEDIA_VIDEO)
                || check(android.Manifest.permission.READ_MEDIA_AUDIO)
                || check(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            check(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        return if (kb < 1024) "%.0f KB".format(kb)
        else "%.1f MB".format(kb / 1024)
    }
}

/** Tiny compatibility shim so we don't depend on a specific core-ktx version for ContentUris. */
private object ContentUrisCompat {
    fun withAppendedId(contentUri: Uri, id: Long): Uri =
        android.content.ContentUris.withAppendedId(contentUri, id)
}
