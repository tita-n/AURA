package com.aura.home

import com.aura.TestPaths
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure media-context logic — no Android dependencies. Covers metadata mapping,
 * playback-state mapping, deterministic session selection, capability extraction,
 * and the privacy property that [MediaContext] carries no notification body.
 */
class MediaContextTest {

    private val art = MediaArtwork(byteArrayOf(1, 2, 3))

    @Test fun `playing maps to Playing with metadata`() {
        val ctx = MediaContext(
            title = "Calm Down", artist = "Rema", appLabel = "Spotify",
            album = "Rave & Roses", artwork = art, playbackState = MediaPlaybackState.PLAYING,
            capabilities = setOf(MediaCapability.PLAY, MediaCapability.PAUSE, MediaCapability.SKIP_NEXT)
        )
        val s = ctx.toMusicState()
        assertTrue(s is MusicState.Playing)
        assertEquals("Calm Down", (s as MusicState.Playing).title)
        assertEquals("Rema", s.artist)
        assertEquals("Spotify", s.appLabel)
        assertEquals(art, s.artwork)
    }

    @Test fun `paused maps to Paused with metadata`() {
        val ctx = MediaContext(title = "Title", artist = "Artist", playbackState = MediaPlaybackState.PAUSED)
        val s = ctx.toMusicState()
        assertTrue(s is MusicState.Paused)
        assertEquals("Title", (s as MusicState.Paused).title)
    }

    @Test fun `stopped buffering error and none map to Hidden`() {
        listOf(MediaPlaybackState.STOPPED, MediaPlaybackState.BUFFERING, MediaPlaybackState.ERROR, MediaPlaybackState.NONE)
            .forEach { state ->
                assertEquals(
                    "state $state must clear the surface",
                    MusicState.Hidden,
                    MediaContext(playbackState = state).toMusicState()
                )
            }
    }

    @Test fun `fromInt maps known PlaybackState integers`() {
        assertEquals(MediaPlaybackState.PLAYING, MediaPlaybackState.fromInt(3))
        assertEquals(MediaPlaybackState.PAUSED, MediaPlaybackState.fromInt(2))
        assertEquals(MediaPlaybackState.STOPPED, MediaPlaybackState.fromInt(1))
        assertEquals(MediaPlaybackState.BUFFERING, MediaPlaybackState.fromInt(6))
        assertEquals(MediaPlaybackState.ERROR, MediaPlaybackState.fromInt(7))
        assertEquals(MediaPlaybackState.NONE, MediaPlaybackState.fromInt(0))
        assertEquals(MediaPlaybackState.NONE, MediaPlaybackState.fromInt(null))
    }

    @Test fun `capabilities extracted from action bitmask`() {
        // ACTION_SKIP_TO_NEXT (1<<52) + ACTION_SKIP_TO_PREVIOUS (1<<53) + ACTION_PLAY (1<<55) + ACTION_PAUSE (1<<56)
        val actions = (1L shl 52) or (1L shl 53) or (1L shl 55) or (1L shl 56)
        val caps = capabilitiesFromActions(actions)
        assertEquals(
            setOf(MediaCapability.SKIP_NEXT, MediaCapability.SKIP_PREVIOUS, MediaCapability.PLAY, MediaCapability.PAUSE),
            caps
        )
    }

    @Test fun `unsupported actions hidden in state`() {
        // Only play/pause supported -> no skip controls.
        val actions = (1L shl 55) or (1L shl 56)
        val ctx = MediaContext(playbackState = MediaPlaybackState.PLAYING, capabilities = capabilitiesFromActions(actions))
        val s = ctx.toMusicState() as MusicState.Playing
        assertFalse("next must be hidden when unsupported", s.canNext)
        assertFalse("prev must be hidden when unsupported", s.canPrev)
    }

    @Test fun `playing beats paused in selection`() {
        val playing = MediaContext(playbackState = MediaPlaybackState.PLAYING, packageName = "a", lastUpdatedMillis = 100)
        val paused = MediaContext(playbackState = MediaPlaybackState.PAUSED, packageName = "b", lastUpdatedMillis = 200)
        assertEquals(playing, MediaSessionSelector.best(listOf(paused, playing)))
    }

    @Test fun `most recent active session is preferred on tie`() {
        val older = MediaContext(playbackState = MediaPlaybackState.PLAYING, lastUpdatedMillis = 100)
        val newer = MediaContext(playbackState = MediaPlaybackState.PLAYING, lastUpdatedMillis = 200)
        assertEquals(newer, MediaSessionSelector.best(listOf(older, newer)))
    }

    @Test fun `deterministic selection never returns null when something is active`() {
        val only = MediaContext(playbackState = MediaPlaybackState.PAUSED, lastUpdatedMillis = 1)
        assertEquals(only, MediaSessionSelector.best(listOf(only)))
    }

    @Test fun `inactive sessions yield no surface`() {
        val stopped = MediaContext(playbackState = MediaPlaybackState.STOPPED, lastUpdatedMillis = 1)
        val buffering = MediaContext(playbackState = MediaPlaybackState.BUFFERING, lastUpdatedMillis = 2)
        assertNull(MediaSessionSelector.best(listOf(stopped, buffering)))
    }

    @Test fun `MediaContext carries no notification body`() {
        // Privacy: only media-session metadata may be present — never notification text/body.
        val file = TestPaths.find("app/src/main/kotlin/com/aura/home/MediaContext.kt")
        assertTrue(file.exists())
        val text = file.readText()
        listOf("val body", "val text", "val message", "val content", "val extras", "val notification")
            .forEach { forbidden -> assertFalse("MediaContext must not contain '$forbidden'", text.contains(forbidden)) }
        // Legitimate media fields are present.
        assertTrue("title expected", text.contains("val title"))
        assertTrue("artwork expected", text.contains("val artwork"))
    }

    @Test fun `MediaArtwork equals by content`() {
        assertEquals(MediaArtwork(byteArrayOf(9, 8)), MediaArtwork(byteArrayOf(9, 8)))
        assertNotEquals(MediaArtwork(byteArrayOf(9, 8)), MediaArtwork(byteArrayOf(9, 7)))
    }
}
