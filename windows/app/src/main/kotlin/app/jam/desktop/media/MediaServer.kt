package app.jam.desktop.media

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import app.jam.desktop.shim.Desk
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The bytes of what the engine plays, served to the decoder (ffmpeg) on 127.0.0.1: each request (a
 * range, as the decoder seeks) opens the item's own DataSource at that place, so the app's sources
 * (decrypting downloads, extension streams, the media cache) are the ones read, as with ExoPlayer.
 */
object MediaServer {
    class Entry(val factory: DataSource.Factory, val uri: android.net.Uri, val key: String?, val headers: Map<String, String>) { @Volatile var length = -2L; @Volatile var error: Throwable? = null }
    private val entries = ConcurrentHashMap<String, Entry>()
    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "jam-media").apply { isDaemon = true } }
            createContext("/m/") { ex -> runCatching { serve(ex) }.onFailure { t -> if (t !is java.io.IOException) Desk.log('E', "MediaServer", "${ex.requestURI}: $t", t); runCatching { ex.close() } } }
            start()
        }
    }
    fun register(e: Entry): String { val id = java.util.UUID.randomUUID().toString(); entries[id] = e; return "http://127.0.0.1:${server.address.port}/m/$id" }
    fun unregister(url: String) { entries.remove(url.substringAfterLast('/')) }
    fun entry(url: String): Entry? = entries[url.substringAfterLast('/')]

    private fun serve(ex: HttpExchange) {
        val e = entries[ex.requestURI.path.removePrefix("/m/")] ?: run { ex.sendResponseHeaders(404, -1); ex.close(); return }
        val range = ex.requestHeaders.getFirst("Range")
        val start = range?.let { Regex("bytes=(\\d+)-").find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val src = e.factory.createDataSource()
        val spec = DataSpec.Builder().setUri(e.uri).setPosition(start).setKey(e.key).setHttpRequestHeaders(e.headers).build()
        val len = try { src.open(spec) } catch (t: Throwable) { e.error = t; runCatching { src.close() }; ex.sendResponseHeaders(502, -1); ex.close(); return }
        e.error = null
        if (start == 0L && len >= 0) e.length = len
        val total = if (e.length >= 0) e.length else if (len >= 0) start + len else -1
        ex.responseHeaders.add("Accept-Ranges", "bytes")
        ex.responseHeaders.add("Content-Type", "application/octet-stream")
        if (range != null && total >= 0) {
            ex.responseHeaders.add("Content-Range", "bytes $start-${total - 1}/$total")
            ex.sendResponseHeaders(206, if (len >= 0) len else 0)
        } else ex.sendResponseHeaders(200, if (len >= 0) len else 0)
        val buf = ByteArray(64 * 1024)
        try {
            ex.responseBody.use { out ->
                while (true) { val n = src.read(buf, 0, buf.size); if (n < 0) break; if (n > 0) out.write(buf, 0, n) }
            }
        } catch (t: java.io.IOException) {
            // The decoder closed the connection (a seek, a stop): normal. A source failure is kept for the error report.
            if (t.message?.contains("closed", true) != true && t.message?.contains("broken pipe", true) != true && t.message?.contains("reset", true) != true) e.error = t
        } finally { runCatching { src.close() } }
    }
}
