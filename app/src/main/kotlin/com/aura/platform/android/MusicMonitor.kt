package com.aura.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.aura.home.MusicState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Music monitor — permission-free, local, event-driven.
 *
 * Strategy (honest, without NotificationListener):
 *  - Best-effort: try [MediaSessionManager.getActiveSessions]. On a normal launcher this
 *    returns empty (it needs MEDIA_CONTENT_CONTROL), but on devices/emulators that allow it
 *    we read real playback state + metadata and drive transport via MediaController.
 *  - Fallback: [AudioManager.isMusicActive] snapshot — tells us whether music is playing,
 *    but not title/artist (that would require notification access, which AURA rejects).
 *  - After a transport command we REFRESH from the actual media state instead of assuming
 *    the command succeeded (no blind state inversion).
 *
 * Metadata is only shown when Android legitimately exposes it. We never fabricate it.
 */
class MusicMonitor(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sessionManager: MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _state: MutableStateFlow<MusicState> = MutableStateFlow(MusicState.Hidden)
    val state: StateFlow<MusicState> = _state

    private var controller: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = reconcile()
        override fun onMetadataChanged(metadata: MediaMetadata?) = reconcile()
        override fun onSessionDestroyed() {
            controller = null
            reconcile()
        }
    }

    private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun start() {
        if (receiver != null) return
        // Playback often stops when audio becomes noisy (headphones unplugged) — refresh then.
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) refresh()
            }
        }
        context.registerReceiver(r, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        receiver = r
    }

    @Synchronized
    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            receiver = null
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    /** Re-evaluate state from the live media session if available, else AudioManager. */
    fun refresh() {
        val sessions = try { sessionManager.getActiveSessions(null) } catch (_: Exception) { emptyList() }
        if (sessions.isNotEmpty()) {
            val token = try { sessions.first().sessionToken } catch (_: Exception) { null }
            if (token != null && controller?.sessionToken != token) {
                controller?.unregisterCallback(controllerCallback)
                controller = try { MediaController(context, token) } catch (_: Exception) { null }
                controller?.registerCallback(controllerCallback)
            }
            reconcile()
        } else {
            controller?.unregisterCallback(controllerCallback)
            controller = null
            _state.value = MusicState.fromActive(audioManager.isMusicActive)
        }
    }

    private fun reconcile() {
        val c = controller
        if (c != null) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val meta = c.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            _state.value = if (playing) MusicState.Playing(title, artist) else MusicState.Paused(title, artist)
        } else {
            _state.value = MusicState.fromActive(audioManager.isMusicActive)
        }
    }

    private fun transportOrKey(keyCode: Int) {
        // We observe real playback state via the media session (when available) and refresh
        // from it after the command. Transport itself goes through synthetic media key events,
        // which is the permission-free path available to a third-party launcher without
        // notification-listener service. (A live MediaController is used for *observation* only.)
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
        // Refresh from the actual media state rather than assuming the command succeeded.
        refresh()
    }

    fun playPause() = transportOrKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next() = transportOrKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev() = transportOrKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
}
