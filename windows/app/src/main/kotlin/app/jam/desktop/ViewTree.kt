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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
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
            Keys.send(page, ev); true
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
    /** A key from Compose (an AWT event under Linux, Compose's own under Windows): to the page. */
    fun send(page: app.jam.desktop.web.Page, ev: androidx.compose.ui.input.key.KeyEvent) {
        val awt = ev.nativeKeyEvent as? java.awt.event.KeyEvent
        val mods = (if (ev.isAltPressed) 1 else 0) or (if (ev.isCtrlPressed) 2 else 0) or (if (ev.isMetaPressed) 4 else 0) or (if (ev.isShiftPressed) 8 else 0)
        val keyCode = awt?.keyCode ?: ev.key.nativeKeyCode
        val code = vk[keyCode] ?: keyCode
        val name = names[keyCode]
        // 0xFFFF is "no character" (AWT's CHAR_UNDEFINED, e.g. Shift on its own under Windows).
        val cp = (ev.utf16CodePoint.takeIf { it in 1..0xFFFE } ?: awt?.keyChar?.takeIf { it != java.awt.event.KeyEvent.CHAR_UNDEFINED }?.code ?: 0)
            .takeIf { keyCode !in setOf(16, 17, 18, 20, 157, 524, 525) } ?: 0
        val ch = if (cp > 0) String(Character.toChars(cp)) else ""
        val printable = cp >= 32 && cp != 127 && (mods and 2) == 0
        if (logged < 30) { logged++; app.jam.desktop.shim.Desk.log('D', "Keys", "${ev.type} key=$keyCode cp=$cp mods=$mods", null) }
        when (ev.type) {
            KeyEventType.KeyDown -> {
                textSent = printable
                val ctrl = (mods and 2) != 0 && (mods and 1) == 0
                when {
                    // Edit shortcuts: the page's own commands (a key event alone does not run them).
                    ctrl && keyCode == java.awt.event.KeyEvent.VK_V -> app.jam.desktop.shim.Clip.get()?.let { page.insertText(it) }
                    ctrl && keyCode == java.awt.event.KeyEvent.VK_A -> page.key("rawKeyDown", "a", "KeyA", 65, mods, null, listOf("selectAll"))
                    ctrl && keyCode == java.awt.event.KeyEvent.VK_C -> page.key("rawKeyDown", "c", "KeyC", 67, mods, null, listOf("copy"))
                    ctrl && keyCode == java.awt.event.KeyEvent.VK_X -> page.key("rawKeyDown", "x", "KeyX", 88, mods, null, listOf("cut"))
                    ctrl && keyCode == java.awt.event.KeyEvent.VK_Z -> page.key("rawKeyDown", "z", "KeyZ", 90, mods, null, listOf(if ((mods and 8) != 0) "redo" else "undo"))
                    // A character goes in as text (as an IME commits it), after its key's own event.
                    printable -> { page.key("rawKeyDown", ch, domCode(keyCode), code, mods, null); page.insertText(ch) }
                    else -> page.key(if (keyCode == 10) "keyDown" else "rawKeyDown", name ?: "", domCode(keyCode), code, mods, if (keyCode == 10) "\r" else null)
                }
            }
            KeyEventType.KeyUp -> page.key("keyUp", name ?: ch, domCode(keyCode), code, mods, null)
            // A character that came on its own (typed after the key press: layouts, dead keys).
            else -> if (!textSent && printable) page.insertText(ch) else textSent = false
        }
    }
    private fun domCode(k: Int): String = when (k) {
        in java.awt.event.KeyEvent.VK_A..java.awt.event.KeyEvent.VK_Z -> "Key" + k.toChar()
        in java.awt.event.KeyEvent.VK_0..java.awt.event.KeyEvent.VK_9 -> "Digit" + k.toChar()
        32 -> "Space"
        else -> names[k] ?: ""
    }
}
