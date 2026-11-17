package app.jam.desktop.web

import app.jam.desktop.shim.Desk
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The browser the app's web views run in, out of sight: Microsoft Edge (on every Windows 10/11 PC; it
 * plays Spotify's protected audio), else Google Chrome or Chromium. One browser per web profile (an
 * account's own cookies, as Android's WebView profiles), each in its own folder under Jam's data. Its
 * windows never show: they are created off screen and taken off the taskbar and Alt+Tab
 * ([HiddenWindows]); what a web view shows is drawn inside Jam's window from the page's screencast.
 */
class Edge private constructor(val profile: String, private val process: Process, val port: Int) {
    val browser: Cdp
    /** The browser's own user agent (its real version), for the app's HTTP calls that go with the page's. */
    val userAgent: String
    val version: String

    init {
        val v = JSONObject(Cdp.http.newCall(Request.Builder().url("http://127.0.0.1:$port/json/version").build()).execute().use { it.body.string() })
        userAgent = v.optString("User-Agent"); version = v.optString("Browser")
        browser = Cdp(v.getString("webSocketDebuggerUrl"), "browser-$profile")
        browser.awaitOpen()
        // New windows (a page's popup, window.open) are found here and hidden like the others.
        browser.onEvent = { method, params, _ ->
            if (method == "Target.targetCreated") {
                val info = params.getJSONObject("targetInfo")
                if (info.optString("type") == "page") { HiddenWindows.hide(process); Pages.popup(this, info) }
            }
        }
        browser.fire("Target.setDiscoverTargets", json("discover" to true))
    }

    val alive: Boolean get() = process.isAlive

    /** A new page (a tab in its own hidden window), ready to drive. */
    fun newTarget(): String {
        val r = browser.callSync("Target.createTarget", json("url" to "about:blank", "newWindow" to true, "background" to true))
        HiddenWindows.hide(process)
        return r.getString("targetId")
    }
    fun pageSocketUrl(targetId: String) = "ws://127.0.0.1:$port/devtools/page/$targetId"
    fun closeTarget(id: String) = browser.fire("Target.closeTarget", json("targetId" to id))

    // ---- cookies (the whole profile's, as CookieManager)
    fun cookies(): JSONArray = browser.callSync("Storage.getCookies").optJSONArray("cookies") ?: JSONArray()
    fun setCookies(list: JSONArray) { browser.callSync("Storage.setCookies", json("cookies" to list)) }
    fun clearCookies() { browser.callSync("Storage.clearCookies") }
    fun clearData() {
        runCatching { clearCookies() }
        // Each origin the browser stored something for: cleared (local storage, IndexedDB, caches...).
        runCatching {
            val origins = cookies().let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("domain").trimStart('.') } }.toSet()
            for (o in origins) browser.fire("Storage.clearDataForOrigin", json("origin" to "https://$o", "storageTypes" to "all"))
        }
    }

    fun quit() {
        runCatching { browser.callSync("Browser.close", ms = 3000) }
        runCatching { process.waitFor(3, TimeUnit.SECONDS) }
        runCatching { process.toHandle().descendants().forEach { it.destroy() } }
        runCatching { process.destroy() }
    }

    companion object {
        private val running = ConcurrentHashMap<String, Edge>()
        private val starting = ConcurrentHashMap<String, Any>()
        const val DEFAULT = "Default"

        fun profileDir(profile: String): File = File(Desk.dataDir, "web/" + profile.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        fun profiles(): List<String> = File(Desk.dataDir, "web").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty().ifEmpty { listOf(DEFAULT) }

        /** The browser of [profile], started if need be (blocks up to 30 s: never on the UI thread for a cold start). */
        fun of(profile: String = DEFAULT): Edge {
            running[profile]?.takeIf { it.alive && !it.browser.closed }?.let { return it }
            synchronized(starting.getOrPut(profile) { Any() }) {
                running[profile]?.takeIf { it.alive && !it.browser.closed }?.let { return it }
                return start(profile).also { running[profile] = it }
            }
        }
        fun runningOrNull(profile: String): Edge? = running[profile]
        fun stop(profile: String) { running.remove(profile)?.quit() }
        fun stopAll() { running.keys.toList().forEach { stop(it) } }

        /** Edge, Chrome or Chromium on this PC, or null. */
        fun find(): File? {
            System.getProperty("jam.browser")?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
            val env = { k: String -> System.getenv(k).orEmpty() }
            val candidates = if (Desk.isWindows) listOf(
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

        private fun start(profile: String): Edge {
            val exe = find() ?: throw IllegalStateException("Microsoft Edge was not found")
            val port = ServerSocket(0).use { it.localPort }
            val dir = profileDir(profile).apply { mkdirs() }
            val headless = System.getProperty("jam.headless") != null || !Desk.isWindows && System.getenv("DISPLAY").isNullOrEmpty()
            val args = mutableListOf(
                exe.absolutePath,
                "--remote-debugging-port=$port",
                "--remote-allow-origins=*",
                "--user-data-dir=${dir.absolutePath}",
                "--no-first-run", "--no-default-browser-check", "--disable-sync", "--disable-extensions",
                "--autoplay-policy=no-user-gesture-required",
                // Scrollbars over the content, as on a phone (the app's pages are made for one).
                "--enable-features=OverlayScrollbar",
                // Out of sight is not paused: no throttling of the hidden windows' pages and timers.
                "--disable-background-timer-throttling", "--disable-backgrounding-occluded-windows", "--disable-renderer-backgrounding",
                "--disable-features=CalculateNativeWinOcclusion,IntensiveWakeUpThrottling,msEdgeSidebarV2,msHubApps,EdgeCollections,msUndersideButton,msShowFirstRunPage,Translate,msEdgeShopping,msImplicitSignin",
                "--window-position=-32000,-32000", "--window-size=1280,800",
                "--hide-crash-restore-bubble", "--noerrdialogs", "--disable-session-crashed-bubble",
                "about:blank",
            )
            if (headless) args.add(1, "--headless=new")
            val log = File(Desk.dataDir, "browser-$profile.log")
            val process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start()
            val end = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < end) {
                if (!process.isAlive) throw IllegalStateException("the browser closed at once (see ${log.name})")
                val up = runCatching { Cdp.http.newCall(Request.Builder().url("http://127.0.0.1:$port/json/version").build()).execute().use { it.isSuccessful } }.getOrDefault(false)
                if (up) {
                    HiddenWindows.hide(process)
                    // The first window (about:blank) is not a page of the app: closed once a page of ours exists.
                    return Edge(profile, process, port).also { Desk.log('I', "Edge", "$profile: ${it.version} on port $port", null) }
                }
                Thread.sleep(200)
            }
            process.destroy(); throw IllegalStateException("the browser did not open its DevTools port")
        }

        init { Runtime.getRuntime().addShutdownHook(Thread { stopAll() }) }
    }
}
