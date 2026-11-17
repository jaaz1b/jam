package app.jam.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.myspoti.api.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.exitProcess

/** Where the main area is. */
sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Library : Route
    data object Settings : Route
    data class Collection(val uri: String, val kind: MediaKind, val title: String) : Route
}

/** The back stack of the main area. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    fun go(r: Route) { if (r is Route.Collection) stack.add(r) else { stack.clear(); stack.add(r) } }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
}

fun main(args: Array<String>) {
    // One Jam at a time: a second one would start a second browser on the same profile.
    val lock = runCatching { RandomAccessFile(File(Paths.dataDir, "jam.lock"), "rw").channel.tryLock() }.getOrNull()
    if (lock == null) exitProcess(0)
    if (Settings.ytmMode) {
        val ytm = Ytm.start()
        if (ytm != null) { ytmTray(ytm); return }
        Settings.ytmMode = false
    }
    Session.start()
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        Window(
            onCloseRequest = { Discord.clear(); Session.stop(); exitApplication() },
            state = windowState,
            title = "Jam",
            icon = JamLogo,
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            JamTheme { App() }
        }
    }
    Session.stop()
    exitProcess(0)
}

/** YouTube Music mode: Jam's YouTube Music engine runs; Jam stays in the tray to switch back. */
private fun ytmTray(ytm: Process) {
    application {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { ytm.waitFor() }
            exitApplication()
        }
        Tray(
            icon = JamLogo,
            tooltip = "Jam (YouTube Music mode)",
            menu = {
                Item("Switch to Spotify mode", onClick = {
                    Settings.ytmMode = false
                    Ytm.stop(ytm)
                    Restart.now()
                })
                Item("Quit Jam", onClick = { Ytm.stop(ytm); exitApplication() })
            },
        )
    }
    exitProcess(0)
}

/** Jam's YouTube Music engine (jam-ytm.exe), shipped in the app's resources. */
object Ytm {
    fun exe(): File? = Paths.appResources?.let { File(it, "jam-ytm/jam-ytm.exe") }?.takeIf { it.isFile }
    val available: Boolean get() = exe() != null

    fun start(): Process? = exe()?.let { e ->
        runCatching { ProcessBuilder(e.absolutePath).directory(e.parentFile).redirectErrorStream(true)
            .redirectOutput(File(Paths.dataDir, "ytm.log")).start() }.getOrNull()
    }

    fun stop(p: Process) {
        runCatching { p.toHandle().descendants().forEach { it.destroy() } }
        runCatching { p.destroy() }
    }
}

/** Starts Jam again (a mode switch) and ends this one. */
object Restart {
    fun now(): Nothing {
        Discord.clear()
        Session.stop()
        val cmd = ProcessHandle.current().info().command().orElse(null)
        // Packaged: Jam.exe itself. From Gradle (development): the same java command line.
        val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
        if (cmd != null) runCatching { ProcessBuilder(listOf(cmd) + args).start() }
        exitProcess(0)
    }
}

@Composable
fun App() {
    val state by Session.state.collectAsState()
    val nav = remember { Nav() }
    // Discord follows the player.
    LaunchedEffect(state) {
        if (state == SessionState.Ready) Session.api?.player?.state?.collectLatest { s ->
            kotlinx.coroutines.withContext(Dispatchers.IO) { Discord.update(s) }
        }
    }
    Column(Modifier.fillMaxSize().background(Jam.Page)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(nav)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (val s = state) {
                    is SessionState.Starting -> Centered { CircularProgressIndicator(color = Jam.Accent); Spacer(Modifier.height(16.dp)); Text(s.step, color = Jam.Muted) }
                    is SessionState.Failed -> Centered { Text("Spotify couldn't start", color = Jam.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(s.message, color = Jam.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp)) }
                    is SessionState.SignedOut -> if (nav.current == Route.Settings) SettingsScreen() else SignIn(s.signingIn)
                    SessionState.Ready -> when (val r = nav.current) {
                        Route.Home -> HomeScreen(nav)
                        Route.Search -> SearchScreen(nav)
                        Route.Library -> LibraryScreen(nav)
                        Route.Settings -> SettingsScreen()
                        is Route.Collection -> CollectionScreen(nav, r)
                    }
                }
            }
        }
        if (state == SessionState.Ready) PlayerBar()
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun SignIn(signingIn: Boolean) = Centered {
    Icon(JamLogo, null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(96.dp))
    Spacer(Modifier.height(20.dp))
    Text("Welcome to Jam", color = Jam.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (!signingIn) {
        Text("Sign in with your Spotify account. You sign in on Spotify's own page: Jam never sees your password.", color = Jam.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Session.signIn() }, colors = ButtonDefaults.buttonColors(containerColor = Jam.Accent)) { Text("Sign in to Spotify") }
    } else {
        Text("Sign in in the Spotify window that just opened. Jam carries on once you're in.", color = Jam.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = Jam.Accent)
        Spacer(Modifier.height(20.dp))
        Text("Can't see it?", color = Jam.AccentLight, modifier = Modifier.clickable { Session.signIn() })
    }
}

@Composable
private fun Sidebar(nav: Nav) {
    Column(Modifier.width(220.dp).fillMaxHeight().background(Jam.Side).padding(12.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JamLogo, null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Jam", color = Jam.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        NavItem(Icons.Home, "Home", nav.stack.first() == Route.Home) { nav.go(Route.Home) }
        NavItem(Icons.Search, "Search", nav.stack.first() == Route.Search) { nav.go(Route.Search) }
        NavItem(Icons.Library, "Your Library", nav.stack.first() == Route.Library) { nav.go(Route.Library) }
        Spacer(Modifier.weight(1f))
        NavItem(Icons.Settings, "Settings", nav.stack.first() == Route.Settings) { nav.go(Route.Settings) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Jam.Raised2 else Jam.Side)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Jam.Text else Jam.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (selected) Jam.Text else Jam.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Runs [block] on the session's scope with the API, ignoring a session that isn't ready. */
fun withApi(block: suspend (app.myspoti.spotify.SpotifyApi) -> Unit) {
    val api = Session.api ?: return
    Session.scope.launch { runCatching { block(api) } }
}
