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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

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
    /**
     * The volume: of the device playing when it is another (Spotify Connect), else of this PC —
     * the Web Player's own (Spotify's audio plays in its page) and Jam's engine's (extensions',
     * local files'). Before, it only ever set the other device's, so on the PC it did nothing.
     */
    var volume: Int
        get() = if (SongPlayer.activeDevice.value != null) SongPlayer.remoteVolume else (app.jam.desktop.media.Volume.level * 100).toInt()
        set(v) {
            val level = v.coerceIn(0, 100)
            if (SongPlayer.activeDevice.value != null) { SongPlayer.setRemoteVolume(level); return }
            app.jam.desktop.media.Volume.level = level / 100f
            webVolume.value = level
        }

    /** The Web Player's volume, set once the dragging settles (each set is a call into its page). */
    private val webVolume = kotlinx.coroutines.flow.MutableStateFlow(-1)
    init {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()).launch {
            webVolume.debounce(60).collect { v ->
                if (v >= 0) runCatching { app.myspoti.host.android.MyspotiRuntime.spotify.player.setVolume(v / 100.0) }
            }
        }
    }
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
