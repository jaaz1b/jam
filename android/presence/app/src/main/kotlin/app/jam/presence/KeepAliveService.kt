package app.jam.presence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * "Stay connected": a quiet ongoing notification that keeps Jam Presence running, so Android doesn't
 * cut its Discord connection while the screen is off. Runs only while that connection is open (a song
 * is showing, or "Stay online"), so not at all while nothing plays.
 */
class KeepAliveService : Service() {
    companion object {
        private const val CHANNEL = "presence"
        private const val ID = 1
        @Volatile private var running: KeepAliveService? = null

        /** Only while connected to Discord (a song is showing, or "Stay online"): nothing to keep alive otherwise. */
        private fun wanted(context: Context): Boolean =
            Prefs.bool("keepAlive") && Prefs.bool("rpcEnabled") && Prefs.secret("discord.access") != null && MediaListener.allowed(context) &&
                (Discord.state == Discord.State.CONNECTED || Discord.state == Discord.State.CONNECTING)

        /** Starts or stops the service to match the settings. Safe to call from anywhere. */
        fun sync(context: Context) {
            val app = context.applicationContext
            if (wanted(app)) {
                if (running != null) { running?.refresh(); return }
                try { app.startForegroundService(Intent(app, KeepAliveService::class.java)) }
                catch (e: Exception) { Log.w("JamPresence", "can't stay connected from the background: $e") } // Android 12+: from the background; the next app open starts it
            } else running?.let { it.stopForeground(Service.STOP_FOREGROUND_REMOVE); it.stopSelf() }
        }

        fun onNowPlaying(context: Context, np: NowPlaying?) { running?.refresh() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Discord connection", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Shown while Jam Presence keeps your Discord status up to date."
            setShowBadge(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, build())
        } catch (e: Exception) {
            Log.w("JamPresence", "foreground: $e"); stopSelf(); return START_NOT_STICKY
        }
        running = this
        if (!wanted(this)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    fun refresh() {
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, build()) }
    }

    private fun build(): Notification {
        val np = Player.current
        val shown = np != null && Presence.build(np) != null
        val text = when {
            Discord.state == Discord.State.CONNECTING -> "Connecting to Discord…"
            Discord.state == Discord.State.ERROR -> "Discord: ${Discord.lastError ?: "problem"} — tap to fix"
            shown -> "On Discord: ${np!!.title}" + (if (np.artist.isNotEmpty()) " · ${np.artist}" else "")
            else -> "Waiting for music"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jam)
            .setContentTitle(if (shown) "Showing on Discord" else "Jam Presence")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }
}
