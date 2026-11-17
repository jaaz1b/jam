package app.jam.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.jam.desktop.studio.Now
import app.jam.desktop.studio.Studio
import com.music.spotui.ui.navigation.Routes

/**
 * Jam's keyboard, as a desktop music app has it:
 * Space play/pause · Ctrl+→/← next/previous · Ctrl+↑/↓ volume · Ctrl+L Big Picture (lyrics) · Ctrl+M mini player ·
 * Ctrl+F search · Ctrl+, settings · F11 full screen · Ctrl+Q quit.
 */
object Shortcuts {
    /** Before the page: the shortcuts with Ctrl (a text field has no use for them). */
    fun preview(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.F11) { fullScreen(); return true }
        if (!e.isCtrlPressed || e.isShiftPressed) return false
        when (e.key) {
            Key.DirectionRight -> Now.next()
            Key.DirectionLeft -> Now.previous()
            Key.DirectionUp -> Now.volume = (Now.volume + 10).coerceAtMost(100)
            Key.DirectionDown -> Now.volume = (Now.volume - 10).coerceAtLeast(0)
            Key.L -> Studio.bigPicture.value = !Studio.bigPicture.value
            Key.M -> app.jam.desktop.studio.MiniPlayer.toggle()
            Key.F -> Studio.open(Routes.Search.route)
            Key.Comma -> Studio.open(Routes.Settings.route)
            Key.Q -> { quit(); return true }
            else -> return false
        }
        return true
    }

    /** After the page, for what nothing on it took: Space plays or pauses (not while typing). */
    fun handle(e: KeyEvent, window: java.awt.Window): Boolean {
        if (e.type != KeyEventType.KeyDown || e.key != Key.Spacebar || e.isCtrlPressed) return false
        if (typing(window)) return false
        Now.toggle(); return true
    }

    /** A text field has the keyboard: Compose then takes text input (an input method session). */
    private fun typing(window: java.awt.Window): Boolean = runCatching {
        val owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        owner.inputMethodRequests != null && owner.inputContext?.let { true } == true && owner.isEnabled && imeActive(owner)
    }.getOrDefault(false)

    private fun imeActive(c: java.awt.Component): Boolean =
        runCatching { c.javaClass.methods.firstOrNull { it.name == "getInputMethodRequests" }?.invoke(c) != null }.getOrDefault(false)

    private var placement: androidx.compose.ui.window.WindowPlacement? = null
    var state: androidx.compose.ui.window.WindowState? = null
    fun fullScreen() {
        val s = state ?: return
        if (s.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) s.placement = placement ?: androidx.compose.ui.window.WindowPlacement.Floating
        else { placement = s.placement; s.placement = androidx.compose.ui.window.WindowPlacement.Fullscreen }
    }
}
