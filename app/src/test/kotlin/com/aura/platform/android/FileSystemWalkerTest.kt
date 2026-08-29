package com.aura.platform.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bounded, cancelable traversal over java.io.File — run on the JVM (no Android).
 * Platform filesystem tests are kept separate from the pure ranking/query tests.
 */
class FileSystemWalkerTest {

    private fun tmpDir(name: String): File {
        val d = File(System.getProperty("java.io.tmpdir"), "aura_fs_test_${name}_${System.nanoTime()}")
        d.deleteRecursively()
        assertTrue(d.mkdirs())
        return d
    }

    private fun touch(dir: File, relative: String): File {
        val f = File(dir, relative)
        f.parentFile?.mkdirs()
        assertTrue(f.createNewFile())
        return f
    }

    @Test
    fun collectsFilesRecursivelyAndSkipsSystemDirs() {
        val root = tmpDir("rec")
        touch(root, "a.pdf")
        touch(root, "sub/b.txt")
        val android = File(root, "Android")
        touch(android, "private.log")
        val hidden = File(root, ".thumbnails")
        touch(hidden, "x.jpg")

        val entries = FileSystemWalker.collect(listOf(root), maxFiles = 10_000, shouldContinue = { true })
        val names = entries.map { it.name }.toSet()
        assertTrue("a.pdf" in names)
        assertTrue("b.txt" in names)
        assertFalse("private.log" in names)   // Android/ skipped
        assertFalse("x.jpg" in names)          // hidden dir skipped
    }

    @Test
    fun respectsFileCap() {
        val root = tmpDir("cap")
        repeat(50) { touch(root, "f$it.txt") }
        val entries = FileSystemWalker.collect(listOf(root), maxFiles = 10, shouldContinue = { true })
        assertEquals(10, entries.size)
    }

    @Test
    fun stopsWhenCancelled() {
        val root = tmpDir("cancel")
        repeat(20) { touch(root, "g$it.txt") }
        var allowed = true
        val entries = FileSystemWalker.collect(listOf(root), maxFiles = 10_000, shouldContinue = { allowed })
        allowed = false
        // The first collect already finished; verify a second collect honoring cancellation early.
        val entries2 = FileSystemWalker.collect(listOf(root), maxFiles = 10_000, shouldContinue = { false })
        assertEquals(0, entries2.size)
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun relativePathIsReported() {
        val root = tmpDir("rel")
        touch(root, "sub/deep/invoice.pdf")
        val entries = FileSystemWalker.collect(listOf(root), maxFiles = 10_000, shouldContinue = { true })
        assertEquals("sub/deep/invoice.pdf", entries.first { it.name == "invoice.pdf" }.relativePath)
    }
}
