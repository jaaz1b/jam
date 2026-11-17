// Jam for Windows: replaces the Android file of the same name (Glide's loader, see android-only.txt).
// Images go through Coil on the PC (app.jam.desktop.shim.Images), whose client applies the data saver.
package com.music.spotui.data

object DataSaverGlide {
    fun install(context: android.content.Context) {}
}
