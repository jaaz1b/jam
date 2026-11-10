package app.jam.android

import android.app.Application
import android.os.Build
import java.io.File

class JamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only Jam's own process (a service in another process would need none of this).
        if (!isMainProcess()) return
        Prefs.init(this)
        Discord.init(this)
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault(packageName)
        return name == packageName
    }
}
