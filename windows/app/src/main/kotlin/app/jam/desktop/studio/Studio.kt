package app.jam.desktop.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.jam.desktop.shim.Images
import app.jam.desktop.themes.Themes
import app.jam.desktop.compat.getString
import coil3.compose.AsyncImage
import com.music.spotui.ui.navigation.Routes

/**
 * Studio: Jam for Windows' own look of the app's window (Settings ▸ Theme ▸ Layout): a sidebar with
 * the library, the player at the top as Apple Music has it, the page in between, and Big Picture —
 * the song full screen with its lyrics. The pages are the app's own (Classic or Immersive).
 */
object Studio {
    private val prefs by lazy { android.content.Prefs.of("jam_studio") }
    /** The Studio layout is on (else the phone layout of the look, as on Android). */
    val on by lazy { mutableStateOf(prefs.getBoolean("on", true)) }
    fun setOn(v: Boolean) { on.value = v; prefs.edit().putBoolean("on", v).apply() }
    /** Big Picture: the song full screen, lyrics beside it. */
    val bigPicture = mutableStateOf(false)
    /** Big Picture opens on its lyrics (else the song alone). */
    val lyrics by lazy { mutableStateOf(prefs.getBoolean("lyrics", true)) }
    fun setLyrics(v: Boolean) { lyrics.value = v; prefs.edit().putBoolean("lyrics", v).apply() }
    /** Lyrics size in Big Picture (1 = default). */
    val lyricsScale by lazy { mutableFloatStateOf(prefs.getFloat("lyrics_scale", 1f)) }
    fun setLyricsScale(v: Float) { lyricsScale.floatValue = v; prefs.edit().putFloat("lyrics_scale", v).apply() }

    var nav: NavHostController? = null

    fun open(route: String) {
        val n = nav ?: return
        bigPicture.value = false
        val top = listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route)
        if (route in top) n.navigate(route) {
            popUpTo(n.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        } else n.navigate(route) { launchSingleTop = true }
    }
}

/** Colours of the Studio chrome, from the theme. */
internal object Chrome {
    val page: Color get() = Themes.page
    val raised: Color get() = Themes.raised
    val accent: Color @Composable get() = com.music.spotui.ui.theme.AppAccent
    val text = Color(0xFFF4F4F8)
    val muted = Color(0xFFA3A3B3)
    val faint = Color(0x14FFFFFF)
}

/** The Studio window: sidebar, player bar, the app's pages, Big Picture over them. */
@Composable
fun StudioShell(navController: NavHostController, immersive: Boolean) {
    Studio.nav = navController
    val cover = Now.cover
    // The cover's colour: Song Colours (the theme) and the window's title bar follow it.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(cover) {
        if (cover.isNotBlank()) runCatching { com.music.spotui.di.Palette().extractFirstColorFromImageUrl(context, cover) { Themes.songColor.value = it } }
    }
    val page = Chrome.page
    androidx.compose.runtime.LaunchedEffect(page) { app.jam.desktop.Front.window?.let { app.jam.desktop.DarkTitleBar.caption(it, page) } }
    Box(Modifier.fillMaxSize().background(Chrome.page)) {
        // The song's colours behind the chrome: its cover, blurred far out, barely there.
        if (cover.isNotBlank()) AsyncImage(
            model = Images.request(cover, 96), imageLoader = Images.loader, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(120.dp).alpha(0.32f),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Chrome.page.copy(alpha = 0.55f), Chrome.page.copy(alpha = 0.92f)))))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 980.dp
        Row(Modifier.fillMaxSize()) {
            Sidebar(navController, compact)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                PlayerBar()
                // The page, on a sheet with a rounded corner under the bar and beside the sidebar.
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .padding(end = 8.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (immersive) com.music.spotui.ui.immersive.Immersive.Page else com.music.spotui.ui.theme.AppBackground)
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(14.dp)),
                ) {
                    com.music.spotui.ui.navigation.MyNavHost(
                        navHostController = navController,
                        modifier = if (immersive) Modifier.background(com.music.spotui.ui.immersive.Immersive.Page) else Modifier,
                    )
                    com.music.spotui.ui.components.HiddenSongUndo()
                    com.music.spotui.podcast.SponsorSkipUndo()
                }
            }
        }
        }
        AnimatedVisibility(
            Studio.bigPicture.value,
            enter = fadeIn(tween(320)) + scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 0.96f),
            exit = fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0.98f),
        ) { BigPicture() }
    }
}

// ---------------------------------------------------------------------------------------- sidebar

private data class Item(val label: String, val icon: ImageVector, val route: String)

@Composable
private fun Sidebar(nav: NavHostController, compact: Boolean) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route.orEmpty()
    val width by androidx.compose.animation.core.animateDpAsState(if (compact) 64.dp else 232.dp, tween(220))
    CompositionLocalProvider(LocalCompact provides compact) {
    Column(Modifier.width(width).fillMaxHeight().padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.padding(start = if (compact) 6.dp else 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource("jam.png"), null, Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)))
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Text("Jam", color = Chrome.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            }
        }
        if (!compact) SearchField { Studio.open(Routes.Search.route) }
        Spacer(Modifier.height(12.dp))
        val main = listOf(
            Item("Home", Icons.Rounded.Home, Routes.Home.route),
            Item("Search", Icons.Rounded.Search, Routes.Search.route),
            Item("Radio", Icons.Rounded.Radio, Routes.LiveRadio.route),
        )
        main.forEach { SideRow(it.label, it.icon, route.startsWith(it.route)) { Studio.open(it.route) } }
        if (!compact) SectionLabel("Library") else Spacer(Modifier.height(14.dp))
        val lib = listOf(
            Item("Your Library", Icons.Rounded.LibraryMusic, Routes.Library.route),
            Item("Liked Songs", Icons.Rounded.Favorite, Routes.Liked.route),
            Item("Recently Played", Icons.Rounded.History, Routes.Recent.route),
            Item("Downloaded", Icons.Rounded.Download, Routes.Downloads.route),
            Item("Local Files", Icons.Rounded.Folder, Routes.LocalFiles.route),
            Item("Friends", Icons.Rounded.People, Routes.Friends.route),
        )
        lib.forEach { SideRow(it.label, it.icon, route.startsWith(it.route)) { Studio.open(it.route) } }
        if (!compact) SectionLabel("Playlists")
        Box(Modifier.weight(1f).fillMaxWidth()) { if (!compact) Playlists(nav) }
        Spacer(Modifier.height(6.dp))
        var themes by remember { mutableStateOf(false) }
        SideRow("Themes", Icons.Rounded.Palette, themes) { themes = true }
        if (themes) app.jam.desktop.themes.ThemePicker { themes = false }
        SideRow("Extensions", Icons.Rounded.Extension, route.startsWith(Routes.Extensions.route)) { Studio.open(Routes.Extensions.route) }
        SideRow("Settings", Icons.Rounded.Settings, route.startsWith(Routes.Settings.route)) { Studio.open(Routes.Settings.route) }
    }
    }
}

/** The sidebar is narrow (a small window): icons only. */
private val LocalCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun SearchField(onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp))
            .background(if (hovered) Color(0x22FFFFFF) else Color(0x16FFFFFF))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(9.dp))
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Chrome.muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search", color = Chrome.muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("Ctrl F", color = Color(0x66FFFFFF), fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0x80FFFFFF), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun SideRow(label: String, icon: ImageVector?, selected: Boolean, cover: String? = null, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(when { selected -> Color(0x26FFFFFF); hovered -> Color(0x12FFFFFF); else -> Color.Transparent }, tween(140))
    Row(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) AsyncImage(
            model = Images.request(cover, 64), imageLoader = Images.loader, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF)),
        ) else if (icon != null) Icon(icon, null, tint = if (selected) Chrome.accent else Chrome.muted, modifier = Modifier.size(19.dp))
        if (!LocalCompact.current) {
            Spacer(Modifier.width(11.dp))
            Text(label, color = if (selected) Chrome.text else Color(0xFFD9D9E3), fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Playlists(nav: NavHostController) {
    val vm: com.music.spotui.ui.viewmodel.LibraryViewModel = app.jam.desktop.compat.viewModel()
    val entries by vm.entries.collectAsState()
    val list = (entries as? com.music.spotui.data.api.Response.Success)?.data.orEmpty()
        .filter { it.isPlaylist && it.folderUri.isEmpty() && !it.spotifyId.startsWith("jam") && it.spotifyId.length > 8 }
    val route = nav.currentBackStackEntryAsState().value
    val openId = route?.arguments?.getString("pId")
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.spotifyId }) { e ->
            SideRow(e.name, null, openId == e.spotifyId, cover = e.coverUri.ifBlank { null }) {
                Studio.open(com.music.spotui.ui.navigation.playlistRoute(e.spotifyId, e.name))
            }
        }
    }
}

// ------------------------------------------------------------------------------------- player bar

/** The player, at the top as Apple Music has it: the buttons, the song's "screen", the volume. */
@Composable
private fun PlayerBar() {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val shuffle = Now.shuffle
        val repeat = Now.repeat
        Glyph(Icons.Rounded.Shuffle, "Shuffle", active = shuffle, size = 18.dp) { Now.shuffle(!shuffle) }
        Glyph(Icons.Rounded.SkipPrevious, "Previous", size = 26.dp) { Now.previous() }
        PlayButton(40.dp)
        Glyph(Icons.Rounded.SkipNext, "Next", size = 26.dp) { Now.next() }
        Glyph(if (repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", active = repeat != 0, size = 18.dp) { Now.cycleRepeat() }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Lcd() }
        Spacer(Modifier.width(14.dp))
        val liked = Now.liked
        Glyph(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", active = liked, size = 19.dp, enabled = Now.hasSong) { Now.like(!liked) }
        Glyph(Icons.Rounded.Lyrics, "Lyrics (Ctrl L)", active = Studio.bigPicture.value, size = 19.dp, enabled = Now.hasSong) {
            Studio.bigPicture.value = !Studio.bigPicture.value
        }
        Glyph(Icons.Rounded.QueueMusic, "Queue", size = 20.dp) { Studio.open(Routes.Queue.route) }
        Glyph(Icons.Rounded.PictureInPictureAlt, "Mini player (Ctrl M)", active = MiniPlayer.open.value, size = 19.dp) { MiniPlayer.toggle() }
        Spacer(Modifier.width(6.dp))
        Volume(Modifier.width(112.dp))
    }
}

@Composable
internal fun PlayButton(size: Dp, light: Boolean = false) {
    val playing = Now.playing
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.06f else 1f, tween(140))
    Box(
        Modifier.padding(horizontal = 6.dp).size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(if (light) Color.White else Color(0xFFF2F2F7))
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable { Now.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Color(0xFF111114), modifier = Modifier.size(size * 0.6f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Glyph(icon: ImageVector, label: String, active: Boolean = false, size: Dp = 22.dp, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val color by animateColorAsState(
        when {
            !enabled -> Color(0x40FFFFFF)
            active -> tint ?: Chrome.accent
            hovered -> Color.White
            else -> tint?.copy(alpha = 0.75f) ?: Color(0xFFC9C9D4)
        }, tween(120),
    )
    androidx.compose.foundation.TooltipArea(
        tooltip = { Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xEE26262E)).padding(horizontal = 8.dp, vertical = 4.dp)) },
        delayMillis = 600,
    ) {
        Box(
            Modifier.size(size + 18.dp).clip(CircleShape).background(if (hovered && enabled) Color(0x14FFFFFF) else Color.Transparent)
                .hoverable(hover).pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = color, modifier = Modifier.size(size)) }
    }
}

/** The song's "screen" in the player bar: cover, title, artist — album, and its progress. */
@Composable
private fun Lcd() {
    val pos by rememberPosition(smooth = false)
    val dur = Now.duration()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.widthIn(max = 620.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF)).border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(10.dp)).hoverable(hover),
    ) {
        if (!Now.hasSong) {
            Text("Jam", color = Color(0x55FFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Images.request(Now.cover, 128), imageLoader = Images.loader, contentDescription = "Open Big Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp).background(Color(0x22FFFFFF)).pointerHoverIcon(PointerIcon.Hand).clickable { Studio.bigPicture.value = true },
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Now.title, color = Chrome.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(Now.artist, Now.album).filter { it.isNotBlank() }.joinToString(" — "), color = Chrome.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(50.dp))
        }
        if (hovered && dur > 0) {
            Text(time(pos), color = Chrome.muted, fontSize = 10.5.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 5.dp))
            Text("-" + time(dur - pos), color = Chrome.muted, fontSize = 10.5.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 5.dp))
        }
        Seek(pos, dur, Modifier.align(Alignment.BottomCenter).padding(start = 50.dp).fillMaxWidth().height(if (hovered) 6.dp else 3.dp), thin = true)
    }
}

/** A seek line: click or drag to move in the song. */
@Composable
internal fun Seek(pos: Long, dur: Long, modifier: Modifier, thin: Boolean = false, color: Color? = null) {
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val accent = color ?: Chrome.accent
    val fraction = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) Now.seek((o.x / width * dur).toLong()) } }
            .pointerInput(dur) {
                detectDragGestures(
                    onDragStart = { o -> dragging = (o.x / width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { if (dur > 0) Now.seek((it * dur).toLong()) }; dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ -> dragging = (change.position.x / width).coerceIn(0f, 1f) }
            }
            .drawBehind {
                val h = if (thin) size.height else 4.dp.toPx()
                val y = (size.height - h) / 2
                drawRoundRect(Color(0x33FFFFFF), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                drawRoundRect(accent, Offset(0f, y), Size(size.width * fraction, h), CornerRadius(h / 2))
                if (!thin) drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * fraction, size.height / 2))
            },
    )
}

@Composable
internal fun Volume(modifier: Modifier) {
    var level by remember { mutableFloatStateOf(Now.volume / 100f) }
    var width by remember { mutableFloatStateOf(1f) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when { level <= 0.01f -> Icons.Rounded.VolumeOff; level < 0.5f -> Icons.Rounded.VolumeDown; else -> Icons.Rounded.VolumeUp },
            "Volume", tint = Color(0xFFC9C9D4), modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        fun set(f: Float) { level = f.coerceIn(0f, 1f); Now.volume = (level * 100).toInt() }
        Box(
            Modifier.weight(1f).height(18.dp).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) { detectTapGestures { o -> set(o.x / width) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> set(change.position.x / width) } }
                .drawBehind {
                    val h = 4.dp.toPx(); val y = (size.height - h) / 2
                    drawRoundRect(Color(0x33FFFFFF), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                    drawRoundRect(Color(0xFFE6E6EE), Offset(0f, y), Size(size.width * level, h), CornerRadius(h / 2))
                },
        )
    }
}
