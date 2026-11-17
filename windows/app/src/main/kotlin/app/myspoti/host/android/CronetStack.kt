// Jam for Windows: replaces the Android file of the same name (Cronet comes from Google Play services,
// which the PC has not). The relayed requests go out over OkHttp or the platform stack (PlatformStack).
package app.myspoti.host.android

import android.content.Context
import okhttp3.Headers

internal object CronetStack {
    @Volatile var status: String = "unavailable: not on the PC"
    val available: Boolean get() = false
    fun prepare(context: Context) {}
    fun fetch(url: String, method: String, headers: Headers, sent: String?, done: (code: Int, headers: List<Pair<String, String>>, text: String, protocol: String, error: String?) -> Unit) =
        done(0, emptyList(), "", "", "Cronet $status")
}
