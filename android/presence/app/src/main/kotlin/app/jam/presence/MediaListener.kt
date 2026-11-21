package app.jam.presence

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Watches the phone's media sessions (what Apple Music and other players show on the lock screen).
 * Android only lets an app see them when it has notification access; notifications themselves
 * aren't read. The chosen player's song goes to [Player].
 */
class MediaListener : NotificationListenerService() {
    companion object {
        private const val TAG = "JamPresence"
        @Volatile var instance: MediaListener? = null
            private set

        fun allowed(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Settings changed (players, cover lookup): choose again. */
        fun refresh() { instance?.let { it.main.post { it.pick() } } }

        /** The packages with a media session right now. */
        fun activePlayers(): Set<String> = instance?.tracked?.values?.map { it.c.packageName }?.toSet() ?: emptySet()
    }

    private val main = Handler(Looper.getMainLooper())
    private val tracked = HashMap<MediaSession.Token, Tracked>()
    private var msm: MediaSessionManager? = null
    private val component by lazy { ComponentName(this, MediaListener::class.java) }
    private val pickLater = Runnable { pick() }

    private inner class Tracked(val c: MediaController) : MediaController.Callback() {
        /** When this player last started playing or changed song (the newest playing one wins). */
        var lastActive = SystemClock.elapsedRealtime()
        private var lastTitle: String? = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state?.state == PlaybackState.STATE_PLAYING) lastActive = SystemClock.elapsedRealtime()
            pick()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val t = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (t != lastTitle) { lastTitle = t; lastActive = SystemClock.elapsedRealtime() }
            pick()
        }
        override fun onSessionDestroyed() { main.post { rescan() } }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list) }

    override fun onListenerConnected() {
        instance = this
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            onSessions(m.getActiveSessions(component))
        }.onFailure { Log.w(TAG, "media sessions: $it") }
        KeepAliveService.sync(this)
        Player.onChanged?.invoke()
    }

    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        tracked.values.forEach { runCatching { it.c.unregisterCallback(it) } }
        tracked.clear()
        if (instance === this) instance = null
        Player.update(null)
        // Android unbinds listeners now and then (updates, low memory): ask to come back.
        runCatching { requestRebind(component) }
    }

    private fun rescan() {
        val m = msm ?: return
        runCatching { onSessions(m.getActiveSessions(component)) }
    }

    private fun onSessions(list: List<MediaController>?) {
        val now = list.orEmpty().associateBy { it.sessionToken }
        tracked.keys.filter { it !in now }.forEach { t -> tracked.remove(t)?.let { runCatching { it.c.unregisterCallback(it) } } }
        for ((token, c) in now) {
            if (token in tracked || c.packageName == packageName) continue
            val t = Tracked(c)
            c.registerCallback(t, main)
            tracked[token] = t
            Prefs.addSeen(c.packageName)
        }
        pick()
    }

    /** Chooses the player to show and reports its song. */
    private fun pick() {
        main.removeCallbacks(pickLater)
        val enabled = Prefs.players()
        val candidates = tracked.values.filter { it.c.packageName in enabled && title(it.c.metadata).isNotEmpty() }
        val playing = candidates.filter { isPlaying(it.c.playbackState) }
        val chosen = playing.maxByOrNull { it.lastActive }
            ?: candidates.filter { it.c.playbackState?.state == PlaybackState.STATE_PAUSED }.maxByOrNull { it.lastActive }
        if (chosen == null) { Player.update(null); return }
        val c = chosen.c
        val md = c.metadata ?: run { Player.update(null); return }
        val pkg = c.packageName
        val title = title(md)
        val artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.toString() ?: "").trim()
        val album = (md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "").trim()
        val ownArt = listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .mapNotNull { md.getString(it) }.firstOrNull { it.startsWith("https://") && it.length <= 512 }

        // Apple's catalog: a cover Discord can load, and the song's Apple Music link.
        var found: Covers.Found? = null
        if (Prefs.bool("lookupCovers") && title.isNotEmpty()) {
            val k = Covers.key(title, artist, album)
            found = Covers.cached(k)
            if (found == null) {
                Covers.fetch(title, artist, album) { pick() }
                // Wait a moment for it (one Discord update with the cover instead of two), not longer.
                val since = Covers.pendingSince(k)
                if (since != null && System.currentTimeMillis() - since < 2500) { main.postDelayed(pickLater, 2600); return }
            }
        }

        val state = c.playbackState
        val playingNow = isPlaying(state)
        val durMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val np = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            artwork = ownArt ?: found?.artwork,
            playing = playingNow,
            position = position(state, playingNow),
            duration = if (durMs > 0) durMs / 1000.0 else null,
            url = Players.link(pkg, title, artist, found?.url),
            sourceId = pkg,
            sourceName = Players.name(this, pkg),
        )
        Player.update(np)
    }

    private fun title(md: MediaMetadata?): String = md?.let {
        (it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: it.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString() ?: "").trim()
    } ?: ""

    // Buffering between songs counts as playing, so the card doesn't blink off.
    private fun isPlaying(s: PlaybackState?) = s != null && (s.state == PlaybackState.STATE_PLAYING || s.state == PlaybackState.STATE_BUFFERING ||
        s.state == PlaybackState.STATE_SKIPPING_TO_NEXT || s.state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS)

    private fun position(s: PlaybackState?, playing: Boolean): Double? {
        if (s == null || s.position < 0) return null
        var ms = s.position.toDouble()
        if (playing && s.state == PlaybackState.STATE_PLAYING && s.lastPositionUpdateTime > 0) {
            ms += (SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * (if (s.playbackSpeed > 0f) s.playbackSpeed else 1f)
        }
        return ms / 1000.0
    }
}
