package app.jam.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Channel
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import org.json.JSONObject

@InvokeArg
class HarnessArgs {
    lateinit var label: String
    var html: String? = null
    var js: String? = null
}

@InvokeArg
class UrlArgs {
    lateinit var url: String
    var addAccount: Boolean = false
}

@InvokeArg
class ControlsArgs {
    lateinit var channel: Channel
}

@InvokeArg
class FullscreenArgs {
    var on: Boolean = false
}

/**
 * The YouTube Music engine's bridge into Android (Rust side: `ytm/src-tauri/src/android/`).
 *
 * - Hidden WebViews that run YouTube's player.js for the cipher (desktop: a hidden window).
 * - Google sign-in and session refresh in an Android WebView (desktop: a login window).
 * - What's playing → [Player] (notification, lock screen, Discord); its buttons → the engine.
 */
@TauriPlugin
class JamPlugin(private val activity: Activity) : Plugin(activity) {
    companion object {
        private const val TAG = "JamPlugin"
        @Volatile private var controls: Channel? = null
        /** Waiting for [LoginActivity] to finish. */
        @Volatile var pendingLogin: Invoke? = null

        /** A media button for the engine: play, pause, toggle, nexttrack, previoustrack, seekto. */
        fun sendControl(action: String, position: Double? = null) {
            val c = controls ?: return
            val o = JSObject().put("action", action)
            if (position != null) o.put("position", position)
            try { c.send(o) } catch (e: Exception) { Log.w(TAG, "control failed: ${e.message}") }
        }

        val engineReady: Boolean get() = controls != null

        /** Chrome on Android, minus WebView's "; wv" marker: Google refuses sign-in in embedded browsers. */
        fun mobileUa(webView: WebView): String {
            val chrome = Regex("Chrome/(\\d+)").find(webView.settings.userAgentString)?.groupValues?.get(1) ?: "130"
            return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.0.0 Mobile Safari/537.36"
        }

        fun youtubeCookies(): String = CookieManager.getInstance().getCookie("https://music.youtube.com/") ?: ""
    }

    private val main = Handler(Looper.getMainLooper())
    private val harnesses = HashMap<String, WebView>()

    // ------------------------------------------------------------------ cipher harness

    @SuppressLint("SetJavaScriptEnabled")
    @Command
    fun harnessCreate(invoke: Invoke) {
        val args = invoke.parseArgs(HarnessArgs::class.java)
        main.post {
            harnesses.remove(args.label)?.destroy()
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.settings.allowFileAccess = false
            wv.settings.blockNetworkImage = true
            var answered = false
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (!answered) { answered = true; invoke.resolve() }
                }
            }
            harnesses[args.label] = wv
            wv.loadDataWithBaseURL("https://localhost/", args.html ?: "<!doctype html>", "text/html", "utf-8", null)
            // The Rust side probes the page itself; don't hold it on a page-load event that may not come.
            main.postDelayed({ if (!answered) { answered = true; invoke.resolve() } }, 1500)
        }
    }

    @Command
    fun harnessExists(invoke: Invoke) {
        val args = invoke.parseArgs(HarnessArgs::class.java)
        main.post { invoke.resolve(JSObject().put("exists", harnesses.containsKey(args.label))) }
    }

    @Command
    fun harnessEval(invoke: Invoke) {
        val args = invoke.parseArgs(HarnessArgs::class.java)
        main.post {
            val wv = harnesses[args.label]
            if (wv == null) invoke.reject("webview gone") else { wv.evaluateJavascript(args.js ?: "", null); invoke.resolve() }
        }
    }

    @Command
    fun harnessEvalJson(invoke: Invoke) {
        val args = invoke.parseArgs(HarnessArgs::class.java)
        main.post {
            val wv = harnesses[args.label]
            if (wv == null) invoke.reject("webview gone")
            else wv.evaluateJavascript(args.js ?: "null") { result -> invoke.resolve(JSObject().put("json", result ?: "null")) }
        }
    }

    @Command
    fun harnessDestroy(invoke: Invoke) {
        val args = invoke.parseArgs(HarnessArgs::class.java)
        main.post {
            harnesses.remove(args.label)?.destroy()
            invoke.resolve()
        }
    }

    // ------------------------------------------------------------------ Google sign-in

    @Command
    fun openLogin(invoke: Invoke) {
        val args = invoke.parseArgs(UrlArgs::class.java)
        main.post {
            pendingLogin?.reject("cancelled")
            pendingLogin = invoke
            if (args.addAccount) {
                // A second account starts from a signed-out browser, as on desktop.
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            activity.startActivity(Intent(activity, LoginActivity::class.java).putExtra("url", args.url))
        }
    }

    /** Visit music.youtube.com in a hidden WebView so Google rotates its short-lived cookies. */
    @SuppressLint("SetJavaScriptEnabled")
    @Command
    fun refreshSession(invoke: Invoke) {
        val args = invoke.parseArgs(UrlArgs::class.java)
        main.post {
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.blockNetworkImage = true
            wv.settings.userAgentString = mobileUa(wv)
            var done = false
            fun finish() {
                if (done) return
                done = true
                CookieManager.getInstance().flush()
                invoke.resolve(JSObject().put("cookie", youtubeCookies()))
                wv.stopLoading()
                wv.destroy()
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (Uri.parse(url ?: "").host == "music.youtube.com") main.postDelayed({ finish() }, 1500)
                }
            }
            wv.loadUrl(args.url)
            main.postDelayed({ finish() }, 40_000)
        }
    }

    // ------------------------------------------------------------------ playback

    @Command
    fun registerControls(invoke: Invoke) {
        val args = invoke.parseArgs(ControlsArgs::class.java)
        controls = args.channel
        invoke.resolve()
    }

    @Command
    fun nowPlaying(invoke: Invoke) {
        val o = JSONObject(invoke.getRawArgs())
        invoke.resolve()
        val np = if (!o.optBoolean("hasTrack")) null else NowPlaying(
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            artwork = if (o.isNull("artwork")) null else o.optString("artwork").takeIf { it.startsWith("https://") },
            playing = o.optBoolean("playing"),
            position = o.optDouble("position", 0.0),
            duration = if (o.isNull("duration")) null else o.optDouble("duration").takeIf { !it.isNaN() },
            url = o.optString("url").ifEmpty { "https://music.youtube.com/" },
            sourceId = Sources.YTM,
            sourceName = "YouTube Music",
        )
        Player.reportEngine(activity.applicationContext, np)
    }

    // ------------------------------------------------------------------ misc

    @Command
    fun openExternal(invoke: Invoke) {
        val args = invoke.parseArgs(UrlArgs::class.java)
        main.post {
            try {
                CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(args.url))
            } catch (e: Exception) {
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.url))) }
            }
            invoke.resolve()
        }
    }

    /** Local music: access to audio files, then Android's folder chooser; answers `{ path }`. */
    @Command
    fun pickMusicFolder(invoke: Invoke) {
        main.post {
            val act = activity as? MainActivity
            if (act == null) { invoke.reject("Jam's window isn't open"); return@post }
            act.pickMusicFolder { path, error ->
                if (error != null) invoke.reject(error)
                else invoke.resolve(JSObject().put("path", path ?: JSONObject.NULL))
            }
        }
    }

    /** Theater mode: hide the status and navigation bars. */
    @Command
    fun setFullscreen(invoke: Invoke) {
        val args = invoke.parseArgs(FullscreenArgs::class.java)
        main.post {
            val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            if (args.on) {
                c.hide(WindowInsetsCompat.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else c.show(WindowInsetsCompat.Type.systemBars())
            (activity as? MainActivity)?.setChromeHidden(args.on)
            invoke.resolve()
        }
    }
}
