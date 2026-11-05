package app.jam.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** What's playing right now, from whichever source is visible. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
    val playing: Boolean,
    /** Seconds, at [readAt]. */
    val position: Double?,
    val duration: Double?,
    val url: String,
    val sourceId: String,
    val sourceName: String,
    val readAt: Long = System.currentTimeMillis(),
    /** A downloaded song's saved cover (YouTube Music): shown when [artwork] can't be fetched. */
    val artworkFile: String? = null,
) {
    fun positionNow(): Double? = position?.let { if (playing) it + (System.currentTimeMillis() - readAt) / 1000.0 else it }
}

/**
 * The hub between the page (reports), the media notification / lock screen (controls),
 * and Discord (presence). Everything runs on the main thread.
 */
object Player {
    private val main = Handler(Looper.getMainLooper())
    var current: NowPlaying? = null
        private set

    /** Set by MainActivity: performs play / pause / toggle / nexttrack / previoustrack on the visible source. */
    var controller: ((String) -> Unit)? = null

    /** Set by Settings to refresh its live preview. */
    var onChanged: (() -> Unit)? = null

    private val PLACEHOLDER = Regex("^unknown( (title|artist|album|track))?$", RegexOption.IGNORE_CASE)
    private fun clean(v: String?): String = (v ?: "").trim().let { if (PLACEHOLDER.matches(it)) "" else it }

    private fun cleanDocTitle(t: String, src: Source): String {
        if (t.isBlank()) return ""
        val names = listOf(src.name, "Octave Streaming", "Monochrome Music", "Apple Music", "Spotify", "YouTube Music", "Jam")
            .joinToString("|") { Regex.escape(it) }
        val s = t.replace(Regex("\\s*[-|•·–—]\\s*($names)\\s*$", RegexOption.IGNORE_CASE), "").trim()
        return if (Regex("^($names)$", RegexOption.IGNORE_CASE).matches(s)) "" else s
    }

    /** A report from the page hook (same JSON as desktop's). */
    fun report(context: Context, src: Source, json: String?) {
        val st = json?.let { runCatching { JSONObject(it) }.getOrNull() }
        val np = st?.let { toNowPlaying(it, src) }
        main.post { update(context, np) }
    }

    private fun toNowPlaying(st: JSONObject, src: Source): NowPlaying? {
        val hasMedia = st.optBoolean("hasMedia")
        val playing = if (hasMedia) !st.optBoolean("paused", true) else st.optString("playbackState") == "playing"
        val title = clean(st.optString("title")).ifEmpty { if (hasMedia) cleanDocTitle(st.optString("docTitle"), src) else "" }
        if (title.isEmpty() && !hasMedia) return null
        val art = st.optString("artwork").takeIf { it.startsWith("https://") && it.length <= 512 }
        val url = st.optString("url").let { if (Sources.belongsTo(src, it)) it else src.url }
        fun num(k: String): Double? = if (st.isNull(k)) null else st.optDouble(k).takeIf { !it.isNaN() && !it.isInfinite() }
        return NowPlaying(title.ifEmpty { "Unknown track" }, clean(st.optString("artist")), clean(st.optString("album")), art,
            playing, num("currentTime"), num("duration"), url, src.id, src.name)
    }

    /**
     * From the YouTube Music engine (JamPlugin.nowPlaying). It plays natively, so it may keep
     * playing while another source is on screen; a paused engine that isn't on screen says nothing.
     */
    fun reportEngine(context: Context, np: NowPlaying?) {
        main.post {
            val onScreen = MainActivity.instance?.active == Sources.YTM
            val owns = current?.sourceId == Sources.YTM
            if (np == null) { if (owns) update(context, null); return@post }
            if (onScreen || owns || np.playing) update(context, np)
        }
    }

    fun update(context: Context, np: NowPlaying?) {
        current = np
        PlaybackService.sync(context, np)
        Discord.onNowPlaying(np)
        onChanged?.invoke()
    }

    fun clear(context: Context) = update(context, null)

    /** A media button. The engine takes its own (it may be playing with no activity at all). */
    fun control(action: String) {
        main.post {
            if (current?.sourceId == Sources.YTM) {
                if (action.startsWith("seekto:")) JamPlugin.sendControl("seekto", action.removePrefix("seekto:").toDoubleOrNull())
                else JamPlugin.sendControl(action)
            } else controller?.invoke(action)
        }
    }
}
