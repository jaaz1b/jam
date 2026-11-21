package app.jam.presence

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A cover Discord can load (an https address) and an Apple Music link for a song, from Apple's
 * public catalog search (itunes.apple.com/search). Players hand their covers to Android as pictures,
 * which Discord can't show. Only the title, artist and album go out; nothing about you.
 */
object Covers {
    data class Found(val artwork: String?, val url: String?)

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    private val MISS = Found(null, null)
    private val cache = object : LinkedHashMap<String, Found>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Found>?) = size > 300
    }
    private val pending = HashMap<String, Long>()

    fun key(title: String, artist: String, album: String) = "${norm(title)}|${norm(artist)}|${norm(album)}"

    /** What's known for this song: a result (maybe empty), or null when it isn't known yet. */
    @Synchronized fun cached(key: String): Found? = cache[key]

    /** When a lookup for this song started, if one is running. */
    @Synchronized fun pendingSince(key: String): Long? = pending[key]

    /** Looks the song up (once); [done] runs on the main thread when the answer is in. */
    fun fetch(title: String, artist: String, album: String, done: () -> Unit) {
        val k = key(title, artist, album)
        synchronized(this) {
            if (cache.containsKey(k) || pending.containsKey(k)) return
            pending[k] = System.currentTimeMillis()
        }
        io.execute {
            val r = runCatching { search(title, artist, album) }.getOrNull()
            synchronized(this) {
                pending.remove(k)
                // A network failure isn't remembered: the next song change tries again.
                if (r != null) cache[k] = r
            }
            main.post(done)
        }
    }

    private fun country(): String = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase() ?: "us"

    /** null = couldn't ask; [MISS] = asked, not found. */
    private fun search(title: String, artist: String, album: String): Found? {
        val plain = stripExtras(title)
        val firstArtist = artist.split(Regex("\\s*(,|&| x | feat\\.?| ft\\.?)\\s*", RegexOption.IGNORE_CASE)).first()
        val tries = linkedSetOf("$firstArtist $title", "$firstArtist $plain")
        for (term in tries) {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term.trim()).addQueryParameter("entity", "song")
                .addQueryParameter("limit", "10").addQueryParameter("country", country()).build()
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return null
                r.body?.string() ?: return null
            }
            val results = JSONObject(body).optJSONArray("results") ?: continue
            var best: JSONObject? = null
            var bestScore = 0
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val s = score(o, title, plain, firstArtist, album)
                if (s > bestScore) { best = o; bestScore = s }
            }
            if (best != null && bestScore >= 2) {
                val art = best.optString("artworkUrl100").takeIf { it.startsWith("http") }
                    ?.replace(Regex("/\\d+x\\d+(bb)?\\.(jpg|png|webp)$"), "/600x600bb.jpg")?.replaceFirst("http://", "https://")
                val link = best.optString("trackViewUrl").takeIf { it.startsWith("https://") }
                    ?.replace(Regex("[?&]uo=\\d+"), "")?.let { if (it.length <= 512) it else null }
                return Found(art, link)
            }
        }
        return MISS
    }

    /** 2 = the title and artist match; +1 for the album too. */
    private fun score(o: JSONObject, title: String, plain: String, artist: String, album: String): Int {
        val t = norm(o.optString("trackName")); val tp = norm(stripExtras(o.optString("trackName")))
        val a = norm(o.optString("artistName"))
        val titleOk = t == norm(title) || tp == norm(plain) || (tp.isNotEmpty() && (tp.startsWith(norm(plain)) || norm(plain).startsWith(tp)))
        val artistOk = norm(artist).let { it.isNotEmpty() && (a.contains(it) || it.contains(a)) }
        if (!titleOk || !artistOk) return 0
        val al = norm(o.optString("collectionName"))
        return if (album.isNotBlank() && (al == norm(album) || norm(stripExtras(album)) == norm(stripExtras(o.optString("collectionName"))))) 3 else 2
    }

    private fun stripExtras(s: String) = s.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace(Regex("\\s+-\\s+.*$"), "").trim()

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace("&", "and").replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
