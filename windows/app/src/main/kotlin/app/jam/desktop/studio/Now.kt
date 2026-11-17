package app.jam.desktop.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.delay

/** What plays, as the Studio look reads it: the app's own now-playing state (both looks use it). */
object Now {
    private val s get() = Graph.currentSongState
    val title: String @Composable get() = s.title.value
    val artist: String @Composable get() = s.singer.value
    val album: String @Composable get() = s.album.value
    val cover: String @Composable get() = s.coverUri.value
    val playing: Boolean @Composable get() = s.playingState.value
    val shuffle: Boolean @Composable get() = s.shuffle.value
    val liked: Boolean @Composable get() = s.likeState.value
    val repeat: Int @Composable get() = SongPlayer.repeatMode.value
    val hasSong: Boolean @Composable get() = s.title.value.isNotBlank()

    fun toggle() { if (SongPlayer.isPlaying()) SongPlayer.pause() else SongPlayer.play() }
    fun next() = SongPlayer.next()
    fun previous() = SongPlayer.previous()
    fun seek(ms: Long) = SongPlayer.seekTo(ms)
    fun shuffle(on: Boolean) = SongPlayer.setShuffle(on)
    fun cycleRepeat() = SongPlayer.cycleRepeat()
    fun like(on: Boolean) = SongPlayer.setLiked(on)
    var volume: Int
        get() = SongPlayer.remoteVolume
        set(v) = SongPlayer.setRemoteVolume(v)
    fun duration(): Long = SongPlayer.getDuration()
    fun position(): Long = SongPlayer.getCurrentPosition()
}

/** The position, read each frame ([smooth], for the lyrics) or a few times a second. */
@Composable
fun rememberPosition(smooth: Boolean): State<Long> {
    val pos = remember { mutableLongStateOf(Now.position()) }
    LaunchedEffect(smooth) {
        while (true) {
            if (smooth) withFrameMillis { pos.longValue = Now.position() }
            else { pos.longValue = Now.position(); delay(250) }
        }
    }
    return pos
}

fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
