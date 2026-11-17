package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Android's AndroidView: the view made once, attached while composed, drawn by the window's view tree (ViewNode). */
@Composable
fun <T : android.view.View> AndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = remember { factory(ctx) }
    SideEffect { update(view) }
    DisposableEffect(view) {
        view.attachAsRoot(true)
        onDispose { view.attachAsRoot(false); onRelease(view) }
    }
    app.jam.desktop.ViewNode(view, modifier)
}

@Composable
fun <T : android.view.View> AndroidView(factory: (android.content.Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}) =
    AndroidView(factory, modifier, null, {}, update)
