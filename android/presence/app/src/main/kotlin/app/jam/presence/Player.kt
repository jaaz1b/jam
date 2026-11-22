package app.jam.presence

import android.content.Context
import android.os.Handler
import android.os.Looper

/** What a player on the phone is playing. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    /** An https image Discord can load, or null. */
    val artwork: String?,
    val playing: Boolean,
    /** Seconds, at [readAt]. */
    val position: Double?,
    val duration: Double?,
    /** The song's page (the "Listen on" button), or "" for none. */
    val url: String,
    /** The player's package, e.g. com.apple.android.music. */
    val sourceId: String,
    /** The player's name, e.g. Apple Music. */
    val sourceName: String,
    /** How the cover was found (or why not), for the settings preview. */
    val coverNote: String = "",
    val readAt: Long = System.currentTimeMillis(),
) {
    fun positionNow(): Double? = position?.let { if (playing) it + (System.currentTimeMillis() - readAt) / 1000.0 else it }
}

/** The hub between the media watcher, Discord and the settings page. Main thread. */
object Player {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    var current: NowPlaying? = null
        private set

    /** Set by the settings page to refresh its preview and players list. */
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) { app = context.applicationContext }

    fun update(np: NowPlaying?) {
        main.post {
            if (same(np, current)) return@post
            current = np
            android.util.Log.i("JamPresence", if (np == null) "nothing playing" else
                "${np.sourceName}: ${np.title} · ${np.artist} (${if (np.playing) "playing" else "paused"}) art=${np.artwork ?: "-"} link=${np.url.ifEmpty { "-" }}")
            Discord.onNowPlaying(np)
            KeepAliveService.onNowPlaying(app, np)
            onChanged?.invoke()
        }
    }

    /** Same song and state, the position where it should be: nothing to tell anyone. */
    private fun same(a: NowPlaying?, b: NowPlaying?): Boolean {
        if (a == null || b == null) return a == b
        if (a.title != b.title || a.artist != b.artist || a.album != b.album || a.artwork != b.artwork || a.playing != b.playing ||
            a.duration != b.duration || a.url != b.url || a.sourceId != b.sourceId || a.coverNote != b.coverNote) return false
        val pa = a.positionNow(); val pb = b.positionNow()
        return pa == null || pb == null || kotlin.math.abs(pa - pb) < 3.0
    }
}
