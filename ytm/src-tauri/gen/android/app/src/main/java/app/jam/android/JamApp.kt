package app.jam.android

import android.app.Application
import android.os.Build
import java.io.File

class JamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // RestartActivity runs in a process of its own, which needs none of this.
        if (!isMainProcess()) return
        Prefs.init(this)
        Discord.init(this)
        // A saved state asked for before Jam restarted (SavedState): made now, before anything
        // opens the site's data.
        SavedState.applyPending(this)
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault(packageName)
        return name == packageName
    }
}
