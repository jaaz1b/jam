package app.jam.desktop.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.jam.desktop.shim.Images
import coil3.compose.AsyncImage

/** The mini player: a small window that stays on top of the others (Ctrl+M), as Apple Music's. */
object MiniPlayer {
    val open = mutableStateOf(false)
    fun toggle() { open.value = !open.value }
}

@Composable
fun ApplicationScope.MiniPlayerWindow() {
    if (!MiniPlayer.open.value) return
    val state = rememberWindowState(size = DpSize(380.dp, 116.dp), position = WindowPosition(Alignment.BottomEnd))
    Window(
        onCloseRequest = { MiniPlayer.open.value = false }, state = state, title = "Jam mini player",
        undecorated = true, transparent = true, alwaysOnTop = true, resizable = false,
        icon = androidx.compose.ui.res.painterResource("jam.png"),
    ) {
        WindowDraggableArea {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily = com.music.spotui.ui.theme.AppSans)) {
                MiniPlayerContent()
            }
        }
    }
}

@Composable
private fun MiniPlayerContent() {
    val pos by rememberPosition(smooth = false)
    val dur = Now.duration()
    Box(Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111116)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))) {
        if (Now.cover.isNotBlank()) AsyncImage(Images.request(Now.cover, 96), null, imageLoader = Images.loader, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(40.dp).alpha(0.55f))
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(Images.request(Now.cover, 200), Now.title, imageLoader = Images.loader, contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Now.title.ifBlank { "Jam" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Now.artist, color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Icons.Rounded.SkipPrevious, "Previous", size = 22.dp, tint = Color.White) { Now.previous() }
                    PlayButton(32.dp, light = true)
                    Glyph(Icons.Rounded.SkipNext, "Next", size = 22.dp, tint = Color.White) { Now.next() }
                }
                Seek(pos, dur, Modifier.fillMaxWidth().height(3.dp), thin = true, color = Color.White)
            }
            Column {
                Glyph(Icons.Rounded.Close, "Close", size = 14.dp, tint = Color.White) { MiniPlayer.open.value = false }
                Glyph(Icons.Rounded.OpenInFull, "Back to Jam", size = 14.dp, tint = Color.White) { MiniPlayer.open.value = false; app.jam.desktop.Front.request() }
            }
        }
    }
}
