package app.jam.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Tray
import app.jam.desktop.studio.MiniPlayerWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.jam.desktop.shim.Desk
import app.jam.desktop.shim.DesktopApp
import app.jam.desktop.shim.Intents
import app.jam.desktop.shim.Toasts
import app.jam.desktop.shim.Windows
import kotlinx.coroutines.delay

/**
 * Jam for Windows: Orpheon's Android app (Jam's), running as it does on a phone: its Application
 * (MyspotiApplication) and its one activity (MainActivity), whose views are drawn in this window.
 */
private var keysSeen = 0

fun main(args: Array<String>) {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: if (Desk.isWindows) "DIRECT3D" else "SOFTWARE_FAST")
    Thread.setDefaultUncaughtExceptionHandler { t, e -> Desk.log('E', "Crash", "uncaught on ${t.name}: $e", e) }
    Single.claim(args) // a second start hands its link to this one and quits
    app.jam.desktop.security.AndroidKeyStore.install()
    // Tests (CI): a page in the web sheet over the app, to try typing into a web view.
    System.getenv("JAM_DEV_WEB")?.takeIf { it.isNotBlank() }?.let { url -> Desk.timer.schedule({ WebSheet.open(url) }, 12, java.util.concurrent.TimeUnit.SECONDS) }
    // The hidden browser starts at once (it takes a second or two), while the app is made.
    Thread({ runCatching { app.jam.desktop.web.Edge.of() }.onFailure { Desk.log('E', "Edge", "start: $it", it) } }, "edge-start").apply { isDaemon = true }.start()
    javax.swing.SwingUtilities.invokeAndWait {
        val application = app.myspoti.MyspotiApplication()
        DesktopApp.context = application
        application.onCreate()
    }
    val activity = mutableStateOf(newActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        args.firstOrNull()?.let { data = android.net.Uri.parse(it); action = android.content.Intent.ACTION_VIEW }
    }) ?: run {
        Desk.log('I', "Main", "the activity finished at start (YouTube Music mode)", null)
        if (YtmCompanion.running) { Single.onLink = { YtmCompanion.front() }; YtmCompanion.run() }
        quit(); return
    })
    Intents.onAppIntent = { intent -> Desk.post { activity.value.onNewIntent(intent); Front.request() } }
    Windows.onRecreate = { old -> Desk.post { finish(old); newActivity(old.intent)?.let { activity.value = it } } }
    Windows.onFinish = { a -> if (a === activity.value) Desk.post { quit() } }
    Single.onLink = { link -> Intents.start(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)).setClassName("app.jam.android", "app.myspoti.MainActivity")) }

    application(exitProcessOnExit = false) {
        val state = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition(Alignment.Center))
        Shortcuts.state = state
        val now by SystemMedia.now
        // Closing the window keeps Jam playing in the tray (as Jam's other modes do); Quit is there.
        Tray(
            state = Front.tray,
            icon = androidx.compose.ui.res.painterResource("jam.png"),
            tooltip = now.title?.let { t -> listOfNotNull(t, now.artist).joinToString(" · ") + " — Jam" } ?: "Jam",
            onAction = { Front.request() },
            menu = {
                Item("Open Jam", onClick = { Front.request() })
                Separator()
                Item(if (now.playing) "Pause" else "Play", enabled = now.title != null, onClick = { SystemMedia.button(if (now.playing) "pause" else "play") })
                Item("Next", enabled = now.title != null, onClick = { SystemMedia.button("next") })
                Item("Previous", enabled = now.title != null, onClick = { SystemMedia.button("previous") })
                Item("Mini player", onClick = { app.jam.desktop.studio.MiniPlayer.toggle() })
                Separator()
                CheckboxItem("Keep playing when the window is closed", checked = Front.toTray.value, onCheckedChange = { Front.setToTray(it) })
                Item("Quit Jam", onClick = { quit(); exitApplication() })
            },
        )
        MiniPlayerWindow()
        Window(
            onCloseRequest = { if (Front.toTray.value) Front.hide() else { quit(); exitApplication() } },
            visible = Front.shown.value,
            state = state,
            title = now.title?.let { t -> listOfNotNull(t, now.artist).joinToString(" · ") + " — Jam" } ?: "Jam",
            icon = androidx.compose.ui.res.painterResource("jam.png"),
            onPreviewKeyEvent = { e ->
                if (keysSeen < 12) { keysSeen++; Desk.log('D', "Window", "key ${e.type} ${e.key} owner=${java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.javaClass?.simpleName}", null) }
                // Back, as the phone's: Esc, Alt+Left, the mouse's back button (below).
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft))) { back(activity.value); true }
                else Shortcuts.preview(e)
            },
            onKeyEvent = { e -> Front.window?.let { w -> Shortcuts.handle(e, w) } ?: false },
        ) {
            LaunchedEffect(Unit) { Front.window = window; DarkTitleBar.apply(window); window.minimumSize = java.awt.Dimension(720, 560) }
            ActivityWindow(activity.value, Modifier.onPointerEvent(PointerEventType.Press) { e ->
                val b = (e.nativeEvent as? java.awt.event.MouseEvent)?.button
                if (b == 4) back(activity.value)
            })
        }
    }
    quit()
}

/** Back: the activity on top hears it (it finishes when nothing in it takes it). */
private fun back(main: android.app.Activity) {
    val top = Windows.stack.lastOrNull()
    if (top != null) { if (!top.onBackPressedDispatcher.dispatch()) top.finish() } else main.onBackPressedDispatcher.onBackPressed()
}

private fun newActivity(intent: android.content.Intent): android.app.Activity? {
    var a: app.myspoti.MainActivity? = null
    javax.swing.SwingUtilities.invokeAndWait {
        val act = app.myspoti.MainActivity()
        act.intent = intent
        DesktopApp.activity = act
        act.onCreate(null)
        if (act.isFinishing) return@invokeAndWait
        (act.content.value)?.attachAsRoot(true)
        act.onStart(); act.onResume()
        a = act
    }
    return a
}

private fun finish(a: android.app.Activity) { runCatching { a.onPause(); a.onStop(); a.content.value?.attachAsRoot(false); a.onDestroy() } }

private var quitting = false
fun quit() {
    if (quitting) return
    quitting = true
    runCatching { DesktopApp.activity?.let(::finish) }
    runCatching { app.jam.desktop.shim.ServiceHost.stopAll() }
    runCatching { app.jam.desktop.web.Edge.stopAll() }
    Desk.timer.schedule({ Runtime.getRuntime().halt(0) }, 4, java.util.concurrent.TimeUnit.SECONDS)
    Thread { Runtime.getRuntime().exit(0) }.start()
}

/** The activity in the window, with what Android's window gives its Compose UI. */
@Composable
fun ActivityWindow(a: android.app.Activity, modifier: Modifier) {
    val root = a.content.value
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides a,
        androidx.compose.ui.platform.LocalView provides (root ?: android.view.View(a)),
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides a,
        androidx.activity.compose.LocalActivity provides a,
        androidx.lifecycle.compose.LocalLifecycleOwner provides a,
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides a,
    ) {
        Box(modifier.fillMaxSize().background(app.jam.desktop.themes.Themes.window)
            .then(if (app.jam.desktop.studio.Studio.pointer.value) Modifier.pointerHoverIcon(app.jam.desktop.studio.Cursors.arrow()) else Modifier)) {
            root?.let { ViewNode(it) }
            for (top in Windows.stack.toList()) ActivityLayer(top)
            WebSheet.Host()
            ToastHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Another activity over the main one: its views, its back (Esc) first. */
@Composable
private fun ActivityLayer(a: android.app.Activity) {
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides a,
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides a,
        androidx.lifecycle.compose.LocalLifecycleOwner provides a,
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides a,
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) { a.content.value?.let { ViewNode(it) } }
    }
}

@Composable
private fun ToastHost(modifier: Modifier) {
    val t by Toasts.current.collectAsState()
    LaunchedEffect(t) { val s = t ?: return@LaunchedEffect; delay(if (s.long) 3500 else 2000); Toasts.dismiss(s) }
    AnimatedVisibility(t != null, modifier.padding(bottom = 96.dp), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.widthIn(max = 520.dp).background(Color(0xEE2A2A30), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(t?.text.orEmpty(), color = Color.White, fontSize = 14.sp)
        }
    }
}

/** Bringing the window to the front (a link opened while Jam runs, a notification tapped). */
object Front {
    var window: java.awt.Window? = null
    /** The window is shown (false: Jam is in the tray). */
    val shown = mutableStateOf(true)
    private val prefs by lazy { android.content.Prefs.of("jam_window") }
    val toTray by lazy { mutableStateOf(prefs.getBoolean("to_tray", true)) }
    fun setToTray(on: Boolean) { toTray.value = on; prefs.edit().putBoolean("to_tray", on).apply() }
    fun hide() {
        shown.value = false
        if (!hinted && !prefs.getBoolean("tray_hint", false)) { hinted = true; prefs.edit().putBoolean("tray_hint", true).apply(); notify("Jam is still playing", "Jam keeps running in the tray. Right-click its icon to quit.") }
    }
    private var hinted = false
    /** Windows' own notifications (from the tray icon). */
    val tray = androidx.compose.ui.window.TrayState()
    fun notify(title: String, text: String?) = tray.sendNotification(androidx.compose.ui.window.Notification(title, text.orEmpty()))
    fun request() = Desk.post {
        shown.value = true
        window?.let { w -> (w as? java.awt.Frame)?.let { if (it.state == java.awt.Frame.ICONIFIED) it.state = java.awt.Frame.NORMAL }; w.toFront(); w.requestFocus() }
    }
    /** A text field has the keyboard (space types, it does not play/pause). */
    fun typing(): Boolean = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let { it.javaClass.name.contains("Text") } ?: false
}

/** Restarts Jam (a mode switch): the same program again, then this one quits. */
object Restart {
    fun now() {
        val exe = ProcessHandle.current().info().command().orElse(null)
        val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
        if (exe != null) runCatching { ProcessBuilder(listOf(exe) + args).start() }
        quit()
    }
}
