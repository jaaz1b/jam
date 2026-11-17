package app.jam.desktop.compat

import androidx.savedstate.read

/** Bundle.getString for navigation's arguments, which are a SavedState on the PC. */
fun androidx.savedstate.SavedState.getString(key: String): String? = read { getStringOrNull(key) }
