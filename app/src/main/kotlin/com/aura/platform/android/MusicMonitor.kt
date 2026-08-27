package com.aura.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.aura.home.MediaArtwork
import com.aura.home.MediaContext
import com.aura.home.MediaPlaybackState
import com.aura.home.MediaSessionSelector
import com.aura.home.MusicState
import com.aura.home.toMusicState
import com.aura.home.capabilitiesFromActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

/**
 * Music monitor — discovers other apps' active media sessions and exposes a single
 * pure [MusicState] for the ONE contextual surface.
 *
 * Architecture (honest, minimal, event-driven):
 *  - PREFERRED: when the user has enabled AURA's narrow media listener
 *    ([AuraMediaNotificationListenerService]), [MediaSessionManager.getActiveSessions]
 *    returns the active media sessions for that component. For each we build a
 *    [MediaController], register [MediaController.Callback], and read REAL playback
 *    state + metadata (title/artist/album/artwork) plus capabilities. The listener also
 *    forwards media-notification events so appearance is prompt.
 *  - FALLBACK (no access granted): best-effort [AudioManager.isMusicActive] snapshot
 *    (Playing/Paused with no metadata — we never fabricate it). Transport via synthetic
 *    media-key events, the legitimate permission-free path.
 *  - After a transport command we do NOT mutate UI state; we let the MediaController
 *    callback (or a single safety refresh on the key path) report the actual state.
 *
 * No NotificationListenerService is used to read notification content. The listener is a
 * narrow bridge; track metadata comes from the media session, never the notification body.
 */
class MusicMonitor(private val context: Context) {

    companion object {
        /** Set while a monitor instance is alive so the listener can forward events. */
        @Volatile var instance: MusicMonitor? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sessionManager: MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _state: MutableStateFlow<MusicState> = MutableStateFlow(MusicState.Hidden)
    val state: StateFlow<MusicState> = _state

    private val controllers = LinkedHashMap<android.media.session.MediaSession.Token, MediaController>()
    private val contexts = LinkedHashMap<android.media.session.MediaSession.Token, MediaContext>()
    private var activeToken: android.media.session.MediaSession.Token? = null
    private var receiver: BroadcastReceiver? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() {
            // The destroyed controller's token is now invalid; refresh removes it.
            refresh()
        }
    }

    @Synchronized
    fun start() {
        instance = this
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) refresh()
            }
        }
        context.registerReceiver(r, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        receiver = r
        refresh()
    }

    @Synchronized
    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            receiver = null
        }
        clearControllers()
        if (instance == this) instance = null
    }

    /** Called by the listener when it connects or when a media notification appears/leaves. */
    @Synchronized
    fun onListenerConnected() = refresh()

    @Synchronized
    fun onMediaNotificationPosted() = refresh()

    @Synchronized
    fun refresh() {
        if (!NotificationAccess.isListenerEnabled(context)) {
            // No media access granted: honest fallback, no metadata. Music stays hidden
            // at the contextual layer until the user opts in (see ContextualEngine.musicAccess).
            clearControllers()
            _state.value = MusicState.fromActive(audioManager.isMusicActive)
            return
        }
        val sessions = try {
            sessionManager.getActiveSessions(NotificationAccess.listenerComponent(context))
        } catch (_: Exception) {
            emptyList()
        }
        val liveTokens = sessions.mapNotNull { it.sessionToken }
        // Drop controllers whose session is gone.
        controllers.keys.filter { it !in liveTokens }.forEach { removeController(it) }
        // Add controllers for newly active sessions.
        sessions.forEach { sc ->
            val token = sc.sessionToken ?: return@forEach
            if (!controllers.containsKey(token)) {
                val c = try { MediaController(context, token) } catch (_: Exception) { null } ?: return@forEach
                c.registerCallback(controllerCallback)
                controllers[token] = c
            }
        }
        reconcile()
    }

    private fun reconcile() {
        contexts.clear()
        controllers.forEach { (token, c) -> contexts[token] = buildMediaContext(c) }
        val best = MediaSessionSelector.best(contexts.values.toList())
        activeToken = contexts.entries.firstOrNull { it.value == best }?.key
        _state.value = best?.toMusicState() ?: MusicState.Hidden
    }

    private fun removeController(token: android.media.session.MediaSession.Token) {
        controllers[token]?.unregisterCallback(controllerCallback)
        controllers.remove(token)
        contexts.remove(token)
        if (activeToken == token) activeToken = null
    }

    private fun clearControllers() {
        controllers.values.forEach { it.unregisterCallback(controllerCallback) }
        controllers.clear()
        contexts.clear()
        activeToken = null
    }

    private fun activeController(): MediaController? = activeToken?.let { controllers[it] }

    // ---- Transport (actual state is reported by the controller callback) -------

    @Synchronized
    fun playPause() {
        val c = activeController()
        if (c != null) {
            // Decide play vs pause from the ACTUAL reported state (not a local guess).
            if (_state.value is MusicState.Playing) c.transportControls.pause()
            else c.transportControls.play()
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            refreshSoon()
        }
    }

    @Synchronized
    fun next() {
        activeController()?.transportControls?.skipToNext() ?: run {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            refreshSoon()
        }
    }

    @Synchronized
    fun prev() {
        activeController()?.transportControls?.skipToPrevious() ?: run {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            refreshSoon()
        }
    }

    /** Single safety refresh on the key-event path (no controller callback there). */
    private fun refreshSoon() {
        handler.postDelayed({ refresh() }, 250)
    }

    private fun dispatchKey(keyCode: Int) {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    // ---- Pure-bridge extraction (Android -> com.aura.home.MediaContext) --------

    private fun buildMediaContext(controller: MediaController): MediaContext {
        val md = controller.metadata
        val ps = controller.playbackState
        val state = MediaPlaybackState.fromInt(ps?.state)
        val capabilities = capabilitiesFromActions(ps?.actions ?: 0L)
        val artwork = md?.let { m ->
            val bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            downsampleArt(bmp)?.let { MediaArtwork(it) }
        }
        return MediaContext(
            packageName = controller.packageName,
            appLabel = appLabel(controller.packageName),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            artwork = artwork,
            playbackState = state,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.let { if (it < 0) null else it },
            positionMs = if (state == MediaPlaybackState.PLAYING) ps?.position else null,
            capabilities = capabilities,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    private fun appLabel(pkg: String?): String? {
        if (pkg == null) return null
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).let { pm.getApplicationLabel(it).toString() }
        } catch (_: Exception) {
            pkg.substringAfterLast(".")
        }
    }

    /** Downsample artwork once to a small PNG so the UI never decodes a large bitmap. */
    private fun downsampleArt(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        return try {
            val maxPx = 128
            val scale = (maxPx.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val tw = maxOf(1, (bitmap.width * scale).toInt())
            val th = maxOf(1, (bitmap.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(bitmap, tw, th, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
