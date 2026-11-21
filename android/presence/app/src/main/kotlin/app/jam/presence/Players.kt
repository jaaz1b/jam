package app.jam.presence

import android.content.Context
import android.content.pm.PackageManager
import java.net.URLEncoder

/** Music players: names, and a page for the playing song (the "Listen on" button). */
object Players {
    private class Known(val name: String, val search: ((String) -> String)?)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val KNOWN = mapOf(
        Prefs.APPLE_MUSIC to Known("Apple Music") { "https://music.apple.com/search?term=${enc(it)}" },
        "com.spotify.music" to Known("Spotify") { "https://open.spotify.com/search/${enc(it).replace("+", "%20")}" },
        "com.google.android.apps.youtube.music" to Known("YouTube Music") { "https://music.youtube.com/search?q=${enc(it)}" },
        "deezer.android.app" to Known("Deezer") { "https://www.deezer.com/search/${enc(it).replace("+", "%20")}" },
        "com.aspiro.tidal" to Known("TIDAL") { "https://tidal.com/search?q=${enc(it)}" },
        "com.amazon.mp3" to Known("Amazon Music") { "https://music.amazon.com/search/${enc(it).replace("+", "%20")}" },
        "com.soundcloud.android" to Known("SoundCloud") { "https://soundcloud.com/search?q=${enc(it)}" },
        "com.qobuz.music" to Known("Qobuz", null),
        "app.jam.android" to Known("Jam", null),
    )

    /** Music apps worth listing even before they've played anything here, if installed. */
    val SUGGESTED = listOf(Prefs.APPLE_MUSIC, "com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app",
        "com.aspiro.tidal", "com.amazon.mp3", "com.soundcloud.android")

    fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    fun name(context: Context, pkg: String): String {
        KNOWN[pkg]?.let { return it.name }
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        }.getOrNull()?.ifBlank { null } ?: pkg
    }

    /**
     * The song's page: its Apple Music page when the catalog lookup found it and the player is Apple
     * Music (or has no search page of its own), else the player's search for "artist title".
     */
    fun link(pkg: String, title: String, artist: String, appleUrl: String?): String {
        if (pkg == Prefs.APPLE_MUSIC && appleUrl != null) return appleUrl
        val q = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        KNOWN[pkg]?.search?.let { return if (q.isEmpty()) "" else it(q) }
        return appleUrl ?: ""
    }
}
