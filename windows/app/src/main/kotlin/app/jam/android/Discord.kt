package app.jam.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Jam for Windows' Discord Rich Presence: through the Discord app on this PC (its local IPC, the
 * named pipe \\.\pipe\discord-ipc-N), as Discord's desktop presence works and as Jam's other PC app
 * did — no sign-in. (Jam for Android signs in with Discord's OAuth page and the Social SDK; on the
 * PC that page never loaded the application, "…", and stayed loading after Authorize.)
 * Same API as Android's Discord object: Settings, Presence and Player use it unchanged.
 */
object Discord {
    const val REDIRECT_URI = "jamdiscord://oauth2/callback"
    private const val MIN_GAP_MS = 2000L
    private const val RETRY_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    enum class State { SIGNED_OUT, CONNECTING, CONNECTED, ERROR, DISABLED, NO_APP_ID }
    @Volatile var state: State = State.SIGNED_OUT
        private set
    @Volatile var lastError: String? = null
        private set
    var onStateChanged: (() -> Unit)? = null
    @Volatile private var user: String? = null
    fun username(): String? = user

    private fun appId(): String = Prefs.str("discordAppId").filter { it.isDigit() }
    private fun enabled(): Boolean = Prefs.bool("rpcEnabled")

    fun init(context: Context) {
        app = context.applicationContext
        main.post { reconnect() }
    }

    /** Nothing to sign in to on the PC: Jam connects to the Discord app. Null = no error to show. */
    fun signIn(context: Context): String? {
        if (appId().isEmpty()) return "Enter your Discord Application ID first."
        reconnect()
        return null
    }

    fun onCallback(uri: Uri) { reconnect() }

    fun signOut() { close(); setState(State.SIGNED_OUT) }

    private fun setState(s: State, error: String? = null) {
        state = s; lastError = error
        main.post { onStateChanged?.invoke() }
    }

    // ---------------------------------------------------------------------------------- the pipe

    private var pipe: RandomAccessFile? = null
    private var connectedId: String? = null
    private val retry: Runnable = Runnable { reconnect() }

    fun reconnect(): Unit { main.post { reconnectNow() } }
    private fun reconnectNow() {
        main.removeCallbacks(retry)
        when {
            !enabled() -> { close(); setState(State.DISABLED); return }
            appId().isEmpty() -> { close(); setState(State.NO_APP_ID); return }
        }
        if (pipe != null && connectedId == appId()) { lastKey = null; flush(); return }
        close()
        setState(State.CONNECTING)
        Thread({ connect(appId()) }, "jam-discord-connect").apply { isDaemon = true; start() }
    }

    private fun connect(id: String) {
        val p = (0..9).firstNotNullOfOrNull { i -> runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() }
        if (p == null) {
            main.post {
                setState(State.ERROR, "Open the Discord app on this PC: Jam shows your status through it.")
                main.postDelayed(retry, RETRY_MS)
            }
            return
        }
        runCatching { synchronized(io) { p.write(frame(0, JSONObject().put("v", 1).put("client_id", id).toString())) } }.onFailure {
            runCatching { p.close() }
            main.post { setState(State.ERROR, "Discord did not answer: ${it.message}"); main.postDelayed(retry, RETRY_MS) }
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
                        main.post { if (pipe === p) { close(); setState(State.ERROR, "Discord refused the connection: ${m.optString("message").ifBlank { "check the Application ID" }}") } }
                        return
                    }
                    m.optString("evt") == "READY" -> {
                        val u = m.optJSONObject("data")?.optJSONObject("user")
                        user = u?.optString("global_name")?.takeIf { it.isNotBlank() && it != "null" } ?: u?.optString("username")
                        main.post { if (pipe === p) { setState(State.CONNECTED); lastKey = null; flush() } }
                    }
                    m.optString("evt") == "ERROR" -> main.post { lastError = m.optJSONObject("data")?.optString("message") }
                }
            }
        } catch (_: Exception) {}
        main.post {
            if (pipe === p) { close(); setState(State.ERROR, "Discord closed. Jam reconnects when it's open again."); main.postDelayed(retry, RETRY_MS) }
        }
    }

    private fun close() {
        runCatching { pipe?.close() }
        pipe = null; connectedId = null
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

    fun onNowPlaying(np: NowPlaying?) = push(Presence.build(np))

    /** Settings changed: rebuild and resend. */
    fun refresh() { lastKey = null; push(Presence.build(Player.current)) }

    private fun push(a: RichActivity?) { main.post { pushNow(a) } }
    private fun pushNow(a: RichActivity?) {
        desired = a
        if (state != State.CONNECTED) return
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
        if (state != State.CONNECTED) return
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
            close(); setState(State.ERROR, "Discord closed. Jam reconnects when it's open again."); main.postDelayed(retry, RETRY_MS)
        }
    }
}
