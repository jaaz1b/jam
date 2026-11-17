package app.jam.desktop.dev

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.jam.desktop.shim.DesktopApp

/** Development check of video: a silent looping clip (like a Canvas) in a PlayerView. */
fun main(args: Array<String>) {
    val ctx = DesktopApp.context
    var view: androidx.media3.ui.PlayerView? = null
    javax.swing.SwingUtilities.invokeAndWait {
        val p = ExoPlayer.Builder(ctx).build()
        p.repeatMode = Player.REPEAT_MODE_ALL; p.volume = 0f
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() = println("first frame, size ${p.videoSize.width}x${p.videoSize.height}")
            override fun onMediaItemTransition(m: MediaItem?, r: Int) = println("transition reason $r pos ${p.currentPosition}")
        })
        p.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(args[0]))))
        p.prepare(); p.play()
        view = androidx.media3.ui.PlayerView(ctx).apply { player = p }
        Thread { repeat(24) { Thread.sleep(500); javax.swing.SwingUtilities.invokeLater { println("t=${it * 0.5}s pos=${p.currentPosition} playing=${p.isPlaying}") } }; System.exit(0) }.start()
    }
    application { Window(onCloseRequest = ::exitApplication, title = "video") { app.jam.desktop.ViewNode(view!!) } }
}
