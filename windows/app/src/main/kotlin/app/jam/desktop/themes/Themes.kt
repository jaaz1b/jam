package app.jam.desktop.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.myspoti.extensions.UiStyle

/**
 * Jam for Windows' themes: colour palettes over the app's looks (the page, the raised surfaces and
 * the accent), as a `ui.style` extension's look would set them. An extension's look still wins.
 */
object Themes {
    data class Theme(val id: String, val name: String, val background: Long?, val surface: Long?, val accent: Long?, val note: String)

    val all = listOf(
        Theme("jam", "Jam", null, null, null, "Jam's own: deep violet-grey, the accent you chose"),
        Theme("oled", "OLED Black", 0xFF000000, 0xFF111114, null, "True black, for OLED screens at night"),
        Theme("midnight", "Midnight", 0xFF0A0F1E, 0xFF161D33, 0xFF6EA8FE, "Navy night sky, cool blue accent"),
        Theme("nord", "Nord", 0xFF2E3440, 0xFF3B4252, 0xFF88C0D0, "Arctic, frosty blue-greys"),
        Theme("dracula", "Dracula", 0xFF282A36, 0xFF343746, 0xFFBD93F9, "The classic dark purple"),
        Theme("mocha", "Catppuccin Mocha", 0xFF1E1E2E, 0xFF313244, 0xFFCBA6F7, "Soft pastels on warm dark"),
        Theme("rosepine", "Rosé Pine", 0xFF191724, 0xFF26233A, 0xFFEBBCBA, "All natural pine, faux fur and a bit of soho vibes"),
        Theme("tokyo", "Tokyo Night", 0xFF1A1B26, 0xFF24283B, 0xFF7AA2F7, "Neon lights of downtown Tokyo"),
        Theme("gruvbox", "Gruvbox", 0xFF282828, 0xFF3C3836, 0xFFFABD2F, "Retro groove, warm and earthy"),
        Theme("forest", "Forest", 0xFF0E1712, 0xFF1A2920, 0xFF5CD08D, "Moss, pine and evening light"),
        Theme("ember", "Ember", 0xFF170D0C, 0xFF2A1714, 0xFFFF7A4D, "Glowing coals"),
        Theme("ocean", "Ocean", 0xFF061418, 0xFF0F252B, 0xFF2ED3C6, "Deep sea teal"),
        Theme("berry", "Strawberry Jam", 0xFF1A0B12, 0xFF2C1520, 0xFFFF4F7B, "Jam's jar, in colour"),
        Theme("mono", "Monochrome", 0xFF101010, 0xFF1E1E1E, 0xFFE8E8E8, "Black, white and greys only"),
        Theme("aurora", "Aurora", 0xFF0A1416, 0xFF132328, 0xFF7CF5C4, "Northern lights over a polar night"),
        Theme("sakura", "Sakura", 0xFF1A1116, 0xFF2A1B23, 0xFFFF9EC6, "Cherry blossoms at dusk"),
        Theme("lavender", "Lavender Haze", 0xFF15121F, 0xFF221D32, 0xFFB9A6FF, "Soft violet, half asleep"),
        Theme("synthwave", "Synthwave", 0xFF140A1F, 0xFF231035, 0xFFFF4FD8, "Neon pink on an 80s night drive"),
        Theme("coffee", "Coffee", 0xFF16110D, 0xFF241B15, 0xFFD4A373, "Dark roast with a little cream"),
        Theme("arctic", "Arctic", 0xFF0C141B, 0xFF16212B, 0xFF9AD7FF, "Cold air, clear ice"),
        Theme("matcha", "Matcha", 0xFF10150F, 0xFF1B2319, 0xFFA8D672, "Green tea, calm and bright"),
        Theme("crimson", "Crimson", 0xFF160A0C, 0xFF261114, 0xFFFF4D5E, "Deep red velvet"),
        Theme("solar", "Solar", 0xFF16130A, 0xFF262011, 0xFFFFC53D, "Golden hour"),
        Theme("graphite", "Graphite", 0xFF141414, 0xFF1F1F1F, 0xFF9EA7B3, "Quiet grey steel"),
        Theme(SONG, "Song Colours", null, null, null, "Follows the cover of the song playing, like Apple Music"),
    )
    const val SONG = "song"
    /** The cover's main colour, for [SONG] (set by the Studio window as songs change). */
    val songColor = mutableStateOf<Color?>(null)

    private val prefs by lazy { android.content.Prefs.of("jam_theme") }
    var current by mutableStateOf(all.firstOrNull { it.id == prefs.getString("id", "jam") } ?: all[0])
        private set

    fun set(t: Theme) { current = t; prefs.edit().putString("id", t.id).apply() }

    /** The theme's colour for a look key (UiStyle.ACCENT / BACKGROUND / SURFACE), or null (the app's own). */
    fun color(key: String): Color? {
        if (current.id == SONG) {
            val c = songColor.value ?: return null
            return when (key) {
                UiStyle.BACKGROUND -> lerp(Color.Black, c, 0.16f)
                UiStyle.SURFACE -> lerp(Color.Black, c, 0.26f)
                UiStyle.ACCENT -> lerp(c, Color.White, 0.25f)
                else -> null
            }
        }
        return when (key) {
            UiStyle.BACKGROUND -> current.background
            UiStyle.SURFACE -> current.surface
            UiStyle.ACCENT -> current.accent
            else -> null
        }?.let { Color(it) }
    }

    private fun lerp(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)

    /** The page colour of the theme (Song Colours: the song's, else Jam's). */
    val page: Color get() = color(UiStyle.BACKGROUND) ?: Color(0xFF0F0F13)
    val raised: Color get() = color(UiStyle.SURFACE) ?: Color(0xFF1A1A21)

    /** The window's own background (behind the app's pages). */
    val window: Color get() = color(UiStyle.BACKGROUND) ?: Color(0xFF0A0A0C)
}

/** The settings row (Classic look): the theme in use, the picker on a click. */
@Composable
fun ThemesRow() {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text("Theme", color = Color.White, fontSize = 16.sp)
        Text(Themes.current.name + " · colours for the whole app", color = Color(0xFFB8B8C6), fontSize = 12.sp)
    }
    if (open) ThemePicker { open = false }
}

/** The settings row (Immersive look). */
@Composable
fun ImmersiveThemesRow() {
    var open by remember { mutableStateOf(false) }
    com.music.spotui.ui.immersive.pages.ImmersiveGroupRow("Theme", detail = Themes.current.name, chevron = true, onClick = { open = true })
    if (open) ThemePicker { open = false }
}

@Composable
fun ThemePicker(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF17171C)).padding(24.dp)) {
            Text("Theme", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Colours for every page. The Immersive look keeps taking its colours from the song's cover.", color = Color(0xFFB8B8C6), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text("Layout", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val studio = app.jam.desktop.studio.Studio.on.value
                LayoutCard("Studio", "Sidebar, player on top, Big Picture lyrics — made for the PC", studio, Modifier.weight(1f)) { app.jam.desktop.studio.Studio.setOn(true) }
                LayoutCard("Phone", "The look as on Android: tabs and the mini player", !studio, Modifier.weight(1f)) { app.jam.desktop.studio.Studio.setOn(false) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val jam = app.jam.desktop.studio.Studio.pointer.value
                LayoutCard("Jam pointer", "A mouse pointer in the theme's colour", jam, Modifier.weight(1f)) { app.jam.desktop.studio.Studio.setPointer(true) }
                LayoutCard("Windows pointer", "The usual Windows mouse pointer", !jam, Modifier.weight(1f)) { app.jam.desktop.studio.Studio.setPointer(false) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Colours", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(400.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Themes.all) { t -> Swatch(t, t.id == Themes.current.id) { Themes.set(t) } }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Done", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Themes.current.accent?.let { Color(it) } ?: com.music.spotui.ui.theme.AppAccent).clickable { onDismiss() }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun LayoutCard(title: String, note: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x22FFFFFF) else Color(0x0FFFFFFF))
            .border(if (selected) 2.dp else 1.dp, if (selected) com.music.spotui.ui.theme.AppAccent else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(note, color = Color(0x99FFFFFF), fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun Swatch(t: Themes.Theme, selected: Boolean, onClick: () -> Unit) {
    val song = if (t.id == Themes.SONG) Themes.songColor.value else null
    val bg = song?.let { androidx.compose.ui.graphics.lerp(Color.Black, it, 0.16f) } ?: Color(t.background ?: 0xFF0F0F13)
    val surface = song?.let { androidx.compose.ui.graphics.lerp(Color.Black, it, 0.3f) } ?: Color(t.surface ?: 0xFF22222B)
    val accent = song ?: t.accent?.let { Color(it) } ?: com.music.spotui.ui.theme.AppAccent
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(surface, bg)))) {
            Box(Modifier.padding(10.dp).size(22.dp).clip(CircleShape).background(accent).align(Alignment.BottomEnd))
            Column(Modifier.padding(10.dp)) {
                Box(Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xCCFFFFFF)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x66FFFFFF)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(t.note, color = Color(0x99FFFFFF), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
    }
}
