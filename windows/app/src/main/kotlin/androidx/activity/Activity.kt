package androidx.activity

import java.util.concurrent.CopyOnWriteArrayList

abstract class OnBackPressedCallback(enabled: Boolean) {
    @Volatile var isEnabled: Boolean = enabled
        set(v) { field = v; changed?.invoke() }
    internal var changed: (() -> Unit)? = null
    internal var owner: OnBackPressedDispatcher? = null
    abstract fun handleOnBackPressed()
    open fun handleOnBackStarted(e: BackEventCompat) {}
    open fun handleOnBackProgressed(e: BackEventCompat) {}
    open fun handleOnBackCancelled() {}
    fun remove() { owner?.remove(this) }
}
class BackEventCompat(val touchX: Float, val touchY: Float, val progress: Float, val swipeEdge: Int)

/** Back on the PC: Esc, the mouse's back button or Alt+Left (see the window). The last callback added, enabled, wins. */
class OnBackPressedDispatcher(private val fallback: (() -> Unit)? = null) {
    private val callbacks = CopyOnWriteArrayList<OnBackPressedCallback>()
    val enabledCount = androidx.compose.runtime.mutableIntStateOf(0)
    private fun recount() { enabledCount.intValue = callbacks.count { it.isEnabled } }
    fun addCallback(cb: OnBackPressedCallback) { cb.owner = this; cb.changed = ::recount; callbacks += cb; recount() }
    fun addCallback(owner: androidx.lifecycle.LifecycleOwner?, cb: OnBackPressedCallback) {
        addCallback(cb)
        owner?.lifecycle?.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) remove(cb)
            }
        })
    }
    internal fun remove(cb: OnBackPressedCallback) { callbacks -= cb; recount() }
    fun hasEnabledCallbacks(): Boolean = callbacks.any { it.isEnabled }
    /** True when a callback took it. */
    fun dispatch(): Boolean { val cb = callbacks.lastOrNull { it.isEnabled } ?: return false; cb.handleOnBackPressed(); return true }
    fun onBackPressed() { if (!dispatch()) fallback?.invoke() }
}
interface OnBackPressedDispatcherOwner : androidx.lifecycle.LifecycleOwner { val onBackPressedDispatcher: OnBackPressedDispatcher }

open class ComponentActivity : android.app.Activity()

fun ComponentActivity.enableEdgeToEdge() {}
fun android.app.Activity.enableEdgeToEdge(statusBarStyle: Any? = null, navigationBarStyle: Any? = null) {}
