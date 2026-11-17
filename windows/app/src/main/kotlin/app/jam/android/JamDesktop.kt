// Jam for Windows: the parts of Jam for Android (ytm/src-tauri/gen/android) its shared files need, as the
// PC has them. YouTube Music mode is Jam's YouTube Music app for Windows (jam-ytm.exe, shipped with Jam).
package app.jam.android

import android.app.Activity
import android.content.Context
import app.jam.desktop.shim.Desk
import java.io.File

object JamMode {
    private const val PREFS = "jam_mode"
    private const val KEY_YTM = "ytm"

    fun ytm(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_YTM, false)

    /** Jam's YouTube Music app for Windows, next to Jam. */
    fun ytmExe(): File? = listOfNotNull(System.getProperty("compose.application.resources.dir"), System.getProperty("jam.ytm.dir"))
        .map { File(it, "jam-ytm/jam-ytm.exe") }.firstOrNull { it.isFile }

    /** In YouTube Music mode: Jam's YouTube Music app opens in place of this one (which closes). */
    fun openYtmInstead(activity: Activity): Boolean {
        if (!ytm(activity)) return false
        val exe = ytmExe() ?: run { Desk.log('W', "JamMode", "YouTube Music mode, but jam-ytm.exe is not there: Spotify mode", null); return false }
        runCatching { ProcessBuilder(exe.absolutePath).directory(exe.parentFile).start() }.onFailure { return false }
        return true
    }

    /** Saves the mode and restarts in it: the YouTube Music app, or Jam itself. */
    fun switchTo(activity: Activity, ytm: Boolean) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_YTM, ytm).commit()
        if (ytm) {
            if (openYtmInstead(activity)) { app.jam.desktop.shim.ServiceHost.stopAll(); Runtime.getRuntime().exit(0) }
        } else app.jam.desktop.Restart.now()
    }

    fun applyEntryPoints(context: Context) {}
}

class JamApp {
    companion object {
        fun start(app: android.app.Application) {
            Prefs.init(app)
            CoverStore.init(app)
            Discord.init(app)
        }
    }
}

/** Jam for Android's YouTube Music screen: not in this app (YouTube Music mode is jam-ytm.exe). */
class MainActivity {
    val active: String? = null
    fun discard(id: String) {}
    companion object {
        var instance: MainActivity? = null
        const val profilesSupported = false
        fun profileName(id: String) = "jam-$id"
        fun clearSourceData(context: Context, id: String) {}
    }
}

/** Jam for Android's YouTube Music player service: YouTube Music plays in its own app here. */
object PlaybackService { fun sync(context: Context, np: NowPlaying?) {} }

/** The YouTube Music engine's bridge: not in this app. */
object JamPlugin { fun sendControl(action: String, value: Double? = null) {} }
