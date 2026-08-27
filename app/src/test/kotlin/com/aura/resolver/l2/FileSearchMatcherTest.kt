package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.resolver.FileSearchRequest
import com.aura.resolver.FileSearchResponse
import com.aura.resolver.FileSearchResult
import com.aura.resolver.FileSearchSource
import com.aura.resolver.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4C — local file search. Pure logic only; the Android MediaStore half lives behind
 * [FileSearchSource] and is faked here. These tests verify verb/location extraction, mapping
 * to ACT/ASK, honest empty/permission behavior, and that non-file queries are not hijacked.
 */
class FileSearchMatcherTest {

    private val source = FakeFileSearchSource()
    private val matcher = FileSearchMatcher(source)

    private fun file(id: String, name: String, location: String, size: String, mime: String? = null) =
        FileSearchResult(
            id = "file:content://$id",
            displayName = name,
            sizeLabel = size,
            mimeType = mime,
            locationLabel = location,
            contentUriString = "content://$id"
        )

    private fun match(q: String) = matcher.match(Normalizer.normalize(q), q)

    @Test
    fun find_invoice_resolves_single_file_as_act() {
        source.responses[FileSearchRequest("invoice", null)] =
            FileSearchResponse(listOf(file("1", "invoice.pdf", "Downloads", "2.4 MB")), false)
        val r = match("find invoice")
        assertTrue(r is L2Result.Resolved)
        val res = (r as L2Result.Resolved).result
        assertEquals("invoice.pdf", res.title)
        assertEquals("Downloads · 2.4 MB", res.subtitle)
        assertTrue(res.action is AuraAction.OpenFile)
        assertEquals("content://1", (res.action as AuraAction.OpenFile).uriString)
        assertEquals(ResultType.File, res.type)
    }

    @Test
    fun find_downloads_lists_location_with_empty_query() {
        source.responses[FileSearchRequest("", "Downloads")] = FileSearchResponse(
            listOf(file("1", "a.pdf", "Downloads", "1 MB"), file("2", "b.pdf", "Downloads", "2 MB")),
            false
        )
        val r = match("find downloads")
        assertTrue(r is L2Result.Ambiguous)
        val group = (r as L2Result.Ambiguous).group
        assertEquals("Files", group.label)
        assertEquals(2, group.candidates.size)
        assertEquals("file:content://1", group.candidates[0].id)
        // location hint + empty term were derived correctly
        assertEquals("Downloads", source.lastRequest?.locationHint)
        assertEquals("", source.lastRequest?.query)
    }

    @Test
    fun multiple_results_become_ask_candidates() {
        source.responses[FileSearchRequest("", null)] = FileSearchResponse(
            listOf(
                file("1", "x.pdf", "Pictures", "1 MB"),
                file("2", "y.pdf", "Downloads", "2 MB"),
                file("3", "z.pdf", "Music", "3 MB")
            ),
            false
        )
        val r = match("find files")
        assertTrue(r is L2Result.Ambiguous)
        assertEquals(3, (r as L2Result.Ambiguous).group.candidates.size)
    }

    @Test
    fun query_is_case_insensitive_and_whitespace_normalized() {
        source.responses[FileSearchRequest("invoice.pdf", null)] =
            FileSearchResponse(listOf(file("1", "invoice.pdf", "Downloads", "2.4 MB")), false)
        match("  Find   INVOICE.PDF  ")
        assertEquals("invoice.pdf", source.lastRequest?.query)
    }

    @Test
    fun location_keyword_is_stripped_from_filename_query() {
        source.responses[FileSearchRequest("pdf", "Downloads")] =
            FileSearchResponse(listOf(file("1", "receipt.pdf", "Downloads", "1 MB")), false)
        match("find the PDF I downloaded")
        assertEquals("Downloads", source.lastRequest?.locationHint)
        assertEquals("pdf", source.lastRequest?.query)
    }

    @Test
    fun unsupported_location_with_no_match_is_unrecognized() {
        val r = match("find mars")
        assertTrue(r is L2Result.Unrecognized)
    }

    @Test
    fun empty_results_are_unrecognized_not_faked() {
        val r = match("find invoice")
        assertTrue(r is L2Result.Unrecognized)
    }

    @Test
    fun permission_denied_is_honest_invalid_not_fake() {
        source.responses[FileSearchRequest("invoice", null)] =
            FileSearchResponse(emptyList(), permissionDenied = true)
        val r = match("find invoice")
        assertTrue(r is L2Result.Invalid)
    }

    @Test
    fun non_file_queries_are_not_hijacked() {
        // "open chrome" has no file-search verb; matcher must not claim a file.
        val r = match("open chrome")
        assertTrue(r is L2Result.Unrecognized)
    }

    @Test
    fun short_query_is_ignored() {
        assertTrue(match("f") is L2Result.Unrecognized)
    }

    private class FakeFileSearchSource : FileSearchSource {
        val responses = mutableMapOf<FileSearchRequest, FileSearchResponse>()
        var lastRequest: FileSearchRequest? = null
        override fun search(request: FileSearchRequest): FileSearchResponse {
            lastRequest = request
            return responses[request] ?: FileSearchResponse(emptyList(), permissionDenied = false)
        }
    }
}
