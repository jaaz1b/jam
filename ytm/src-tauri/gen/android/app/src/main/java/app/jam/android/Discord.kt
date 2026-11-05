package app.jam.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Discord Rich Presence on Android, through Discord's Social SDK presence: you sign in with
 * Discord's own OAuth page (PKCE, scope `sdk.social_layer_presence`), and Jam sets your activity
 * over Discord's gateway with that OAuth token. Same approach as Meld/Metrolist (GPL-3.0).
 *
 * Needs a Discord application (yours: the same "Jam" app desktop uses) with:
 *  - OAuth2 → Redirects: jamdiscord://oauth2/callback
 *  - OAuth2 → Public Client: on
 *  - the Social SDK enabled for the app.
 */
object Discord {
    private const val TAG = "JamDiscord"
    const val REDIRECT_URI = "jamdiscord://oauth2/callback"
    private const val SCOPES = "openid sdk.social_layer_presence"
    private const val GATEWAY = "wss://gateway.discord.gg/?v=10&encoding=json"
    private const val MIN_GAP_MS = 4000L

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private lateinit var app: Context

    // ---- state visible to Settings
    enum class State { SIGNED_OUT, CONNECTING, CONNECTED, ERROR, DISABLED, NO_APP_ID }
    @Volatile var state: State = State.SIGNED_OUT
        private set
    @Volatile var lastError: String? = null
        private set
    var onStateChanged: (() -> Unit)? = null
    fun username(): String? = Prefs.secret("discord.user")

    fun init(context: Context) {
        app = context.applicationContext
        main.post { reconnect() }
    }

    private fun setState(s: State, err: String? = null) {
        state = s
        lastError = err
        main.post { onStateChanged?.invoke() }
    }

    private fun appId(): String = Prefs.str("discordAppId").filter { it.isDigit() }

    // ===================================================================== OAuth (PKCE)

    // Kept in storage so sign-in survives Android reclaiming Jam while the browser is open.
    private var pendingVerifier: String?
        get() = Prefs.secret("discord.pkce.verifier")
        set(v) = Prefs.setSecret("discord.pkce.verifier", v)
    private var pendingState: String?
        get() = Prefs.secret("discord.pkce.state")
        set(v) = Prefs.setSecret("discord.pkce.state", v)

    private fun b64url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Opens Discord's sign-in page in a Custom Tab. The result arrives in [onCallback]. */
    fun signIn(context: Context): String? {
        val id = appId()
        if (id.isEmpty()) return "Enter your Discord Application ID first."
        val verifierBytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = b64url(verifierBytes)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val st = b64url(ByteArray(16).also { SecureRandom().nextBytes(it) })
        pendingVerifier = verifier
        pendingState = st
        val url = Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", st)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        return try {
            val intent = CustomTabsIntent.Builder().build().intent
            intent.data = url
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "No browser available to sign in to Discord."
        }
    }

    /** jamdiscord://oauth2/callback?code=…&state=… */
    fun onCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val st = uri.getQueryParameter("state")
        val err = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        val verifier = pendingVerifier
        if (err != null) { setState(State.ERROR, "Discord said: $err"); return }
        if (code == null || verifier == null || st != pendingState) { setState(State.ERROR, "Sign-in didn't complete. Try again."); return }
        pendingVerifier = null; pendingState = null
        setState(State.CONNECTING)
        io.execute {
            val err2 = exchange(FormBody.Builder()
                .add("client_id", appId()).add("grant_type", "authorization_code")
                .add("code", code).add("redirect_uri", REDIRECT_URI).add("code_verifier", verifier).build())
            if (err2 != null) setState(State.ERROR, err2) else main.post { reconnect() }
        }
    }

    /** Token endpoint call; stores the tokens. Returns an error message or null. */
    private fun exchange(form: FormBody): String? = try {
        http.newCall(Request.Builder().url("https://discord.com/api/v10/oauth2/token").post(form).build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val e = runCatching { JSONObject(body) }.getOrNull()
                if (e?.optString("error") == "invalid_grant") { signOutLocal(); "Discord sign-in expired. Sign in again." }
                else "Discord sign-in failed (${r.code}): ${e?.optString("error_description")?.ifEmpty { null } ?: e?.optString("error") ?: body.take(120)}"
            } else {
                val j = JSONObject(body)
                Prefs.setSecret("discord.access", j.getString("access_token"))
                j.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { Prefs.setSecret("discord.refresh", it) }
                Prefs.setSecret("discord.expires", (System.currentTimeMillis() + j.optLong("expires_in", 604800) * 1000).toString())
                null
            }
        }
    } catch (e: Exception) {
        "Couldn't reach Discord: ${e.message}"
    }

    private fun refreshIfNeeded(force: Boolean = false): String? {
        val refresh = Prefs.secret("discord.refresh") ?: return null
        val exp = Prefs.secret("discord.expires")?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() < exp - 24 * 3600 * 1000L) return null
        return exchange(FormBody.Builder().add("client_id", appId()).add("grant_type", "refresh_token").add("refresh_token", refresh).build())
    }

    private fun signOutLocal() {
        Prefs.setSecret("discord.access", null)
        Prefs.setSecret("discord.refresh", null)
        Prefs.setSecret("discord.expires", null)
        Prefs.setSecret("discord.user", null)
    }

    fun signOut() {
        val token = Prefs.secret("discord.access")
        disconnect()
        signOutLocal()
        setState(State.SIGNED_OUT)
        if (token != null) io.execute {
            runCatching {
                http.newCall(Request.Builder().url("https://discord.com/api/v10/oauth2/token/revoke")
                    .post(FormBody.Builder().add("client_id", appId()).add("token", token).build()).build()).execute().close()
            }
        }
    }

    // ===================================================================== gateway

    private var ws: WebSocket? = null
    private var wsGen = 0
    private var seq: Int? = null
    private var heartbeat: Runnable? = null
    private var ready = false
    private var attempts = 0
    private var reconnectPending: Runnable? = null

    /** (Re)connect according to current settings and tokens. Main thread. */
    fun reconnect() {
        disconnect()
        when {
            !Prefs.bool("rpcEnabled") -> setState(State.DISABLED)
            appId().isEmpty() -> setState(State.NO_APP_ID)
            Prefs.secret("discord.access") == null -> setState(State.SIGNED_OUT)
            else -> {
                setState(State.CONNECTING)
                io.execute {
                    val err = refreshIfNeeded()
                    main.post { if (err != null && Prefs.secret("discord.access") == null) setState(State.ERROR, err) else open() }
                }
            }
        }
    }

    private fun open() {
        val gen = ++wsGen
        ws = http.newWebSocket(Request.Builder().url(GATEWAY).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { main.post { if (gen == wsGen) handle(text) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (gen == wsGen) onClosed(-1, t.message ?: "connection failed") }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { main.post { if (gen == wsGen) onClosed(code, reason) } }
        })
    }

    private fun send(op: Int, d: Any?) {
        ws?.send(JSONObject().put("op", op).put("d", d ?: JSONObject.NULL).toString())
    }

    private fun handle(text: String) {
        val m = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (!m.isNull("s")) seq = m.optInt("s")
        when (m.optInt("op")) {
            10 -> { // HELLO
                val interval = m.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                startHeartbeat(interval)
                val d = JSONObject()
                    .put("token", "Bearer " + (Prefs.secret("discord.access") ?: ""))
                    .put("intents", 0)
                    .put("properties", JSONObject().put("os", "android").put("browser", "Discord Android").put("device", appId()))
                    .put("compress", false)
                send(2, d)
            }
            0 -> if (m.optString("t") == "READY") {
                ready = true
                attempts = 0
                m.optJSONObject("d")?.optJSONObject("user")?.let { u ->
                    val global = if (u.isNull("global_name")) "" else u.optString("global_name")
                    Prefs.setSecret("discord.user", global.ifEmpty { u.optString("username") })
                }
                setState(State.CONNECTED)
                lastKey = null
                push(Presence.build(Player.current))
            }
            1 -> send(1, seq) // heartbeat request
            7 -> { disconnect(); scheduleReconnect() } // reconnect request
            9 -> { disconnect(); scheduleReconnect(2000) } // invalid session
        }
    }

    private fun startHeartbeat(interval: Long) {
        heartbeat?.let { main.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() { send(1, seq); main.postDelayed(this, interval) }
        }
        heartbeat = r
        main.postDelayed(r, (interval * Math.random()).toLong())
    }

    private fun onClosed(code: Int, reason: String) {
        Log.w(TAG, "gateway closed $code $reason")
        val wasReady = ready
        disconnect()
        when (code) {
            4004 -> io.execute { // authentication failed: refresh once, else sign out
                val err = refreshIfNeeded(force = true)
                main.post { if (err == null && Prefs.secret("discord.access") != null) open() else { signOutLocal(); setState(State.ERROR, err ?: "Discord sign-in expired. Sign in again.") } }
            }
            4013, 4014 -> setState(State.ERROR, "Discord refused presence for this app ($code). Is the Social SDK enabled for it?")
            else -> { if (!wasReady && attempts >= 3) setState(State.ERROR, "Can't connect to Discord ($code ${reason.take(80)})"); scheduleReconnect() }
        }
    }

    private fun scheduleReconnect(min: Long = 0) {
        reconnectPending?.let { main.removeCallbacks(it) }
        if (!Prefs.bool("rpcEnabled") || Prefs.secret("discord.access") == null) return
        attempts++
        val delay = maxOf(min, minOf(64_000L, 1000L shl minOf(attempts, 6)))
        val r = Runnable { reconnectPending = null; setState(State.CONNECTING); open() }
        reconnectPending = r
        main.postDelayed(r, delay)
    }

    private fun disconnect() {
        wsGen++
        heartbeat?.let { main.removeCallbacks(it) }; heartbeat = null
        reconnectPending?.let { main.removeCallbacks(it) }; reconnectPending = null
        ws?.close(1000, null); ws = null
        ready = false
        seq = null
    }

    // ===================================================================== presence

    private var lastKey: String? = null
    private var lastStart: Long? = null
    private var lastSentAt = 0L
    private var pendingSend: Runnable? = null
    private var desired: RichActivity? = null
    private val assetCache = HashMap<String, String>()

    fun onNowPlaying(np: NowPlaying?) = push(Presence.build(np))

    /** Settings changed: rebuild and resend. */
    fun refresh() { lastKey = null; push(Presence.build(Player.current)) }

    private fun push(a: RichActivity?) {
        desired = a
        if (!ready) return
        val key = a?.key() ?: "null"
        val start = a?.startMs
        val prevStart = lastStart
        val seeked = start != null && prevStart != null && kotlin.math.abs(start - prevStart) > 3000
        if (key == lastKey && !seeked) return
        if (pendingSend != null) return
        val wait = maxOf(0L, lastSentAt + MIN_GAP_MS - System.currentTimeMillis())
        val r = Runnable { pendingSend = null; flush() }
        pendingSend = r
        main.postDelayed(r, wait)
    }

    private fun flush() {
        val a = desired
        lastSentAt = System.currentTimeMillis()
        lastKey = a?.key() ?: "null"
        lastStart = a?.startMs
        if (a == null) { sendPresence(null, null); return }
        val img = a.largeImage
        if (img != null && img.startsWith("https://")) {
            assetCache[img]?.let { sendPresence(a, it); return }
            io.execute {
                val resolved = resolveExternal(img)
                main.post { if (desired == a) sendPresence(a, resolved ?: "jam") }
            }
        } else sendPresence(a, img)
    }

    /** Discord only shows external images through its media proxy (mp:external/…). */
    private fun resolveExternal(url: String): String? = try {
        val body = JSONObject().put("urls", JSONArray().put(url)).toString().toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("https://discord.com/api/v9/applications/${appId()}/external-assets")
            .header("Authorization", "Bearer " + (Prefs.secret("discord.access") ?: ""))
            .post(body).build()).execute().use { r ->
            val txt = r.body?.string() ?: ""
            val path = runCatching { JSONArray(txt).optJSONObject(0)?.optString("external_asset_path") }.getOrNull()
            if (r.isSuccessful && !path.isNullOrEmpty()) ("mp:$path").also { synchronized(assetCache) { assetCache[url] = it } } else null
        }
    } catch (e: Exception) { null }

    private fun sendPresence(a: RichActivity?, image: String?) {
        if (!ready) return
        val activities = JSONArray()
        if (a != null) {
            val o = JSONObject().put("name", a.name).put("type", 2).put("status_display_type", a.statusDisplay)
            a.details?.let { o.put("details", it) }
            a.state?.let { o.put("state", it) }
            if (a.startMs != null) o.put("timestamps", JSONObject().put("start", a.startMs).apply { a.endMs?.let { put("end", it) } })
            if (image != null || a.largeText != null) o.put("assets", JSONObject().apply {
                image?.let { put("large_image", it) }
                a.largeText?.let { put("large_text", it) }
            })
            if (a.buttons.isNotEmpty()) {
                o.put("buttons", JSONArray(a.buttons.map { it.first }))
                o.put("metadata", JSONObject().put("button_urls", JSONArray(a.buttons.map { it.second })))
            }
            activities.put(o)
        }
        send(3, JSONObject().put("since", 0).put("activities", activities).put("status", "online").put("afk", false))
    }
}
