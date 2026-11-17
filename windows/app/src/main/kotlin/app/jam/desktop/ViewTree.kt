package app.jam.desktop

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Draws an Android view tree: its ComposeViews' content, its WebViews' pages, stacked as a FrameLayout does. */
@Composable
fun ViewNode(v: View, modifier: Modifier = Modifier.fillMaxSize()) {
    val vis = v.drawnVisibility()
    if (vis == View.GONE) return
    val m = modifier.alpha(v.alpha.coerceIn(0f, 1f))
    when (v) {
        is ComposeView -> Box(m) { v.content.value?.invoke() }
        is WebView -> if (vis == View.VISIBLE && v.alpha > 0.01f) WebSurface(v, m) else Box(modifier.size(0.dp))
        is androidx.media3.ui.PlayerView -> app.jam.desktop.media.VideoView.Video(v, m, zoom = true) { v.player as? app.jam.desktop.media.DesktopPlayer }
        is android.view.TextureView -> app.jam.desktop.media.VideoView.Video(v, m, zoom = false) { app.jam.desktop.media.VideoView.targets[v] }
        is ViewGroup -> Box(m) { for (c in v.children.toList()) ViewNode(c, sizeOf(c)) }
        else -> Box(m)
    }
}

@Composable
private fun sizeOf(v: View): Modifier {
    val p = v.layoutParams ?: return Modifier.fillMaxSize()
    val d = LocalDensity.current
    fun dim(px: Int) = with(d) { px.toDp() }
    return when {
        p.width < 0 && p.height < 0 -> Modifier.fillMaxSize()
        p.width <= 2 && p.height <= 2 -> Modifier.size(0.dp)   // a hidden page kept attached (1×1 on Android)
        else -> Modifier.size(if (p.width < 0) 4000.dp else dim(p.width), if (p.height < 0) 4000.dp else dim(p.height))
    }
}

/**
 * A web view on screen: its page drawn from the hidden browser's screencast, at the view's size, and
 * the mouse, wheel and keyboard sent to the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebSurface(view: WebView, modifier: Modifier) {
    val page = view.desktopPage()
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(size) {
        if (size.width > 4 && size.height > 4) {
            view.setDrawnSize(size.width, size.height)
            page.screencast(true, (size.width / density).toInt(), (size.height / density).toInt(), density)
        }
    }
    DisposableEffect(view) { onDispose { page.screencast(false, 0, 0, 1f) } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(modifier
        .onSizeChanged { size = it }
        .focusRequester(focus).focusable()
        .pointerInput(view) {
            awaitPointerEventScope {
                var buttons = 0
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: continue
                    val x = c.position.x / density; val y = c.position.y / density
                    val mods = Keys.modifiers(e.keyboardModifiers)
                    when (e.type) {
                        PointerEventType.Press -> {
                            runCatching { focus.requestFocus() }
                            val btn = if (e.buttons.isSecondaryPressed) "right" else "left"
                            buttons = if (btn == "right") 2 else 1
                            page.mouse("mousePressed", x, y, btn, 1, mods, buttons)
                        }
                        PointerEventType.Release -> { page.mouse("mouseReleased", x, y, if (buttons == 2) "right" else "left", 1, mods, 0); buttons = 0 }
                        PointerEventType.Move -> page.mouse("mouseMoved", x, y, "none", 0, mods, if (e.buttons.isPrimaryPressed) 1 else 0)
                        PointerEventType.Scroll -> page.wheel(x, y, c.scrollDelta.x * 100, c.scrollDelta.y * 100)
                    }
                    c.consume()
                }
            }
        }
        .onKeyEvent { ev ->
            val awt = ev.nativeKeyEvent as? java.awt.event.KeyEvent ?: run { app.jam.desktop.shim.Desk.log('W', "Keys", "not an AWT key event: ${ev.nativeKeyEvent?.javaClass}", null); return@onKeyEvent false }
            Keys.send(page, ev.type, awt); true
        }
    ) {
        page.frame.value?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

/** Keys as the DevTools protocol takes them (Windows virtual key codes, DOM key names). */
object Keys {
    fun modifiers(m: androidx.compose.ui.input.pointer.PointerKeyboardModifiers): Int =
        (if (m.isAltPressed) 1 else 0) or (if (m.isCtrlPressed) 2 else 0) or (if (m.isMetaPressed) 4 else 0) or (if (m.isShiftPressed) 8 else 0)
    private val names = mapOf(
        8 to "Backspace", 9 to "Tab", 10 to "Enter", 27 to "Escape", 127 to "Delete", 37 to "ArrowLeft", 38 to "ArrowUp", 39 to "ArrowRight", 40 to "ArrowDown",
        36 to "Home", 35 to "End", 33 to "PageUp", 34 to "PageDown", 16 to "Shift", 17 to "Control", 18 to "Alt",
    )
    private val vk = mapOf(10 to 13, 127 to 46)
    @Volatile private var textSent = false
    @Volatile private var logged = 0
    fun send(page: app.jam.desktop.web.Page, type: KeyEventType, e: java.awt.event.KeyEvent) {
        val mods = (if (e.isAltDown) 1 else 0) or (if (e.isControlDown) 2 else 0) or (if (e.isMetaDown) 4 else 0) or (if (e.isShiftDown) 8 else 0)
        val code = vk[e.keyCode] ?: e.keyCode
        val name = names[e.keyCode]
        val ch = e.keyChar
        val printable = ch != java.awt.event.KeyEvent.CHAR_UNDEFINED && !ch.isISOControl() && (mods and 2) == 0
        if (logged < 40) { logged++; app.jam.desktop.shim.Desk.log('D', "Keys", "$type id=${e.id} code=${e.keyCode} char=${ch.code} mods=$mods", null) }
        when (type) {
            KeyEventType.KeyDown -> {
                val text = when { printable -> ch.toString(); e.keyCode == 10 -> "\r"; else -> null }
                textSent = printable
                // A character goes in as text (as an IME commits it), after its key's own event: it
                // does not depend on the browser turning the key into a character itself.
                if (printable) { page.key("rawKeyDown", ch.toString(), awtCode(e), code, mods, null); page.insertText(ch.toString()) }
                else page.key(if (text != null) "keyDown" else "rawKeyDown", name ?: "", awtCode(e), code, mods, text)
            }
            KeyEventType.KeyUp -> page.key("keyUp", name ?: if (printable) ch.toString() else "", awtCode(e), code, mods, null)
            // The typed character (AWT's KEY_TYPED) when the key press did not carry it: keyboard
            // layouts and dead keys (é, ñ) on Windows give the character only there.
            else -> if (e.id == java.awt.event.KeyEvent.KEY_TYPED && !textSent && ch != java.awt.event.KeyEvent.CHAR_UNDEFINED && !ch.isISOControl() && (mods and 2) == 0) page.insertText(ch.toString())
                else if (e.id == java.awt.event.KeyEvent.KEY_TYPED) textSent = false
        }
    }
    private fun awtCode(e: java.awt.event.KeyEvent): String = when (e.keyCode) {
        in java.awt.event.KeyEvent.VK_A..java.awt.event.KeyEvent.VK_Z -> "Key" + e.keyCode.toChar()
        in java.awt.event.KeyEvent.VK_0..java.awt.event.KeyEvent.VK_9 -> "Digit" + e.keyCode.toChar()
        else -> names[e.keyCode] ?: ""
    }
}
