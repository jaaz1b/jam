package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** The Android CompositionLocals the app reads, provided by the window (app.jam.desktop.Window). */
val LocalContext = staticCompositionLocalOf<android.content.Context> { app.jam.desktop.shim.DesktopApp.activity ?: app.jam.desktop.shim.DesktopApp.context }
val LocalConfiguration = staticCompositionLocalOf { android.content.res.Configuration.current }
val LocalView = staticCompositionLocalOf<android.view.View> { android.view.View(app.jam.desktop.shim.DesktopApp.activity ?: app.jam.desktop.shim.DesktopApp.context) }

/** A View holding Compose UI (setContent): the window draws its content where the view sits. */
class ComposeView(context: android.content.Context) : android.view.View(context) {
    val content = mutableStateOf<(@Composable () -> Unit)?>(null)
    fun setContent(c: @Composable () -> Unit) { content.value = c }
    fun setViewCompositionStrategy(s: ViewCompositionStrategy) {}
    fun disposeComposition() { content.value = null }
    val hasComposition: Boolean get() = content.value != null
}
interface ViewCompositionStrategy {
    object DisposeOnViewTreeLifecycleDestroyed : ViewCompositionStrategy
    object DisposeOnDetachedFromWindow : ViewCompositionStrategy
    object DisposeOnDetachedFromWindowOrReleasedFromPool : ViewCompositionStrategy
}
