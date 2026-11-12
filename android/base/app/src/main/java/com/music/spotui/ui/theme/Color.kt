package com.music.spotui.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The page: black with the AMOLED setting (an extension's look never undoes it), else a look's background or Jam's #0F0F13. */
val AppBackground: Color get() =
    if (app.myspoti.power.EnergySettings.oledBlack) Color.Black
    else com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND) ?: Color(0xFF0F0F13)
val GridBackground: Color get() = com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.SURFACE) ?: tone(0xFF2A2A2A)
/** The accent (Spotify green, the colour chosen in the settings, the wallpaper's: see DynamicColorPref, or an extension's look). */
val AppPalette: Color get() = AppAccent
val AppAccent: Color get() =
    com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
        ?: com.music.spotui.data.preferences.DynamicColorPref.accent() ?: com.music.spotui.data.api.SupportReminder.accentColor

/** Primary text and icons. */
val AppText: Color = Color.White
/** Secondary text and icons. */
val AppMuted: Color = Color(0xFFB8B8C6) // Jam's secondary text
/** Destructive actions (delete, leave, block) and failures shown as text. */
val AppDestructive = Color(0xFFF15E6C)
/** What a tint is mixed with to make a header. */
val AppShade: Color = Color.Black

/**
 * One of the palette's raised greys (#181818 ... #3E3E3E): "a bit raised from the page".
 * Jam: in Jam's cool greys (#1B1B22, #22222B, #2B2B35...): the same lightness, a touch of blue.
 */
fun tone(argb: Long): Color {
    val r = ((argb shr 16) and 0xFF).toInt(); val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
    if (r != g || g != b) return Color(argb)
    return Color(r, g, minOf(255, b + (b * 6 + 12) / 25))
}

/** The cover's colour as a page header: darkened toward black, so the title stays readable. */
fun headerTint(tint: Color, amount: Float): Color = lerp(tint, Color.Black, amount)
