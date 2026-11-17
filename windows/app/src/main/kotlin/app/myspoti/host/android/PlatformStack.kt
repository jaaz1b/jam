// Jam for Windows: replaces the Android file of the same name (Android 14's HttpEngine, Chromium's
// network stack). On the PC the relayed request goes out over OkHttp with the brotli and zstd codecs.
package app.myspoti.host.android

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal object PlatformStack {
    private val client by lazy {
        OkHttpClient.Builder().addInterceptor(okhttp3.CompressionInterceptor(okhttp3.brotli.Brotli, okhttp3.zstd.Zstd, okhttp3.Gzip)).build()
    }
    fun fetch(context: Context, url: String, method: String, headers: Headers, sent: String?,
              done: (code: Int, headers: List<Pair<String, String>>, text: String, protocol: String, error: String?) -> Unit) {
        val payload = if (method == "GET" || method == "HEAD") null else (sent ?: "").toByteArray().toRequestBody(null)
        client.newCall(Request.Builder().url(url).headers(headers).method(method, payload).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(0, emptyList(), "", "", e.message ?: e.toString())
            override fun onResponse(call: Call, response: Response) {
                val r = runCatching { response.use { Triple(it.code, it.headers.map { h -> h.first to h.second }, it.body.string()) to it.protocol.toString() } }
                r.fold({ (t, p) -> done(t.first, t.second, t.third, p, null) }, { done(0, emptyList(), "", "", it.message) })
            }
        })
    }
}
