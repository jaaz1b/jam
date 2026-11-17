package app.jam.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.api.Repeat
import kotlinx.coroutines.delay

/** The player along the bottom: the song, the controls and the time bar, the volume. */
@Composable
fun PlayerBar() {
    val api = Session.api ?: return
    val state by api.player.state.collectAsState()
    val s = state
    val item = s?.item
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) { runCatching { volume = api.player.volume().toFloat() } }

    Row(
        Modifier.fillMaxWidth().height(88.dp).background(Jam.Side).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The song.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Cover(item?.imageUrl, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item?.name ?: "Nothing playing", color = Jam.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item?.artists?.joinToString(", ") { it.name }.orEmpty(), color = Jam.Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The controls and the time bar.
        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Control(Icons.Shuffle, active = s?.shuffle == true) { withApi { it.player.setShuffle(!(s?.shuffle ?: false)) } }
                Control(Icons.Previous) { withApi { it.player.previous() } }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Jam.Text).clickable {
                        withApi { if (s?.isPaused != false) it.player.resume() else it.player.pause() }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (s?.isPaused != false) Icons.Play else Icons.Pause, "Play", tint = Jam.Page, modifier = Modifier.size(24.dp)) }
                Control(Icons.Next) { withApi { it.player.next() } }
                Control(Icons.Repeat, active = s?.repeat != null && s.repeat != Repeat.OFF) {
                    val next = when (s?.repeat) { Repeat.OFF, null -> Repeat.CONTEXT; Repeat.CONTEXT -> Repeat.TRACK; Repeat.TRACK -> Repeat.OFF }
                    withApi { it.player.setRepeat(next) }
                }
            }
            val duration = item?.durationMs ?: 0L
            val position = s?.positionAt(now) ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time(seeking?.toLong() ?: position), color = Jam.Faint, fontSize = 12.sp)
                Slider(
                    value = (seeking ?: position.toFloat()).coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { val to = seeking?.toLong(); seeking = null; if (to != null) withApi { it.player.seek(to) } },
                    modifier = Modifier.width(460.dp).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = Jam.Text, activeTrackColor = Jam.Accent, inactiveTrackColor = Jam.Line),
                )
                Text(time(duration), color = Jam.Faint, fontSize = 12.sp)
            }
        }
        // The volume.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Volume, "Volume", tint = Jam.Muted, modifier = Modifier.size(20.dp))
            Slider(
                value = volume.coerceIn(0f, 1f), onValueChange = { volume = it },
                onValueChangeFinished = { val v = volume.toDouble(); withApi { it.player.setVolume(v) } },
                enabled = volume >= 0f,
                modifier = Modifier.width(130.dp).padding(start = 8.dp),
                colors = SliderDefaults.colors(thumbColor = Jam.Text, activeTrackColor = Jam.Accent, inactiveTrackColor = Jam.Line),
            )
        }
    }
}

@Composable
private fun Control(icon: ImageVector, active: Boolean = false, onClick: () -> Unit) {
    Icon(icon, null, tint = if (active) Jam.AccentLight else Jam.Muted,
        modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClick))
}
