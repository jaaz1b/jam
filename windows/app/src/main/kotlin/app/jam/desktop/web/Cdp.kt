package app.jam.desktop.web

import app.jam.desktop.shim.Desk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One DevTools protocol connection (a page's or the browser's). Calls are futures; events go to
 * [onEvent] on this connection's own thread (never the socket's reader, which must stay free to
 * read the answers that a handler may wait for).
 */
class Cdp(url: String, private val name: String) {
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject>>()
    private val events = Executors.newSingleThreadExecutor { r -> Thread(r, "cdp-$name").apply { isDaemon = true } }
    private val opened = CompletableFuture<Unit>()
    @Volatile var onEvent: ((method: String, params: JSONObject, session: String?) -> Unit)? = null
    @Volatile var onClose: (() -> Unit)? = null
    @Volatile var closed = false; private set
    private val socket: WebSocket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (o.has("id")) {
                val f = pending.remove(o.getInt("id")) ?: return
                if (o.has("error")) f.completeExceptionally(CdpException(o.getJSONObject("error").optString("message"))) else f.complete(o.optJSONObject("result") ?: JSONObject())
                return
            }
            val method = o.optString("method").ifEmpty { return }
            val params = o.optJSONObject("params") ?: JSONObject()
            val session = o.optString("sessionId").ifEmpty { null }
            events.execute { runCatching { onEvent?.invoke(method, params, session) }.onFailure { Desk.log('E', "Cdp", "$name $method: $it", it) } }
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(null)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = gone(t)
    })

    private fun gone(t: Throwable?) {
        if (closed) return
        closed = true
        opened.completeExceptionally(t ?: IllegalStateException("closed"))
        pending.values.forEach { it.completeExceptionally(t ?: IllegalStateException("closed")) }
        pending.clear()
        events.execute { onClose?.invoke() }
    }

    fun awaitOpen(ms: Long = 15_000) { opened.get(ms, TimeUnit.MILLISECONDS) }

    fun call(method: String, params: JSONObject = JSONObject(), session: String? = null): CompletableFuture<JSONObject> {
        val id = ids.incrementAndGet()
        val f = CompletableFuture<JSONObject>()
        if (closed) { f.completeExceptionally(IllegalStateException("closed")); return f }
        pending[id] = f
        val msg = JSONObject().put("id", id).put("method", method).put("params", params)
        if (session != null) msg.put("sessionId", session)
        if (!socket.send(msg.toString())) { pending.remove(id); f.completeExceptionally(IllegalStateException("not sent")) }
        return f.orTimeout(30, TimeUnit.SECONDS)
    }
    /** A call whose answer is waited for (never on the event thread of this same connection). */
    fun callSync(method: String, params: JSONObject = JSONObject(), ms: Long = 10_000): JSONObject = call(method, params).get(ms, TimeUnit.MILLISECONDS)
    fun fire(method: String, params: JSONObject = JSONObject()) { call(method, params).exceptionally { null } }

    fun close() { runCatching { socket.close(1000, null) }; gone(null); events.shutdown() }

    class CdpException(m: String) : Exception(m)
    companion object {
        val http: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(5, TimeUnit.SECONDS).build()
    }
}

fun json(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply { for ((k, v) in pairs) put(k, v ?: JSONObject.NULL) }
