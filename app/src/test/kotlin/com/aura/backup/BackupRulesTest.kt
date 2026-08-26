package com.aura.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupRulesTest {

    private fun manifestText(): String {
        val candidates = listOf(
            File("/home/titan/AURA/app/src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val f = candidates.firstOrNull { it.exists() } ?: error("AndroidManifest.xml not found")
        return f.readText()
    }

    private fun backupRulesText(): String {
        val f = File("/home/titan/AURA/app/src/main/res/xml/backup_rules.xml")
        val alt = File("app/src/main/res/xml/backup_rules.xml")
        val file = if (f.exists()) f else alt
        assertTrue("backup_rules.xml must exist", file.exists())
        return file.readText()
    }

    private fun dataExtractionText(): String {
        val f = File("/home/titan/AURA/app/src/main/res/xml/data_extraction_rules.xml")
        val alt = File("app/src/main/res/xml/data_extraction_rules.xml")
        val file = if (f.exists()) f else alt
        assertTrue("data_extraction_rules.xml must exist", file.exists())
        return file.readText()
    }

    @Test fun `manifest keeps allowBackup true`() {
        val text = manifestText()
        assertTrue(text.contains("android:allowBackup=\"true\""))
        assertFalse("Do not set allowBackup=false — we want explicit exclusion, not blanket disable",
            text.contains("android:allowBackup=\"false\""))
    }

    @Test fun `manifest references fullBackupContent`() {
        val text = manifestText()
        assertTrue(text.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }

    @Test fun `manifest references dataExtractionRules for API 31+`() {
        val text = manifestText()
        assertTrue(text.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test fun `backup_rules excludes aura_home sharedpref`() {
        val text = backupRulesText()
        assertTrue(text.contains("domain=\"sharedpref\""))
        assertTrue(text.contains("aura_home.xml"))
        assertTrue(text.contains("<exclude"))
    }

    @Test fun `data_extraction_rules excludes aura_home for both cloud and device transfer`() {
        val text = dataExtractionText()
        assertTrue(text.contains("<cloud-backup>"))
        assertTrue(text.contains("<device-transfer>"))
        // Both sections must exclude
        val cloudSection = text.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val deviceSection = text.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(cloudSection.contains("aura_home.xml"))
        assertTrue(deviceSection.contains("aura_home.xml"))
    }

    @Test fun `no accidental broad backup exclude`() {
        val backup = backupRulesText()
        val data = dataExtractionText()
        // Must not exclude everything — only aura_home.xml
        assertFalse(backup.contains("domain=\"file\""))
        assertFalse(backup.contains("domain=\"database\""))
        assertFalse(data.contains("domain=\"file\""))
    }
}
