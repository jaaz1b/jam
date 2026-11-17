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
fun main(args: Array<String>) {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: if (Desk.isWindows) "DIRECT3D" else "SOFTWARE_FAST")
    Thread.setDefaultUncaughtExceptionHandler { t, e -> Desk.log('E', "Crash", "uncaught on ${t.name}: $e", e) }
    Single.claim(args) // a second start hands its link to this one and quits
    app.jam.desktop.security.AndroidKeyStore.install()
    // The hidden browser starts at once (it takes a second or two), while the app is made.
    Thread({ runCatching { app.jam.desktop.web.Edge.of() }.onFailure { Desk.log('E', "Edge", "start: $it", it) } }, "edge-start").apply { isDaemon = true }.start()
    javax.swing.SwingUtilities.invokeAndWait {
        val application = app.myspoti.MyspotiApplication()
        DesktopApp.context = application
        application.onCreate()
    }
    val activity = mutableStateOf(newActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        args.firstOrNull()?.let { data = android.net.Uri.parse(it); action = android.content.Intent.ACTION_VIEW }
    }) ?: run { Desk.log('I', "Main", "the activity finished at start (YouTube Music mode)", null); quit(); return })
    Intents.onAppIntent = { intent -> Desk.post { activity.value.onNewIntent(intent); Front.request() } }
    Windows.onRecreate = { old -> Desk.post { finish(old); newActivity(old.intent)?.let { activity.value = it } } }
    Windows.onFinish = { a -> if (a === activity.value) Desk.post { quit() } }
    Single.onLink = { link -> Intents.start(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)).setClassName("app.jam.android", "app.myspoti.MainActivity")) }

    application(exitProcessOnExit = false) {
        val state = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))
        val now by SystemMedia.now
        Window(
            onCloseRequest = { quit(); exitApplication() },
            state = state,
            title = now.title?.let { t -> listOfNotNull(t, now.artist).joinToString(" · ") + " — Jam" } ?: "Jam",
            icon = androidx.compose.ui.res.painterResource("jam.png"),
            onPreviewKeyEvent = { e ->
                // Back, as the phone's: Esc, Alt+Left, the mouse's back button (below).
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft))) { activity.value.onBackPressedDispatcher.onBackPressed(); true }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.Spacebar && !Front.typing()) { SystemMedia.key(0xB3); true }
                else false
            },
        ) {
            LaunchedEffect(Unit) { Front.window = window; DarkTitleBar.apply(window); window.minimumSize = java.awt.Dimension(420, 640) }
            ActivityWindow(activity.value, Modifier.onPointerEvent(PointerEventType.Press) { e ->
                val b = (e.nativeEvent as? java.awt.event.MouseEvent)?.button
                if (b == 4) activity.value.onBackPressedDispatcher.onBackPressed()
            })
        }
    }
    quit()
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
        Box(modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            root?.let { ViewNode(it) }
            WebSheet.Host()
            ToastHost(Modifier.align(Alignment.BottomCenter))
        }
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
    fun request() { window?.let { w -> (w as? java.awt.Frame)?.let { if (it.state == java.awt.Frame.ICONIFIED) it.state = java.awt.Frame.NORMAL }; w.toFront(); w.requestFocus() } }
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
