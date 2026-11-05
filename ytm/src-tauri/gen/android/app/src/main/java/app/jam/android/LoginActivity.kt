package app.jam.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.tauri.plugin.JSObject

/**
 * Google sign-in for YouTube Music (desktop: the engine's login window). Lands back on
 * music.youtube.com, then hands the youtube.com cookies to the engine, which signs in with them
 * exactly as on desktop. Shares the app's cookie store, so signing in again later is one tap.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0F0F13.toInt()) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        }
        val title = TextView(this).apply {
            text = "Sign in to YouTube Music"
            textSize = 16f
            setTextColor(0xFFECECF1.toInt())
        }
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(0xFFB8B8C6.toInt())
            background = null
            contentDescription = "Close"
            setOnClickListener { cancel() }
        }, LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()))
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, (56 * dp).toInt()))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, (2 * dp).toInt()))

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = JamPlugin.mobileUa(web)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet())
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme?.lowercase()
                return s != "http" && s != "https"
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                title.text = Uri.parse(url ?: "").host ?: "Sign in"
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (Uri.parse(url ?: "").host == "music.youtube.com") waitForCookies(0)
            }
        }
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else cancel() }
        web.loadUrl(intent.getStringExtra("url") ?: "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/")
    }

    /** The redirect sets the cookies; they can land a moment after the page does. */
    private fun waitForCookies(attempt: Int) {
        if (finished) return
        CookieManager.getInstance().flush()
        val cookie = JamPlugin.youtubeCookies()
        if (cookie.contains("SAPISID=") || attempt >= 8) {
            finished = true
            JamPlugin.pendingLogin?.resolve(JSObject().put("cookie", cookie))
            JamPlugin.pendingLogin = null
            finish()
        } else {
            main.postDelayed({ waitForCookies(attempt + 1) }, 500)
        }
    }

    private fun cancel() {
        if (!finished) {
            finished = true
            JamPlugin.pendingLogin?.reject("cancelled")
            JamPlugin.pendingLogin = null
        }
        finish()
    }

    override fun onDestroy() {
        if (!finished) {
            JamPlugin.pendingLogin?.reject("cancelled")
            JamPlugin.pendingLogin = null
        }
        web.destroy()
        super.onDestroy()
    }
}
