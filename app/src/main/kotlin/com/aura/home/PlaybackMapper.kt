package com.aura.home

/**
 * Pure mapping from a media [PlaybackState] integer to AURA's [MusicState].
 *
 * Kept Android-free so the "no stale state" rule is unit-testable. The integer values
 * mirror [android.media.session.PlaybackState] so [MusicMonitor] can call this directly:
 *   STATE_PLAYING = 3, STATE_PAUSED = 2, everything else (stopped / buffering /
 *   connecting / none) is treated as not-active and yields [MusicState.Hidden].
 *
 * This is the fix for the previous "paused icon stays after stop" bug: only the two
 * explicit active states map to Playing/Paused; anything else clears the surface.
 */
object PlaybackMapper {
    const val STATE_PLAYING = 3
    const val STATE_PAUSED = 2

    /** Pure integer-state mapping (legacy fallback path). Enriched [MusicState] defaults
     *  keep artwork/capabilities neutral. */
    fun derive(playbackState: Int?, title: String?, artist: String?): MusicState = when (playbackState) {
        STATE_PLAYING -> MusicState.Playing(title = title, artist = artist)
        STATE_PAUSED -> MusicState.Paused(title = title, artist = artist)
        else -> MusicState.Hidden
    }
}
