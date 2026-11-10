package app.jam.android

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Album and track covers for web apps that work offline (Octave), kept by Jam itself.
 *
 * The site's own offline worker keeps only its last few hundred images, and can't keep covers
 * from other sites unless they allow it, so a saved state (or no internet) showed downloaded songs
 * without their art. installed-app.js tells Jam which covers the site shows, and which ones its
 * saved pages and downloads refer to; Jam downloads each once into its own folder, and answers
 * the site's requests for them from there whenever it has no network (saved state or offline).
 * Jam's notification and lock screen use them too.
 */
object CoverStore {
    private const val TAG = "JamCovers"
    private const val MAX_FILE = 4L shl 20
    private const val MAX_TOTAL = 600L shl 20

    private lateinit var dir: File
    private val pool = Executors.newFixedThreadPool(3)
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var total = -1L
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "covers").apply { mkdirs() }
    }

    private fun ready() = ::dir.isInitialized

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * The image a URL stands for: the URL itself and, for the site's image proxies
     * (`/api/img?u=…`, `/_next/image?url=…`), the image behind it.
     */
    private fun candidates(url: String): List<String> {
        val out = ArrayList<String>(2)
        if (url.startsWith("http")) out += url
        runCatching {
            val u = Uri.parse(url)
            for (p in arrayOf("u", "url")) {
                val inner = u.getQueryParameter(p) ?: continue
                when {
                    inner.startsWith("http") -> out += inner
                    inner.startsWith("/") -> out += "${u.scheme}://${u.encodedAuthority}$inner"
                }
            }
        }
        return out
    }

    fun file(url: String?): File? {
        if (url == null || !ready()) return null
        for (c in candidates(url)) {
            val f = File(dir, key(c))
            if (f.isFile && f.length() > 0) return f
        }
        return null
    }

    fun count(): Int = if (ready()) dir.list()?.size ?: 0 else 0

    fun size(): Long {
        if (!ready()) return 0
        if (total < 0) total = dir.listFiles()?.sumOf { it.length() } ?: 0
        return total
    }

    fun clear() {
        if (!ready()) return
        dir.listFiles()?.forEach { it.delete() }
        total = 0
        failed.clear()
    }

    /** Covers to keep: each missing one is downloaded once, in the background. */
    fun want(urls: Collection<String>): Int {
        if (!ready()) return 0
        var queued = 0
        for (url in urls) {
            if (url.length > 2000 || file(url) != null) continue
            // The image itself rather than a resized copy from the site's proxy, when there is one.
            val target = candidates(url).lastOrNull() ?: continue
            if (target in failed || !pending.add(target)) continue
            queued++
            pool.execute {
                try {
                    if (size() < MAX_TOTAL) fetch(target, url)
                } finally {
                    pending.remove(target)
                }
            }
        }
        if (queued > 0) Log.i(TAG, "saving $queued covers (${count()} kept, ${size() shr 20} MB)")
        return queued
    }

    private fun fetch(target: String, original: String) {
        for (u in linkedSetOf(target, original)) {
            if (!u.startsWith("http")) continue
            val bytes = try {
                http.newCall(Request.Builder().url(u).header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8").build()).execute().use { r ->
                    val len = r.body?.contentLength() ?: -1
                    if (!r.isSuccessful || len > MAX_FILE) null else r.body?.bytes()?.takeIf { it.size <= MAX_FILE }
                }
            } catch (e: Exception) { null }
            if (bytes == null || mime(bytes) == null) continue
            val f = File(dir, key(target))
            val tmp = File(dir, f.name + ".part")
            try {
                tmp.writeBytes(bytes)
                if (tmp.renameTo(f)) { if (total >= 0) total += bytes.size }
            } catch (e: Exception) {
                tmp.delete()
            }
            return
        }
        failed += target
    }

    /** What an image is, from its first bytes (null: not an image, e.g. an error page). */
    private fun mime(b: ByteArray): String? {
        fun at(i: Int, s: String) = b.size >= i + s.length && s.indices.all { b[i + it] == s[it].code.toByte() }
        return when {
            b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
            b.size > 8 && b[0] == 0x89.toByte() && at(1, "PNG") -> "image/png"
            at(0, "RIFF") && at(8, "WEBP") -> "image/webp"
            at(0, "GIF8") -> "image/gif"
            at(4, "ftypavif") || at(4, "ftypavis") -> "image/avif"
            at(0, "<svg") || at(0, "<?xml") -> "image/svg+xml"
            else -> null
        }
    }

    /** The saved cover for a request, if Jam has it (any origin may use it, like a public image). */
    fun serve(url: String): WebResourceResponse? {
        val f = file(url) ?: return null
        return try {
            val head = ByteArray(16)
            val n = f.inputStream().use { it.read(head) }
            val type = mime(head.copyOf(maxOf(n, 0))) ?: return null
            WebResourceResponse(type, null, 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=31536000", "Content-Length" to f.length().toString()),
                f.inputStream())
        } catch (e: Exception) {
            null
        }
    }
}
