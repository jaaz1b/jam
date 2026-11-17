package app.jam.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp

/** Jam's colours (the Android app's and Jam's Windows app's). */
object Jam {
    val Page = Color(0xFF0F0F13)
    val Side = Color(0xFF131318)
    val Raised = Color(0xFF1B1B22)
    val Raised2 = Color(0xFF22222B)
    val Line = Color(0xFF2B2B35)
    val Text = Color(0xFFECECF1)
    val Muted = Color(0xFFB8B8C6)
    val Faint = Color(0xFF9B9BAB)
    val Accent = Color(0xFF8B5CF6)
    val AccentLight = Color(0xFFA78BFA)
    val Danger = Color(0xFFF87171)
}

@Composable
fun JamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Jam.Accent, onPrimary = Color.White, secondary = Jam.AccentLight,
            background = Jam.Page, onBackground = Jam.Text, surface = Jam.Raised, onSurface = Jam.Text,
            surfaceVariant = Jam.Raised2, onSurfaceVariant = Jam.Muted, outline = Jam.Line, error = Jam.Danger,
        ),
        content = content,
    )
}

/** The few icons Jam draws (Material Design's shapes, 24 × 24). */
object Icons {
    private fun icon(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White)).build()

    val Home = icon("home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Search = icon("search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    val Library = icon("library", "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM18,7h-3v5.5c0,1.38 -1.12,2.5 -2.5,2.5S10,13.88 10,12.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51V5h4v2zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z")
    val Settings = icon("settings", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    val Play = icon("play", "M8,5v14l11,-7z")
    val Pause = icon("pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val Next = icon("next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val Previous = icon("previous", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Shuffle = icon("shuffle", "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.12z")
    val Repeat = icon("repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val Back = icon("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Volume = icon("volume", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
}

/** Jam's jar (src/main/resources/jam.png), for the window, the tray and the pages. */
val JamLogo: androidx.compose.ui.graphics.painter.BitmapPainter by lazy {
    val img = javax.imageio.ImageIO.read(Jam::class.java.getResourceAsStream("/jam.png"))
    androidx.compose.ui.graphics.painter.BitmapPainter(img.toComposeImageBitmap())
}
