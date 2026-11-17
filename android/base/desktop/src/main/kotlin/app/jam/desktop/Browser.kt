package app.jam.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The browser the Spotify Web Player runs in, out of sight: Microsoft Edge (on every Windows 10/11
 * PC; it plays Spotify's protected audio), else Google Chrome or Chromium. Its own profile
 * ([Paths.webProfile]), the DevTools protocol on a free local port, the window off-screen. It comes
 * on screen only for the Spotify sign-in ([show]).
 */
class Browser private constructor(private val process: Process, val port: Int) {
    val cdpUrl: String get() = "http://127.0.0.1:$port"
    private val http = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    val alive: Boolean get() = process.isAlive

    /** Brings the Web Player's window on screen, in front, for the user to sign in. */
    suspend fun show() = bounds(intArrayOf(160, 90, 1000, 780))

    /** Out of sight again: minimised (Windows keeps a window it starts off-screen partly in view). */
    suspend fun hide() = bounds(null)

    private suspend fun bounds(at: IntArray?) = runCatching {
        val target = spotifyTarget() ?: return@runCatching
        browserCall { send ->
            val win = send("Browser.getWindowForTarget", buildJsonObject { put("targetId", target) })["windowId"]!!.jsonPrimitive.int
            send("Browser.setWindowBounds", buildJsonObject { put("windowId", win); putJsonObject("bounds") { put("windowState", "normal") } })
            if (at == null) {
                send("Browser.setWindowBounds", buildJsonObject { put("windowId", win); putJsonObject("bounds") { put("windowState", "minimized") } })
            } else {
                send("Browser.setWindowBounds", buildJsonObject {
                    put("windowId", win)
                    putJsonObject("bounds") { put("left", at[0]); put("top", at[1]); put("width", at[2]); put("height", at[3]) }
                })
                send("Target.activateTarget", buildJsonObject { put("targetId", target) })
            }
        }
    }

    private suspend fun spotifyTarget(): String? = withContext(Dispatchers.IO) {
        val list = http.newCall(Request.Builder().url("$cdpUrl/json/list").build()).execute().use { it.body.string() }
        json.parseToJsonElement(list).jsonArray.map { it.jsonObject }.firstOrNull {
            it["type"]?.jsonPrimitive?.content == "page" && it["url"]?.jsonPrimitive?.content.orEmpty().contains("spotify.com")
        }?.get("id")?.jsonPrimitive?.content
    }

    /** A few calls on the browser's own DevTools endpoint (windows, not pages). */
    private suspend fun browserCall(block: suspend (suspend (String, JsonObject) -> JsonObject) -> Unit) {
        val version = withContext(Dispatchers.IO) { http.newCall(Request.Builder().url("$cdpUrl/json/version").build()).execute().use { it.body.string() } }
        val wsUrl = json.parseToJsonElement(version).jsonObject["webSocketDebuggerUrl"]!!.jsonPrimitive.content
        val ids = AtomicInteger(0)
        val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        val opened = CompletableDeferred<Unit>()
        val socket = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                val id = o["id"]?.jsonPrimitive?.int ?: return
                pending.remove(id)?.complete(o["result"]?.jsonObject ?: JsonObject(emptyMap()))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t); pending.values.forEach { it.completeExceptionally(t) }
            }
        })
        try {
            withTimeout(10_000) { opened.await() }
            block { method, params ->
                val id = ids.incrementAndGet()
                val d = CompletableDeferred<JsonObject>(); pending[id] = d
                socket.send(buildJsonObject { put("id", id); put("method", method); put("params", params) }.toString())
                withTimeout(10_000) { d.await() }
            }
        } finally { socket.close(1000, null) }
    }

    /** Closes the browser (and its helper processes). */
    fun quit() {
        runCatching { process.toHandle().descendants().forEach { it.destroy() } }
        runCatching { process.destroy() }
    }

    companion object {
        /** Edge, Chrome or Chromium on this PC, or null. */
        fun find(): File? {
            val env = { k: String -> System.getenv(k).orEmpty() }
            val candidates = if (Paths.isWindows) listOf(
                "${env("ProgramFiles(x86)")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${env("ProgramFiles")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${env("LOCALAPPDATA")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${env("ProgramFiles")}\\Google\\Chrome\\Application\\chrome.exe",
                "${env("ProgramFiles(x86)")}\\Google\\Chrome\\Application\\chrome.exe",
                "${env("LOCALAPPDATA")}\\Google\\Chrome\\Application\\chrome.exe",
            ) else listOf(
                "/usr/bin/microsoft-edge", "/usr/bin/microsoft-edge-stable", "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser",
            )
            return candidates.map(::File).firstOrNull { it.isFile }
        }

        /** Starts [exe] on the Web Player and waits for its DevTools endpoint. */
        suspend fun start(exe: File): Browser = withContext(Dispatchers.IO) {
            val port = ServerSocket(0).use { it.localPort }
            val args = listOf(
                exe.absolutePath,
                "--remote-debugging-port=$port",
                "--remote-allow-origins=http://127.0.0.1:$port",
                "--user-data-dir=${Paths.webProfile.absolutePath}",
                "--no-first-run", "--no-default-browser-check", "--disable-sync",
                "--autoplay-policy=no-user-gesture-required",
                // Out of sight is not paused: no throttling of the hidden window's page and timers.
                "--disable-background-timer-throttling", "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-features=CalculateNativeWinOcclusion,msEdgeSidebarV2,msHubApps,EdgeCollections,msUndersideButton,msShowFirstRunPage",
                "--window-position=-32000,-32000", "--window-size=1000,780",
                "--app=https://open.spotify.com/",
            )
            val process = ProcessBuilder(args).redirectErrorStream(true)
                .redirectOutput(File(Paths.dataDir, "browser.log")).start()
            val b = Browser(process, port)
            val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).build()
            val end = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < end) {
                if (!process.isAlive) error("the browser closed at once (another copy using its profile?)")
                val up = runCatching {
                    client.newCall(Request.Builder().url("${b.cdpUrl}/json/version").build()).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (up) return@withContext b
                delay(300)
            }
            b.quit(); error("the browser did not open its DevTools port")
        }
    }
}
