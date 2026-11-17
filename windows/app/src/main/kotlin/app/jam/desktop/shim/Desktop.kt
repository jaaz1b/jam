package app.jam.desktop.shim

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** The app's Context: Jam's Application once it is created (MyspotiApplication), a plain one before. */
object DesktopApp {
    @Volatile var context: Context = object : android.content.ContextWrapper(null) {}
    @Volatile var activity: android.app.Activity? = null
}

/** The system services the app asks Context for, as the PC has them. */
object Services {
    private val byName = HashMap<String, Any?>()
    @Synchronized fun get(name: String): Any? = byName.getOrPut(name) {
        when (name) {
            Context.CLIPBOARD_SERVICE -> android.content.ClipboardManager()
            Context.CONNECTIVITY_SERVICE -> android.net.ConnectivityManager()
            Context.AUDIO_SERVICE -> android.media.AudioManager()
            Context.POWER_SERVICE -> android.os.PowerManager()
            Context.VIBRATOR_SERVICE -> android.os.Vibrator()
            Context.VIBRATOR_MANAGER_SERVICE -> android.os.VibratorManager()
            Context.NOTIFICATION_SERVICE -> android.app.NotificationManager()
            Context.WIFI_SERVICE -> android.net.wifi.WifiManager()
            Context.ACTIVITY_SERVICE -> android.app.ActivityManager()
            Context.UI_MODE_SERVICE -> android.app.UiModeManager()
            Context.NSD_SERVICE -> android.net.nsd.NsdManager()
            Context.BATTERY_SERVICE -> android.os.BatteryManager()
            Context.ALARM_SERVICE -> android.app.AlarmManager()
            Context.KEYGUARD_SERVICE -> android.app.KeyguardManager()
            Context.INPUT_METHOD_SERVICE -> android.view.inputmethod.InputMethodManager()
            Context.WINDOW_SERVICE -> android.view.WindowManager()
            else -> null
        }
    }
    fun get(c: Class<*>): Any? = when (c.name) {
        "android.content.ClipboardManager" -> get(Context.CLIPBOARD_SERVICE)
        "android.net.ConnectivityManager" -> get(Context.CONNECTIVITY_SERVICE)
        "android.media.AudioManager" -> get(Context.AUDIO_SERVICE)
        "android.os.PowerManager" -> get(Context.POWER_SERVICE)
        "android.os.Vibrator" -> get(Context.VIBRATOR_SERVICE)
        "android.os.VibratorManager" -> get(Context.VIBRATOR_MANAGER_SERVICE)
        "android.app.NotificationManager" -> get(Context.NOTIFICATION_SERVICE)
        "android.net.wifi.WifiManager" -> get(Context.WIFI_SERVICE)
        "android.app.ActivityManager" -> get(Context.ACTIVITY_SERVICE)
        "android.app.UiModeManager" -> get(Context.UI_MODE_SERVICE)
        "android.net.nsd.NsdManager" -> get(Context.NSD_SERVICE)
        "android.os.BatteryManager" -> get(Context.BATTERY_SERVICE)
        "android.app.AlarmManager" -> get(Context.ALARM_SERVICE)
        // Any other manager the app asks for by class: one of its own, made once (they hold no state of Android's).
        else -> synchronized(this) { byName.getOrPut(c.name) { runCatching { c.getDeclaredConstructor().newInstance() }.getOrNull() } }
    }
}

/**
 * Intents on the PC. A web page opens in the default browser, a share copies the text (and says so),
 * the app's own activity gets the intent as Android gives it a new one; broadcasts go to the
 * receivers the app registered in this process.
 */
object Intents {
    private class Reg(val receiver: BroadcastReceiver, val filter: IntentFilter?)
    private val receivers = CopyOnWriteArrayList<Reg>()
    /** The app's main activity, given intents meant for it (links to play, notification taps). */
    @Volatile var onAppIntent: ((Intent) -> Unit)? = null
    /** Services started by intent (the playback service's commands): their handler by class name. */
    val services = java.util.concurrent.ConcurrentHashMap<String, (Intent) -> Unit>()

    fun start(intent: Intent) {
        val data = intent.data
        val cls = intent.component?.className
        when {
            intent.action == Intent.ACTION_CHOOSER -> (intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))?.let { start(it) }
            intent.action == Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                when {
                    text != null -> { Clip.set(text); Toasts.show(Res.string("jam_desktop_copied") ?: "Copied to the clipboard", false) }
                    stream != null -> Files.file(stream)?.let { Shell.reveal(it) }
                }
            }
            cls != null && (cls.endsWith("MainActivity") || cls.startsWith("app.myspoti")) && data == null -> onAppIntent?.invoke(intent)
            data != null && data.scheme in setOf("http", "https", "mailto") -> Shell.browse(data.toString())
            data != null && data.scheme == "file" -> Files.file(data)?.let { Shell.open(it) }
            data != null && data.scheme in setOf("spotify", "myspoti", "jam") -> onAppIntent?.invoke(intent)
            intent.action?.startsWith("android.settings.") == true -> Unit // no system settings page to open on the PC
            cls != null -> onAppIntent?.invoke(intent)
            else -> Desk.log('W', "Intents", "nothing on the PC handles $intent", null)
        }
    }

    fun service(intent: Intent): ComponentName? {
        val cls = intent.component?.className ?: return null
        if (!ServiceHost.start(intent)) services[cls]?.invoke(intent)
        return intent.component
    }

    fun broadcast(intent: Intent) {
        for (r in receivers) if (r.filter == null || r.filter.matchAction(intent.action)) Desk.post { runCatching { r.receiver.onReceive(DesktopApp.context, intent) } }
    }
    fun register(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? { if (receiver != null) receivers += Reg(receiver, filter); return null }
    fun unregister(receiver: BroadcastReceiver?) { receivers.removeAll { it.receiver === receiver } }
}

/** Files behind Uris: file:// paths (what the PC's file pickers give), the app's own files. */
object Files {
    fun file(uri: Uri): File? = when (uri.scheme) {
        "file", null -> uri.path?.let(::File)
        "content" -> uri.getQueryParameter("path")?.let(::File) ?: uri.path?.let { p -> File(p.removePrefix("/").substringAfter('/', p)) }.takeIf { it?.exists() == true }
        else -> null
    }
    fun open(uri: Uri): java.io.InputStream? = when (uri.scheme) {
        "http", "https" -> java.net.URI(uri.toString()).toURL().openStream()
        "android.resource" -> uri.lastPathSegment?.toIntOrNull()?.let { Res.file(it) }?.let { Res.open(it) }
        else -> file(uri)?.inputStream()
    }
    fun openOut(uri: Uri, append: Boolean): java.io.OutputStream? = file(uri)?.let { it.parentFile?.mkdirs(); java.io.FileOutputStream(it, append) }
}

/** The PC's clipboard (AWT). */
object Clip {
    fun set(text: String) = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
    fun get(): String? = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String }.getOrNull()
}

/** Opening things with Windows (the browser, Explorer). */
object Shell {
    fun browse(url: String) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }.onFailure { runCatching { ProcessBuilder(if (Desk.isWindows) listOf("rundll32", "url.dll,FileProtocolHandler", url) else listOf("xdg-open", url)).start() } } }
    fun open(f: File) { runCatching { java.awt.Desktop.getDesktop().open(f) } }
    fun reveal(f: File) { runCatching { if (Desk.isWindows) ProcessBuilder("explorer.exe", "/select,", f.absolutePath).start() else java.awt.Desktop.getDesktop().open(f.parentFile) } }
}
