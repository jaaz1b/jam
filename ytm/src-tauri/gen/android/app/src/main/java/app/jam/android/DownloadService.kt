package app.jam.android

import android.annotation.SuppressLint
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** What the engine's download queue looks like right now (`downloads.rs` → JamPlugin.downloadStatus). */
data class DownloadStatus(
    /** Queued or running. 0: the queue just emptied. */
    val pending: Int,
    val running: Int,
    /** Finished / failed since the queue was last empty. */
    val finished: Int,
    val failed: Int,
    val title: String?,
    /** All of it, 0..1. */
    val progress: Double,
)

/**
 * YouTube Music downloads, like Meld's ExoDownloadService: keeps Jam alive while songs download in
 * the background, with a progress notification and Cancel. When the queue empties it leaves a
 * "Downloaded N songs" notification behind and stops.
 */
class DownloadService : Service() {
    companion object {
        private const val TAG = "JamDownloads"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 2
        private const val DONE_ID = 3
        private const val CANCEL = "app.jam.CANCEL_DOWNLOADS"

        @Volatile private var instance: DownloadService? = null
        @Volatile private var latest: DownloadStatus? = null

        fun update(context: Context, st: DownloadStatus) {
            latest = st
            val app = context.applicationContext
            val svc = instance
            if (st.pending == 0) {
                svc?.finish(st) ?: done(app, st)
                return
            }
            if (svc != null) { svc.show(st); return }
            try {
                ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
            } catch (e: Exception) {
                // Android 12+ won't start one from the background; downloads still run while Jam does.
                Log.w(TAG, "can't start the download service: ${e.message}")
                channel(app)
                app.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(app, st))
            }
        }

        private fun channel(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "YouTube Music songs downloading for offline listening"
                    setShowBadge(false)
                })
            }
        }

        private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 10, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun build(c: Context, st: DownloadStatus): Notification {
            val done = st.finished
            val total = st.pending + st.finished
            val title = if (total <= 1) "Downloading 1 song" else "Downloading ${done + 1} of $total"
            val cancel = PendingIntent.getService(
                c, 11, Intent(c, DownloadService::class.java).setAction(CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(st.title ?: "Waiting to start")
                .setProgress(1000, (st.progress.coerceIn(0.0, 1.0) * 1000).toInt(), st.running == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openApp(c))
                .addAction(0, "Cancel", cancel)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        /** The queue emptied: say how it went, in a notification the user can swipe away. */
        private fun done(c: Context, st: DownloadStatus) {
            channel(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_ID)
            if (st.finished == 0 && st.failed == 0) return
            val title = when {
                st.failed == 0 -> if (st.finished == 1) "Downloaded 1 song" else "Downloaded ${st.finished} songs"
                st.finished == 0 -> if (st.failed == 1) "1 download failed" else "${st.failed} downloads failed"
                else -> "Downloaded ${st.finished} of ${st.finished + st.failed} songs"
            }
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(if (st.failed > 0) "Open Downloads in YouTube Music to try again" else "Ready to play offline")
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(openApp(c))
                .build()
            try { nm.notify(DONE_ID, n) } catch (e: SecurityException) { /* notifications are off */ }
        }
    }

    override fun onCreate() {
        super.onCreate()
        channel(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            JamPlugin.sendControl("cancel-downloads")
            // The engine answers with a final status; until it does, don't leave a bar that looks live.
            latest?.let { finish(it.copy(pending = 0, finished = 0, failed = 0)) } ?: stopSelf()
            return START_NOT_STICKY
        }
        val st = latest
        if (st == null || st.pending == 0) {
            // startForegroundService() demands startForeground() whatever happened since.
            goForeground(build(this, st ?: DownloadStatus(1, 0, 0, 0, null, 0.0)))
            finish(st ?: DownloadStatus(0, 0, 0, 0, null, 1.0))
            return START_NOT_STICKY
        }
        goForeground(build(this, st))
        return START_NOT_STICKY
    }

    fun show(st: DownloadStatus) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(this, st))
        } catch (e: SecurityException) { /* notifications are off; the service still runs */ }
    }

    @SuppressLint("InlinedApi")
    private fun goForeground(n: Notification) {
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    fun finish(st: DownloadStatus) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        done(this, st)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15 caps dataSync services at 6 hours a day: stop cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "download service timed out")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
