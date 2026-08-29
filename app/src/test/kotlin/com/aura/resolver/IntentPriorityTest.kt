package com.aura.resolver

import com.aura.domain.AuraAction
import com.aura.domain.ResolutionOutcome
import com.aura.domain.ResultType
import com.aura.resolver.l1.L1Resolver
import com.aura.resolver.l2.L2Resolver
import com.aura.resolver.l3.L3Validator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Intent precedence across the pipeline (L0 -> L1 -> L2). File search and calculator are now
 * first-class L2 capabilities but must not steal ordinary app searches or each other.
 */
class IntentPriorityTest {

    private val notesApp = IndexedEntity(
        id = "app:com.example.notes",
        displayLabel = "Notes",
        normalizedLabel = "notes",
        category = EntityCategory.App,
        resultType = ResultType.App,
        action = AuraAction.OpenApp("com.example.notes")
    )
    private val spotifyApp = IndexedEntity(
        id = "app:com.spotify",
        displayLabel = "Spotify",
        normalizedLabel = "spotify",
        category = EntityCategory.App,
        resultType = ResultType.App,
        action = AuraAction.OpenApp("com.spotify")
    )

    private val fakeFileSource = object : FileSearchSource {
        override fun search(request: FileSearchRequest): FileSearchResponse {
            val hit = request.query.contains("invoice") || request.query.contains("notes") || request.locationHint != null
            if (!hit) return FileSearchResponse(emptyList())
            return FileSearchResponse(
                listOf(
                    FileSearchResult(
                        id = "file:content://x/${request.query}",
                        displayName = "${request.query}.pdf",
                        pathLabel = "Docs/${request.query}.pdf",
                        locationLabel = "Docs",
                        sizeLabel = "1 KB",
                        modifiedMillis = 1000,
                        mimeType = "application/pdf",
                        contentUriString = "content://x/${request.query}"
                    )
                )
            )
        }
    }

    private fun router(): IntentRouter {
        val index = L0IndexFactory.buildIndex(listOf(notesApp, spotifyApp), contacts = emptyList())
        return IntentRouter(L0Resolver(index), L1Resolver(index), L2Resolver(index, fakeFileSource), L3Validator(index))
    }

    private fun isFile(outcome: ResolutionOutcome): Boolean =
        outcome is ResolutionOutcome.Act && outcome.result.type == ResultType.File

    @Test
    fun bareAppNameResolvesToAppNotFile() {
        val o = router().route("notes")
        assertTrue(o is ResolutionOutcome.Act)
        assertTrue((o as ResolutionOutcome.Act).result.action is AuraAction.OpenApp)
    }

    @Test
    fun spotifyResolvesToAppNotFile() {
        val o = router().route("spotify")
        assertTrue(o is ResolutionOutcome.Act)
        assertTrue((o as ResolutionOutcome.Act).result.action is AuraAction.OpenApp)
    }

    @Test
    fun findPhraseRoutesToFileSearch() {
        val o = router().route("find my notes file")
        assertTrue(isFile(o))
    }

    @Test
    fun findDownloadsRoutesToFileSearch() {
        val o = router().route("find downloads")
        assertTrue(isFile(o))
    }

    @Test
    fun calculatorQueryResolvesImmediately() {
        val o = router().route("25 * 4")
        assertTrue(o is ResolutionOutcome.Act)
        assertEquals("100", (o as ResolutionOutcome.Act).result.title)
    }

    @Test
    fun fileSearchDoesNotHijackUnknownAppName() {
        // "open chrome" has no indexed app and the fake source only matches invoice/notes/downloads,
        // so it must fall through to a calm Empty, never a file.
        val o = router().route("open chrome")
        assertTrue(o is ResolutionOutcome.Empty)
    }
}
