package app.jam.desktop.shim

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Jam for Windows: what the Android APIs of the shim (android.*, androidx.*) rest on: where the
 * app's files go, the UI thread (Swing's), a log.
 */
object Desk {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** %LOCALAPPDATA%\Jam\App on Windows (the phone's app data folder), ~/.local/share/jam-app elsewhere. */
    val dataDir: File by lazy {
        val base = if (isWindows) System.getenv("LOCALAPPDATA")?.let { File(it, "Jam\\App") }
        else File(System.getProperty("user.home"), ".local/share/jam-app")
        (base ?: File(System.getProperty("user.home"), ".jam-app")).apply { mkdirs() }
    }
    val filesDir: File get() = File(dataDir, "files").apply { mkdirs() }
    val cacheDir: File get() = File(dataDir, "cache").apply { mkdirs() }
    val prefsDir: File get() = File(dataDir, "shared_prefs").apply { mkdirs() }

    /** This PC's name (Spotify Connect shows it, like the phone's name). */
    val deviceName: String by lazy { (System.getenv("COMPUTERNAME") ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull() ?: "PC").let { "Jam on $it" } }
    /** A stable id for this install (the phone's ANDROID_ID). */
    val deviceId: String by lazy {
        val f = File(dataDir, "device_id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString().replace("-", "").take(16).also { f.writeText(it) }
    }

    /** The "main thread": Swing's event thread, where Compose for Desktop runs. */
    fun isMain(): Boolean = javax.swing.SwingUtilities.isEventDispatchThread()
    fun post(r: Runnable) = javax.swing.SwingUtilities.invokeLater(r)

    /** Timers for postDelayed on the main thread (they hop onto it to run). */
    val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "jam-main-timer").apply { isDaemon = true } }

    private val logFile: File by lazy { File(dataDir, "jam.log").apply { if (length() > 8_000_000) delete() } }
    @Synchronized fun log(level: Char, tag: String?, msg: String?, t: Throwable? = null) {
        val line = "${java.time.LocalTime.now()} $level/${tag ?: "-"}: ${msg ?: ""}" + (t?.let { "\n" + it.stackTraceToString() } ?: "")
        if (System.getProperty("jam.log.stdout") != null) println(line)
        runCatching { logFile.appendText(line + "\n") }
    }
}
