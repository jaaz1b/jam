package app.jam

import android.os.SystemClock

/**
 * Jam: start-up timings in logcat (tag JamStartup), for the emulator test's speed report. Only in
 * builds made with -PjamStartupTrace (BuildConfig.JAM_STARTUP_TRACE); otherwise it costs a branch.
 */
object JamStartup {
    @JvmStatic
    inline fun <T> step(name: String, block: () -> T): T {
        if (!app.myspoti.engine.BuildConfig.JAM_STARTUP_TRACE) return block()
        val t = SystemClock.elapsedRealtimeNanos()
        try { return block() } finally {
            android.util.Log.w("JamStartup", "$name ${(SystemClock.elapsedRealtimeNanos() - t) / 100_000 / 10.0} ms (main=${android.os.Looper.myLooper() == android.os.Looper.getMainLooper()})")
        }
    }

    fun mark(name: String) {
        if (!app.myspoti.engine.BuildConfig.JAM_STARTUP_TRACE) return
        android.util.Log.w("JamStartup", "$name at ${SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()} ms since process start")
    }
}
