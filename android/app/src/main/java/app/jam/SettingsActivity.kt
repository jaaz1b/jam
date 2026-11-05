package app.jam

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject

/** Settings: a local page (assets/settings.html, ported from desktop Jam) talking to the app through `Jam`. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        web = WebView(this)
        web.setBackgroundColor(0xFF131318.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.addJavascriptInterface(Bridge(), "Jam")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString().startsWith("file:///android_asset/")) return false
                openExternal(request.url.toString())
                return true
            }
        }
        // Keep the page clear of the status bar / navigation bar / keyboard.
        val frame = android.widget.FrameLayout(this).apply { setBackgroundColor(0xFF131318.toInt()) }
        frame.addView(web, android.widget.FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(frame)
        val section = intent.getStringExtra("section")
        web.loadUrl("file:///android_asset/settings.html" + (section?.let { "?section=" + Uri.encode(it) } ?: ""))
        onBackPressedDispatcher.addCallback(this) { finish() }

        Discord.onStateChanged = { emit("status", status()) }
        Player.onChanged = { emit("preview", preview()) }
    }

    override fun onResume() {
        super.onResume()
        emit("status", status())
        emit("preview", preview())
    }

    override fun onDestroy() {
        Discord.onStateChanged = null
        Player.onChanged = null
        web.destroy()
        super.onDestroy()
    }

    private fun emit(name: String, payload: JSONObject) {
        main.post { web.evaluateJavascript("window.__jamEvent && window.__jamEvent(${JSONObject.quote(name)}, $payload)", null) }
    }

    private fun openExternal(url: String) {
        try { CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url)) }
        catch (e: Exception) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }

    // ------------------------------------------------------------------ data for the page

    private fun status(): JSONObject {
        val st = when (Discord.state) {
            Discord.State.CONNECTED -> "connected"
            Discord.State.CONNECTING -> "connecting"
            Discord.State.ERROR -> "error"
            Discord.State.DISABLED -> "disabled"
            Discord.State.NO_APP_ID -> "no-client-id"
            Discord.State.SIGNED_OUT -> "signed-out"
        }
        return JSONObject().put("state", st)
            .put("user", Discord.username() ?: JSONObject.NULL)
            .put("signedIn", Prefs.secret("discord.access") != null)
            .put("error", if (Discord.state == Discord.State.ERROR) Discord.lastError ?: "" else "")
    }

    private fun preview(): JSONObject {
        val real = Player.current
        val src = Sources.byId(MainActivity.instance?.active ?: Prefs.str("lastSource"))
        val np = real ?: NowPlaying("Neon Coast", "Midnight Arcade", "Afterglow (Deluxe)", null, true, 42.0, 213.0, src.url, src.id, src.name)
        val a = Presence.build(np)
        val act: Any = a?.let { x ->
            JSONObject().put("name", x.name).put("type", 2).apply {
                x.details?.let { put("details", it) }
                x.state?.let { put("state", it) }
                if (x.startMs != null) put("timestamps", JSONObject().put("start", x.startMs).apply { x.endMs?.let { put("end", it) } })
                put("assets", JSONObject().apply { x.largeImage?.let { put("large_image", it) }; x.largeText?.let { put("large_text", it) } })
                if (x.buttons.isNotEmpty()) {
                    put("buttons", JSONArray(x.buttons.map { it.first }))
                    put("metadata", JSONObject().put("button_urls", JSONArray(x.buttons.map { it.second })))
                }
                put("status_display_type", x.statusDisplay)
            }
        } ?: JSONObject.NULL
        val member = a?.let { when (it.statusDisplay) { 2 -> it.details; 1 -> it.state; else -> it.name } ?: it.name }
        return JSONObject()
            .put("enabled", Prefs.bool("rpcEnabled"))
            .put("sample", real == null)
            .put("playing", np.playing)
            .put("appName", Presence.appName())
            .put("activity", act)
            .put("memberText", member?.let { "Listening to $it" } ?: JSONObject.NULL)
    }

    private fun sources(): JSONObject = JSONObject()
        .put("sources", JSONArray(Sources.all().map { Sources.toJson(it, MainActivity.instance?.active) }))
        .put("active", MainActivity.instance?.active ?: JSONObject.NULL)
        .put("palette", JSONArray(Sources.PALETTE))
        .put("max", Sources.MAX_CUSTOM)
        .put("profiles", MainActivity.profilesSupported)

    private fun sanitize(patch: JSONObject): JSONObject {
        val out = JSONObject()
        for (k in patch.keys()) {
            var v = patch.get(k)
            when (k) {
                "discordAppId" -> v = v.toString().filter { it.isDigit() }.take(24)
                "keepWarmMinutes" -> v = (v as? Number)?.toInt()?.coerceIn(-1, 60) ?: v.toString().toIntOrNull()?.coerceIn(-1, 60) ?: 5
                "defaultSource" -> if (!Sources.exists(v.toString())) continue
                "appName", "pausedLabel" -> v = v.toString().take(64)
                "line1", "line2", "line3", "button1Label", "button2Label" -> v = v.toString().take(128)
                "button1Url", "button2Url", "fallbackImage" -> v = v.toString().trim().take(512)
                "progress" -> if (v !in listOf("bar", "elapsed", "off")) continue
                "statusDisplay" -> if (v !in listOf("line1", "line2", "app")) continue
                "customSources", "sourceUrls", "lastSource" -> continue // changed through their own calls
            }
            out.put(k, v)
        }
        return out
    }

    inner class Bridge {
        @JavascriptInterface fun getSettings(): String = Prefs.all().toString()

        @JavascriptInterface fun saveSettings(json: String): String {
            val patch = sanitize(runCatching { JSONObject(json) }.getOrDefault(JSONObject()))
            val before = Prefs.str("discordAppId") to Prefs.bool("rpcEnabled")
            Prefs.set(patch)
            val after = Prefs.str("discordAppId") to Prefs.bool("rpcEnabled")
            main.post {
                if (before.first != after.first) Discord.signOut() // tokens belong to the old application
                if (before != after) Discord.reconnect() else Discord.refresh()
                emit("preview", preview())
            }
            return Prefs.all().toString()
        }

        @JavascriptInterface fun resetPresence(): String {
            Prefs.resetPresence()
            main.post { Discord.refresh(); emit("preview", preview()) }
            return Prefs.all().toString()
        }

        @JavascriptInterface fun getStatus(): String = status().toString()
        @JavascriptInterface fun getPreview(): String = preview().toString()
        @JavascriptInterface fun getSources(): String = sources().toString()

        @JavascriptInterface fun saveSource(json: String): String {
            val err = Sources.save(runCatching { JSONObject(json) }.getOrDefault(JSONObject()))
            return if (err != null) JSONObject().put("error", err).toString()
            else JSONObject().put("payload", sources()).toString()
        }

        @JavascriptInterface fun removeSource(id: String): String {
            Sources.remove(id)
            main.post {
                MainActivity.instance?.discard(id)
                if (MainActivity.profilesSupported) runCatching { androidx.webkit.ProfileStore.getInstance().deleteProfile(MainActivity.profileName(id)) }
            }
            return sources().toString()
        }

        @JavascriptInterface fun resetSourceUrl(id: String): String { Sources.resetUrl(id); return sources().toString() }

        @JavascriptInterface fun clearSource(id: String): String {
            main.post {
                MainActivity.clearSourceData(this@SettingsActivity, id)
                Toast.makeText(this@SettingsActivity, "Signed out of ${Sources.byId(id).name}", Toast.LENGTH_SHORT).show()
                emit("sources", sources())
            }
            return sources().toString()
        }

        @JavascriptInterface fun switchSource(id: String) {
            main.post {
                startActivity(Intent(this@SettingsActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("source", id))
                finish()
            }
        }

        @JavascriptInterface fun discordSignIn(): String {
            if (!Prefs.bool("rpcEnabled")) return "Turn on Rich Presence first."
            return Discord.signIn(this@SettingsActivity) ?: ""
        }

        @JavascriptInterface fun discordSignOut() { main.post { Discord.signOut(); emit("status", status()) } }

        @JavascriptInterface fun copy(text: String) {
            main.post {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Jam", text))
                Toast.makeText(this@SettingsActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface fun openExternal(url: String) { if (url.startsWith("https://")) main.post { openExternal(url) } }
        @JavascriptInterface fun close() { main.post { finish() } }
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
    }
}
