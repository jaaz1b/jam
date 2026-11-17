package app.jam.desktop

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jam.desktop.shim.Desk
import app.jam.desktop.shim.DesktopApp
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * A web page over the app in Jam's own window: what a Custom Tab is on Android (Discord's sign-in).
 * A link to the app's own scheme ends it and goes to the app (jamdiscord://… to Discord's sign-in).
 */
object WebSheet {
    private val open = mutableStateOf<WebView?>(null)
    fun open(url: String) = Desk.post {
        val v = open.value ?: WebView(DesktopApp.activity ?: DesktopApp.context).also { open.value = it }
        v.settings.javaScriptEnabled = true; v.settings.domStorageEnabled = true
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                if (u.scheme == "jamdiscord") { app.jam.android.Discord.onCallback(u); close() }
                else app.jam.desktop.shim.Intents.start(android.content.Intent(android.content.Intent.ACTION_VIEW, u))
                return true
            }
        }
        v.loadUrl(url)
    }
    fun close() = Desk.post { open.value?.let { it.destroy() }; open.value = null }

    @Composable
    fun Host() {
        val v = open.value ?: return
        Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(0.72f).fillMaxSize(0.86f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF16161A))) {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(v.url?.let { runCatching { java.net.URI(it).host }.getOrNull() } ?: "", color = Color(0xFFB8B8C0), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { close() }.padding(8.dp))
                }
                androidx.compose.ui.viewinterop.AndroidView(factory = { v }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Dark title bar on Windows 10/11, to go with the app. */
object DarkTitleBar {
    private interface Dwm : StdCallLibrary { fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: IntByReference, size: Int): Int }
    fun apply(w: java.awt.Window) {
        if (!Desk.isWindows) return
        runCatching {
            val dwm = Native.load("dwmapi", Dwm::class.java)
            val hwnd = WinDef.HWND(Native.getComponentPointer(w))
            dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1), 4)          // dark mode
            dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(2), 4)          // Mica backdrop (Windows 11)
        }
    }
}

/**
 * One Jam at a time: a second start (a spotify: link opened in the browser, the Start menu again)
 * hands its link to the running one, which comes to the front, and quits.
 */
object Single {
    @Volatile var onLink: ((String) -> Unit)? = null
    private val portFile get() = java.io.File(Desk.dataDir, "instance.port")
    fun claim(args: Array<String>) {
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull()
        if (port != null) runCatching {
            java.net.Socket("127.0.0.1", port).use { s -> s.soTimeout = 2000; s.getOutputStream().write(((args.firstOrNull() ?: "") + "\n").toByteArray()); s.getOutputStream().flush(); s.getInputStream().read() }
            Runtime.getRuntime().exit(0)
        }
        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress())
        portFile.writeText(server.localPort.toString())
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { portFile.delete() } })
        Thread({
            while (true) runCatching {
                server.accept().use { s ->
                    val link = s.getInputStream().bufferedReader().readLine().orEmpty().trim()
                    s.getOutputStream().write(1)
                    Desk.post { Front.request(); if (link.isNotEmpty()) onLink?.invoke(link) }
                }
            }
        }, "jam-single").apply { isDaemon = true }.start()
    }
}
