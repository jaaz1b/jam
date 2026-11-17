package app.jam.desktop.dev

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.jam.desktop.shim.DesktopApp

/** Development check of the playback engine: plays the files given, prints what the player says. */
fun main(args: Array<String>) {
    val ctx = DesktopApp.context
    javax.swing.SwingUtilities.invokeAndWait {
        val p = ExoPlayer.Builder(ctx).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) = println("state $s pos ${p.currentPosition} dur ${p.duration}")
            override fun onMediaItemTransition(m: MediaItem?, r: Int) = println("transition ${m?.mediaId} reason $r")
            override fun onIsPlayingChanged(b: Boolean) = println("playing $b")
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) = println("error ${e.errorCodeName} ${e.message}")
        })
        p.setMediaItems(args.mapIndexed { i, f -> MediaItem.Builder().setMediaId("item$i").setUri(android.net.Uri.fromFile(java.io.File(f))).build() })
        p.prepare(); p.play()
        Thread {
            repeat(30) { Thread.sleep(500); javax.swing.SwingUtilities.invokeLater { println("t=${it * 0.5}s idx=${p.currentMediaItemIndex} pos=${p.currentPosition} buffered=${p.bufferedPosition}") } }
            javax.swing.SwingUtilities.invokeLater { p.seekTo(1, 1000); println("seek to item 1 @1s") }
            Thread.sleep(3000); javax.swing.SwingUtilities.invokeLater { println("after seek idx=${p.currentMediaItemIndex} pos=${p.currentPosition}"); p.setPlaybackSpeed(1.5f) }
            Thread.sleep(2000); javax.swing.SwingUtilities.invokeLater { println("speed 1.5 pos=${p.currentPosition}"); p.release(); System.exit(0) }
        }.start()
    }
}
