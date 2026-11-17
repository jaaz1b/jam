package app.jam.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.api.Image
import app.myspoti.api.MediaCard
import app.myspoti.api.MediaKind
import app.myspoti.api.Track
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Shared pieces ──

fun pick(images: List<Image>): String? =
    images.filter { (it.width ?: 300) in 160..700 }.minByOrNull { it.width ?: 300 }?.url ?: images.firstOrNull()?.url

@Composable
fun Cover(url: String?, size: Dp, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Box(Modifier.size(size).clip(shape).background(Jam.Raised2)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Heading(text: String, size: Int = 22) =
    Text(text, color = Jam.Text, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Jam.Accent) }

@Composable
fun Problem(message: String) = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(message, color = Jam.Muted) }

@Composable
fun CardTile(card: MediaCard, nav: Nav) {
    val round = card.kind == MediaKind.ARTIST || card.kind == MediaKind.USER
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(10.dp)).clickable { open(nav, card.uri, card.kind, card.title) }.padding(8.dp)) {
        Cover(pick(card.images), 152.dp, round)
        Spacer(Modifier.height(8.dp))
        Text(card.title, color = Jam.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let { Text(it, color = Jam.Faint, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** A card opens its page; a song plays (in its album). */
fun open(nav: Nav, uri: String, kind: MediaKind, title: String) {
    when (kind) {
        MediaKind.TRACK -> withApi { api ->
            val album = runCatching { api.catalog.track(uri).album?.uri }.getOrNull()
            api.player.play(contextUri = album ?: uri, trackUri = uri)
        }
        MediaKind.EPISODE -> withApi { it.player.play(contextUri = uri) }
        MediaKind.ALBUM, MediaKind.PLAYLIST, MediaKind.ARTIST, MediaKind.COLLECTION -> nav.go(Route.Collection(uri, kind, title))
        else -> withApi { it.player.play(contextUri = uri) }
    }
}

@Composable
fun TrackRow(index: Int, t: Track, onPlay: () -> Unit, showCover: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = Jam.Faint, modifier = Modifier.width(32.dp), fontSize = 14.sp)
        if (showCover) { Cover(t.album?.images?.let(::pick), 40.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(t.name, color = if (t.playable) Jam.Text else Jam.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (t.explicit) "E · " else "") + t.artists.joinToString(", ") { it.name }, color = Jam.Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showCover) t.album?.name?.let { Text(it, color = Jam.Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp)) }
        Text(time(t.durationMs), color = Jam.Faint, fontSize = 13.sp)
    }
}

fun time(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

// ── Home ──

@Composable
fun HomeScreen(nav: Nav) {
    var feed by remember { mutableStateOf<app.myspoti.api.HomeFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val api = Session.api ?: return@LaunchedEffect
        runCatching { api.home.feed(itemsPerShelf = 12) }.onSuccess { feed = it }.onFailure { error = it.message ?: "Home didn't load" }
    }
    val f = feed ?: return if (error != null) Problem(error!!) else Loading()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item { Heading(f.greeting.ifBlank { "Home" }, 30); Spacer(Modifier.height(16.dp)) }
        items(f.shelves.filter { it.items.isNotEmpty() }) { shelf ->
            Column(Modifier.padding(bottom = 20.dp)) {
                shelf.title?.let { Heading(it, 20); Spacer(Modifier.height(8.dp)) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(shelf.items) { CardTile(it, nav) } }
            }
        }
    }
}

// ── Search ──

@Composable
fun SearchScreen(nav: Nav) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<app.myspoti.api.SearchResults?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(350)
        val api = Session.api ?: return@LaunchedEffect
        busy = true
        runCatching { api.search.all(query, limit = 12) }.onSuccess { results = it }
        busy = false
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("What do you want to play?") },
            leadingIcon = { Icon(Icons.Search, null, tint = Jam.Muted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jam.Accent, unfocusedBorderColor = Jam.Line, focusedTextColor = Jam.Text, unfocusedTextColor = Jam.Text, cursorColor = Jam.Accent),
        )
        Spacer(Modifier.height(16.dp))
        val r = results
        when {
            busy && r == null -> Loading()
            r == null -> Problem("Search Spotify: songs, artists, albums and playlists.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (r.tracks.items.isNotEmpty()) {
                    item { Heading("Songs", 20); Spacer(Modifier.height(4.dp)) }
                    itemsIndexed(r.tracks.items.take(8)) { i, t -> TrackRow(i, t, onPlay = { open(nav, t.uri, MediaKind.TRACK, t.name) }) }
                }
                fun row(title: String, cards: List<MediaCard>) {
                    if (cards.isEmpty()) return
                    item { Spacer(Modifier.height(16.dp)); Heading(title, 20); Spacer(Modifier.height(8.dp)) }
                    item { LazyRow { items(cards) { CardTile(it, nav) } } }
                }
                row("Artists", r.artists.items.map { MediaCard(it.uri, MediaKind.ARTIST, it.name, "Artist", it.images) })
                row("Albums", r.albums.items.map { MediaCard(it.uri, MediaKind.ALBUM, it.name, it.artists.joinToString(", ") { a -> a.name }, it.images) })
                row("Playlists", r.playlists.items.map { MediaCard(it.uri, MediaKind.PLAYLIST, it.name, it.owner, it.images) })
            }
        }
    }
}

// ── Library ──

@Composable
fun LibraryScreen(nav: Nav) {
    var items by remember { mutableStateOf<List<app.myspoti.api.LibraryItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val api = Session.api ?: return@LaunchedEffect
        runCatching {
            val all = mutableListOf<app.myspoti.api.LibraryItem>()
            var offset: Int? = 0
            while (offset != null && all.size < 1000) {
                val page = api.library.items(offset = offset, limit = 50)
                all += page.items; offset = page.nextOffset
                items = all.toList()
            }
        }.onFailure { error = it.message ?: "Your library didn't load" }
    }
    val list = items ?: return if (error != null) Problem(error!!) else Loading()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item { Heading("Your Library", 30); Spacer(Modifier.height(16.dp)) }
        items(list) { it ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(nav, it.uri, it.kind, it.name) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(pick(it.images), 52.dp, round = it.kind == MediaKind.ARTIST)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, color = Jam.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(kindLabel(it.kind), it.subtitle).joinToString(" · "), color = Jam.Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (it.pinned) Text("Pinned", color = Jam.AccentLight, fontSize = 12.sp)
            }
        }
    }
}

fun kindLabel(k: MediaKind): String? = when (k) {
    MediaKind.PLAYLIST -> "Playlist"; MediaKind.ALBUM -> "Album"; MediaKind.ARTIST -> "Artist"
    MediaKind.SHOW -> "Podcast"; MediaKind.AUDIOBOOK -> "Audiobook"; MediaKind.FOLDER -> "Folder"
    MediaKind.COLLECTION -> "Playlist"; else -> null
}

// ── A playlist, an album, an artist, Liked Songs ──

private class Page(val title: String, val subtitle: String, val image: String?, val round: Boolean, val playUri: String, val tracks: List<Track>, val showCovers: Boolean)

@Composable
fun CollectionScreen(nav: Nav, r: Route.Collection) {
    var page by remember(r) { mutableStateOf<Page?>(null) }
    var error by remember(r) { mutableStateOf<String?>(null) }
    LaunchedEffect(r) {
        val api = Session.api ?: return@LaunchedEffect
        runCatching {
            page = when (r.kind) {
                MediaKind.PLAYLIST -> {
                    val p = api.playlists.playlist(r.uri)
                    val tracks = mutableListOf<Track>()
                    var offset: Int? = 0
                    while (offset != null && tracks.size < 2000) {
                        val pg = api.playlists.tracks(r.uri, offset, 100); tracks += pg.items; offset = pg.nextOffset
                        page = Page(p.name, listOfNotNull(p.owner, "${p.trackCount} songs").joinToString(" · "), pick(p.images), false, r.uri, tracks.toList(), true)
                    }
                    Page(p.name, listOfNotNull(p.owner, "${p.trackCount} songs").joinToString(" · "), pick(p.images), false, r.uri, tracks, true)
                }
                MediaKind.ALBUM -> {
                    val a = api.catalog.album(r.uri)
                    val tracks = api.catalog.albumTracks(r.uri, 0, 50).items
                    Page(a.name, listOfNotNull(a.artists.joinToString(", ") { it.name }, a.releaseDate?.take(4), "${a.trackCount} songs").joinToString(" · "), pick(a.images), false, r.uri, tracks, false)
                }
                MediaKind.ARTIST -> {
                    val p = api.catalog.artistPage(r.uri)
                    Page(p.artist.name, p.artist.monthlyListeners?.let { "%,d monthly listeners".format(it) } ?: "Artist", pick(p.artist.images), true, r.uri, p.topTracks.map { it.track }, true)
                }
                else -> {
                    // Liked Songs (and the other built-in collections).
                    val me = api.users.me()
                    val tracks = mutableListOf<Track>()
                    var offset: Int? = 0
                    while (offset != null && tracks.size < 3000) {
                        val pg = api.library.likedTracks(offset, 50); tracks += pg.items.map { it.track }; offset = pg.nextOffset
                        page = Page(r.title.ifBlank { "Liked Songs" }, "${tracks.size} songs", null, false, "spotify:user:${me.username}:collection", tracks.toList(), true)
                    }
                    Page(r.title.ifBlank { "Liked Songs" }, "${tracks.size} songs", null, false, "spotify:user:${me.username}:collection", tracks, true)
                }
            }
        }.onFailure { error = it.message ?: "This page didn't load" }
    }
    val p = page ?: return if (error != null) Problem(error!!) else Loading()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Back, "Back", tint = Jam.Muted, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { nav.back() })
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (p.image != null) Cover(p.image, 180.dp, p.round)
                else Box(Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)).background(Jam.Accent), contentAlignment = Alignment.Center) { Text("♥", color = Jam.Text, fontSize = 64.sp) }
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(kindLabel(r.kind) ?: "", color = Jam.Muted, fontSize = 13.sp)
                    Text(p.title, color = Jam.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(p.subtitle, color = Jam.Muted)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { withApi { it.player.play(contextUri = p.playUri) } }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Accent)) {
                            Icon(Icons.Play, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Play")
                        }
                        Button(onClick = { withApi { it.player.setShuffle(true); it.player.play(contextUri = p.playUri) } }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Raised2)) {
                            Icon(Icons.Shuffle, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        itemsIndexed(p.tracks) { i, t ->
            TrackRow(i, t, showCover = p.showCovers, onPlay = {
                withApi { api ->
                    if (r.kind == MediaKind.ARTIST) api.player.play(contextUri = t.album?.uri ?: t.uri, trackUri = t.uri)
                    else api.player.play(contextUri = p.playUri, trackUri = t.uri, index = i)
                }
            })
        }
    }
}

// ── Settings ──

@Composable
fun SettingsScreen() {
    var discordOn by remember { mutableStateOf(Settings.discordEnabled) }
    var clientId by remember { mutableStateOf(Settings.discordClientId) }
    var showPaused by remember { mutableStateOf(Settings.showPaused) }
    var confirmYtm by remember { mutableStateOf(false) }
    var discordStatus by remember { mutableStateOf(Discord.status) }
    LaunchedEffect(Unit) { while (true) { discordStatus = Discord.status; delay(1500) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item { Heading("Settings", 30); Spacer(Modifier.height(20.dp)) }
        item {
            Section("Jam")
            SettingRow("YouTube Music mode",
                if (Ytm.available) "Plays YouTube Music in Jam instead of Spotify. Jam restarts in it; switch back from Jam's icon in the taskbar tray."
                else "Not in this build of Jam (its YouTube Music engine is missing).") {
                Switch(checked = false, enabled = Ytm.available, onCheckedChange = { if (it) confirmYtm = true }, colors = jamSwitch())
            }
            if (confirmYtm) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Jam.Raised).padding(16.dp)) {
                Text("Jam restarts in YouTube Music mode. What plays now stops. Your Spotify sign-in stays for when you switch back.", color = Jam.Text)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { Settings.ytmMode = true; Restart.now() }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Accent)) { Text("Switch") }
                    Button(onClick = { confirmYtm = false }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Raised2)) { Text("Cancel") }
                }
            }
        }
        item {
            Section("Discord Rich Presence")
            SettingRow("Show what you play on Discord", "Through the Discord app on this PC: $discordStatus") {
                Switch(checked = discordOn, onCheckedChange = { discordOn = it; Settings.discordEnabled = it; Discord.clear() }, colors = jamSwitch())
            }
            SettingRow("Also while paused", "Keeps the status up with the song paused") {
                Switch(checked = showPaused, onCheckedChange = { showPaused = it; Settings.showPaused = it }, colors = jamSwitch())
            }
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Discord application ID", color = Jam.Text)
                Text("The application your status shows as (discord.com/developers). Jam's other Windows app's one is used if it has one.", color = Jam.Faint, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientId, onValueChange = { v -> clientId = v.filter(Char::isDigit); Settings.discordClientId = clientId; Discord.clear() },
                    singleLine = true, modifier = Modifier.width(360.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jam.Accent, unfocusedBorderColor = Jam.Line, focusedTextColor = Jam.Text, unfocusedTextColor = Jam.Text),
                )
            }
        }
        item {
            Section("Spotify")
            SettingRow("The Spotify window", "The Web Player Jam plays through, normally out of sight") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Session.showPlayerWindow(true) }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Raised2)) { Text("Show") }
                    Button(onClick = { Session.showPlayerWindow(false) }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Raised2)) { Text("Hide") }
                }
            }
            SettingRow("Log out", "Signs this PC out of Spotify") {
                Button(onClick = { Session.scope.launch { Session.signOut() } }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Raised2)) { Text("Log out") }
            }
        }
        item {
            Section("About")
            Text("Jam for Windows ${System.getProperty("jpackage.app-version") ?: ""}".trim(), color = Jam.Text)
            Text("Free software under the GPL-3.0. Spotify mode is Orpheon (formerly Myspoti) by kota-zo, rebranded as Jam. github.com/jaaz1b/jam", color = Jam.Faint, fontSize = 13.sp)
        }
    }
}


@Composable
private fun jamSwitch() = SwitchDefaults.colors(checkedTrackColor = Jam.Accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White)

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title.uppercase(), color = Jam.AccentLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingRow(title: String, detail: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = Jam.Text, fontSize = 16.sp)
            Text(detail, color = Jam.Faint, fontSize = 13.sp)
        }
        trailing()
    }
}
