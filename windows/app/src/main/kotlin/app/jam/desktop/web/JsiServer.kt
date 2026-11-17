package app.jam.desktop.web

import app.jam.desktop.shim.Desk
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A server on 127.0.0.1 for the web views: the app's bundled pages (file:///android_asset/… is
 * served as http://127.0.0.1:<port>/android_asset/…) and the objects given to pages with
 * addJavascriptInterface, whose methods pages call synchronously and get answers from, as on Android
 * (a synchronous request to this server; each object behind its own random token).
 */
object JsiServer {
    private val objects = ConcurrentHashMap<String, Any>()
    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "jam-jsi").apply { isDaemon = true } }
            createContext("/") { ex -> runCatching { handle(ex) }.onFailure { Desk.log('E', "JsiServer", "${ex.requestURI}: $it", it); runCatching { ex.sendResponseHeaders(500, -1); ex.close() } } }
            start()
        }
    }
    val port: Int get() = server.address.port
    val origin: String get() = "http://127.0.0.1:$port"
    fun assetUrl(fileUrl: String): String = origin + "/android_asset/" + fileUrl.removePrefix("file:///android_asset/")
    fun isAsset(url: String) = url.startsWith("$origin/android_asset/")
    fun toFileUrl(url: String): String = if (isAsset(url)) "file:///android_asset/" + url.removePrefix("$origin/android_asset/") else url

    fun register(obj: Any): String = java.util.UUID.randomUUID().toString().replace("-", "").also { objects[it] = obj }
    fun unregister(token: String) { objects.remove(token) }

    /** The script that gives a page `window.<name>`, each @JavascriptInterface method calling [obj]'s. */
    fun stub(name: String, token: String, obj: Any): String {
        val methods = obj.javaClass.methods.filter { m -> m.annotations.any { it.annotationClass.java.name == "android.webkit.JavascriptInterface" } }.map { it.name }.distinct()
        val base = "$origin/jsi/$token/"
        return """(function(){var P=${JSONObject.quote(base)};function c(m,a){var x=new XMLHttpRequest();x.open('POST',P+m,false);x.setRequestHeader('Content-Type','text/plain');x.send(JSON.stringify(a));var r=JSON.parse(x.responseText||'{}');if(r.e)throw new Error(r.e);return r.v===null?undefined:r.v;}
var o={};${methods.joinToString("") { "o[${JSONObject.quote(it)}]=function(){return c(${JSONObject.quote(it)},Array.prototype.slice.call(arguments));};" }}
try{Object.defineProperty(window,${JSONObject.quote(name)},{value:o,configurable:true,writable:true});}catch(e){window[${JSONObject.quote(name)}]=o;}})();"""
    }

    private fun cors(ex: HttpExchange) {
        val h = ex.responseHeaders
        h.add("Access-Control-Allow-Origin", ex.requestHeaders.getFirst("Origin") ?: "*")
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        h.add("Access-Control-Allow-Headers", "Content-Type")
        h.add("Access-Control-Allow-Private-Network", "true")
    }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        cors(ex)
        if (ex.requestMethod == "OPTIONS") { ex.sendResponseHeaders(204, -1); ex.close(); return }
        when {
            path.startsWith("/android_asset/") -> {
                val name = path.removePrefix("/android_asset/")
                val bytes = JsiServer::class.java.classLoader.getResourceAsStream("assets/$name")?.use { it.readBytes() }
                if (bytes == null) { ex.sendResponseHeaders(404, -1); ex.close(); return }
                ex.responseHeaders.add("Content-Type", mime(name))
                ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
            }
            path.startsWith("/jsi/") -> {
                val parts = path.removePrefix("/jsi/").split('/')
                val obj = objects[parts.getOrNull(0)]
                val out = if (obj == null) JSONObject().put("e", "gone") else runCatching { JSONObject().put("v", JSONObject.wrap(invoke(obj, parts[1], JSONArray(ex.requestBody.readBytes().decodeToString().ifEmpty { "[]" })))) }
                    .getOrElse { JSONObject().put("e", (it.cause ?: it).toString()) }
                val bytes = out.toString().toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
            }
            else -> { ex.sendResponseHeaders(404, -1); ex.close() }
        }
    }

    private fun invoke(obj: Any, name: String, args: JSONArray): Any? {
        val m = obj.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.length() && it.annotations.any { a -> a.annotationClass.java.name == "android.webkit.JavascriptInterface" } }
            ?: throw NoSuchMethodException(name)
        val converted = m.parameterTypes.mapIndexed { i, t ->
            val v = if (args.isNull(i)) null else args.get(i)
            when (t) {
                String::class.java -> v?.let { if (it is String) it else it.toString() }
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> (v as? Number)?.toInt() ?: v?.toString()?.toIntOrNull() ?: 0
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> (v as? Number)?.toLong() ?: 0L
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> (v as? Number)?.toDouble() ?: 0.0
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> (v as? Number)?.toFloat() ?: 0f
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> v == true || v == "true"
                else -> v
            }
        }
        return m.invoke(obj, *converted.toTypedArray())
    }

    private fun mime(n: String) = when (n.substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"; "js" -> "text/javascript; charset=utf-8"; "css" -> "text/css; charset=utf-8"
        "json" -> "application/json"; "png" -> "image/png"; "svg" -> "image/svg+xml"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"
        "woff2" -> "font/woff2"; "ttf" -> "font/ttf"; "mp3" -> "audio/mpeg"; else -> "application/octet-stream"
    }
}
