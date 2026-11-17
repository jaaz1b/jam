package app.jam.desktop

import java.io.File
import java.util.Properties

/** Where Jam for Windows keeps its files: %LOCALAPPDATA%\Jam\App on Windows, ~/.local/share/jam-app elsewhere. */
object Paths {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    val dataDir: File by lazy {
        val base = if (isWindows) System.getenv("LOCALAPPDATA")?.let { File(it, "Jam\\App") }
        else File(System.getProperty("user.home"), ".local/share/jam-app")
        (base ?: File(System.getProperty("user.home"), ".jam-app")).apply { mkdirs() }
    }

    /** The hidden browser's own profile: the Spotify sign-in lives there. */
    val webProfile: File get() = File(dataDir, "WebPlayer").apply { mkdirs() }

    /** Files shipped next to the app (jam-ytm.exe...), from the packaged app's resources folder. */
    val appResources: File? get() = System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
}

/** Jam for Windows' settings, a small properties file. */
object Settings {
    private val file = File(Paths.dataDir, "settings.properties")
    private val props = Properties().apply { runCatching { file.inputStream().use { load(it) } } }

    @Synchronized fun get(key: String, default: String = ""): String = props.getProperty(key, default)
    @Synchronized fun set(key: String, value: String) {
        props.setProperty(key, value)
        runCatching { file.outputStream().use { props.store(it, "Jam for Windows") } }
    }

    var ytmMode: Boolean
        get() = get("ytmMode") == "true"
        set(v) = set("ytmMode", v.toString())

    var discordEnabled: Boolean
        get() = get("discordEnabled", "true") == "true"
        set(v) = set("discordEnabled", v.toString())

    /** The Discord application ID of the status; first from Jam's Windows app (Electron) if it has one. */
    var discordClientId: String
        get() = get("discordClientId").ifBlank { electronJamClientId() }
        set(v) = set("discordClientId", v.trim())

    var showPaused: Boolean
        get() = get("discordShowPaused", "false") == "true"
        set(v) = set("discordShowPaused", v.toString())

    private fun electronJamClientId(): String = runCatching {
        val f = File(System.getenv("APPDATA") ?: return@runCatching "", "Jam\\settings.json")
        Regex("\"discordClientId\"\\s*:\\s*\"(\\d{6,})\"").find(f.readText())?.groupValues?.get(1).orEmpty()
    }.getOrDefault("")
}
