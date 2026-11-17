package androidx.activity.compose

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

val LocalOnBackPressedDispatcherOwner = compositionLocalOf<OnBackPressedDispatcherOwner?> { app.jam.desktop.shim.DesktopApp.activity }
object LocalActivityResultRegistryOwner
val LocalActivity = compositionLocalOf<android.app.Activity?> { app.jam.desktop.shim.DesktopApp.activity }

/** Android's BackHandler, on the activity's dispatcher (Esc / mouse back on the PC). */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val cb = remember { object : OnBackPressedCallback(enabled) { override fun handleOnBackPressed() { current() } } }
    SideEffect { cb.isEnabled = enabled }
    val owner = LocalOnBackPressedDispatcherOwner.current
    DisposableEffect(owner) {
        owner?.onBackPressedDispatcher?.addCallback(cb)
        onDispose { cb.remove() }
    }
}
private operator fun <T> androidx.compose.runtime.State<T>.getValue(t: Any?, p: kotlin.reflect.KProperty<*>): T = value

@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat>) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled) { scope.launch { onBack(kotlinx.coroutines.flow.emptyFlow()) } }
}

/** ComponentActivity.setContent: the window draws [content]. */
fun androidx.activity.ComponentActivity.setContent(parent: androidx.compose.runtime.CompositionContext? = null, content: @Composable () -> Unit) {
    setContentView(androidx.compose.ui.platform.ComposeView(this).apply { setContent(content) })
}

/** Activity results on the PC: file pickers are Windows' own dialogs; permissions are granted. */
@Composable
fun <I, O> rememberLauncherForActivityResult(contract: androidx.activity.result.contract.ActivityResultContract<I, O>, onResult: (O) -> Unit): androidx.activity.result.ActivityResultLauncher<I> {
    val cb by rememberUpdatedState(onResult)
    return remember(contract) {
        object : androidx.activity.result.ActivityResultLauncher<I>() {
            override fun launch(input: I, options: Any?) { contract.desktopLaunch(input) { out -> app.jam.desktop.shim.Desk.post { cb(out) } } }
            override fun unregister() {}
            override val contract = contract
        }
    }
}
