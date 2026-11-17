package app.jam.desktop.shim

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Toasts waiting to be shown by the window (one at a time, at the bottom). */
object Toasts {
    data class Shown(val text: String, val long: Boolean, val at: Long = System.nanoTime())
    private val _current = MutableStateFlow<Shown?>(null)
    val current: StateFlow<Shown?> = _current
    fun show(text: String, long: Boolean) { _current.value = Shown(text, long) }
    fun dismiss(s: Shown) { if (_current.value == s) _current.value = null }
}
