package app.jam.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Restarts Jam (for [SavedState]): runs in its own process, waits for the old one to go, then opens
 * Jam again in a fresh one.
 */
class RestartActivity : Activity() {
    companion object { const val PID = "pid" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = intent.getIntExtra(PID, -1)
        if (old > 0 && old != Process.myPid()) Process.killProcess(old)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            Process.killProcess(Process.myPid())
        }, 600)
    }
}
