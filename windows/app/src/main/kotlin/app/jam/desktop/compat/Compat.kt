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
