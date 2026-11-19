package app.jam.desktop

import app.jam.android.Prefs
import app.jam.android.RichActivity

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The fallback of Jam for Windows' Discord presence: when Jam is not signed in to Discord (the
 * Social SDK sign-in, Discord.kt, as on Android — which also works with Discord in a browser), the
 * Discord app on this PC shows it through its local IPC (\\.\pipe\discord-ipc-N), no sign-in.
 * Off while the signed-in presence runs, so there's never two.
 */
object DiscordIpc {
    private const val MIN_GAP_MS = 2000L
    private const val RETRY_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var connected = false
    @Volatile private var user: String? = null
    fun username(): String? = user?.takeIf { connected }

    private fun appId(): String = Prefs.str("discordAppId").filter { it.isDigit() }
    private fun enabled(): Boolean = Prefs.bool("rpcEnabled")
    /** The signed-in presence has it: this one stays out (and clears what it set). */
    @Volatile private var standby = false

    /** What should show, and whether the signed-in presence is showing it already. */
    fun update(a: RichActivity?, signedIn: Boolean) = main.post {
        standby = signedIn
        if (signedIn || !enabled() || appId().isEmpty()) {
            if (pipe != null) { desired = null; lastKey = null; flush(); close() }
            main.removeCallbacks(retry)
            return@post
        }
        desired = a
        if (pipe == null) reconnectNow() else pushNow(a)
    }

    // ---------------------------------------------------------------------------------- the pipe

    private var pipe: RandomAccessFile? = null
    private var connectedId: String? = null
    private val retry: Runnable = Runnable { reconnectNow() }

    private fun reconnectNow() {
        main.removeCallbacks(retry)
        if (standby || !enabled() || appId().isEmpty()) return
        if (pipe != null && connectedId == appId()) { lastKey = null; flush(); return }
        close()
        Thread({ connect(appId()) }, "jam-discord-connect").apply { isDaemon = true; start() }
    }

    private fun connect(id: String) {
        val p = (0..9).firstNotNullOfOrNull { i -> runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() }
        if (p == null) {
            main.post { main.postDelayed(retry, RETRY_MS) }
            return
        }
        runCatching { synchronized(io) { p.write(frame(0, JSONObject().put("v", 1).put("client_id", id).toString())) } }.onFailure {
            runCatching { p.close() }
            main.post { main.postDelayed(retry, RETRY_MS) }
            return
        }
        main.post { pipe = p; connectedId = id }
        // Discord's answers (READY with the user, errors), read on the side so the pipe never fills.
        Thread({ read(p) }, "jam-discord-read").apply { isDaemon = true; start() }
    }

    /**
     * A named pipe opened like a file is synchronous: a read waiting for Discord would hold up
     * every write. So reads happen only when bytes are there (a pipe's length is what it holds),
     * and reads and writes take turns ([io]).
     */
    private val io = Any()

    private fun read(p: RandomAccessFile) {
        val head = ByteArray(8)
        try {
            while (true) {
                val ready = synchronized(io) { p.length() >= 8 }
                if (!ready) { Thread.sleep(120); continue }
                synchronized(io) { p.readFully(head) }
                val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
                val op = bb.int; val len = bb.int
                if (len < 0 || len > 1 shl 20) break
                val body = ByteArray(len).also { synchronized(io) { p.readFully(it) } }
                val m = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull() ?: continue
                when {
                    op == 2 -> { // CLOSE: a wrong application ID, mostly
                        main.post { if (pipe === p) close() }
                        return
                    }
                    m.optString("evt") == "READY" -> {
                        val u = m.optJSONObject("data")?.optJSONObject("user")
                        user = u?.optString("global_name")?.takeIf { it.isNotBlank() && it != "null" } ?: u?.optString("username")
                        main.post { if (pipe === p) { connected = true; lastKey = null; flush() } }
                    }
                    m.optString("evt") == "ERROR" -> Unit
                }
            }
        } catch (_: Exception) {}
        main.post {
            if (pipe === p) { close(); main.postDelayed(retry, RETRY_MS) }
        }
    }

    private fun close() {
        runCatching { pipe?.close() }
        pipe = null; connectedId = null; connected = false
    }

    private fun frame(op: Int, payload: String): ByteArray {
        val data = payload.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(data.size).put(data).array()
    }

    // ------------------------------------------------------------------------------- presence

    @Volatile private var desired: RichActivity? = null
    private var lastKey: String? = null
    private var lastStart: Long? = null
    private var lastSentAt = 0L
    private var pending: Runnable? = null

    private fun pushNow(a: RichActivity?) {
        desired = a
        if (!connected) return
        val key = a?.key() ?: "null"
        val start = a?.startMs
        val seeked = start != null && lastStart != null && kotlin.math.abs(start - lastStart!!) > 3000
        if (key == lastKey && !seeked) return
        if (pending != null) return
        val r = Runnable { pending = null; flush() }
        pending = r
        main.postDelayed(r, maxOf(0L, lastSentAt + MIN_GAP_MS - System.currentTimeMillis()))
    }

    private fun flush() {
        val p = pipe ?: return
        if (!connected) return
        val a = desired
        lastSentAt = System.currentTimeMillis()
        lastKey = a?.key() ?: "null"
        lastStart = a?.startMs
        val args = JSONObject().put("pid", ProcessHandle.current().pid())
        if (a != null) {
            val o = JSONObject().put("type", 2).put("status_display_type", a.statusDisplay)
            if (a.name.isNotBlank()) o.put("name", a.name)
            a.details?.takeIf { it.isNotBlank() }?.let { o.put("details", it.take(128)) }
            a.state?.takeIf { it.isNotBlank() }?.let { o.put("state", it.take(128)) }
            if (a.startMs != null) o.put("timestamps", JSONObject().put("start", a.startMs).apply { a.endMs?.let { put("end", it) } })
            val assets = JSONObject()
            a.largeImage?.let { assets.put("large_image", it) }
            a.largeText?.takeIf { it.isNotBlank() }?.let { assets.put("large_text", it.take(128)) }
            if (assets.length() > 0) o.put("assets", assets)
            if (a.buttons.isNotEmpty()) o.put("buttons", JSONArray(a.buttons.take(2).map { (label, url) -> JSONObject().put("label", label.take(32)).put("url", url) }))
            args.put("activity", o)
        }
        val msg = JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", java.util.UUID.randomUUID().toString())
        runCatching { synchronized(io) { p.write(frame(1, msg.toString())) } }.onFailure {
            close(); main.postDelayed(retry, RETRY_MS)
        }
    }
}
