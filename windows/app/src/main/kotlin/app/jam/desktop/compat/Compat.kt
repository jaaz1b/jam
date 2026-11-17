package app.jam.desktop.compat

import androidx.savedstate.read

/** Bundle.getString for navigation's arguments, which are a SavedState on the PC. */
fun androidx.savedstate.SavedState.getString(key: String): String? = read { getStringOrNull(key) }

/** viewModel(), with Android's reflective factory (no-arg, Application or SavedStateHandle constructors). */
@androidx.compose.runtime.Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> viewModel(
    viewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner = checkNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" },
    key: String? = null,
): VM = androidx.lifecycle.viewmodel.compose.viewModel(VM::class, viewModelStoreOwner, key, app.jam.desktop.shim.ReflectiveViewModels)

/** Font(R.font.x, weight, style, variationSettings): the TTF from the app's res/font, its variation axes applied. */
fun Font(
    resId: Int,
    weight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
    style: androidx.compose.ui.text.font.FontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
    variationSettings: androidx.compose.ui.text.font.FontVariation.Settings = androidx.compose.ui.text.font.FontVariation.Settings(weight, style),
): androidx.compose.ui.text.font.Font {
    val path = app.jam.desktop.shim.Res.file(resId) ?: error("font $resId")
    val bytes = fontBytes.getOrPut(path) { app.jam.desktop.shim.Res.open(path)!!.use { it.readBytes() } }
    return androidx.compose.ui.text.platform.Font("$path-${weight.weight}-$style-$variationSettings", bytes, weight, style, variationSettings)
}
private val fontBytes = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
