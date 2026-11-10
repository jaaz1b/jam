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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keeps Jam alive while music plays in the background, and gives it a media notification,
 * lock-screen controls, Bluetooth/headset buttons and Android Auto-style metadata.
 */
class PlaybackService : Service() {
    companion object {
        private const val TAG = "JamPlayback"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
        private const val ACTION = "app.jam.CONTROL"

        @Volatile private var instance: PlaybackService? = null
        private var pending: NowPlaying? = null

        /** Called by [Player] on every change (main thread). */
        fun sync(context: Context, np: NowPlaying?) {
            val svc = instance
            if (svc != null) { svc.show(np); return }
            if (np == null || !np.playing) return
            pending = np
            try {
                ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, PlaybackService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses to start foreground services from the background; we'll try again on the next report.
                Log.w(TAG, "can't start playback service: ${e.message}")
            }
        }
    }

    private lateinit var session: MediaSessionCompat
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build() }
    private var artUrl: String? = null
    private var art: Bitmap? = null
    private var foreground = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var current: NowPlaying? = null
    private val stopIdle = Runnable { stopSelf() }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Playback controls"
                setShowBadge(false)
            })
        }
        session = MediaSessionCompat(this, "Jam").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Player.control("play")
                override fun onPause() = Player.control("pause")
                override fun onSkipToNext() = Player.control("nexttrack")
                override fun onSkipToPrevious() = Player.control("previoustrack")
                override fun onStop() = Player.control("pause")
                override fun onSeekTo(pos: Long) = Player.control("seekto:" + pos / 1000.0)
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        instance = this
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, PlaybackService::class.java).setAction(ACTION).putExtra("a", action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION -> {
                val a = intent.getStringExtra("a")
                if (a == "dismissed") { if (current?.playing != true) stopEverything() }
                else if (a != null) Player.control(a)
            }
            Intent.ACTION_MEDIA_BUTTON -> androidx.media.session.MediaButtonReceiver.handleIntent(session, intent)
            else -> {
                // Started with startForegroundService(): startForeground() is due right away, whatever the state.
                val np = pending ?: current ?: Player.current
                pending = null
                show(np, force = true)
            }
        }
        return START_NOT_STICKY
    }

    fun show(np: NowPlaying?, force: Boolean = false) {
        current = np
        main.removeCallbacks(stopIdle)
        if (np == null) {
            if (force) goForeground(build(null)) // satisfy the start contract, then leave
            session.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 0f).build())
            stopEverything()
            return
        }
        updateSession(np)
        if (artKey(np) != artUrl) loadArt(np)
        val n = build(np)
        if (np.playing || force) {
            goForeground(n)
            holdWifi(np.playing)
            if (!np.playing) letGo(n)
        } else {
            letGo(n)
            holdWifi(false)
        }
    }

    @SuppressLint("InlinedApi")
    private fun goForeground(n: Notification) {
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
            notifyQuiet(n)
        }
    }

    /** Paused: the notification stays (swipe to dismiss), but Android may reclaim us after a while. */
    private fun letGo(n: Notification) {
        if (foreground) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH); foreground = false }
        notifyQuiet(n)
        main.postDelayed(stopIdle, 30 * 60 * 1000L)
    }

    private fun notifyQuiet(n: Notification) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }

    private fun stopEverything() {
        holdWifi(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun holdWifi(on: Boolean) {
        if (on) {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Jam:playback").apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld == false) runCatching { wifiLock?.acquire() }
        } else if (wifiLock?.isHeld == true) runCatching { wifiLock?.release() }
    }

    private fun updateSession(np: NowPlaying) {
        val md = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, np.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, np.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, np.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, listOf(np.artist, np.sourceName).filter { it.isNotEmpty() }.joinToString(" · "))
        np.duration?.let { if (it > 0) md.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (it * 1000).toLong()) }
        if (artKey(np) == artUrl) art?.let { md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(md.build())
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            (if (np.duration != null && np.duration > 0) PlaybackStateCompat.ACTION_SEEK_TO else 0L)
        val pos = ((np.positionNow() ?: 0.0) * 1000).toLong()
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(if (np.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, pos, if (np.playing) 1f else 0f)
                .build(),
        )
    }

    private fun artKey(np: NowPlaying): String? = np.artworkFile ?: np.artwork

    /** The cover: a downloaded song's saved file first (no network, works offline), else the URL. */
    private fun loadArt(np: NowPlaying) {
        val key = artKey(np)
        val url = np.artwork
        val file = np.artworkFile
        artUrl = key
        art = null
        if (key == null) return
        io.execute {
            var bmp: Bitmap? = null
            if (file != null) {
                bmp = try { java.io.File(file).takeIf { it.isFile }?.readBytes()?.let { decode(it) } } catch (e: Exception) { null }
            }
            // A web app's cover Jam kept (CoverStore): works in a saved state and offline.
            if (bmp == null && url != null) {
                bmp = try { CoverStore.file(url)?.readBytes()?.let { decode(it) } } catch (e: Exception) { null }
            }
            if (bmp == null && url != null) {
                bmp = try {
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) null else r.body?.bytes()?.let { decode(it) }
                    }
                } catch (e: Exception) { null }
            }
            main.post {
                if (artUrl == key && bmp != null) {
                    art = bmp
                    current?.let { show(it) }
                }
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 512 && o.outHeight / (sample * 2) >= 512) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun build(np: NowPlaying?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jam)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(actionIntent("dismissed", 9))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
        if (np == null) return b.setContentTitle("Jam").build()
        b.setContentTitle(np.title)
            .setContentText(np.artist.ifEmpty { np.sourceName })
            .setSubText(np.sourceName)
            .setOngoing(np.playing)
            .addAction(R.drawable.ic_prev, "Previous", actionIntent("previoustrack", 1))
            .addAction(
                if (np.playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (np.playing) "Pause" else "Play",
                actionIntent(if (np.playing) "pause" else "play", 2),
            )
            .addAction(R.drawable.ic_next, "Next", actionIntent("nexttrack", 3))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        if (artKey(np) == artUrl) art?.let { b.setLargeIcon(it) }
        return b.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recents while paused: tidy up. While playing, keep going.
        if (current?.playing != true) { Player.clear(this); stopEverything() }
    }

    override fun onDestroy() {
        instance = null
        main.removeCallbacks(stopIdle)
        holdWifi(false)
        session.isActive = false
        session.release()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
