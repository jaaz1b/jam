package app.jam.android

import android.app.Application
import android.os.Build
import java.io.File

class JamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        start(this)
    }

    companion object {
        /**
         * Jam for Android's start-up. Also run by the Jam app's Application (MyspotiApplication) in
         * both modes: Discord's presence and its settings are shared by Spotify and YouTube Music mode.
         */
        fun start(app: Application) {
            // Only Jam's own process (a service in another process would need none of this).
            if (!isMainProcess(app)) return
            Prefs.init(app)
            CoverStore.init(app)
            Discord.init(app)
        }

        private fun isMainProcess(app: Application): Boolean {
            val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
            else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault(app.packageName)
            return name == app.packageName
        }
    }
}
