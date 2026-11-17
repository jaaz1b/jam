package app.jam.desktop

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.mutableStateOf
import app.jam.desktop.shim.Desk
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * The app's media session on the PC: the keyboard's media keys (play/pause, next, previous, stop)
 * reach its callback, as a headset's buttons do on Android, and what it plays is kept for the
 * window's title and taskbar.
 */
object SystemMedia {
    private var session: MediaSessionCompat? = null
    data class Now(val title: String?, val artist: String?, val playing: Boolean)
    val now = mutableStateOf(Now(null, null, false))

    fun attach(s: MediaSessionCompat) { session = s; keys }
    fun detach(s: MediaSessionCompat) { if (session === s) session = null }
    fun update(s: MediaSessionCompat) {
        if (session !== s) session = s
        val m = s.metadata
        now.value = Now(m?.getString(MediaMetadataCompat.METADATA_KEY_TITLE), m?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST), s.playbackState?.state == PlaybackStateCompat.STATE_PLAYING)
    }

    fun key(k: Int) = Desk.post {
        val cb = session?.callback ?: return@post
        when (k) {
            VK_PLAY_PAUSE -> if (session?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) cb.onPause() else cb.onPlay()
            VK_NEXT -> cb.onSkipToNext()
            VK_PREV -> cb.onSkipToPrevious()
            VK_STOP -> cb.onStop()
        }
    }

    private const val VK_NEXT = 0xB0; private const val VK_PREV = 0xB1; private const val VK_STOP = 0xB2; private const val VK_PLAY_PAUSE = 0xB3

    /** The media keys, registered once (Windows): a thread with its own message loop. */
    private val keys: Unit by lazy {
        if (!Desk.isWindows) return@lazy
        Thread({
            val u = User32.INSTANCE
            val ids = listOf(VK_PLAY_PAUSE, VK_NEXT, VK_PREV, VK_STOP)
            ids.forEachIndexed { i, vk -> if (!u.RegisterHotKey(null, 0x4A00 + i, 0x4000 /* MOD_NOREPEAT */, vk)) Desk.log('W', "SystemMedia", "media key $vk taken by another app", null) }
            val msg = WinUser.MSG()
            while (u.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WinUser.WM_HOTKEY) ids.getOrNull(msg.wParam.toInt() - 0x4A00)?.let(::key)
            }
        }, "jam-media-keys").apply { isDaemon = true }.start()
    }
}
