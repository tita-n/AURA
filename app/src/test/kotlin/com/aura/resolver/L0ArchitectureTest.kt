package com.aura.resolver
import com.aura.TestPaths

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class L0ArchitectureTest {

    private fun readFile(path: String): String = File(path).readText()

    private fun assertNoImport(filePath: String, forbidden: String) {
        val text = readFile(filePath)
        assertFalse("File $filePath must not import $forbidden", text.contains(forbidden))
    }

    @Test
    fun `domain does not import Android APIs`() {
        val domainDir = TestPaths.find("app/src/main/kotlin/com/aura/domain")
        domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse("${f.name} must not import PackageManager", text.contains("import android.content.pm.PackageManager"))
            assertFalse("${f.name} must not import ContactsContract", text.contains("import android.provider.ContactsContract"))
            assertFalse("${f.name} must not import AlarmClock", text.contains("AlarmClock"))
            assertFalse("${f.name} must not import android.content.Intent", text.contains("android.content.Intent"))
            assertFalse("${f.name} must not import android.content.Context", text.contains("android.content.Context"))
        }
    }

    @Test
    fun `resolver does not import Android APIs`() {
        val resolverDir = TestPaths.find("app/src/main/kotlin/com/aura/resolver")
        resolverDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse("${f.name} must not import PackageManager", text.contains("import android.content.pm.PackageManager"))
            assertFalse("${f.name} must not import ContactsContract", text.contains("import android.provider.ContactsContract"))
            assertFalse("${f.name} must not import AlarmClock", text.contains("import android.provider.AlarmClock"))
            assertFalse("${f.name} must not import android.content.Context", text.contains("import android.content.Context"))
        }
    }

    @Test
    fun `ui does not directly call Android execution APIs`() {
        val uiDir = TestPaths.find("app/src/main/kotlin/com/aura/ui")
        uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            assertFalse("${f.name} must not import PackageManager", text.contains("import android.content.pm.PackageManager"))
            assertFalse("${f.name} must not import ContactsContract", text.contains("import android.provider.ContactsContract"))
        }
    }

    @Test
    fun `L0 does not store Android Intent`() {
        val file = resolveFile("app/src/main/kotlin/com/aura/resolver/IndexedEntity.kt")
        val resolverText = file.readText()
        assertFalse(resolverText.contains("android.content.Intent"))
        assertFalse(resolverText.contains("Intent("))
    }

    private fun resolveFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File(System.getProperty("user.dir"), relative),
            File(System.getProperty("user.dir"), "app/$relative"),
            TestPaths.find("$relative")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }

    @Test
    fun `platform is the only place with PackageManager`() {
        val platformFile = resolveFile("app/src/main/kotlin/com/aura/platform/AndroidAppIndexProvider.kt")
        assertTrue(platformFile.exists())
        assertTrue(platformFile.readText().contains("PackageManager"))
        // Ensure domain/resolver don't (check imports only)
        val domainHasPM = TestPaths.find("app/src/main/kotlin/com/aura/domain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .any { it.readText().contains("import android.content.pm.PackageManager") }
        assertFalse(domainHasPM)
    }
}
