package com.aura.platform.android

import java.io.File

/**
 * Bounded, cancelable filesystem traversal. Pure over [java.io.File] so it can be unit-tested
 * on the JVM without Android. The repository uses this to build an in-memory index of shared
 * storage when the user has granted all-files access; the main thread only ever queries that
 * index (fast) — the walk itself runs on a background dispatcher and honors [shouldContinue].
 */
object FileSystemWalker {

    data class Entry(
        val file: File,
        /** Path relative to the walked root, e.g. "Download/invoice.pdf". */
        val relativePath: String,
        val name: String,
        val sizeBytes: Long,
        val modifiedMillis: Long
    )

    val DEFAULT_SKIP_DIRS = setOf("Android", "lost+found", "cache", "caches")

    fun collect(
        roots: List<File>,
        maxFiles: Int,
        shouldContinue: () -> Boolean,
        skipDirs: Set<String> = DEFAULT_SKIP_DIRS
    ): List<Entry> {
        val out = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()
        for (root in roots) {
            if (!shouldContinue() || out.size >= maxFiles) break
            if (root.exists() && root.isDirectory) {
                walk(root, root, out, seen, maxFiles, shouldContinue, skipDirs)
            }
        }
        return out
    }

    private fun walk(
        root: File,
        dir: File,
        out: MutableList<Entry>,
        seen: MutableSet<String>,
        maxFiles: Int,
        shouldContinue: () -> Boolean,
        skipDirs: Set<String>
    ) {
        if (!shouldContinue() || out.size >= maxFiles) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (!shouldContinue() || out.size >= maxFiles) return
            if (child.isDirectory) {
                val name = child.name
                if (name in skipDirs || name.startsWith(".")) continue
                walk(root, child, out, seen, maxFiles, shouldContinue, skipDirs)
            } else {
                val path = relative(root, child)
                if (seen.add(path)) {
                    out.add(
                        Entry(
                            file = child,
                            relativePath = path,
                            name = child.name,
                            sizeBytes = runCatching { child.length() }.getOrDefault(0L),
                            modifiedMillis = child.lastModified()
                        )
                    )
                }
            }
        }
    }

    private fun relative(root: File, file: File): String {
        val rootPath = root.absolutePath.trimEnd(File.separatorChar)
        val fp = file.absolutePath
        return if (fp.startsWith(rootPath + File.separator)) fp.substring(rootPath.length + 1) else fp
    }
}
