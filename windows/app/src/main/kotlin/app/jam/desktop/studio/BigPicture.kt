package app.jam.desktop.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jam.desktop.shim.Images
import coil3.compose.AsyncImage
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay

/**
 * Big Picture: the song full screen, as Apple Music shows it — its cover drifting, blurred, behind
 * everything; the cover, the song and its controls on one side; the lyrics on the other, large,
 * the line sung bright and the others dimmed and softened, scrolling with the song.
 */
@Composable
fun BigPicture() {
    androidx.activity.compose.BackHandler { Studio.bigPicture.value = false }
    val cover = Now.cover
    Box(Modifier.fillMaxSize().background(Color(0xFF07070A)).onPointerEvent(PointerEventType.Press) { }) {
        LivingCover(cover)
        val lyricsVm: LyricsViewModel = app.jam.desktop.compat.viewModel()
        val title = Now.title
        val artist = Now.artist
        LaunchedEffect(title, artist) { SongPlayer.currentLyricsTrack()?.let { lyricsVm.load(it) } }
        val state by lyricsVm.state.collectAsState()
        val loaded = devLyrics?.let { LyricsViewModel.State.Loaded(it) } ?: state as? LyricsViewModel.State.Loaded
        val showLyrics = Studio.lyrics.value && loaded != null
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            val wide = maxWidth > 900.dp
            val split by animateFloatAsState(if (showLyrics && wide) 0.42f else 1f, spring(stiffness = Spring.StiffnessLow))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().weight(split), contentAlignment = Alignment.Center) { SongSide(showLyrics && wide) }
                if (showLyrics && wide) {
                    Spacer(Modifier.width(48.dp))
                    Box(Modifier.fillMaxHeight().weight(1f - split + 0.0001f)) { LyricsColumn(loaded!!.lyrics.lines, loaded.lyrics.synced) }
                }
            }
            if (showLyrics && !wide) Box(Modifier.fillMaxSize().padding(top = 0.dp)) { LyricsColumn(loaded!!.lyrics.lines, loaded.lyrics.synced) }
        }
        // Top corners: close, and the lyrics' size and switch.
        Row(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Glyph(Icons.Rounded.KeyboardArrowDown, "Close (Esc)", size = 28.dp) { Studio.bigPicture.value = false }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showLyrics) {
                Glyph(Icons.Rounded.TextDecrease, "Smaller lyrics", size = 20.dp) { Studio.setLyricsScale((Studio.lyricsScale.floatValue - 0.1f).coerceAtLeast(0.6f)) }
                Glyph(Icons.Rounded.TextIncrease, "Larger lyrics", size = 20.dp) { Studio.setLyricsScale((Studio.lyricsScale.floatValue + 0.1f).coerceAtMost(1.6f)) }
            }
            Glyph(Icons.Rounded.Lyrics, if (loaded == null) "No lyrics for this song" else "Lyrics", active = Studio.lyrics.value && loaded != null, size = 21.dp, enabled = loaded != null, tint = Color.White) {
                Studio.setLyrics(!Studio.lyrics.value)
            }
            Glyph(Icons.Rounded.QueueMusic, "Queue", size = 21.dp) { Studio.open(com.music.spotui.ui.navigation.Routes.Queue.route) }
        }
    }
}

/** The cover, three times, enlarged, blurred and slowly turning: the song's colours, moving. */
@Composable
private fun LivingCover(cover: String) {
    val t = rememberInfiniteTransition()
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)))
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse))
    AnimatedContent(cover, transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) }, modifier = Modifier.fillMaxSize()) { c ->
        Box(Modifier.fillMaxSize()) {
            if (c.isNotBlank()) {
                val model = Images.request(c, 160)
                AsyncImage(model, null, imageLoader = Images.loader, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.9f; scaleY = 1.9f; rotationZ = a }.blur(90.dp))
                AsyncImage(model, null, imageLoader = Images.loader, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.3f + b * 0.4f; scaleY = 1.3f + b * 0.4f; rotationZ = -a * 1.6f; translationX = (b - 0.5f) * size.width * 0.3f; alpha = 0.7f
                    }.blur(110.dp))
            }
            // Depth: darker at the edges and where the lyrics are read.
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), radius = 1800f)))
        }
    }
}

@Composable
private fun SongSide(withLyrics: Boolean) {
    val pos by rememberPosition(smooth = false)
    val dur = Now.duration()
    val artSize by animateDpAsState(if (withLyrics) 420.dp else 520.dp, spring(stiffness = Spring.StiffnessLow))
    Column(Modifier.widthIn(max = artSize), horizontalAlignment = Alignment.Start) {
        AnimatedContent(Now.cover, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) }) { c ->
            val playing = Now.playing
            val scale by animateFloatAsState(if (playing) 1f else 0.9f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            AsyncImage(
                Images.request(c, 1000), Now.title, imageLoader = Images.loader, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(40.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF)),
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Now.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
                Text(Now.artist, color = Color(0xB3FFFFFF), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val liked = Now.liked
            Glyph(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", active = liked, size = 24.dp, tint = Color.White) { Now.like(!liked) }
        }
        Spacer(Modifier.height(18.dp))
        Seek(pos, dur, Modifier.fillMaxWidth().height(18.dp), color = Color.White)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(time(pos), color = Color(0x99FFFFFF), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + time((dur - pos).coerceAtLeast(0)), color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val shuffle = Now.shuffle
            val repeat = Now.repeat
            Glyph(Icons.Rounded.Shuffle, "Shuffle", active = shuffle, size = 22.dp, tint = Color.White) { Now.shuffle(!shuffle) }
            Glyph(Icons.Rounded.SkipPrevious, "Previous", size = 40.dp, tint = Color.White) { Now.previous() }
            PlayButton(68.dp, light = true)
            Glyph(Icons.Rounded.SkipNext, "Next", size = 40.dp, tint = Color.White) { Now.next() }
            Glyph(if (repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", active = repeat != 0, size = 22.dp, tint = Color.White) { Now.cycleRepeat() }
        }
        Spacer(Modifier.height(14.dp))
        Volume(Modifier.fillMaxWidth().padding(horizontal = 10.dp))
    }
}

/**
 * The lyrics: synced, the line sung large and bright at a third of the height, the lines around
 * it dimmer and blurred the further they are (sharp again while the mouse is over them, to read or
 * pick one); a click on a line plays from it. Word-synced lyrics light up word by word.
 */
@Composable
private fun LyricsColumn(lines: List<LyricLine>, synced: Boolean) {
    val pos by rememberPosition(smooth = true)
    val list = rememberLazyListState()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    val active by remember(lines) {
        derivedStateOf { if (!synced) -1 else lines.indexOfLast { it.timeMs <= pos + 250 }.coerceAtLeast(0) }
    }
    val scale = Studio.lyricsScale.floatValue
    BoxWithConstraints(Modifier.fillMaxSize().hoverable(hover)) {
        val anchor = with(LocalDensity.current) { (maxHeight * 0.32f).roundToPx() }
        LaunchedEffect(active, lines) {
            if (!synced || active < 0) return@LaunchedEffect
            if (System.currentTimeMillis() - userScrollAt < 2500) return@LaunchedEffect
            val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active + 1 }
            if (info != null) list.animateScrollBy((info.offset - anchor).toFloat(), spring(dampingRatio = 0.9f, stiffness = 60f))
            else list.scrollToItem(active + 1, -anchor)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { userScrollAt = System.currentTimeMillis() },
            contentPadding = PaddingValues(bottom = maxHeight * 0.6f),
        ) {
            item { Spacer(Modifier.height(maxHeight * 0.32f)) }
            itemsIndexed(lines) { i, line ->
                val d = if (active < 0) 0 else kotlin.math.abs(i - active)
                val isActive = i == active
                val alpha by animateFloatAsState(
                    when { !synced -> 0.85f; isActive -> 1f; i < active -> 0.32f; else -> 0.42f }, tween(380),
                )
                val blurAmount by animateDpAsState(if (!synced || hovered || isActive) 0.dp else (d * 1.1f).coerceAtMost(5f).dp, tween(500))
                val grow by animateFloatAsState(if (isActive || !synced) 1f else 0.94f, spring(dampingRatio = 0.75f, stiffness = 120f))
                val words = line.words
                val text = if (isActive && words != null) buildAnnotatedString {
                    words.forEachIndexed { wi, w ->
                        val sung = w.startMs <= pos
                        withStyle(SpanStyle(color = if (sung) Color.White else Color(0x66FFFFFF))) { append(w.text) }
                        if (wi < words.lastIndex && !w.text.endsWith(" ") && !words[wi + 1].text.startsWith(" ")) append("")
                    }
                } else buildAnnotatedString { append(line.text.ifBlank { "♪" }) }
                Column(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = TransformOrigin(0f, 0.5f); this.alpha = alpha }
                        .blur(blurAmount)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (synced && line.timeMs > 0) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { Now.seek(line.timeMs); userScrollAt = 0 } else Modifier)
                        .padding(horizontal = 14.dp, vertical = (10 * scale).dp),
                ) {
                    Text(text, color = Color.White, fontSize = (34 * scale).sp, lineHeight = (42 * scale).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color(0xB3FFFFFF), fontSize = (20 * scale).sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                    line.background?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color(0x99FFFFFF), fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Development: lyrics from an .lrc file (-Djam.dev.lrc=…), to look at Big Picture without a source. */
private val devLyrics: com.music.spotui.data.entity.Lyrics? by lazy {
    val f = System.getProperty("jam.dev.lrc")?.let { java.io.File(it) }?.takeIf { it.isFile } ?: return@lazy null
    val lines = f.readLines().mapNotNull { l ->
        Regex("^\\[(\\d+):(\\d+)\\.(\\d+)](.*)$").find(l)?.destructured?.let { (m, s, cs, t) ->
            LyricLine(m.toLong() * 60_000 + s.toLong() * 1000 + cs.padEnd(3, '0').take(3).toLong(), t.trim())
        }
    }
    com.music.spotui.data.entity.Lyrics(lines, synced = true)
}
