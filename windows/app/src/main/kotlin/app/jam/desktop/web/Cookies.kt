package app.jam.desktop.web

import android.webkit.ValueCallback
import app.jam.desktop.shim.Desk
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** CookieManager for a web profile: its browser's cookie jar, over the DevTools protocol. */
class Cookies private constructor(private val profile: String) : android.webkit.CookieManager() {
    private fun edge() = Edge.of(profile)
    override fun setAcceptCookie(accept: Boolean) {}
    override fun acceptCookie() = true
    override fun setAcceptThirdPartyCookies(view: android.webkit.WebView?, accept: Boolean) {}
    override fun acceptThirdPartyCookies(view: android.webkit.WebView?) = true

    override fun getCookie(url: String?): String? {
        val u = runCatching { java.net.URI(url ?: return null) }.getOrNull() ?: return null
        val host = u.host ?: return null
        val path = u.path.ifEmpty { "/" }
        val secure = u.scheme == "https"
        val all = runCatching { edge().cookies() }.getOrElse { Desk.log('W', "Cookies", "getCookie: $it", null); return null }
        val out = (0 until all.length()).map { all.getJSONObject(it) }.filter { c ->
            val d = c.optString("domain"); val dm = d.trimStart('.')
            (host == dm || (d.startsWith(".") || !c.optBoolean("hostOnly", !d.startsWith("."))) && host.endsWith(".$dm")) &&
                path.startsWith(c.optString("path", "/")) && (!c.optBoolean("secure") || secure)
        }.sortedByDescending { it.optString("path").length }.joinToString("; ") { it.optString("name") + "=" + it.optString("value") }
        return out.ifEmpty { null }
    }

    override fun setCookie(url: String?, value: String?) { setCookie(url, value, null) }
    override fun setCookie(url: String?, value: String?, callback: ValueCallback<Boolean>?) {
        val ok = runCatching {
            val parts = value!!.split(';').map { it.trim() }
            val (name, v) = parts[0].substringBefore('=') to parts[0].substringAfter('=', "")
            val c = JSONObject().put("name", name).put("value", v).put("url", url)
            for (a in parts.drop(1)) {
                val k = a.substringBefore('=').trim().lowercase(); val av = a.substringAfter('=', "").trim()
                when (k) {
                    "domain" -> { c.put("domain", av); c.remove("url"); c.put("path", c.optString("path", "/")) ; c.put("secure", url?.startsWith("https") == true) }
                    "path" -> c.put("path", av)
                    "secure" -> c.put("secure", true)
                    "httponly" -> c.put("httpOnly", true)
                    "samesite" -> c.put("sameSite", av.replaceFirstChar { it.uppercase() })
                    "max-age" -> c.put("expires", System.currentTimeMillis() / 1000.0 + (av.toLongOrNull() ?: 0))
                    "expires" -> runCatching { java.time.ZonedDateTime.parse(av, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond().toDouble() }.getOrNull()?.let { c.put("expires", it) }
                }
            }
            if (c.has("domain") && !c.has("url")) c.put("url", url)
            edge().setCookies(JSONArray().put(c))
        }.isSuccess
        callback?.let { cb -> Desk.post { cb.onReceiveValue(ok) } }
    }

    override fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        Pages.io.execute { val ok = runCatching { edge().clearCookies() }.isSuccess; callback?.let { cb -> Desk.post { cb.onReceiveValue(ok) } } }
    }
    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        Pages.io.execute {
            val ok = runCatching {
                val all = edge().cookies(); val keep = JSONArray()
                for (i in 0 until all.length()) { val c = all.getJSONObject(i); if (!c.optBoolean("session")) keep.put(c) }
                edge().clearCookies()
                if (keep.length() > 0) edge().setCookies(JSONArray((0 until keep.length()).map { i -> keep.getJSONObject(i).let { c -> JSONObject().apply { for (k in listOf("name", "value", "domain", "path", "secure", "httpOnly", "sameSite", "expires")) if (c.has(k)) put(k, c.get(k)) } } }))
            }.isSuccess
            callback?.let { cb -> Desk.post { cb.onReceiveValue(ok) } }
        }
    }
    override fun hasCookies(): Boolean = runCatching { edge().cookies().length() > 0 }.getOrDefault(false)
    /** The browser writes its cookies to disk itself (within seconds); nothing to force. */
    override fun flush() {}

    companion object {
        private val byProfile = ConcurrentHashMap<String, Cookies>()
        fun of(profile: String): Cookies = byProfile.getOrPut(profile) { Cookies(profile) }
        fun clearData(profile: String) { Pages.io.execute { runCatching { Edge.of(profile).clearData() } } }
    }
}

/** The browser's version and user agent, as the app reports them (the default profile's browser). */
object Browsers {
    fun version(): String = runCatching { Edge.of(Edge.DEFAULT).version.substringAfter('/') }.getOrDefault("130.0.0.0")
    fun userAgent(): String = runCatching { Edge.of(Edge.DEFAULT).userAgent }.getOrDefault(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
}
