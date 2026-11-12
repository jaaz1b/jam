package app.jam

import app.myspoti.host.android.NowPlayingInfo

/**
 * Jam: Discord Rich Presence in Spotify mode. What SongPlayer shows in the notification goes to
 * Jam for Android's Discord presence (app.jam.android.Discord, the same sign-in, lines, buttons
 * and artwork settings as YouTube Music mode), as a NowPlaying of the source "Spotify".
 */
object JamPresence {
    private var last: app.jam.android.NowPlaying? = null

    @Synchronized
    fun update(info: NowPlayingInfo?) {
        val np = info?.takeIf { it.title.isNotBlank() && it.id != "spotify:ad" }?.let {
            app.jam.android.NowPlaying(
                title = it.title,
                artist = it.artist,
                album = it.album,
                artwork = it.coverUrl?.takeIf { u -> u.startsWith("https://") && u.length <= 512 },
                playing = it.playing,
                position = if (it.positionMs >= 0) it.positionMs / 1000.0 else null,
                duration = if (it.durationMs > 0) it.durationMs / 1000.0 else null,
                url = link(it.id),
                sourceId = "spotify",
                sourceName = "Spotify",
            )
        }
        // The notification is pushed again for every buffering or position change: Discord's own
        // throttle sorts out real changes, this only skips the identical reports.
        if (same(np, last)) return
        last = np
        app.jam.android.Player.reportSpotify(np)
    }

    /** "spotify:track:ID" → its open.spotify.com page (the "Listen on" button); else Spotify's home. */
    private fun link(uri: String): String {
        val p = uri.split(':')
        return if (p.size == 3 && p[0] == "spotify" && p[1] in setOf("track", "episode") && p[2].matches(Regex("[A-Za-z0-9]{22}")))
            "https://open.spotify.com/${p[1]}/${p[2]}" else "https://open.spotify.com/"
    }

    private fun same(a: app.jam.android.NowPlaying?, b: app.jam.android.NowPlaying?): Boolean {
        if (a == null || b == null) return a == b
        if (a.title != b.title || a.artist != b.artist || a.album != b.album || a.artwork != b.artwork || a.playing != b.playing || a.duration != b.duration) return false
        // Same song, same state: only a seek (the position off by more than 3 s from where it should be) is news.
        val pa = a.positionNow(); val pb = b.positionNow()
        return pa == null || pb == null || kotlin.math.abs(pa - pb) < 3.0
    }
}
