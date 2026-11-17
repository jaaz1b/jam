package app.jam.desktop

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.jam.desktop.shim.Desk
import app.jam.desktop.shim.DesktopApp
import app.jam.desktop.shim.Windows
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * YouTube Music mode on Windows: Jam's YouTube Music app (jam-ytm.exe) is the window, and Jam runs
 * behind it, without one, for what Jam does in every mode: the Discord presence (Jam's own, with its
 * custom settings, fed by what jam-ytm says it plays), the presence settings and the way back to
 * Spotify mode, both from jam-ytm's tray ("Jam"). Jam quits with it.
 */
object YtmCompanion {
    @Volatile private var ytm: Process? = null
    @Volatile private var exe: java.io.File? = null
    val running: Boolean get() = ytm?.isAlive == true

    /** Jam started again while in this mode: jam-ytm's window comes up (it allows one instance). */
    fun front() { exe?.let { e -> runCatching { ProcessBuilder(e.absolutePath).directory(e.parentFile).start() } } }

    /** Starts jam-ytm with Jam behind it; false when it can't (then Spotify mode opens). */
    fun start(exe: java.io.File): Boolean {
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val server = runCatching { HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8) }.getOrNull() ?: return false
        server.createContext("/") { ex ->
            val ok = ex.requestHeaders.getFirst("X-Jam-Token") == token && ex.requestMethod == "POST"
            val body = runCatching { ex.requestBody.readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
            ex.sendResponseHeaders(if (ok) 204 else 403, -1); ex.close()
            if (ok) when (ex.requestURI.path) {
                "/now-playing" -> nowPlaying(body)
                "/open-settings" -> openSettings()
                "/switch-spotify" -> switchToSpotify()
            }
        }
        server.start()
        val p = runCatching {
            ProcessBuilder(exe.absolutePath).directory(exe.parentFile).apply {
                environment()["JAM_COMPANION"] = "http://127.0.0.1:${server.address.port}"
                environment()["JAM_COMPANION_TOKEN"] = token
            }.start()
        }.getOrElse { Desk.log('E', "YtmCompanion", "jam-ytm.exe did not start", it); server.stop(0); return false }
        ytm = p; this.exe = exe
        Desk.log('I', "YtmCompanion", "YouTube Music mode: jam-ytm.exe started, Jam behind it", null)
        p.onExit().thenRun { if (!switching) { Desk.log('I', "YtmCompanion", "jam-ytm.exe closed: Jam quits", null); quit() } }
        return true
    }

    private fun nowPlaying(body: String) {
        val o = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return
        val np = if (!o.optBoolean("hasTrack")) null else app.jam.android.NowPlaying(
            title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
            artwork = if (o.isNull("artwork")) null else o.optString("artwork").takeIf { it.startsWith("https://") },
            playing = o.optBoolean("playing"), position = o.optDouble("position", 0.0),
            duration = if (o.isNull("duration")) null else o.optDouble("duration").takeIf { !it.isNaN() },
            url = "https://music.youtube.com/", sourceId = app.jam.android.Sources.YTM, sourceName = "YouTube Music",
        )
        Desk.post { app.jam.android.Player.update(DesktopApp.context, np) }
    }

    private fun openSettings() = Desk.post {
        if (Windows.stack.any { it is app.jam.android.SettingsActivity }) return@post
        Windows.openActivity(android.content.Intent(DesktopApp.context, app.jam.android.SettingsActivity::class.java))
    }

    @Volatile private var switching = false
    private fun switchToSpotify() {
        DesktopApp.context.getSharedPreferences("jam_mode", android.content.Context.MODE_PRIVATE).edit().putBoolean("ytm", false).commit()
        stopYtm()
        Restart.now()
    }

    /** jam-ytm closes, and Jam does not quit with it (Jam restarts in Spotify mode). */
    fun stopYtm() {
        switching = true
        ytm?.let { p -> p.toHandle().descendants().forEach { it.destroy() }; p.destroy() }
    }

    /** Jam with no window: the activities it opens (the presence settings), each in a window. */
    fun run() {
        application(exitProcessOnExit = false) {
            for (a in Windows.stack.toList()) androidx.compose.runtime.key(a) {
                val state = rememberWindowState(size = DpSize(520.dp, 820.dp))
                Window(
                    onCloseRequest = { Desk.post { Windows.finish(a) } },
                    state = state, title = "Jam — Discord presence",
                    icon = androidx.compose.ui.res.painterResource("jam.png"),
                ) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { DarkTitleBar.apply(window) }
                    ActivityWindow(a, Modifier)
                }
            }
        }
        // The windows closed: Jam stays behind jam-ytm until it closes.
        while (true) Thread.sleep(60_000)
    }
}
