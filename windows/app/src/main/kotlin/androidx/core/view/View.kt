package androidx.core.view

object WindowCompat {
    @JvmStatic fun setDecorFitsSystemWindows(w: android.app.Window, b: Boolean) {}
    @JvmStatic fun getInsetsController(w: android.app.Window, v: android.view.View): WindowInsetsControllerCompat = WindowInsetsControllerCompat(w, v)
    @JvmStatic fun enableEdgeToEdge(w: android.app.Window) {}
}
class WindowInsetsControllerCompat(w: android.app.Window, v: android.view.View) {
    var isAppearanceLightStatusBars = false
    var isAppearanceLightNavigationBars = false
    var systemBarsBehavior = 0
    fun hide(t: Int) { app.jam.desktop.shim.Windows.systemBars(t, false) }
    fun show(t: Int) { app.jam.desktop.shim.Windows.systemBars(t, true) }
    companion object { const val BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2; const val BEHAVIOR_DEFAULT = 1 }
}
class WindowInsetsCompat {
    fun getInsets(t: Int): androidx.core.graphics.Insets = androidx.core.graphics.Insets.NONE
    fun isVisible(t: Int): Boolean = false
    object Type { @JvmStatic fun systemBars() = 7; @JvmStatic fun statusBars() = 1; @JvmStatic fun navigationBars() = 2; @JvmStatic fun ime() = 8; @JvmStatic fun displayCutout() = 128; @JvmStatic fun systemGestures() = 16; @JvmStatic fun tappableElement() = 64; @JvmStatic fun captionBar() = 4 }
    companion object { @JvmField val CONSUMED = WindowInsetsCompat() }
}
fun interface OnApplyWindowInsetsListener { fun onApplyWindowInsets(v: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat }
object ViewCompat {
    @JvmStatic fun setOnApplyWindowInsetsListener(v: android.view.View, l: OnApplyWindowInsetsListener?) {}
    @JvmStatic fun requestApplyInsets(v: android.view.View) {}
    @JvmStatic fun getRootWindowInsets(v: android.view.View): WindowInsetsCompat? = WindowInsetsCompat()
    @JvmStatic fun setSystemGestureExclusionRects(v: android.view.View, r: List<android.graphics.Rect>) {}
    @JvmStatic fun performHapticFeedback(v: android.view.View, c: Int): Boolean = false
}
inline fun <reified T : android.view.ViewGroup.LayoutParams> android.view.View.updateLayoutParams(block: T.() -> Unit) { (layoutParams as? T)?.block() }
var android.view.View.isVisible: Boolean get() = visibility == android.view.View.VISIBLE; set(v) { visibility = if (v) android.view.View.VISIBLE else android.view.View.GONE }
