package com.aura.home

/**
 * Pure media-context model + logic for the optional Music surface.
 *
 * PRIVACY BOUNDARY: this file contains NO Android types (no MediaController,
 * MediaMetadata, PlaybackState, Notification, Bitmap, android.graphics…). The
 * platform layer (`platform/android`) extracts structured data from a media session
 * and converts it into the types below; nothing escapes back upward with Android
 * references. The notification listener never passes a raw notification object into
 * this model — only a [MediaNotificationSignal] (already stripped of content).
 *
 * The goal is "obtain media context", not "read notifications".
 */

// ---- Playback state (mirrors android.media.session.PlaybackState ints) -------

enum class MediaPlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
    BUFFERING,
    ERROR,
    NONE; // unknown / not active

    companion object {
        /** Pure mapping from a [android.media.session.PlaybackState] integer to our enum. */
        fun fromInt(state: Int?): MediaPlaybackState = when (state) {
            3 -> PLAYING     // STATE_PLAYING
            2 -> PAUSED      // STATE_PAUSED
            1 -> STOPPED     // STATE_STOPPED
            6 -> BUFFERING   // STATE_BUFFERING
            7 -> ERROR       // STATE_ERROR
            else -> NONE     // 0 NONE, 8 CONNECTING, 9-12 transient, null
        }
    }
}

// ---- Capabilities (mirrors PlaybackState.ACTION_* long bits) -----------------

enum class MediaCapability { PLAY, PAUSE, SKIP_NEXT, SKIP_PREVIOUS }

/** Pure mapping from a PlaybackState `actions` bitmask to a capability set. */
fun capabilitiesFromActions(actions: Long): Set<MediaCapability> {
    val set = mutableSetOf<MediaCapability>()
    if (actions and (1L shl 55) != 0L) set += MediaCapability.PLAY          // ACTION_PLAY
    if (actions and (1L shl 56) != 0L) set += MediaCapability.PAUSE         // ACTION_PAUSE
    if (actions and (1L shl 53) != 0L) set += MediaCapability.SKIP_NEXT     // ACTION_SKIP_TO_NEXT
    if (actions and (1L shl 52) != 0L) set += MediaCapability.SKIP_PREVIOUS // ACTION_SKIP_TO_PREVIOUS
    return set
}

// ---- Artwork (downsampled bytes only — never a Bitmap) -----------------------

/**
 * Album artwork carried as small, already-downsampled PNG bytes. The platform layer
 * produces this once (max ~128px) so the UI never decodes a large bitmap on the
 * main thread and we never persist or transmit the original. A [ByteArray] is a JVM
 * type, not an Android graphic type, so it is safe inside the pure model.
 */
data class MediaArtwork(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaArtwork) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
}

// ---- The pure media-context value object -------------------------------------

/**
 * Everything AURA needs to render the Music contextual surface. No notification body,
 * no message content, no contact info — only media-session metadata.
 */
data class MediaContext(
    val packageName: String? = null,
    val appLabel: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: MediaArtwork? = null,
    val playbackState: MediaPlaybackState = MediaPlaybackState.NONE,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val capabilities: Set<MediaCapability> = emptySet(),
    val lastUpdatedMillis: Long = 0L
)

/** Map a single media session to AURA's [MusicState] (authoritative Android state). */
fun MediaContext.toMusicState(): MusicState {
    val canNext = MediaCapability.SKIP_NEXT in capabilities
    val canPrev = MediaCapability.SKIP_PREVIOUS in capabilities
    return when (playbackState) {
        MediaPlaybackState.PLAYING -> MusicState.Playing(
            title = title, artist = artist, appLabel = appLabel, artwork = artwork,
            canNext = canNext, canPrev = canPrev
        )
        MediaPlaybackState.PAUSED -> MusicState.Paused(
            title = title, artist = artist, appLabel = appLabel, artwork = artwork,
            canNext = canNext, canPrev = canPrev
        )
        // Stopped / buffering / error / none / destroyed all clear the surface.
        else -> MusicState.Hidden
    }
}

// ---- Deterministic active-session selection ----------------------------------

/**
 * Choose which of several media sessions to surface. Deterministic and stable:
 *   1. Prefer active (PLAYING > PAUSED) over inactive.
 *   2. Tie-break by most-recently-updated (no random switching).
 * Returns null when nothing is actively playing/paused (surface hidden).
 */
object MediaSessionSelector {
    fun best(contexts: List<MediaContext>): MediaContext? {
        val active = contexts.filter {
            it.playbackState == MediaPlaybackState.PLAYING ||
                it.playbackState == MediaPlaybackState.PAUSED
        }
        if (active.isEmpty()) return null
        return active.maxWithOrNull(
            Comparator { a, b ->
                val ra = if (a.playbackState == MediaPlaybackState.PLAYING) 1 else 0
                val rb = if (b.playbackState == MediaPlaybackState.PLAYING) 1 else 0
                // 1) Prefer active playback: PLAYING ranks above PAUSED.
                if (ra != rb) ra - rb
                // 2) Tie-break by most recently updated (deterministic, no random switching).
                //    maxWithOrNull returns the GREATEST element, so the larger (more recent)
                //    timestamp must compare greater -> ascending timestamp comparison.
                else a.lastUpdatedMillis.compareTo(b.lastUpdatedMillis)
            }
        )
    }
}

// ---- Narrow notification-signal filter (pure) --------------------------------

/**
 * Structured, content-free summary of a notification, extracted by the platform
 * listener. It contains no notification body — only whether the notification is
 * demonstrably media-related. The original notification object never enters this model.
 */
data class MediaNotificationSignal(
    val packageName: String?,
    val category: String?,
    val hasMediaSession: Boolean,
    val isMediaStyle: Boolean,
    val isOngoing: Boolean
)

/** Known notification category strings (mirror android.app.Notification.CATEGORY_*). */
const val CAT_TRANSPORT = "transport"
const val CAT_PROGRESS = "progress"
const val CAT_MESSAGE = "msg"
const val CAT_EMAIL = "email"
const val CAT_CALL = "call"
const val CAT_SOCIAL = "social"

/**
 * Aggressively accept ONLY media-related notifications. Anything that is not
 * demonstrably a media session / media transport is rejected immediately and never
 * reaches the media pipeline. This is what keeps AURA from reading ordinary
 * messages, email, or social notifications.
 */
object MediaNotificationFilter {
    fun accept(signal: MediaNotificationSignal): Boolean {
        if (signal.hasMediaSession) return true
        if (signal.isMediaStyle) return true
        if (signal.category == CAT_TRANSPORT) return true
        // Some players expose ongoing playback under CATEGORY_PROGRESS.
        if (signal.isOngoing && signal.category == CAT_PROGRESS) return true
        return false
    }
}
