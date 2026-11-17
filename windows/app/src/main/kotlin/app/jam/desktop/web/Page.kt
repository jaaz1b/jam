package app.jam.desktop.web

import android.webkit.WebView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.jam.desktop.shim.Desk
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The pages of the app's web views, by target id (popups of theirs are folded back into them). */
object Pages {
    val io = Executors.newCachedThreadPool { r -> Thread(r, "jam-web").apply { isDaemon = true } }
    private val byTarget = ConcurrentHashMap<String, Page>()
    internal fun register(id: String, p: Page) { byTarget[id] = p }
    internal fun unregister(id: String) { byTarget.remove(id) }
    /** A page of ours opened a window: Android's web view (one window) loads it in place (Page.windowOpen does), this one is closed. */
    fun popup(edge: Edge, info: JSONObject) {
        val opener = info.optString("openerId").ifEmpty { return }
        if (byTarget.containsKey(opener)) edge.closeTarget(info.getString("targetId"))
    }

    /** Runs [block] on the UI thread and waits for its answer (callbacks Android makes on its main thread). */
    fun <T> onMain(ms: Long = 8_000, block: () -> T): T {
        if (Desk.isMain()) return block()
        val f = CompletableFuture<T>()
        Desk.post { runCatching(block).fold({ f.complete(it) }, { f.completeExceptionally(it) }) }
        return f.get(ms, TimeUnit.MILLISECONDS)
    }
}

/**
 * What one android.webkit.WebView is on the PC: a page of the hidden browser, driven over the
 * DevTools protocol, its events turned into the WebViewClient / WebChromeClient calls Android makes,
 * in the same order and on the same threads. Commands given before the page exists wait for it.
 */
class Page(private val view: WebView) {
    @Volatile var profile: String = Edge.DEFAULT
    @Volatile var url: String? = null; private set
    @Volatile var title: String? = null; private set
    @Volatile var progress = 0; private set
    /** The page drawn, while the view is on screen ([screencast]). */
    val frame = mutableStateOf<ImageBitmap?>(null)

    private var cdp: Cdp? = null
    private var targetId: String? = null
    private var starting = false
    private var destroyed = false
    private val waiting = ArrayList<(Cdp) -> Unit>()
    @Volatile private var mainFrame: String? = null
    private val requests = ConcurrentHashMap<String, android.webkit.WebResourceRequest>()
    private val contexts = ConcurrentHashMap<Int, Pair<String, Boolean>>() // context id -> (origin, main frame)
    private val expected = java.util.Collections.synchronizedList(ArrayList<String>())
    private val docScripts = ArrayList<Pair<Int, String>>() // handle -> source
    private val docScriptIds = ConcurrentHashMap<Int, String>()
    private val listeners = ConcurrentHashMap<String, Pair<Set<String>, androidx.webkit.WebViewCompat.WebMessageListener>>()
    private val interfaces = ConcurrentHashMap<String, String>() // name -> token
    private val handles = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var userAgent: String? = null
    @Volatile var noCache = false

    // ---- life
    private fun ensure() {
        if (cdp != null || starting || destroyed) return
        starting = true
        Pages.io.execute { runCatching { connect() }.onFailure { t -> Desk.log('E', "Page", "page could not start: $t", t); starting = false
            Desk.post { view.getWebViewClient()?.onReceivedError(view, DesktopRequest(url ?: "about:blank", "GET", emptyMap(), true), android.webkit.WebResourceError.of(-1, "net::ERR_BROWSER_UNAVAILABLE ${t.message}")) } } }
    }

    private fun connect() {
        val edge = Edge.of(profile)
        val id = edge.newTarget()
        val c = Cdp(edge.pageSocketUrl(id), "page-${id.take(6)}")
        c.awaitOpen()
        c.onEvent = { m, p, _ -> event(m, p) }
        c.onClose = { if (!destroyed) Desk.post { gone(crashed = true) } }
        val setup = listOf(
            c.call("Page.enable"), c.call("Runtime.enable"), c.call("Network.enable", json("maxPostDataSize" to 0)),
            c.call("Inspector.enable"),
            c.call("Page.setLifecycleEventsEnabled", json("enabled" to true)),
            c.call("Fetch.enable", json("patterns" to JSONArray().put(json("urlPattern" to "*", "requestStage" to "Request")))),
            c.call("Emulation.setFocusEmulationEnabled", json("enabled" to true)),
            c.call("Runtime.addBinding", json("name" to BINDING)),
            c.call("Browser.setDownloadBehavior", json("behavior" to "deny")).exceptionally { null },
        )
        setup.forEach { runCatching { it.get(10, TimeUnit.SECONDS) } }
        if (noCache) c.fire("Network.setCacheDisabled", json("cacheDisabled" to true))
        userAgent?.let { applyUa(c, it) }
        mainFrame = runCatching { c.callSync("Page.getFrameTree").getJSONObject("frameTree").getJSONObject("frame").getString("id") }.getOrNull()
        synchronized(docScripts) { for ((h, src) in docScripts) addDocScript(c, h, src) }
        c.fire("Page.addScriptToEvaluateOnNewDocument", json("source" to SELECT_LISTS))
        Pages.register(id, this)
        synchronized(waiting) {
            if (destroyed) { c.close(); edge.closeTarget(id); return }
            targetId = id; cdp = c; starting = false
            waiting.forEach { runCatching { it(c) } }; waiting.clear()
        }
    }

    private fun run(block: (Cdp) -> Unit) {
        synchronized(waiting) {
            val c = cdp
            if (c != null && !c.closed) Pages.io.execute { runCatching { block(c) } } else { waiting += block; ensure() }
        }
    }

    fun destroy() {
        destroyed = true
        val c = cdp; val id = targetId
        cdp = null; targetId = null
        interfaces.values.forEach { JsiServer.unregister(it) }
        if (id != null) { Pages.unregister(id); Pages.io.execute { runCatching { Edge.runningOrNull(profile)?.closeTarget(id) }; c?.close() } }
    }

    /** The page died with its renderer (or its browser): the client hears it, as onRenderProcessGone. */
    private fun gone(crashed: Boolean) {
        val id = targetId
        cdp = null; targetId = null; starting = false
        if (id != null) Pages.unregister(id)
        val handled = runCatching { view.getWebViewClient()?.onRenderProcessGone(view, android.webkit.RenderProcessGoneDetail(crashed)) }.getOrNull() ?: false
        if (!handled) Desk.log('W', "Page", "renderer gone, not handled by the client", null)
    }

    // ---- commands (Android's WebView API)
    fun load(target: String, headers: Map<String, String>?) {
        if (target.startsWith("javascript:")) { evaluate(target.removePrefix("javascript:"), null); return }
        val real = if (target.startsWith("file:///android_asset/")) JsiServer.assetUrl(target) else target
        expected += real
        run { c ->
            val p = json("url" to real)
            if (!headers.isNullOrEmpty()) p.put("extraHeaders", JSONObject(headers)) // not a CDP field: headers go with Network.setExtraHTTPHeaders
            if (!headers.isNullOrEmpty()) c.fire("Network.setExtraHTTPHeaders", json("headers" to JSONObject(headers)))
            c.call("Page.navigate", json("url" to real))
        }
    }
    fun loadHtml(html: String, baseUrl: String?) = run { c ->
        val base = baseUrl?.takeIf { it.startsWith("http") }
        if (base != null) {
            // The document gets the base's origin: the first request to it is answered with this HTML.
            pendingHtml[base] = html; expected += base
            c.call("Page.navigate", json("url" to base))
        } else {
            c.callSync("Page.navigate", json("url" to "about:blank"))
            mainFrame?.let { c.fire("Page.setDocumentContent", json("frameId" to it, "html" to html)) }
        }
    }
    private val pendingHtml = ConcurrentHashMap<String, String>()
    fun stop() = run { it.fire("Page.stopLoading") }
    fun reload() = run { it.fire("Page.reload", json("ignoreCache" to noCache)) }
    fun goBack() = run { c ->
        val h = c.callSync("Page.getNavigationHistory"); val i = h.getInt("currentIndex")
        if (i > 0) c.fire("Page.navigateToHistoryEntry", json("entryId" to h.getJSONArray("entries").getJSONObject(i - 1).getInt("id")))
    }
    fun canGoBack(): Boolean = runCatching { cdp?.callSync("Page.getNavigationHistory", ms = 2000)?.getInt("currentIndex") ?: 0 }.getOrDefault(0) > 0
    fun clearCache() = run { it.fire("Network.clearBrowserCache") }
    fun setUserAgent(ua: String?) { userAgent = ua; ua?.let { u -> cdp?.let { applyUa(it, u) } } }
    private fun applyUa(c: Cdp, ua: String) {
        // The browser's own user agent is kept (a desktop browser is what the page sees on the PC); only a
        // user agent naming Windows replaces it, so the client hints never contradict it.
        if ("Windows" in ua) c.fire("Emulation.setUserAgentOverride", json("userAgent" to ua))
    }

    fun evaluate(script: String, callback: android.webkit.ValueCallback<String>?) = run { c ->
        c.call("Runtime.evaluate", json("expression" to script, "returnByValue" to true, "awaitPromise" to false, "allowUnsafeEvalBlockedByCSP" to true)).whenComplete { r, t ->
            if (callback == null) return@whenComplete
            val out = if (t != null) "null" else r.optJSONObject("result")?.let { res -> if (res.has("value")) jsonValue(res.get("value")) else "null" } ?: "null"
            Desk.post { callback.onReceiveValue(out) }
        }
    }
    private fun jsonValue(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(v)
        is JSONObject, is JSONArray -> v.toString()
        else -> v.toString()
    }

    fun addJsInterface(obj: Any, name: String) {
        interfaces.remove(name)?.let { JsiServer.unregister(it) }
        val token = JsiServer.register(obj)
        interfaces[name] = token
        val src = JsiServer.stub(name, token, obj)
        addDocumentStart(src, setOf("*"))
        run { it.fire("Runtime.evaluate", json("expression" to src)) }
    }
    fun removeJsInterface(name: String) { interfaces.remove(name)?.let { JsiServer.unregister(it) } }

    /** A script run at each new document of [origins] ("*" for all), before the page's own. Returns a handle. */
    fun addDocumentStart(source: String, origins: Set<String>): Int {
        val h = handles.incrementAndGet()
        val guarded = if ("*" in origins) source else "if (${JSONArray(origins.map { it.trimEnd('/') })}.indexOf(location.origin) >= 0) { $source\n}"
        synchronized(docScripts) { docScripts += h to guarded }
        cdp?.let { addDocScript(it, h, guarded) } ?: ensure()
        return h
    }
    private fun addDocScript(c: Cdp, h: Int, src: String) {
        c.call("Page.addScriptToEvaluateOnNewDocument", json("source" to src)).thenAccept { docScriptIds[h] = it.optString("identifier") }
    }
    fun removeDocumentStart(h: Int) {
        synchronized(docScripts) { docScripts.removeAll { it.first == h } }
        docScriptIds.remove(h)?.let { id -> run { it.fire("Page.removeScriptToEvaluateOnNewDocument", json("identifier" to id)) } }
    }

    /** `window.<name>.postMessage(string)` for pages of [origins], heard by [listener] (androidx.webkit's web message listener). */
    fun addWebMessageListener(name: String, origins: Set<String>, listener: androidx.webkit.WebViewCompat.WebMessageListener) {
        listeners[name] = origins to listener
        val src = "(function(){var b=window.$BINDING;if(!b)return;var o={postMessage:function(m){b(JSON.stringify({n:${JSONObject.quote(name)},m:String(m)}));}};" +
            "try{Object.defineProperty(window,${JSONObject.quote(name)},{value:o,configurable:true});}catch(e){window[${JSONObject.quote(name)}]=o;}})();"
        addDocumentStart(src, origins)
        run { it.fire("Runtime.evaluate", json("expression" to src)) }
    }
    fun removeWebMessageListener(name: String) { listeners.remove(name) }

    // ---- what the view shows (screencast) and the input it gets
    @Volatile private var casting = false
    fun screencast(on: Boolean, w: Int, h: Int, scale: Float) = run { c ->
        if (on) {
            c.fire("Emulation.setDeviceMetricsOverride", json("width" to w, "height" to h, "deviceScaleFactor" to scale.toDouble(), "mobile" to false))
            c.fire("Page.startScreencast", json("format" to "jpeg", "quality" to 88, "maxWidth" to (w * scale).toInt(), "maxHeight" to (h * scale).toInt(), "everyNthFrame" to 1))
            casting = true
        } else if (casting) {
            casting = false
            c.fire("Page.stopScreencast"); c.fire("Emulation.clearDeviceMetricsOverride")
            frame.value = null
        }
    }
    fun mouse(type: String, x: Float, y: Float, button: String = "none", clicks: Int = 0, modifiers: Int = 0, buttons: Int = 0) = run { c ->
        c.fire("Input.dispatchMouseEvent", json("type" to type, "x" to x.toDouble(), "y" to y.toDouble(), "button" to button, "clickCount" to clicks, "modifiers" to modifiers, "buttons" to buttons))
    }
    fun wheel(x: Float, y: Float, dx: Float, dy: Float) = run { c ->
        c.fire("Input.dispatchMouseEvent", json("type" to "mouseWheel", "x" to x.toDouble(), "y" to y.toDouble(), "deltaX" to dx.toDouble(), "deltaY" to dy.toDouble()))
    }
    fun key(type: String, key: String, code: String, vk: Int, modifiers: Int, text: String?) = run { c ->
        val p = json("type" to type, "key" to key, "code" to code, "windowsVirtualKeyCode" to vk, "nativeVirtualKeyCode" to vk, "modifiers" to modifiers)
        if (text != null) { p.put("text", text); p.put("unmodifiedText", text) }
        c.fire("Input.dispatchKeyEvent", p)
    }
    fun insertText(text: String) = run { it.fire("Input.insertText", json("text" to text)) }

    // ---- events
    private fun client() = view.getWebViewClient()
    private fun chrome() = view.getWebChromeClient()

    private fun event(method: String, p: JSONObject) {
        when (method) {
            "Fetch.requestPaused" -> Pages.io.execute { runCatching { paused(p) }.onFailure { t -> Desk.log('E', "Page", "intercept: $t", t); cdp?.fire("Fetch.continueRequest", json("requestId" to p.getString("requestId"))) } }
            "Network.requestWillBeSent" -> {
                val r = p.getJSONObject("request")
                val isMain = p.optString("type") == "Document" && p.optString("frameId") == mainFrame
                requests[p.getString("requestId")] = DesktopRequest(r.getString("url"), r.optString("method", "GET"), headers(r.optJSONObject("headers")), isMain)
                if (requests.size > 4000) requests.keys.take(2000).forEach { requests.remove(it) }
            }
            "Network.responseReceived" -> {
                val resp = p.getJSONObject("response"); val status = resp.optInt("status")
                val req = requests[p.getString("requestId")] ?: return
                if (status >= 400) {
                    val r = android.webkit.WebResourceResponse(resp.optString("mimeType"), null, status, resp.optString("statusText").ifEmpty { "Error" }, headers(resp.optJSONObject("headers")), null)
                    Desk.post { client()?.onReceivedHttpError(view, req, r) }
                }
            }
            "Network.loadingFailed" -> {
                val req = requests.remove(p.getString("requestId")) ?: return
                val text = p.optString("errorText")
                if (p.optBoolean("canceled") && !req.isForMainFrame) return
                Desk.post { client()?.onReceivedError(view, req, android.webkit.WebResourceError.of(errorCode(text), text)) }
            }
            "Network.loadingFinished" -> requests.remove(p.getString("requestId"))
            "Page.frameNavigated" -> {
                val f = p.getJSONObject("frame")
                if (f.has("parentId")) return
                mainFrame = f.getString("id")
                val u = JsiServer.toFileUrl(f.getString("url") + f.optString("urlFragment"))
                url = u; progress = 10
                Desk.post { client()?.onPageStarted(view, u, null); chrome()?.onProgressChanged(view, 10) }
            }
            "Page.navigatedWithinDocument" -> if (p.optString("frameId") == mainFrame) {
                val u = JsiServer.toFileUrl(p.getString("url")); url = u
                Desk.post { client()?.doUpdateVisitedHistory(view, u, false) }
            }
            "Page.loadEventFired" -> {
                val u = url ?: return
                progress = 100
                cdp?.call("Runtime.evaluate", json("expression" to "document.title", "returnByValue" to true))?.thenAccept { r -> title = r.optJSONObject("result")?.optString("value"); Desk.post { chrome()?.onReceivedTitle(view, title) } }
                Desk.post { chrome()?.onProgressChanged(view, 100); client()?.onPageFinished(view, u) }
            }
            "Page.frameRequestedNavigation" -> if (p.optString("frameId") == mainFrame) {
                val u = p.optString("url")
                val scheme = u.substringBefore(':').lowercase()
                if (scheme !in setOf("http", "https", "about", "data", "blob", "javascript", "file")) {
                    Desk.post { client()?.shouldOverrideUrlLoading(view, DesktopRequest(u, "GET", emptyMap(), true)) }
                }
            }
            "Page.windowOpen" -> { val u = p.optString("url"); if (u.isNotEmpty()) load(u, null) }
            "Page.javascriptDialogOpening" -> cdp?.fire("Page.handleJavaScriptDialog", json("accept" to true))
            "Page.screencastFrame" -> {
                cdp?.fire("Page.screencastFrameAck", json("sessionId" to p.getInt("sessionId")))
                if (!casting) return
                val bytes = java.util.Base64.getDecoder().decode(p.getString("data"))
                val img = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() ?: return
                frame.value = img
            }
            "Inspector.targetCrashed" -> Desk.post { gone(crashed = true) }
            "Runtime.executionContextCreated" -> {
                val ctx = p.getJSONObject("context"); val aux = ctx.optJSONObject("auxData")
                contexts[ctx.getInt("id")] = ctx.optString("origin") to (aux?.optString("frameId") == mainFrame && aux?.optBoolean("isDefault") == true)
            }
            "Runtime.executionContextDestroyed" -> contexts.remove(p.optInt("executionContextId"))
            "Runtime.executionContextsCleared" -> contexts.clear()
            "Runtime.bindingCalled" -> if (p.optString("name") == BINDING) {
                val msg = runCatching { JSONObject(p.getString("payload")) }.getOrNull() ?: return
                val (origins, listener) = listeners[msg.optString("n")] ?: return
                val (origin, isMain) = contexts[p.optInt("executionContextId")] ?: return
                if ("*" !in origins && origins.none { it.trimEnd('/') == origin }) return
                val data = msg.optString("m")
                Desk.post { listener.onPostMessage(view, androidx.webkit.WebMessageCompat(data), android.net.Uri.parse(origin), isMain, androidx.webkit.JavaScriptReplyProxy.NONE) }
            }
            "Runtime.consoleAPICalled" -> {
                val chrome = chrome() ?: return
                val text = p.optJSONArray("args")?.let { a -> (0 until a.length()).joinToString(" ") { i -> a.getJSONObject(i).let { it.opt("value")?.toString() ?: it.optString("description") } } }.orEmpty()
                val level = when (p.optString("type")) { "error" -> android.webkit.ConsoleMessage.MessageLevel.ERROR; "warning" -> android.webkit.ConsoleMessage.MessageLevel.WARNING; "debug" -> android.webkit.ConsoleMessage.MessageLevel.DEBUG; else -> android.webkit.ConsoleMessage.MessageLevel.LOG }
                Desk.post { chrome.onConsoleMessage(android.webkit.ConsoleMessage(text, url ?: "", 0, level)) }
            }
            "Runtime.exceptionThrown" -> {
                val chrome = chrome() ?: return
                val d = p.optJSONObject("exceptionDetails"); val text = d?.optJSONObject("exception")?.optString("description") ?: d?.optString("text").orEmpty()
                Desk.post { chrome.onConsoleMessage(android.webkit.ConsoleMessage(text, url ?: "", d?.optInt("lineNumber") ?: 0, android.webkit.ConsoleMessage.MessageLevel.ERROR)) }
            }
        }
    }

    private fun paused(p: JSONObject) {
        val c = cdp ?: return
        val id = p.getString("requestId")
        val r = p.getJSONObject("request")
        val u = r.getString("url")
        val type = p.optString("resourceType")
        val isMain = type == "Document" && (mainFrame == null || p.optString("frameId") == mainFrame)
        val req = DesktopRequest(JsiServer.toFileUrl(u), r.optString("method", "GET"), headers(r.optJSONObject("headers")), isMain)
        pendingHtml.remove(u)?.let { html -> fulfill(c, id, android.webkit.WebResourceResponse("text/html", "utf-8", html.byteInputStream())); return }
        if (JsiServer.isAsset(u) || u.startsWith(JsiServer.origin)) { c.fire("Fetch.continueRequest", json("requestId" to id)); return }
        val client = client()
        if (isMain && type == "Document" && client != null) {
            val ours = synchronized(expected) { expected.remove(u) || expected.removeIf { e -> e.substringBefore('#') == u.substringBefore('#') } }
            if (!ours) {
                val override = runCatching { Pages.onMain { client.shouldOverrideUrlLoading(view, req) } }.getOrDefault(false)
                if (override) { c.fire("Fetch.failRequest", json("requestId" to id, "errorReason" to "Aborted")); return }
            }
        }
        val resp = runCatching { client?.shouldInterceptRequest(view, req) }.onFailure { Desk.log('E', "Page", "shouldInterceptRequest: $it", it) }.getOrNull()
        if (resp != null) fulfill(c, id, resp) else c.fire("Fetch.continueRequest", json("requestId" to id))
    }

    private fun fulfill(c: Cdp, id: String, r: android.webkit.WebResourceResponse) {
        val body = r.data?.use { it.readBytes() } ?: ByteArray(0)
        val hs = JSONArray()
        r.responseHeaders?.forEach { (k, v) -> hs.put(json("name" to k, "value" to v)) }
        if (r.responseHeaders?.keys?.none { it.equals("content-type", true) } != false && r.mimeType != null)
            hs.put(json("name" to "Content-Type", "value" to r.mimeType + (r.encoding?.let { "; charset=$it" } ?: "")))
        c.fire("Fetch.fulfillRequest", json("requestId" to id, "responseCode" to r.statusCode, "responsePhrase" to (r.reasonPhrase ?: "OK"),
            "responseHeaders" to hs, "body" to java.util.Base64.getEncoder().encodeToString(body)))
    }

    private fun headers(o: JSONObject?): Map<String, String> = o?.keys()?.asSequence()?.associateWith { o.optString(it) }.orEmpty()
    private fun errorCode(text: String) = when {
        "NAME_NOT_RESOLVED" in text -> -2; "CONNECTION" in text -> -6; "TIMED_OUT" in text -> -8; "SSL" in text || "CERT" in text -> -11
        "INTERNET_DISCONNECTED" in text -> -6; "ABORTED" in text -> -1; else -> -1
    }

    companion object {
        const val BINDING = "__jamPost"
        /**
         * A page's drop-down lists (<select>) open as the browser's own popup window, which is not part
         * of the page's picture: they open in the page instead, as a list under the field.
         */
        val SELECT_LISTS = """(function(){if(window.__jamSelect)return;window.__jamSelect=1;
function close(){var o=document.getElementById('__jam_sel');if(o)o.remove();}
document.addEventListener('mousedown',function(e){var s=e.target&&e.target.closest&&e.target.closest('select');
if(!s||s.multiple||s.disabled){if(!(e.target.closest&&e.target.closest('#__jam_sel')))close();return;}
e.preventDefault();e.stopPropagation();if(document.getElementById('__jam_sel')){close();return;}s.focus();
var r=s.getBoundingClientRect(),cs=getComputedStyle(s),o=document.createElement('div');o.id='__jam_sel';
o.style.cssText='position:fixed;z-index:2147483647;left:'+r.left+'px;top:'+(r.bottom+4)+'px;min-width:'+r.width+'px;max-height:'+Math.max(160,innerHeight-r.bottom-16)+'px;overflow:auto;background:#1e1e24;color:#fff;border:1px solid #3a3a44;border-radius:10px;box-shadow:0 8px 28px rgba(0,0,0,.5);font:'+cs.font+';padding:4px';
Array.prototype.forEach.call(s.options,function(op,i){if(op.hidden)return;var d=document.createElement('div');d.textContent=op.text;
d.style.cssText='padding:8px 12px;border-radius:7px;cursor:pointer;white-space:nowrap;'+(op.disabled?'opacity:.4;':'')+(i==s.selectedIndex?'background:#8b5cf6;color:#000;':'');
d.onmouseenter=function(){if(i!=s.selectedIndex)d.style.background='#2c2c34';};d.onmouseleave=function(){if(i!=s.selectedIndex)d.style.background='';};
d.onmousedown=function(ev){ev.preventDefault();ev.stopPropagation();if(op.disabled)return;s.selectedIndex=i;s.dispatchEvent(new Event('input',{bubbles:true}));s.dispatchEvent(new Event('change',{bubbles:true}));close();};o.appendChild(d);});
document.documentElement.appendChild(o);},true);
addEventListener('scroll',function(e){if(!(e.target&&e.target.id=='__jam_sel'))close();},true);addEventListener('blur',close);})();"""
    }
}

/** A request as WebViewClient sees it. */
class DesktopRequest(private val u: String, private val m: String, private val h: Map<String, String>, private val main: Boolean) : android.webkit.WebResourceRequest {
    private val parsed by lazy { android.net.Uri.parse(u) }
    override fun getUrl(): android.net.Uri = parsed
    override fun isForMainFrame(): Boolean = main
    override fun isRedirect(): Boolean = false
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = m
    override fun getRequestHeaders(): Map<String, String> = h
}
