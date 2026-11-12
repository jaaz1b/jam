package app.jam.android

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Jam's two modes. Spotify (the default): the Jam app (Orpheon's, app.myspoti.*), with its
 * extensions. YouTube Music: Jam for Android's YouTube Music app ([MainActivity], the Rust engine).
 *
 * One mode runs per process. A switch is saved, then the app restarts in the other mode: the Spotify
 * session (its hidden Web Player, downloads, extensions) only starts in Spotify mode
 * (MyspotiApplication), and the YouTube Music engine only when its activity opens. In YouTube Music
 * mode the Spotify mode's outside entry points (its media service for Android Auto and headset
 * buttons, its widget, the watch link) are turned off, so nothing starts the Spotify side there.
 */
object JamMode {
    private const val PREFS = "jam_mode"
    private const val KEY_YTM = "ytm"

    /** Spotify mode's components that something outside the app can start on its own. */
    private val SPOTIFY_ENTRY_POINTS = listOf(
        "app.myspoti.host.android.PlaybackService",
        "androidx.media.session.MediaButtonReceiver",
        "com.music.spotui.widget.NowPlayingWidget",
        "com.music.spotui.wear.WearLinkService",
    )

    /** Playback and download services of both modes, stopped before a restart. */
    private val SERVICES = listOf(
        "app.myspoti.host.android.PlaybackService",
        "app.jam.android.PlaybackService",
        "app.jam.android.DownloadService",
    )

    /** YouTube Music mode is on. */
    fun ytm(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_YTM, false)

    /**
     * Spotify mode's launcher activity, first thing in its onCreate: in YouTube Music mode, opens
     * YouTube Music instead and says so (the caller finishes without building anything).
     */
    fun openYtmInstead(activity: Activity): Boolean {
        if (!ytm(activity)) return false
        activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.intent?.data?.takeIf { it.host == "music.youtube.com" }?.let { data = it; action = Intent.ACTION_VIEW }
        })
        activity.overridePendingTransition(0, 0)
        return true
    }

    /** Saves the mode, turns the other mode's entry points off or on, and restarts the app in it. */
    fun switchTo(activity: Activity, ytm: Boolean) {
        val ctx = activity.applicationContext
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_YTM, ytm).commit()
        applyEntryPoints(ctx)
        for (name in SERVICES) runCatching { ctx.stopService(Intent().setClassName(ctx, name)) }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component ?: return
        activity.startActivity(Intent.makeRestartActivityTask(launch))
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /** Spotify mode's outside entry points: on in Spotify mode, off in YouTube Music mode. */
    fun applyEntryPoints(context: Context) {
        val state = if (ytm(context)) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        val pm = context.packageManager
        for (name in SPOTIFY_ENTRY_POINTS) runCatching {
            val c = ComponentName(context, name)
            if (pm.getComponentEnabledSetting(c) != state) pm.setComponentEnabledSetting(c, state, PackageManager.DONT_KILL_APP)
        }
    }
}
