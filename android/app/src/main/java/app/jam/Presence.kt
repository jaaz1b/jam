package app.jam

/** Builds the Discord activity from what's playing + the user's templates (port of desktop presence.js). */
data class RichActivity(
    val name: String,
    val details: String?,
    val state: String?,
    val largeImage: String?,   // https URL (resolved to mp:… before sending) or an art-asset key
    val largeText: String?,
    val startMs: Long?,
    val endMs: Long?,
    val buttons: List<Pair<String, String>>,
    /** What the member list shows: 0 = app name, 1 = state (line 2), 2 = details (line 1). */
    val statusDisplay: Int = 0,
) {
    /** Identity for "did anything change?" — the exact start time drifts between reads. */
    fun key(): String = listOf(name, details, state, largeImage, largeText, if (endMs != null) "bar:${(endMs - (startMs ?: 0)) / 1000}" else if (startMs != null) "elapsed" else "none", buttons, statusDisplay).toString()
}

object Presence {
    fun appName(): String = Prefs.str("appName").trim().ifEmpty { "Jam" }

    private val TOKEN = Regex("\\{(title|artist|album|app|source)\\}", RegexOption.IGNORE_CASE)

    fun render(template: String, np: NowPlaying): String {
        val vals = mapOf("title" to np.title, "artist" to np.artist, "album" to np.album, "app" to appName(), "source" to np.sourceName)
        var hadEmpty = false
        var out = TOKEN.replace(template) { m -> val v = vals[m.groupValues[1].lowercase()] ?: ""; if (v.isEmpty()) hadEmpty = true; v }
        if (!hadEmpty) return out.trim()
        val sep = "\\s*[-–—|·•:,/]\\s*"
        out = out.replace(Regex("^(?:$sep)+"), "").replace(Regex("(?:$sep)+$"), "")
            .replace(Regex("\\s*([-–—|·•])\\s*(?:[-–—|·•]\\s*)+"), " $1 ")
            .replace(Regex("\\(\\s*\\)|\\[\\s*]"), "").replace(Regex("\\s{2,}"), " ").trim()
        return out
    }

    private fun clamp(s: String?, max: Int = 128): String? {
        var v = (s ?: "").trim()
        if (v.isEmpty()) return null
        if (v.length < 2) v = v.padEnd(2, '\u200b')
        return if (v.length > max) v.take(max - 1) + "…" else v
    }

    private fun fallbackImage(): String {
        val f = Prefs.str("fallbackImage").trim()
        return when {
            f.startsWith("https://") -> f
            Regex("^[a-z0-9_-]{1,32}$", RegexOption.IGNORE_CASE).matches(f) -> f.lowercase()
            else -> "jam"
        }
    }

    fun build(np: NowPlaying?): RichActivity? {
        if (np == null) return null
        if (!np.playing && !Prefs.bool("showWhenPaused")) return null
        val name = appName()
        if (Prefs.bool("hideDetails")) {
            return RichActivity(name, clamp(if (np.playing) "Listening to music" else "Paused"), null, fallbackImage(), clamp(name), null, null, emptyList())
        }
        var details = render(Prefs.str("line1"), np)
        var state = render(Prefs.str("line2"), np)
        val largeText = render(Prefs.str("line3"), np)
        val paused = Prefs.str("pausedLabel").trim()
        if (!np.playing && paused.isNotEmpty()) state = if (state.isNotEmpty()) "$paused · $state" else paused
        if (details.isEmpty()) details = np.title.ifEmpty { name }

        var start: Long? = null
        var end: Long? = null
        val pos = np.positionNow()
        val mode = Prefs.str("progress")
        if (np.playing && mode != "off" && pos != null) {
            val now = System.currentTimeMillis()
            start = now - (pos * 1000).toLong()
            if (mode != "elapsed" && np.duration != null && np.duration > 0) end = start + (np.duration * 1000).toLong()
        }

        val buttons = mutableListOf<Pair<String, String>>()
        if (Prefs.bool("button1Enabled")) {
            val label = render(Prefs.str("button1Label"), np).ifEmpty { "Listen on ${np.sourceName}" }.take(32).trim()
            val custom = Prefs.str("button1Url").trim()
            val url = if (custom.startsWith("http")) custom else np.url
            if (url.startsWith("http") && url.length <= 512) buttons += label to url
        }
        if (Prefs.bool("button2Enabled")) {
            val label = render(Prefs.str("button2Label"), np).take(32).trim()
            val url = Prefs.str("button2Url").trim()
            if (label.isNotEmpty() && url.startsWith("http") && url.length <= 512) buttons += label to url
        }
        val art = if (Prefs.bool("showAlbumArt") && np.artwork != null) np.artwork else fallbackImage()
        val sd = when (Prefs.str("statusDisplay")) { "line2" -> 1; "app" -> 0; else -> 2 }
        return RichActivity(name, clamp(details), clamp(state), art, clamp(largeText), start, end, buttons, sd)
    }
}
