package app.jam.presence

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A cover Discord can load (an https address) and an Apple Music link for a song. Players hand
 * their covers to Android as pictures, which Discord can't show, so the song is looked up:
 *  1. Apple Music's own song id, when the player gives one (exact);
 *  2. Apple's catalog search (itunes.apple.com): the song, then the title alone;
 *  3. Deezer's catalog search (api.deezer.com): the song;
 *  4. the album, on Apple's catalog then Deezer's.
 * Only the title, artist and album go out; nothing about you.
 *
 * Battery: every answer (found or not) is saved on the phone, so a song is looked up once, ever.
 * A lookup that fails (no internet, a catalog saying "slow down") is retried later, not right away.
 */
object Covers {
    private const val TAG = "JamPresence"
    data class Found(val artwork: String?, val url: String?, val source: String) {
        val hit get() = artwork != null
    }

    /** What the player said about the song. */
    data class Query(val title: String, val artist: String, val album: String, val appleId: String?) {
        val key = "${norm(title)}|${norm(artist)}|${norm(album)}"
    }

    sealed interface State {
        data class Known(val found: Found) : State
        data class Pending(val since: Long) : State
        /** Couldn't ask; will try again from [retryAt]. */
        data class Failed(val retryAt: Long) : State
        data object Unknown : State
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build()

    private const val MAX_SAVED = 1500
    /** Not found anywhere: asked again after this long (catalogs add songs). */
    private const val MISS_TTL = 14L * 24 * 3600 * 1000
    /** Old saved answers from before a matching fix are re-asked (bump when matching changes). */
    private const val VERSION = 2

    private lateinit var file: File
    private val cache = object : LinkedHashMap<String, Pair<Found, Long>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Found, Long>>?) = size > MAX_SAVED
    }
    private val pending = HashMap<String, Long>()
    private val failed = HashMap<String, Pair<Long, Int>>() // key → (retry at, failures)
    private var itunesQuietUntil = 0L
    private var deezerQuietUntil = 0L
    private var saveScheduled = false

    fun init(context: Context) {
        file = File(context.filesDir, "covers.json")
        io.execute {
            runCatching {
                if (!file.exists()) return@runCatching
                val j = JSONObject(file.readText())
                if (j.optInt("v") != VERSION) return@runCatching
                val items = j.optJSONArray("items") ?: return@runCatching
                val now = System.currentTimeMillis()
                synchronized(this) {
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        val f = Found(o.optString("a").ifEmpty { null }, o.optString("u").ifEmpty { null }, o.optString("s"))
                        val t = o.optLong("t")
                        if (!f.hit && now - t > MISS_TTL) continue
                        cache[o.optString("k")] = f to t
                    }
                }
            }.onFailure { Log.w(TAG, "covers cache: $it") }
        }
    }

    @Synchronized
    fun state(q: Query): State {
        cache[q.key]?.let { (f, t) ->
            if (f.hit || System.currentTimeMillis() - t < MISS_TTL) return State.Known(f)
        }
        pending[q.key]?.let { return State.Pending(it) }
        failed[q.key]?.let { (at, _) -> if (System.currentTimeMillis() < at) return State.Failed(at) }
        return State.Unknown
    }

    /** Looks the song up unless it's known, running, or failed recently; [done] runs on the main thread after. */
    fun fetch(q: Query, done: () -> Unit) {
        synchronized(this) {
            if (state(q) !is State.Unknown) return
            pending[q.key] = System.currentTimeMillis()
        }
        io.execute {
            val r = try { find(q) } catch (e: Exception) { Log.w(TAG, "cover lookup: $e"); null }
            synchronized(this) {
                pending.remove(q.key)
                if (r != null) {
                    cache[q.key] = r to System.currentTimeMillis()
                    failed.remove(q.key)
                    scheduleSave()
                } else {
                    // 1, 2, 4, 8… minutes, at most 30.
                    val n = (failed[q.key]?.second ?: 0) + 1
                    failed[q.key] = (System.currentTimeMillis() + minOf(30L, 1L shl minOf(n - 1, 5)) * 60_000L) to n
                }
            }
            Log.i(TAG, "cover for \"${q.title}\" / \"${q.artist}\" / \"${q.album}\": " +
                (r?.let { if (it.hit) "found (${it.source})" else "not found" } ?: "lookup failed, retrying later"))
            main.post(done)
        }
    }

    private fun scheduleSave() {
        if (saveScheduled) return
        saveScheduled = true
        main.postDelayed({ io.execute { save() } }, 5000)
    }

    private fun save() {
        val arr = JSONArray()
        synchronized(this) {
            saveScheduled = false
            for ((k, v) in cache) arr.put(JSONObject().put("k", k).put("a", v.first.artwork ?: "").put("u", v.first.url ?: "")
                .put("s", v.first.source).put("t", v.second))
        }
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(JSONObject().put("v", VERSION).put("items", arr).toString())
            tmp.renameTo(file)
        }
    }

    // ------------------------------------------------------------------ the lookup

    /** Outcome of one source: a hit, "not there" (null artwork), or null = couldn't ask. */
    private fun find(q: Query): Found? {
        val c = Clean(q)
        var anyFailed = false
        var appleLink: String? = null
        val tries: List<() -> Found?> = listOfNotNull(
            q.appleId?.let { id -> { itunesLookup(id) } },
            { itunesSong(c, "${c.mainArtist} ${c.plainTitle}") },
            { deezerTrack(c) },
            { if (c.plainTitle.length >= 3) itunesSong(c, c.plainTitle) else MISS },
            { if (c.plainAlbum.isNotEmpty()) itunesAlbum(c) else MISS },
            { if (c.plainAlbum.isNotEmpty()) deezerAlbum(c) else MISS },
        )
        for (t in tries) {
            val r = t()
            if (r == null) { anyFailed = true; continue }
            if (appleLink == null && r.url?.startsWith("https://music.apple.com/") == true) appleLink = r.url
            if (r.hit) return if (r.url == null && appleLink != null) r.copy(url = appleLink) else r
        }
        return if (anyFailed) null else Found(null, appleLink, "")
    }

    private val MISS = Found(null, null, "")

    private fun country(): String = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase() ?: "us"

    /** GET → JSON, or null when the catalog couldn't be asked. A "slow down" quiets that catalog for a while. */
    private fun get(url: HttpUrl, itunes: Boolean): JSONObject? {
        val now = System.currentTimeMillis()
        if (itunes && now < itunesQuietUntil) return null
        if (!itunes && now < deezerQuietUntil) return null
        return http.newCall(Request.Builder().url(url).header("User-Agent", "JamPresence/1.0 (Android)").build()).execute().use { r ->
            if (r.code == 403 || r.code == 429 || r.code >= 500) {
                if (itunes) itunesQuietUntil = now + 3 * 60_000 else deezerQuietUntil = now + 60_000
                Log.w(TAG, "${url.host} said ${r.code}; pausing it")
                return null
            }
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body.string())
            // Deezer reports its limits inside a 200.
            if (!itunes && j.has("error")) {
                if (j.optJSONObject("error")?.optInt("code") == 4) deezerQuietUntil = now + 60_000
                return null
            }
            j
        }
    }

    private fun itunesUrl(path: String) = "https://itunes.apple.com/$path".toHttpUrl().newBuilder().addQueryParameter("country", country())

    private fun appleArt(o: JSONObject): String? = o.optString("artworkUrl100").takeIf { it.startsWith("http") }
        ?.replace(Regex("/\\d+x\\d+(bb|cc|-\\d+)?\\.(jpg|png|webp)$"), "/600x600bb.jpg")?.replaceFirst("http://", "https://")
        ?.takeIf { it.length <= 512 }

    private fun appleLink(s: String?): String? = s?.takeIf { it.startsWith("https://") }
        ?.replace(Regex("([?&])uo=\\d+&?"), "$1")?.trimEnd('?', '&')?.takeIf { it.length <= 512 }

    private fun itunesLookup(id: String): Found? {
        val j = get(itunesUrl("lookup").addQueryParameter("id", id).build(), true) ?: return null
        val o = j.optJSONArray("results")?.optJSONObject(0) ?: return MISS
        return Found(appleArt(o), appleLink(o.optString("trackViewUrl")), "Apple Music")
    }

    private fun itunesSong(c: Clean, term: String): Found? {
        val j = get(itunesUrl("search").addQueryParameter("term", term.trim()).addQueryParameter("entity", "song")
            .addQueryParameter("limit", "25").build(), true) ?: return null
        val best = bestOf(j.optJSONArray("results")) { o -> c.scoreSong(o.optString("trackName"), o.optString("artistName"), o.optString("collectionName")) }
            ?: return MISS
        return Found(appleArt(best), appleLink(best.optString("trackViewUrl")), "Apple Music")
    }

    private fun itunesAlbum(c: Clean): Found? {
        val j = get(itunesUrl("search").addQueryParameter("term", "${c.mainArtist} ${c.plainAlbum}".trim()).addQueryParameter("entity", "album")
            .addQueryParameter("limit", "15").build(), true) ?: return null
        val best = bestOf(j.optJSONArray("results")) { o -> c.scoreAlbum(o.optString("collectionName"), o.optString("artistName")) } ?: return MISS
        return Found(appleArt(best), appleLink(best.optString("collectionViewUrl")), "Apple Music (album)")
    }

    private fun deezerArt(album: JSONObject?): String? =
        album?.let { a -> listOf("cover_xl", "cover_big").map { a.optString(it) }.firstOrNull { it.startsWith("https://") && it.length <= 512 } }

    private fun deezerTrack(c: Clean): Found? {
        val q1 = "artist:\"${c.mainArtist}\" track:\"${c.plainTitle}\""
        for (q in listOf(q1, "${c.mainArtist} ${c.plainTitle}")) {
            val j = get("https://api.deezer.com/search".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("limit", "15").build(), false)
                ?: return null
            val best = bestOf(j.optJSONArray("data")) { o ->
                c.scoreSong(o.optString("title"), o.optJSONObject("artist")?.optString("name") ?: "", o.optJSONObject("album")?.optString("title") ?: "")
            }
            if (best != null) deezerArt(best.optJSONObject("album"))?.let { return Found(it, null, "Deezer") }
        }
        return MISS
    }

    private fun deezerAlbum(c: Clean): Found? {
        val j = get("https://api.deezer.com/search/album".toHttpUrl().newBuilder()
            .addQueryParameter("q", "artist:\"${c.mainArtist}\" album:\"${c.plainAlbum}\"").addQueryParameter("limit", "15").build(), false) ?: return null
        val best = bestOf(j.optJSONArray("data")) { o -> c.scoreAlbum(o.optString("title"), o.optJSONObject("artist")?.optString("name") ?: "") } ?: return MISS
        return deezerArt(best)?.let { Found(it, null, "Deezer (album)") } ?: MISS
    }

    private fun bestOf(arr: JSONArray?, score: (JSONObject) -> Int): JSONObject? {
        if (arr == null) return null
        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = score(o)
            if (s > bestScore) { best = o; bestScore = s }
        }
        return best
    }

    // ------------------------------------------------------------------ matching

    /** The player's text, cleaned up for searching and comparing. */
    class Clean(q: Query) {
        val artists: List<String>
        val mainArtist: String
        val title = q.title.trim()
        val plainTitle = plain(title)
        val album: String
        val plainAlbum: String

        init {
            var artist = q.artist.trim()
            var alb = q.album.trim()
            // Some players put "Artist — Album" in the artist line.
            val dash = Regex("\\s+[—–]\\s+").find(artist)
            if (dash != null) {
                val rest = artist.substring(dash.range.last + 1).trim()
                artist = artist.substring(0, dash.range.first).trim()
                if (alb.isEmpty()) alb = rest
            }
            album = alb
            plainAlbum = plain(alb)
            // Featured artists in the title count too.
            val feat = Regex("[(\\[](?:feat\\.?|ft\\.?|featuring|with)\\s+([^)\\]]+)[)\\]]", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)
            artists = (splitArtists(artist) + (feat?.let { splitArtists(it) } ?: emptyList())).filter { norm(it).isNotEmpty() }.distinct()
            mainArtist = artists.firstOrNull() ?: artist
        }

        private fun titleMatches(candidate: String): Boolean {
            val a = norm(plain(candidate)); val b = norm(plainTitle)
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b || norm(candidate) == norm(title)) return true
            // "Song" vs "Song (Live at X)" / "Song - 2011 Remaster" survive plain(); this catches what's left.
            val (s, l) = if (a.length < b.length) a to b else b to a
            return s.length >= 4 && l.startsWith(s) && s.length * 10 >= l.length * 6
        }

        private fun artistMatches(candidate: String): Boolean {
            val c = norm(candidate)
            if (c.isEmpty()) return false
            val cParts = splitArtists(candidate).map { norm(it) }
            return artists.any { a -> val n = norm(a); n.isNotEmpty() && (c.contains(n) || n.contains(c) || cParts.any { it == n }) }
        }

        private fun albumMatches(candidate: String): Boolean =
            plainAlbum.isNotEmpty() && norm(plain(candidate)).let { it.isNotEmpty() && (it == norm(plainAlbum) || it == norm(album)) }

        /** 0 = not this song; higher = surer. */
        fun scoreSong(t: String, a: String, al: String): Int {
            if (!titleMatches(t)) return 0
            val artistOk = artistMatches(a)
            val albumOk = albumMatches(al)
            return when {
                artistOk && albumOk -> 4
                artistOk -> 3
                albumOk -> 2 // e.g. the artist's name written another way
                else -> 0
            }
        }

        fun scoreAlbum(al: String, a: String): Int = if (albumMatches(al) && artistMatches(a)) 2 else 0
    }

    private fun splitArtists(s: String): List<String> =
        s.split(Regex("\\s*(?:,|&|\\+|;|/|\\s[xX×]\\s|\\sand\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s|\\swith\\s)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }

    /** Without "(feat. …)", "[Explicit]", " - 2011 Remaster", " - Single", "(Deluxe Edition)"… */
    fun plain(s: String): String = s
        .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "")
        .replace(Regex("\\s+[-–—]\\s+.*$"), "")
        .replace(Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE), "")
        .replace("🅴", "")
        .trim()

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
            .replace("&", "and").replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
