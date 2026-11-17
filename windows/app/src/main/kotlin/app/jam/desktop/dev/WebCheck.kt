package app.jam.desktop.dev

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.jam.desktop.shim.DesktopApp

/** Development check of a web view's input: a page with a text field, typed into. */
fun main(args: Array<String>) {
    var view: android.webkit.WebView? = null
    javax.swing.SwingUtilities.invokeAndWait {
        view = android.webkit.WebView(DesktopApp.context).apply { settings.javaScriptEnabled = true; loadUrl(args[0]) }
    }
    application { Window(onCloseRequest = ::exitApplication, title = "web") { app.jam.desktop.ViewNode(view!!) } }
}
