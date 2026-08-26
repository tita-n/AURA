package com.aura

import java.io.File

object TestPaths {
    fun find(relative: String): File {
        // relative like "app/src/main/AndroidManifest.xml"
        // Try various bases: user.dir, user.dir/.., ".", ".."
        val candidates = listOf(
            File(relative),
            File(System.getProperty("user.dir"), relative),
            File(System.getProperty("user.dir"), "../$relative"),
            File("../$relative"),
            File("./$relative")
        )
        // Also try walking up from user.dir to find project root containing app/src/main
        val userDir = File(System.getProperty("user.dir"))
        val walked = generateSequence(userDir) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.exists() }
        if (walked != null) return walked
        return candidates.firstOrNull { it.exists() } ?: File(relative)
    }

    fun readText(relative: String): String = find(relative).readText()
}
