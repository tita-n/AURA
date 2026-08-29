package com.aura.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure file-search ranking — exact > prefix > substring > path > recency > type > location.
 * No Android, no filesystem. The matcher and platform source both rely on [rankFileResults].
 */
class FileSearchRankingTest {

    private fun r(
        name: String,
        path: String,
        modified: Long,
        uri: String = "content://x/$path"
    ) = FileSearchResult(
        id = "file:$uri",
        displayName = name,
        pathLabel = path,
        locationLabel = path.substringBefore('/').ifBlank { "Storage" },
        sizeLabel = "1 KB",
        modifiedMillis = modified,
        mimeType = null,
        contentUriString = uri
    )

    @Test
    fun exactNameBeatsPrefixBeatsSubstringBeatsPathOnly() {
        val results = listOf(
            r("minvoice.txt", "Docs/minvoice.txt", 100),
            r("invoice_scan.pdf", "Docs/invoice_scan.pdf", 200),
            r("invoice.pdf", "Docs/invoice.pdf", 300),
            r("other.txt", "invoice/other.txt", 400)
        )
        val ranked = rankFileResults(FileSearchRequest("invoice"), results)
        assertEquals("invoice.pdf", ranked[0].displayName)        // exact
        assertEquals("invoice_scan.pdf", ranked[1].displayName)    // startsWith
        assertEquals("minvoice.txt", ranked[2].displayName)        // contains
        assertEquals("other.txt", ranked[3].displayName)           // path only
    }

    @Test
    fun recencyIsTiebreakerForEqualScore() {
        val results = listOf(
            r("same.pdf", "Docs/same.pdf", 1000, uri = "content://x/a"),
            r("same.pdf", "Docs/same.pdf", 5000, uri = "content://x/b")
        )
        val ranked = rankFileResults(FileSearchRequest("same.pdf"), results)
        assertEquals(5000, ranked[0].modifiedMillis)
    }

    @Test
    fun locationHintBoostsMatchingFolder() {
        val results = listOf(
            r("report.pdf", "Documents/report.pdf", 100),
            r("report.pdf", "Download/report.pdf", 200)
        )
        val ranked = rankFileResults(FileSearchRequest("report", "download"), results)
        assertEquals("Download/report.pdf", ranked[0].pathLabel)
    }

    @Test
    fun deduplicatesByIdenticalUri() {
        val results = listOf(
            r("invoice.pdf", "Docs/invoice.pdf", 100, uri = "content://dup/1"),
            r("invoice.pdf", "Docs/invoice.pdf", 200, uri = "content://dup/1")
        )
        val ranked = rankFileResults(FileSearchRequest("invoice"), results)
        assertEquals(1, ranked.size)
    }

    @Test
    fun emptyInputYieldsEmptyRanking() {
        assertEquals(emptyList<FileSearchResult>(), rankFileResults(FileSearchRequest("x"), emptyList()))
    }

    @Test
    fun typeExtensionsMapCorrectly() {
        assertTrue("pdf" in queryTypeExtensions("find pdf"))
        assertTrue("png" in queryTypeExtensions("show me images"))
        assertTrue("mp3" in queryTypeExtensions("my music"))
        assertTrue("zip" in queryTypeExtensions("the archive"))
        assertTrue("doc" in queryTypeExtensions("budget document"))
        assertFalse("mp4" in queryTypeExtensions("find invoice"))
    }

    @Test
    fun locationFolderMapsHint() {
        assertEquals("download", locationFolder("downloads"))
        assertEquals("picture", locationFolder("pictures"))
        assertEquals("movie", locationFolder("videos"))
        assertEquals("music", locationFolder("audio"))
        assertEquals("document", locationFolder("documents"))
        assertEquals(null, locationFolder("nowhere"))
    }
}
