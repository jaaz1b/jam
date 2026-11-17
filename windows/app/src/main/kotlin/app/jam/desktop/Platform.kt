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
        // Discord's sign-in goes to the PC's own browser, where Discord is usually signed in already
        // and its page works as Discord means it to (in Jam's page it never loaded the app: stuck
        // on "…"). Discord then opens jamdiscord://…, registered for Jam, which hands it over.
        if (url.startsWith("https://discord.com/oauth2/authorize") && AppLinks.register()) {
            app.jam.desktop.shim.Shell.browse(url)
            app.jam.desktop.shim.Toasts.show("Finish connecting Discord in your browser, then come back to Jam.", true)
            return@post
        }
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
            dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2), 4)          // rounded corners (Windows 11)
        }
    }

    /**
     * The title bar in the window's own colour (Windows 11): it reads as part of the app, not as a
     * strip over it. Its text white, its border the same colour.
     */
    fun caption(w: java.awt.Window, color: androidx.compose.ui.graphics.Color) {
        if (!Desk.isWindows) return
        runCatching {
            val dwm = Native.load("dwmapi", Dwm::class.java)
            val hwnd = WinDef.HWND(Native.getComponentPointer(w))
            fun ref(c: androidx.compose.ui.graphics.Color): Int {
                val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt(); val b = (c.blue * 255).toInt()
                return r or (g shl 8) or (b shl 16) // COLORREF: 0x00BBGGRR
            }
            dwm.DwmSetWindowAttribute(hwnd, 35, IntByReference(ref(color)), 4)   // caption
            dwm.DwmSetWindowAttribute(hwnd, 34, IntByReference(ref(color)), 4)   // border
            dwm.DwmSetWindowAttribute(hwnd, 36, IntByReference(0x00F4F4F4), 4)  // caption text
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
                    Desk.post {
                        if (AppLinks.handle(link)) return@post
                        if (YtmCompanion.running) YtmCompanion.front() else { Front.request(); if (link.isNotEmpty()) onLink?.invoke(link) }
                    }
                }
            }
        }, "jam-single").apply { isDaemon = true }.start()
    }
}


/**
 * Jam's own link scheme on Windows (jamdiscord://, Discord's sign-in coming back): registered for the
 * user (HKCU\Software\Classes), opening Jam.exe with the link, which the running Jam then receives.
 */
object AppLinks {
    @Volatile private var done = false
    fun register(): Boolean {
        if (done) return true
        if (!Desk.isWindows) return false
        val exe = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith(".exe", true) && !it.endsWith("java.exe", true) && !it.endsWith("javaw.exe", true) } ?: return false
        done = runCatching {
            val u = com.sun.jna.platform.win32.Advapi32Util::class.java
            val hk = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
            val key = "Software\\Classes\\jamdiscord"
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(hk, "Software\\Classes", "jamdiscord")
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(hk, key, "", "URL:Jam Discord sign-in")
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(hk, key, "URL Protocol", "")
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(hk, key, "shell")
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(hk, "$key\\shell", "open")
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(hk, "$key\\shell\\open", "command")
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(hk, "$key\\shell\\open\\command", "", "\"$exe\" \"%1\"")
            u != null
        }.onFailure { Desk.log('W', "AppLinks", "register: $it", it) }.getOrDefault(false)
        Desk.log('I', "AppLinks", "jamdiscord:// registered: $done", null)
        return done
    }

    /** A link Jam was started with, or handed by a second start: true when it was Jam's own. */
    fun handle(link: String): Boolean {
        if (!link.startsWith("jamdiscord:")) return false
        Desk.post { app.jam.android.Discord.onCallback(android.net.Uri.parse(link)); Front.request() }
        return true
    }
}