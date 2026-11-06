package app.jam.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/** A WebView that keeps playing when Jam is in the background or the screen is off. */
@SuppressLint("ViewConstructor")
class JamWebView(context: Context, val sourceId: String) : WebView(context) {
    var keepAwake = true
    var clearHistoryOnLoad = false
    var failedUrl: String? = null
    /** A main-frame error on an offline-capable app, waiting to see if its service worker answered. */
    var errorCheck: Runnable? = null

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Leaving the app hides the window; don't let the page find out, or it may stop the music.
        if (!keepAwake) super.onWindowVisibilityChanged(visibility)
        else if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

/**
 * Jam's one screen: the source bar on top, and under it either YouTube Music (the engine's own
 * webview, created by Tauri) or one of the web sources. Extends Tauri's activity, so the YouTube
 * Music engine starts with the app and its webview lands here through [setContentView].
 */
class MainActivity : TauriActivity() {
    companion object {
        private const val TAG = "Jam"
        private const val BG = 0xFF0F0F13.toInt()
        private const val OFFLINE = "file:///android_asset/offline.html"
        /**
         * Whether the page on screen is the site, answered by its service worker, rather than
         * WebView's error page: "served", "loading" (nothing to judge yet) or "error".
         */
        private const val SERVED_PROBE = "(function(){try{" +
            "if(!/^https?:\$/.test(location.protocol))return 'error';" +
            "if(document.readyState==='loading')return 'loading';" +
            "var t=document.body?document.body.innerText:'';" +
            "if(navigator.serviceWorker&&navigator.serviceWorker.controller&&t.indexOf('net::ERR_')<0)return 'served';" +
            "return t?'error':'loading';}catch(e){return 'error'}})()"
        var instance: MainActivity? = null
            private set

        fun profileName(id: String) = "src_$id"
        val profilesSupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

        /** Sign out of a source: forget its cookies and site data. */
        fun clearSourceData(context: Context, id: String) {
            instance?.discard(id)
            if (profilesSupported) {
                ProfileStore.getInstance().getProfile(profileName(id))?.let { p ->
                    p.cookieManager.removeAllCookies(null)
                    p.webStorage.deleteAllData()
                }
            } else {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            }
            instance?.let { if (it.active == id) { it.active = null; it.switchSource(id) } }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var container: FrameLayout
    private val views = LinkedHashMap<String, JamWebView>()
    private val discardTimers = HashMap<String, Runnable>()
    var active: String? = null
        private set
    private var sourcesSig = ""
    private var hook = ""
    private var keepAlive = ""
    private var desktopJs = ""
    private var installedAppJs = ""
    private var mediaSessionJs = ""
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var popup: Dialog? = null
    private var lastHttpToast = 0L
    /** Saved state (SavedState.kt): shown for web apps that work offline. */
    private lateinit var savedButton: ImageButton
    private var savedWait: View? = null
    /** YouTube Music's webview (Tauri's), once Tauri hands it over. */
    private var engineView: WebView? = null
    private var pendingLink: String? = null
    private lateinit var bar: View

    // Back is ours: the source bar, web history and "leave the app playing" all go through goBack().
    override val handleBackNavigation: Boolean = false

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // ------------------------------------------------------------------ local music folder

    private var folderCallback: ((String?, String?) -> Unit)? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) folderPicker.launch(null)
        else finishFolder(null, "Jam needs permission to read your music to add a folder.")
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) finishFolder(null, null)
        else {
            val path = treeToPath(uri)
            if (path == null) finishFolder(null, "Pick a folder on your phone's storage or SD card.")
            else finishFolder(path, null)
        }
    }

    /** YouTube Music ▸ Local files: permission to read audio, then the system folder chooser. */
    fun pickMusicFolder(cb: (path: String?, error: String?) -> Unit) {
        folderCallback?.invoke(null, null)
        folderCallback = cb
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) folderPicker.launch(null)
        else audioPermission.launch(perm)
    }

    private fun finishFolder(path: String?, error: String?) {
        val cb = folderCallback ?: return
        folderCallback = null
        cb(path, error)
    }

    /**
     * A folder from the system chooser as a file path the engine can read (with the audio
     * permission, Android serves media files under these paths). Internal storage and SD cards;
     * anything else (cloud providers) has no path.
     */
    private fun treeToPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val rel = id.substringAfter(':', "").trim('/')
        val root = if (volume.equals("primary", true)) android.os.Environment.getExternalStorageDirectory().absolutePath
            else "/storage/$volume"
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    // ================================================================== lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        hook = assets.open("jam-hook.js").bufferedReader().readText()
        keepAlive = assets.open("keepalive.js").bufferedReader().readText()
        desktopJs = assets.open("desktop-site.js").bufferedReader().readText()
        installedAppJs = assets.open("installed-app.js").bufferedReader().readText()
        mediaSessionJs = assets.open("mediasession.js").bufferedReader().readText()
        cleanUpProfiles()
        buildUi()
        Player.controller = { action -> control(action) }
        onBackPressedDispatcher.addCallback(this) { goBack() }

        sourcesSig = signature()
        val wanted = intent?.getStringExtra("source")
        val start = when {
            wanted != null && Sources.exists(wanted) -> wanted
            Prefs.bool("openLastSource") && Sources.exists(Prefs.str("lastSource")) -> Prefs.str("lastSource")
            Sources.exists(Prefs.str("defaultSource")) -> Prefs.str("defaultSource")
            else -> "octave"
        }
        // A saved state being made (Jam just restarted for it): nothing may open the site's data
        // until it's done.
        SavedState.busy?.let { id ->
            savedWait = TextView(this).apply {
                text = "Saving ${if (Sources.exists(id)) Sources.byId(id).name else "the site"} as it is now…"
                setTextColor(0xFFB8B8C6.toInt()); textSize = 15f; gravity = Gravity.CENTER
            }.also { container.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
            progress.isIndeterminate = true; progress.visibility = View.VISIBLE
        }
        val intentNow = intent
        SavedState.whenReady {
            savedWait?.let { container.removeView(it); savedWait = null; progress.isIndeterminate = false; progress.visibility = View.INVISIBLE }
            Prefs.secret("savedJustMade")?.let { id ->
                Prefs.setSecretNow("savedJustMade", null)
                val err = SavedState.lastError(id)
                Toast.makeText(this, if (err != null) "Couldn't save: $err" else "Saved. ${Sources.byId(id).name} now opens your saved state.", Toast.LENGTH_LONG).show()
            }
            switchSource(start)
            handleLink(intentNow)
        }
        askForNotifications()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("source")?.let { if (Sources.exists(it)) switchSource(it) }
        handleLink(intent)
    }

    /** A music.youtube.com link opened with Jam: YouTube Music plays it (the SPA's `open-link`). */
    private fun handleLink(intent: Intent?) {
        val url = intent?.data?.takeIf { it.host == "music.youtube.com" }?.toString() ?: return
        intent.data = null
        switchSource(Sources.YTM)
        pendingLink = url
        deliverLink()
    }

    private fun deliverLink() {
        val url = pendingLink ?: return
        val wv = engineView ?: return
        pendingLink = null
        // Give a cold-started SPA a moment to mount and subscribe before the event goes out.
        main.postDelayed({
            wv.evaluateJavascript(
                "window.__TAURI_INTERNALS__&&window.__TAURI_INTERNALS__.invoke('plugin:event|emit',{event:'open-link',payload:[${JSONObject.quote(url)}]})",
                null,
            )
        }, if (JamPlugin.engineReady) 300 else 2500)
    }

    // ================================================================== YouTube Music (engine)

    /**
     * Tauri sets its webview as the whole content view; Jam puts it under the source bar instead.
     * Everything else (our own root) goes through as usual.
     */
    override fun setContentView(view: View?) {
        if (view is WebView && view !is JamWebView) {
            attachEngineView(view)
            return
        }
        super.setContentView(view)
    }

    private fun attachEngineView(wv: WebView) {
        engineView = wv
        if (!::container.isInitialized) {
            main.post { attachEngineView(wv) }
            return
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.setBackgroundColor(BG)
        container.addView(wv, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wv.visibility = if (active == Sources.YTM) View.VISIBLE else View.GONE
        if (active == Sources.YTM) wv.requestFocus()
        engineLoading?.let { container.removeView(it) }
        engineLoading = null
        deliverLink()
        updateEngineVisibility()
    }

    private var engineLoading: View? = null

    private fun showEngineLoading() {
        if (engineView != null || engineLoading != null) return
        engineLoading = ProgressBar(this).apply { isIndeterminate = true }
        container.addView(engineLoading, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
    }

    /** Theater mode hides Jam's bar too (JamPlugin.setFullscreen). */
    fun setChromeHidden(hidden: Boolean) {
        bar.visibility = if (hidden) View.GONE else View.VISIBLE
        progress.visibility = if (hidden) View.GONE else progress.visibility
    }

    override fun onResume() {
        super.onResume()
        reconcileSources()
    }

    private var started = false
    private var engineShown: Boolean? = null

    override fun onStart() {
        super.onStart()
        started = true
        updateEngineVisibility()
    }

    override fun onStop() {
        started = false
        updateEngineVisibility()
        super.onStop()
    }

    /**
     * Tells YouTube Music's UI when nobody can see it (Jam in the background, or another source on
     * screen), the same `ui-visible` signal desktop sends from the tray, so it stops restyling for
     * nobody. Playback carries on regardless: it's the engine's, not the page's.
     */
    private fun updateEngineVisibility() {
        val wv = engineView ?: return
        val shown = started && active == Sources.YTM
        if (shown == engineShown) return
        engineShown = shown
        wv.evaluateJavascript(
            "window.__TAURI_INTERNALS__&&window.__TAURI_INTERNALS__.invoke('plugin:event|emit',{event:'ui-visible',payload:$shown})",
            null,
        )
    }

    // Deliberately no WebView.onPause()/pauseTimers(): music keeps playing in the background.

    override fun onDestroy() {
        if (instance == this) instance = null
        Player.controller = null
        popup?.dismiss()
        views.values.forEach { it.destroy() }
        views.clear()
        // YouTube Music plays natively and can outlive this screen; a web source can't.
        if (Player.current?.sourceId != Sources.YTM) Player.clear(this)
        super.onDestroy()
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (Prefs.secret("askedNotifications") != null) return
        Prefs.setSecret("askedNotifications", "1")
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ================================================================== UI

    private fun buildUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(2), 0)
        }
        this.bar = bar
        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(chips)
        }
        bar.addView(scroll, LinearLayout.LayoutParams(0, dp(46), 1f))
        savedButton = iconButton(R.drawable.ic_saved, "Saved state") { active?.let { showSavedState(it) } }.apply { visibility = View.GONE }
        bar.addView(savedButton)
        bar.addView(iconButton(R.drawable.ic_refresh, "Reload") { reload() }.apply {
            setOnLongClickListener { goHome(); true }
        })
        bar.addView(iconButton(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.INVISIBLE
        }
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, dp(2)))

        container = FrameLayout(this).apply { setBackgroundColor(BG) }
        root.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
    }

    private fun iconButton(icon: Int, label: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = label
        tooltipText = label
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        setBackgroundResource(tv.resourceId)
        setColorFilter(0xFFB8B8C6.toInt())
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun renderChips() {
        chips.removeAllViews()
        var activeChip: View? = null
        for (s in Sources.all()) {
            val color = runCatching { Color.parseColor(s.color) }.getOrDefault(0xFF8B5CF6.toInt())
            val on = s.id == active
            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setSize(dp(8), dp(8)) }
            val chip = TextView(this).apply {
                text = if (s.offlineApp && SavedState.enabled(s.id)) "${s.name} · Saved" else s.name
                textSize = 14f
                setTextColor(if (on) Color.WHITE else 0xFFB8B8C6.toInt())
                typeface = if (on) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
                compoundDrawablePadding = dp(7)
                setPadding(dp(12), dp(7), dp(14), dp(7))
                setSingleLine(true)
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    if (on) { setColor((color and 0x00FFFFFF) or 0x33000000); setStroke(dp(1), color) }
                    else { setColor(0xFF1C1C24.toInt()); setStroke(dp(1), 0xFF2B2B35.toInt()) }
                }
                contentDescription = if (on) "${s.name}, playing here" else "Switch to ${s.name}"
                setOnClickListener { switchSource(s.id) }
                setOnLongClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)); true }
            }
            chips.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
            if (on) activeChip = chip
            if (on) progress.progressTintList = android.content.res.ColorStateList.valueOf(color)
        }
        activeChip?.let { c -> chips.post { (chips.parent as? HorizontalScrollView)?.smoothScrollTo(maxOf(0, c.left - dp(40)), 0) } }
        val a = active
        val offlineApp = a != null && a != Sources.YTM && Sources.exists(a) && Sources.byId(a).offlineApp && profilesSupported
        savedButton.visibility = if (offlineApp) View.VISIBLE else View.GONE
        if (offlineApp) {
            val on = SavedState.enabled(a!!)
            savedButton.setColorFilter(if (on) 0xFFA78BFA.toInt() else 0xFFB8B8C6.toInt())
            savedButton.contentDescription = if (on) "Saved state, on" else "Saved state"
        }
    }

    // ================================================================== sources

    fun switchSource(id: String) {
        if (!Sources.exists(id)) return
        if (SavedState.busy == id) return
        if (id == active && (views[id] != null || id == Sources.YTM)) return
        val prev = active
        // The source you leave is paused, as on desktop.
        if (prev == Sources.YTM && prev != id) {
            JamPlugin.sendControl("pause")
            engineView?.visibility = View.GONE
        } else if (prev != null && prev != id) views[prev]?.let { v ->
            v.evaluateJavascript(controlJs("pause"), null)
            v.keepAwake = false
            v.visibility = View.GONE
            scheduleDiscard(prev)
        }
        active = id
        Prefs.set("lastSource", id)
        if (id == Sources.YTM) {
            if (prev != id && Player.current?.sourceId != Sources.YTM) Player.clear(this)
            val e = engineView
            if (e != null) { e.visibility = View.VISIBLE; e.requestFocus() } else showEngineLoading()
            progress.visibility = View.INVISIBLE
            renderChips()
            updateEngineVisibility()
            return
        }
        discardTimers.remove(id)?.let { main.removeCallbacks(it) }
        val v = views[id] ?: createView(Sources.byId(id)).also {
            views[id] = it
            container.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        v.keepAwake = true
        v.visibility = View.VISIBLE
        v.requestFocus()
        if (prev != id) Player.clear(this)
        progress.visibility = if (v.progress in 1..99) View.VISIBLE else View.INVISIBLE
        renderChips()
        updateEngineVisibility()
    }

    private fun scheduleDiscard(id: String) {
        discardTimers.remove(id)?.let { main.removeCallbacks(it) }
        val minutes = Prefs.int("keepWarmMinutes")
        if (minutes < 0) return
        if (minutes == 0) { discard(id); return }
        val r = Runnable { discardTimers.remove(id); if (active != id) discard(id) }
        discardTimers[id] = r
        main.postDelayed(r, minutes * 60_000L)
    }

    fun discard(id: String) {
        discardTimers.remove(id)?.let { main.removeCallbacks(it) }
        val v = views.remove(id) ?: return
        container.removeView(v)
        v.destroy()
        if (Player.current?.sourceId == id) Player.clear(this)
    }

    private fun signature(): String = Sources.all().joinToString("|") { "${it.id},${it.url},${it.desktopSite},${it.domains}" }

    /** After Settings: drop pages whose source was removed or whose link changed. */
    private fun reconcileSources() {
        val sig = signature()
        if (sig == sourcesSig) { renderChips(); return }
        sourcesSig = sig
        val all = Sources.all().associateBy { it.id }
        for (id in views.keys.toList()) {
            val s = all[id]
            val v = views[id] ?: continue
            val home = v.tag as? String
            if (s == null || home != s.url + s.desktopSite) discard(id)
        }
        val a = active
        if (a == null || !Sources.exists(a)) { active = null; switchSource(Prefs.str("defaultSource").takeIf { Sources.exists(it) } ?: "octave") }
        else if (a != Sources.YTM && views[a] == null) { active = null; switchSource(a) }
        else renderChips()
    }

    private fun cleanUpProfiles() {
        if (!profilesSupported) return
        val keep = Sources.all().map { profileName(it.id) }.toSet()
        val store = ProfileStore.getInstance()
        for (name in store.allProfileNames) {
            if (name.startsWith("src_c_") && name !in keep) runCatching { store.deleteProfile(name) }
        }
    }

    // ================================================================== controls

    private fun activeView(): JamWebView? = active?.let { views[it] }

    private fun controlJs(action: String) = "(window.__jamControl ? window.__jamControl(${JSONObject.quote(action)}) : false)"

    private fun control(action: String) {
        val id = Player.current?.sourceId ?: active ?: return
        if (id == Sources.YTM) {
            JamPlugin.sendControl(action)
            return
        }
        (views[id] ?: activeView())?.evaluateJavascript(controlJs(action), null)
    }

    private fun reload() {
        if (active == Sources.YTM) { engineView?.reload(); return }
        val v = activeView() ?: return
        val failed = v.failedUrl
        if (failed != null) retry(v) else v.reload()
    }

    private fun goHome() {
        val id = active ?: return
        if (id == Sources.YTM) {
            engineView?.evaluateJavascript("location.hash='';history.pushState(null,'','/');dispatchEvent(new PopStateEvent('popstate'))", null)
            return
        }
        views[id]?.let { it.failedUrl = null; it.loadUrl(Sources.byId(id).url) }
    }

    private fun retry(v: JamWebView) {
        val url = v.failedUrl ?: Sources.byId(v.sourceId).url
        v.failedUrl = null
        v.clearHistoryOnLoad = true
        v.loadUrl(url)
    }

    private fun goBack() {
        if (customView != null) { hideCustomView(); return }
        if (active == Sources.YTM) {
            val e = engineView
            if (e != null && e.canGoBack()) { e.goBack(); return }
            moveTaskToBack(true)
            return
        }
        val v = activeView()
        if (v != null && v.canGoBack() && v.failedUrl == null) { v.goBack(); return }
        // Keep playing: go to the home screen instead of closing.
        moveTaskToBack(true)
    }

    private fun openExternal(uri: Uri) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
        }
    }

    // ================================================================== WebView setup

    private fun online(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun userAgent(desktop: Boolean): String {
        val def = runCatching { WebSettings.getDefaultUserAgent(this) }.getOrDefault("")
        val chrome = Regex("Chrome/(\\d+)").find(def)?.groupValues?.get(1) ?: "130"
        // Look like Chrome rather than an embedded browser, so sign-in pages and players treat Jam normally.
        return if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
        else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.0.0 Mobile Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun configure(wv: WebView, src: Source) {
        val saved = savedMode(src)
        if (profilesSupported) {
            val name = if (saved) SavedState.savedProfile(src.id) else profileName(src.id)
            val profile = ProfileStore.getInstance().getOrCreateProfile(name)
            // The saved state never touches the network: its own pages answer everything, from
            // the site's offline worker, exactly as with no internet.
            if (saved) runCatching { profile.serviceWorkerController.serviceWorkerWebSettings.blockNetworkLoads = true }
            WebViewCompat.setProfile(wv, name)
        }
        wv.setBackgroundColor(BG)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            if (!src.offlineApp) userAgentString = userAgent(src.desktopSite)
            blockNetworkLoads = saved
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, false)
        }
    }

    private fun pageScript(src: Source) = buildString {
        append("if (window === window.top) {\n")
        append(keepAlive).append('\n')
        append(mediaSessionJs).append('\n')
        if (src.desktopSite) append(desktopJs).append('\n')
        if (src.offlineApp) append(installedAppJs).append('\n')
        append(hook)
        append("\n}")
    }

    @SuppressLint("JavascriptInterface", "RequiresFeature")
    private fun createView(src: Source): JamWebView {
        val wv = JamWebView(this, src.id)
        configure(wv, src)
        wv.tag = src.url + src.desktopSite
        wv.addJavascriptInterface(Bridge(src.id), "JamNative")
        val script = pageScript(src)
        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
        wv.webViewClient = Client(src.id, if (docStart) null else script)
        wv.webChromeClient = Chrome(src.id)
        wv.setDownloadListener { url, _, _, _, _ -> openExternal(Uri.parse(url)) }
        wv.loadUrl(src.url)
        return wv
    }

    /** What pages (and Jam's offline page) can call. */
    inner class Bridge(private val sourceId: String) {
        @JavascriptInterface fun nowPlaying(json: String?) {
            main.post { if (sourceId == active) Player.report(this@MainActivity, Sources.byId(sourceId), json) }
        }
        @JavascriptInterface fun retry() { main.post { views[sourceId]?.let { retry(it) } } }
        @JavascriptInterface fun switchTo(id: String) { main.post { switchSource(id) } }
        @JavascriptInterface fun openSettings() { main.post { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) } }
        /** An offline-capable app's service-worker status (assets/installed-app.js), kept for the offline page. */
        @JavascriptInterface fun offlineStatus(json: String?) {
            if (json != null && json.length < 8000 && Sources.exists(sourceId) && Sources.byId(sourceId).offlineApp) {
                Prefs.setSecret("offline.$sourceId", json)
            }
        }
        @JavascriptInterface fun offlineStatusFor(id: String): String = Prefs.secret("offline.$id") ?: ""
        /** This page is a saved state (SavedState.kt): it must behave as offline. */
        @JavascriptInterface fun savedMode(): Boolean = Sources.exists(sourceId) && savedMode(Sources.byId(sourceId))
        @JavascriptInterface fun savedModeFor(id: String): Boolean = Sources.exists(id) && savedMode(Sources.byId(id))
        @JavascriptInterface fun hasSaved(id: String): Boolean = Sources.exists(id) && Sources.byId(id).offlineApp && profilesSupported && SavedState.exists(id)
        @JavascriptInterface fun useSaved(id: String, on: Boolean) { main.post { setSavedMode(id, on) } }
        /** What the app did with its service worker, newest last (the last 40 events), for the offline page. */
        @JavascriptInterface fun offlineLog(json: String?) {
            if (json == null || json.length > 1000) return
            val o = try { JSONObject(json) } catch (e: Exception) { return }
            appendOfflineLog(sourceId, o)
        }
        @JavascriptInterface fun offlineLogFor(id: String): String = Prefs.secret("offlinelog.$id") ?: "[]"
        @JavascriptInterface fun sources(): String =
            JSONArray(Sources.all().map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color) }).toString()
    }

    private fun savedMode(src: Source) = src.offlineApp && profilesSupported && SavedState.enabled(src.id)

    /** Switch a source between its live site and its saved state: a new page in the other profile. */
    private fun setSavedMode(id: String, on: Boolean) {
        if (!Sources.exists(id) || (on && !SavedState.exists(id))) return
        if (SavedState.enabled(id) == on && views[id] != null) return
        SavedState.setEnabled(id, on)
        discard(id)
        if (active == id) { active = null; switchSource(id) } else renderChips()
        val name = Sources.byId(id).name
        Toast.makeText(this, if (on) "$name: your saved state, offline" else "$name: the live site", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedState(id: String) {
        if (!Sources.exists(id)) return
        val src = Sources.byId(id)
        val ctx = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val pad = dp(22)
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0) }
        fun text(t: String, size: Float, color: Int, top: Int = 0) = TextView(ctx).apply {
            text = t; textSize = size; setTextColor(color); setPadding(0, top, 0, 0)
        }
        val has = SavedState.exists(id)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text("Open my saved state", 16f, Color.WHITE))
        labels.addView(text(if (has) "Opens with no internet, exactly as saved. Off: the live site." else "Save a state first.", 13f, 0xFF9B9BAB.toInt(), dp(2)))
        row.addView(labels, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val toggle = android.widget.Switch(ctx).apply {
            isChecked = SavedState.enabled(id)
            isEnabled = has
            contentDescription = "Open my saved state"
        }
        row.addView(toggle)
        box.addView(row)
        val info = if (has) {
            val whenSaved = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(SavedState.savedAt(id)))
            "Saved $whenSaved · ${formatSize(SavedState.size(id))}"
        } else "No saved state yet."
        box.addView(text(info, 13f, 0xFF9B9BAB.toInt(), dp(16)))
        SavedState.lastError(id)?.let { box.addView(text("Last save failed: $it", 13f, 0xFFF87171.toInt(), dp(6))) }
        box.addView(text("Saving keeps everything ${src.name} has on this phone as it is now: its pages, your downloaded tracks and your login. Download the tracks you want first. Jam restarts for a few seconds to save.", 13f, 0xFF9B9BAB.toInt(), dp(12)))

        val dialog = android.app.AlertDialog.Builder(ctx)
            .setTitle("${src.name}: saved state")
            .setView(box)
            .setPositiveButton(if (has) "Save again" else "Save now", null)
            .setNegativeButton("Close", null)
            .apply { if (has) setNeutralButton("Delete", null) }
            .create()
        toggle.setOnCheckedChangeListener { _, on -> dialog.dismiss(); setSavedMode(id, on) }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                confirmSave(id)
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                dialog.dismiss()
                android.app.AlertDialog.Builder(ctx)
                    .setTitle("Delete the saved state?")
                    .setMessage("${src.name} will open the live site. Your live site and its downloads aren't touched. Jam restarts for a moment.")
                    .setPositiveButton("Delete") { _, _ -> SavedState.deleteAndRestart(this, id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun confirmSave(id: String) {
        val src = Sources.byId(id)
        val ctx = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        android.app.AlertDialog.Builder(ctx)
            .setTitle("Save ${src.name} as it is now?")
            .setMessage((if (SavedState.exists(id)) "This replaces your current saved state. " else "") +
                "Jam saves the live site's pages and downloads, then opens ${src.name} from the save from now on. Music stops while Jam restarts.")
            .setPositiveButton("Save") { _, _ ->
                progress.isIndeterminate = true; progress.visibility = View.VISIBLE
                // The live site's page is closed first so its data stops changing.
                discard(id)
                Prefs.setSecretNow("savedJustMade", id)
                SavedState.saveAndRestart(this, id) { err ->
                    if (err != null) {
                        Prefs.setSecretNow("savedJustMade", null)
                        progress.isIndeterminate = false; progress.visibility = View.INVISIBLE
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                        if (active == id) { active = null; switchSource(id) }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatSize(b: Long): String = when {
        b >= 1L shl 30 -> String.format("%.1f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format("%.0f MB", b / (1L shl 20).toDouble())
        else -> "${b / 1024} KB"
    }

    private fun showOffline(v: JamWebView, url: String, desc: String) {
        v.failedUrl = url
        val name = Sources.byId(v.sourceId).name
        val q = "?src=" + Uri.encode(v.sourceId) + "&name=" + Uri.encode(name) + "&err=" + Uri.encode(desc)
        v.loadUrl(OFFLINE + q)
    }

    private fun logOffline(sourceId: String, ev: String, detail: String) {
        appendOfflineLog(sourceId, JSONObject().put("t", System.currentTimeMillis()).put("ev", ev).put("d", detail.take(300)).put("p", "jam"))
    }

    /** An offline-capable app's event log (installed-app.js and Jam's own), the last 40, for the offline page. */
    private fun appendOfflineLog(sourceId: String, o: JSONObject) {
        if (!Sources.exists(sourceId) || !Sources.byId(sourceId).offlineApp) return
        synchronized(offlineLogLock) {
            val arr = try { JSONArray(Prefs.secret("offlinelog.$sourceId") ?: "[]") } catch (e: Exception) { JSONArray() }
            arr.put(o)
            val keep = JSONArray()
            for (i in maxOf(0, arr.length() - 40) until arr.length()) keep.put(arr.get(i))
            Prefs.setSecret("offlinelog.$sourceId", keep.toString())
        }
    }
    private val offlineLogLock = Any()

    private inner class Client(private val sourceId: String, private val fallbackScript: String?) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val url = uri.toString()
            when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    if (!Sources.exists(sourceId)) return false
                    val src = Sources.byId(sourceId)
                    if (Sources.belongsTo(src, url) || Sources.isAuth(url) || !request.isForMainFrame) return false
                    openExternal(uri)
                    return true
                }
                "about", "data", "blob", "javascript" -> return false
                "mailto", "tel", "sms", "market" -> {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                    return true
                }
                "intent" -> {
                    // "Open in the app" links: use the web fallback if it stays on this service, otherwise ignore.
                    val fallback = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }.getOrNull()
                    if (fallback != null && Sources.belongsTo(Sources.byId(sourceId), fallback)) view.loadUrl(fallback)
                    return true
                }
                else -> return true
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            fallbackScript?.let { view.evaluateJavascript(it, null) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val v = view as? JamWebView ?: return
            if (v.clearHistoryOnLoad && url != null && !url.startsWith(OFFLINE)) { v.clearHistoryOnLoad = false; v.clearHistory() }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val v = view as? JamWebView ?: return
            val url = request.url.toString()
            if (url.startsWith("file:")) return
            val desc = error.description?.toString() ?: ""
            Log.w(TAG, "load failed ${error.errorCode} $desc $url")
            if (Sources.exists(sourceId) && Sources.byId(sourceId).offlineApp) {
                // An installed web app's service worker may be answering this page from its offline
                // copy. WebView reports the request a worker sends alongside the page (navigation
                // preload, which Octave's uses) failing as the page's own error, and swapping in the
                // offline page now would cancel the worker's answer. So look before replacing it.
                logOffline(sourceId, "page-error", "$desc $url")
                v.errorCheck?.let { main.removeCallbacks(it) }
                val check = object : Runnable {
                    var tries = 0
                    override fun run() {
                        if (v.errorCheck !== this) return
                        v.evaluateJavascript(SERVED_PROBE) { r ->
                            if (v.errorCheck !== this) return@evaluateJavascript
                            when {
                                r?.contains("served") == true -> {
                                    v.errorCheck = null
                                    logOffline(sourceId, "served-offline", url)
                                }
                                r?.contains("loading") == true && ++tries < 5 -> main.postDelayed(this, 1500)
                                else -> {
                                    v.errorCheck = null
                                    logOffline(sourceId, "not-served", r ?: "")
                                    showOffline(v, url, desc)
                                }
                            }
                        }
                    }
                }
                v.errorCheck = check
                main.postDelayed(check, 1200)
                return
            }
            showOffline(v, url, desc)
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame || response.statusCode < 500 || sourceId != active) return
            // Offline, a web app's own offline page answers 503 (Octave's does): nothing to report.
            if (!online()) return
            val now = System.currentTimeMillis()
            if (now - lastHttpToast < 15_000) return
            lastHttpToast = now
            val src = Sources.byId(sourceId)
            val hint = if (src.offlineApp && SavedState.exists(sourceId)) "Tap the save button above to open your saved state." else "Tap another source above to switch."
            Toast.makeText(this@MainActivity, "${src.name} is having trouble (${response.statusCode}). $hint", Toast.LENGTH_LONG).show()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "renderer gone for $sourceId (crash=${detail.didCrash()})")
            main.post {
                val wasActive = active == sourceId
                discard(sourceId)
                if (wasActive) { active = null; switchSource(sourceId) }
            }
            return true
        }
    }

    private inner class Chrome(private val sourceId: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (sourceId != active) return
            progress.progress = newProgress
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Protected (DRM) playback for Spotify / Apple Music; nothing else.
            val ok = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (ok.isNotEmpty()) request.grant(ok.toTypedArray()) else request.deny()
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            return try { fileLauncher.launch(params.createIntent()); true } catch (e: Exception) { fileCallback = null; false }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            hideCustomView()
            customView = view
            customCallback = callback
            (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onHideCustomView() = hideCustomView()

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!Sources.exists(sourceId)) return false
            openPopup(Sources.byId(sourceId), resultMsg)
            return true
        }
    }

    private fun hideCustomView() {
        val v = customView ?: return
        (window.decorView as ViewGroup).removeView(v)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    /** Sign-in popups (Google, Apple, Discord…) open on top of the page, sharing its login. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun openPopup(src: Source, resultMsg: Message) {
        popup?.dismiss()
        val pop = WebView(this)
        configure(pop, src)
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(4), 0) }
        val title = TextView(this).apply { setTextColor(0xFFECECF1.toInt()); textSize = 15f; setSingleLine(true); text = src.name }
        head.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(iconButton(R.drawable.ic_close, "Close") { dialog.dismiss() })
        box.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        box.addView(pop, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(box) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        pop.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                title.text = Sources.hostOf(url).ifEmpty { src.name }
                // A plain "open in new tab" link to another site: hand it to the browser.
                if (url != null && url.startsWith("http") && !Sources.belongsTo(src, url) && !Sources.isAuth(url)) {
                    view.stopLoading()
                    openExternal(Uri.parse(url))
                    dialog.dismiss()
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme?.lowercase()
                return s != "http" && s != "https" && s != "about" && s != "data" && s != "blob"
            }
        }
        pop.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) { dialog.dismiss() }
        }
        dialog.setContentView(box)
        dialog.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setLayout(MATCH_PARENT, MATCH_PARENT)
        }
        dialog.setOnDismissListener { popup = null; box.removeView(pop); pop.destroy() }
        popup = dialog
        dialog.show()
        (resultMsg.obj as WebView.WebViewTransport).webView = pop
        resultMsg.sendToTarget()
    }
}
