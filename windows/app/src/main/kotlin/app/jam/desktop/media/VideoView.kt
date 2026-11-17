package app.jam.desktop.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The video of what a [DesktopPlayer] plays (the Canvas loop, a music video), as PlayerView and
 * TextureView show it on Android: ffmpeg decodes the same item from the player's position, paced in
 * real time (-re), and is started again when the player pauses, seeks or moves to another item.
 */
object VideoView {
    /** TextureViews given to a player (setVideoTextureView). */
    val targets = ConcurrentHashMap<android.view.TextureView, DesktopPlayer>()

    private class Decoder(val player: DesktopPlayer, val url: String, val fromMs: Long, val maxWidth: Int, val onFrame: (ImageBitmap, Int, Int) -> Unit) {
        @Volatile var stopped = false
        val startedAt = System.currentTimeMillis()
        private var proc: Process? = null
        fun start() = Thread({
            val p = runCatching {
                ProcessBuilder(
                    Ffmpeg.exe, "-hide_banner", "-nostdin", "-loglevel", "info", "-re",
                    "-ss", "%.3f".format(java.util.Locale.ROOT, fromMs / 1000.0), "-i", url,
                    "-an", "-sn", "-dn", "-map", "0:v:0?", "-vf", "scale='min($maxWidth,iw)':-2", "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1",
                ).start()
            }.getOrNull() ?: return@Thread
            proc = p
            var size: Pair<Int, Int>? = null
            val sizeLock = Object()
            Thread({
                var output = false
                runCatching {
                    p.errorStream.bufferedReader().forEachLine { l ->
                        if (l.startsWith("Output #0")) output = true
                        if (output && size == null && "Video:" in l) Regex(", (\\d{2,5})x(\\d{2,5})").find(l)?.let { m ->
                            size = m.groupValues[1].toInt() to m.groupValues[2].toInt()
                            synchronized(sizeLock) { sizeLock.notifyAll() }
                        }
                    }
                }
                synchronized(sizeLock) { sizeLock.notifyAll() }
            }, "jam-video-log").apply { isDaemon = true }.start()
            synchronized(sizeLock) { if (size == null) sizeLock.wait(15_000) }
            val (w, h) = size ?: run { p.destroyForcibly(); return@Thread }
            val frame = ByteArray(w * h * 4)
            val input = p.inputStream.buffered(frame.size * 2)
            val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
            try {
                while (!stopped) {
                    var got = 0
                    while (got < frame.size) { val n = input.read(frame, got, frame.size - got); if (n < 0) break; got += n }
                    if (got < frame.size) break
                    val bmp = Bitmap().apply { allocPixels(info); installPixels(info, frame, w * 4); setImmutable() }
                    if (!stopped) onFrame(bmp.asComposeImageBitmap(), w, h)
                }
            } catch (_: Throwable) {}
            p.destroyForcibly()
        }, "jam-video").apply { isDaemon = true }.start()
        fun stop() { stopped = true; proc?.destroyForcibly() }
    }

    /** Draws the video of the player given to [view], filling the box (zoom) or fitting it. */
    @Composable
    fun Video(view: android.view.View, modifier: Modifier, zoom: Boolean, playerOf: () -> DesktopPlayer?) {
        var image by remember { mutableStateOf<ImageBitmap?>(null) }
        DisposableEffect(view) {
            var decoder: Decoder? = null
            var shownUrl: String? = null
            val watch = Thread({
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val player = playerOf()
                        if (player == null || decoder?.player.let { it != null && it !== player }) { decoder?.stop(); decoder = null }
                        if (player == null) { TimeUnit.MILLISECONDS.sleep(400); continue }
                        val url = player.videoSource()
                        val playing = player.isPlaying
                        val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                        val d = decoder
                        val expected = d?.let { it.fromMs + (System.currentTimeMillis() - it.startedAt) }
                        val restart = url != null && playing && (d == null || d.stopped || d.url != url || (expected != null && kotlin.math.abs(expected - pos) > 1500))
                        if ((!playing || url == null) && d != null) { d.stop(); decoder = null }
                        if (restart) {
                            d?.stop()
                            decoder = Decoder(player, url!!, pos, 960) { img, w, h ->
                                image = img
                                if (shownUrl != url) { shownUrl = url; player.videoShown(w, h) }
                            }.also { it.start() }
                        }
                        TimeUnit.MILLISECONDS.sleep(400)
                    }
                } catch (_: InterruptedException) {}
            }, "jam-video-watch").apply { isDaemon = true; start() }
            onDispose { watch.interrupt(); decoder?.stop() }
        }
        Box(modifier) {
            image?.let { Image(it, null, Modifier.matchParentSize(), contentScale = if (zoom) ContentScale.Crop else ContentScale.Fit) }
        }
    }
}
