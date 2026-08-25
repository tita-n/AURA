package com.aura.platform.android

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Music monitor — permission-free, local, event-driven-ish.
 *
 * Implementation: [AudioManager.isMusicActive] snapshot on [refresh] (triggered by
 * lifecycle resume and AUDIO_BECOMING_NOISY broadcasts). Play/pause/next/prev via
 * synthetic [KeyEvent] dispatch to [AudioManager.dispatchMediaKeyEvent].
 *
 * Limitation documented in PRODUCT.md: without NotificationListener access (rejected),
 * track metadata is not available to a third-party launcher. The module remains
 * honest about that — it shows playback state with transport controls, not fabricated
 * title/artist data.
 */
class MusicMonitor(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _playing = MutableStateFlow(isMusicActiveSnapshot())
    val playing: StateFlow<Boolean> = _playing

    fun refresh() {
        _playing.value = isMusicActiveSnapshot()
    }

    private fun isMusicActiveSnapshot(): Boolean =
        try { audioManager.isMusicActive } catch (_: Exception) { false }

    private fun dispatch(keyCode: Int) {
        try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            // State follows quickly; schedule a short re-check on caller side
        } catch (_: Exception) {}
    }

    fun playPause() { dispatch(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    fun next() { dispatch(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun prev() { dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
}
