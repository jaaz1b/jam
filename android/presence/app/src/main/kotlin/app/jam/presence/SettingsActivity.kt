package app.jam.presence

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject

/** The app's one screen: a local page (assets/settings.html, Jam's Discord settings) talking to the app through `Jam`. */
class SettingsActivity : Activity() {
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
        web.settings.allowFileAccess = false
        web.addJavascriptInterface(Bridge(), "Jam")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString().startsWith("file:///android_asset/")) return false
                openExternal(request.url.toString())
                return true
            }
        }
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF131318.toInt()) }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(frame)
        load(intent)

        Discord.onStateChanged = { emit("status", status()); emit("setup", setup()) }
        Player.onChanged = { emit("preview", preview()); emit("players", players()) }
    }

    private fun load(i: Intent?) {
        val section = i?.getStringExtra("section")
        web.loadUrl("file:///android_asset/settings.html" + (section?.let { "?section=" + Uri.encode(it) } ?: ""))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("section")?.let { s -> web.evaluateJavascript("window.__jamGoto && window.__jamGoto(${JSONObject.quote(s)})", null) }
    }

    override fun onResume() {
        super.onResume()
        // Back from Android's settings: notification access, notifications or battery may have changed.
        KeepAliveService.sync(this)
        emit("setup", setup())
        emit("status", status())
        emit("preview", preview())
        emit("players", players())
    }

    override fun onDestroy() {
        Discord.onStateChanged = null
        Player.onChanged = null
        web.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        emit("setup", setup())
        KeepAliveService.sync(this)
    }

    private fun emit(name: String, payload: Any) {
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

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun batteryFree(): Boolean = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun setup(): JSONObject = JSONObject()
        .put("access", MediaListener.allowed(this))
        .put("notifications", notificationsAllowed())
        .put("battery", batteryFree())
        .put("appleMusic", Players.installed(this, Prefs.APPLE_MUSIC))
        .put("discord", Discord.state == Discord.State.CONNECTED)
        .put("signedIn", Prefs.secret("discord.access") != null)

    private fun players(): JSONObject {
        val enabled = Prefs.players()
        val active = MediaListener.activePlayers()
        val pkgs = LinkedHashSet<String>()
        pkgs += Prefs.APPLE_MUSIC
        pkgs += enabled
        pkgs += Prefs.seen()
        pkgs += active
        Players.SUGGESTED.filter { Players.installed(this, it) }.forEach { pkgs += it }
        val list = JSONArray()
        for (p in pkgs) {
            if (p == packageName) continue
            list.put(JSONObject().put("id", p).put("name", Players.name(this, p)).put("enabled", p in enabled)
                .put("installed", Players.installed(this, p)).put("active", p in active)
                .put("showing", Player.current?.sourceId == p))
        }
        return JSONObject().put("players", list).put("access", MediaListener.allowed(this))
            .put("connected", Discord.state == Discord.State.CONNECTED && Presence.build(Player.current) != null)
    }

    private fun preview(): JSONObject {
        val real = Player.current
        val np = real ?: NowPlaying("Neon Coast", "Midnight Arcade", "Afterglow (Deluxe)", null, true, 42.0, 213.0,
            "https://music.apple.com/", Prefs.APPLE_MUSIC, "Apple Music")
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
            .put("appName", Presence.appName(np))
            .put("source", np.sourceName)
            .put("activity", act)
            .put("memberText", member?.let { "Listening to $it" } ?: JSONObject.NULL)
    }

    private fun sanitize(patch: JSONObject): JSONObject {
        val out = JSONObject()
        for (k in patch.keys()) {
            var v = patch.get(k)
            when (k) {
                "discordAppId" -> v = v.toString().filter { it.isDigit() }.take(24)
                "appName", "pausedLabel" -> v = v.toString().take(64)
                "line1", "line2", "line3", "button1Label", "button2Label" -> v = v.toString().take(128)
                "button1Url", "button2Url", "fallbackImage" -> v = v.toString().trim().take(512)
                "progress" -> if (v !in listOf("bar", "elapsed", "off")) continue
                "statusDisplay" -> if (v !in listOf("line1", "line2", "app")) continue
                "players" -> continue // through setPlayer
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
                if (patch.has("lookupCovers")) MediaListener.refresh()
                KeepAliveService.sync(this@SettingsActivity)
                emit("preview", preview())
                emit("setup", setup())
            }
            return Prefs.all().toString()
        }

        @JavascriptInterface fun resetPresence(): String {
            Prefs.resetPresence()
            main.post { Discord.refresh(); MediaListener.refresh(); emit("preview", preview()) }
            return Prefs.all().toString()
        }

        @JavascriptInterface fun getStatus(): String = status().toString()
        @JavascriptInterface fun getPreview(): String = preview().toString()
        @JavascriptInterface fun getSetup(): String = setup().toString()
        @JavascriptInterface fun getPlayers(): String = players().toString()

        @JavascriptInterface fun setPlayer(id: String, on: Boolean) {
            Prefs.setPlayer(id, on)
            main.post { MediaListener.refresh(); emit("players", players()); emit("preview", preview()) }
        }

        /** Android's notification-access page, at this app when the phone can. */
        @JavascriptInterface fun openAccess() {
            main.post {
                val cn = ComponentName(this@SettingsActivity, MediaListener::class.java)
                val tries = listOfNotNull(
                    if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString()) else null,
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                )
                for (i in tries) if (runCatching { startActivity(i) }.isSuccess) break
            }
        }

        /** App info: where "Allow restricted settings" is, for an app installed from a download. */
        @JavascriptInterface fun openAppInfo() {
            main.post { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } }
        }

        @JavascriptInterface fun askNotifications() {
            main.post {
                if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed()) {
                    val ui = getSharedPreferences("jam_presence_ui", Context.MODE_PRIVATE)
                    // Android's prompt once (and again while it would still show); after that, the app's notification settings.
                    if (!ui.getBoolean("askedNotif", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        ui.edit().putBoolean("askedNotif", true).apply()
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    } else runCatching {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    }
                }
            }
        }

        @SuppressLint("BatteryLife")
        @JavascriptInterface fun askBattery() {
            main.post {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                    .onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
            }
        }

        @JavascriptInterface fun openAppleMusic() {
            main.post {
                val launch = packageManager.getLaunchIntentForPackage(Prefs.APPLE_MUSIC)
                if (launch != null) startActivity(launch)
                else openExternal("https://play.google.com/store/apps/details?id=${Prefs.APPLE_MUSIC}")
            }
        }

        @JavascriptInterface fun discordSignIn(): String {
            if (!Prefs.bool("rpcEnabled")) return "Turn on Rich Presence first."
            return Discord.signIn(this@SettingsActivity) ?: ""
        }

        @JavascriptInterface fun discordSignOut() { main.post { Discord.signOut(); emit("status", status()); emit("setup", setup()) } }

        @JavascriptInterface fun copy(text: String) {
            main.post {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Jam Presence", text))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(this@SettingsActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface fun openExternal(url: String) { if (url.startsWith("https://")) main.post { openExternal(url) } }
        @JavascriptInterface fun close() { main.post { finish() } }
        @JavascriptInterface fun version(): String = runCatching {
            val p = packageManager.getPackageInfo(packageName, 0)
            p.versionName + " (" + (if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()) + ")"
        }.getOrDefault("")
    }
}
