package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * PlaybackState -> MusicState mapping must be honest: only the two explicit active
 * states map to Playing/Paused; everything else (stopped / buffering / connecting /
 * none) resolves to Hidden so a stale "paused" card can never linger.
 *
 * The integer constants mirror android.media.session.PlaybackState.
 */
class PlaybackMapperTest {

    @Test fun `playing state maps to Playing with metadata`() {
        val s = PlaybackMapper.derive(PlaybackMapper.STATE_PLAYING, "Title", "Artist")
        assertTrue(s is MusicState.Playing)
        assertEquals("Title", (s as MusicState.Playing).title)
        assertEquals("Artist", s.artist)
    }

    @Test fun `paused state maps to Paused with metadata`() {
        val s = PlaybackMapper.derive(PlaybackMapper.STATE_PAUSED, "Title", null)
        assertTrue(s is MusicState.Paused)
        assertEquals("Title", (s as MusicState.Paused).title)
    }

    @Test fun `stopped state maps to Hidden (no stale card)`() {
        // PlaybackState.STATE_STOPPED == 1
        assertEquals(MusicState.Hidden, PlaybackMapper.derive(1, "x", "y"))
    }

    @Test fun `buffering state maps to Hidden`() {
        // PlaybackState.STATE_BUFFERING == 6
        assertEquals(MusicState.Hidden, PlaybackMapper.derive(6, null, null))
    }

    @Test fun `null playback state maps to Hidden`() {
        assertEquals(MusicState.Hidden, PlaybackMapper.derive(null, null, null))
    }

    @Test fun `fallback active flag maps to Playing or Hidden`() {
        assertEquals(MusicState.Playing(null, null), MusicState.fromActive(true))
        assertEquals(MusicState.Hidden, MusicState.fromActive(false))
    }
}
