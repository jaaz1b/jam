package app.jam.desktop

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.mutableStateOf
import app.jam.desktop.shim.Desk
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * The app's media session on the PC: Windows' media controls (the flyout of the volume keys, the
 * lock screen, the taskbar's) show what it plays through jam-smtc.exe, and their buttons and the
 * keyboard's media keys reach its callback, as a headset's do on Android. Without the helper the
 * media keys are registered as hotkeys. What it plays is also kept for the window's title.
 */
object SystemMedia {
    private var session: MediaSessionCompat? = null
    data class Now(val title: String?, val artist: String?, val playing: Boolean)
    val now = mutableStateOf(Now(null, null, false))

    fun attach(s: MediaSessionCompat) { session = s; if (!Smtc.start()) keys }
    fun detach(s: MediaSessionCompat) { if (session === s) session = null }
    fun update(s: MediaSessionCompat) {
        if (session !== s) session = s
        val m = s.metadata
        now.value = Now(m?.getString(MediaMetadataCompat.METADATA_KEY_TITLE), m?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST), s.playbackState?.state == PlaybackStateCompat.STATE_PLAYING)
        Smtc.update(s)
    }

    /** The same on Windows' side: the button names jam-smtc.exe writes. */
    fun button(b: String) = Desk.post {
        val s = session ?: return@post
        val cb = s.callback ?: return@post
        when {
            b == "play" -> cb.onPlay(); b == "pause" -> cb.onPause(); b == "stop" -> cb.onStop()
            b == "next" -> cb.onSkipToNext(); b == "previous" -> cb.onSkipToPrevious()
            b.startsWith("seek\t") -> b.substringAfter('\t').toLongOrNull()?.let(cb::onSeekTo)
        }
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

/** jam-smtc.exe, shipped next to ffmpeg: Windows' SystemMediaTransportControls for Jam. */
private object Smtc {
    private var proc: Process? = null
    private var out: java.io.Writer? = null
    private var lastMeta = ""
    private var lastState = ""
    @Volatile private var timeline: Pair<Long, Long>? = null
    private var playing = false
    private var stamp = 0L

    @Synchronized fun start(): Boolean {
        if (proc?.isAlive == true) return true
        if (!Desk.isWindows) return false
        val exe = System.getProperty("compose.application.resources.dir")?.let { java.io.File(it, "jam-smtc.exe") }?.takeIf { it.isFile } ?: return false
        return runCatching {
            val p = ProcessBuilder(exe.absolutePath).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            proc = p; out = p.outputStream.bufferedWriter(Charsets.UTF_8)
            lastMeta = ""; lastState = ""
            Thread({ runCatching { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { if (it != "ready") SystemMedia.button(it) } } }, "jam-smtc").apply { isDaemon = true }.start()
            // Windows does not move the position on by itself everywhere: a refresh every few seconds.
            Thread({
                while (p.isAlive) {
                    Thread.sleep(4000)
                    val t = timeline
                    if (playing && t != null) send("timeline\t${(t.first + System.currentTimeMillis() - stamp).coerceAtMost(t.second)}\t${t.second}")
                }
            }, "jam-smtc-timeline").apply { isDaemon = true }.start()
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { p.destroy() } })
            true
        }.getOrElse { Desk.log('W', "SystemMedia", "jam-smtc.exe did not start", it); false }
    }

    @Synchronized private fun send(line: String) {
        val w = out ?: return
        runCatching { w.write(line.replace('\n', ' ')); w.write("\n"); w.flush() }.onFailure { out = null }
    }

    private fun clean(v: String?) = v.orEmpty().replace('\t', ' ')

    fun update(s: MediaSessionCompat) {
        if (proc?.isAlive != true) return
        val m = s.metadata
        val st = s.playbackState
        if (m != null) {
            val art = m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)?.takeIf { it.startsWith("http") }
                ?: m.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)?.takeIf { it.startsWith("http") }
                ?: m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)?.let { android.net.Uri.parse(it) }?.let { app.jam.desktop.shim.Files.file(it) }?.takeIf { it.isFile }?.absolutePath
                ?: (m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))?.let { bmp ->
                    runCatching {
                        val f = java.io.File(Desk.cacheDir, "smtc-cover-${System.identityHashCode(bmp)}.png")
                        if (!f.isFile) { Desk.cacheDir.listFiles { x -> x.name.startsWith("smtc-cover-") }?.forEach { it.delete() }; f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                        f.absolutePath
                    }.getOrNull()
                }
            val meta = "meta\t${clean(m.getString(MediaMetadataCompat.METADATA_KEY_TITLE))}\t${clean(m.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))}\t${clean(m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))}\t${clean(art)}"
            if (meta != lastMeta) { lastMeta = meta; send(meta) }
        }
        if (st != null) {
            playing = st.state == PlaybackStateCompat.STATE_PLAYING || st.state == PlaybackStateCompat.STATE_BUFFERING
            val state = when (st.state) {
                PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> "playing"
                PlaybackStateCompat.STATE_PAUSED -> "paused"
                else -> "stopped"
            }
            if (state != lastState) { lastState = state; send("state\t$state") }
            val dur = m?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
            if (dur > 0) {
                val elapsed = if (playing && st.lastPositionUpdateTime > 0) (android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime).coerceIn(0, 10_000) else 0
                val pos = (st.position + elapsed).coerceIn(0, dur)
                timeline = pos to dur; stamp = System.currentTimeMillis()
                send("timeline\t$pos\t$dur")
            }
            send("caps\t${if (st.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L || st.actions == 0L) 1 else 0}\t${if (st.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L || st.actions == 0L) 1 else 0}")
        }
    }
}
