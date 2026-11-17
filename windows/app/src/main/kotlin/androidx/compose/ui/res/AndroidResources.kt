package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jam.desktop.shim.Res

/** Android's resource functions for Compose, by R id, over the app's res/ (see app.jam.desktop.shim.Res). */
@Composable fun stringResource(id: Int): String { val l = app.jam.desktop.shim.Windows.localeKey.value; return remember(id, l) { Res.string(id) } }
@Composable fun stringResource(id: Int, vararg formatArgs: Any): String { stringResource(id); return Res.format(id, formatArgs) }
@Composable fun pluralStringResource(id: Int, count: Int): String { app.jam.desktop.shim.Windows.localeKey.value; return Res.plural(id, count) }
@Composable fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String { app.jam.desktop.shim.Windows.localeKey.value; return runCatching { String.format(Res.locale, Res.plural(id, count), *formatArgs) }.getOrElse { Res.plural(id, count) } }
@Composable fun stringArrayResource(id: Int): Array<String> = remember(id) { Res.array(id).toTypedArray() }
@Composable fun colorResource(id: Int): Color = remember(id) { Color(Res.color(id)) }
@Composable fun dimensionResource(id: Int): Dp = remember(id) { Res.dimen(id).dp }
@Composable fun integerResource(id: Int): Int = remember(id) { Res.integer(id) }
@Composable fun booleanResource(id: Int): Boolean = remember(id) { Res.bool(id) }

/** A drawable or mipmap: Android vector XML (as an ImageVector), PNG/JPEG/WebP (as a bitmap). */
@Composable
fun painterResource(id: Int): Painter {
    val density = LocalDensity.current
    val loaded = remember(id) { AndroidDrawables.load(id, density) }
    return when (loaded) {
        is ImageVector -> rememberVectorPainter(loaded)
        is ImageBitmap -> remember(loaded) { BitmapPainter(loaded) }
        else -> remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent) }
    }
}
@Composable fun ImageVector.Companion.vectorResource(id: Int): ImageVector {
    val density = LocalDensity.current
    return remember(id) { AndroidDrawables.load(id, density) as? ImageVector ?: ImageVector.Builder(defaultWidth = 1.dp, defaultHeight = 1.dp, viewportWidth = 1f, viewportHeight = 1f).build() }
}
fun ImageVector.Companion.vectorResource(theme: Any?, res: android.content.res.Resources, id: Int): ImageVector =
    AndroidDrawables.load(id, androidx.compose.ui.unit.Density(1f)) as? ImageVector ?: ImageVector.Builder(defaultWidth = 1.dp, defaultHeight = 1.dp, viewportWidth = 1f, viewportHeight = 1f).build()
@Composable fun ImageBitmap.Companion.imageResource(id: Int): ImageBitmap = remember(id) { AndroidDrawables.load(id, androidx.compose.ui.unit.Density(1f)) as? ImageBitmap ?: ImageBitmap(1, 1) }

object AndroidDrawables {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Any>()
    fun load(id: Int, density: androidx.compose.ui.unit.Density): Any? = cache[id] ?: run {
        val path = Res.file(id) ?: return null
        val v: Any? = runCatching {
            if (path.endsWith(".xml")) {
                var text = Res.open(path).use { it.readBytes().decodeToString() }
                // An adaptive icon (mipmap-anydpi): its foreground drawable.
                Regex("<foreground[^>]*android:drawable=\"@(drawable|mipmap)/([A-Za-z0-9_]+)\"").find(text)?.let { m ->
                    return@runCatching load(Res.id(m.groupValues[1], m.groupValues[2]), density)
                }
                // Colours given as @color/… resolved here; the XML loader takes literal colours only.
                text = Regex("@color/([A-Za-z0-9_]+)").replace(text) { m -> "#%08X".format(Res.colorValue(m.groupValues[1])) }
                text = Regex("\\?attr/[A-Za-z0-9_]+|\\?android:attr/[A-Za-z0-9_]+").replace(text, "#FFFFFFFF")
                loadXmlImageVector(org.xml.sax.InputSource(text.byteInputStream()), density)
            } else Res.open(path).use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).let { i -> i.toComposeImageBitmap() } }
        }.onFailure { app.jam.desktop.shim.Desk.log('W', "Res", "drawable $path: $it", null) }.getOrNull()
        if (v != null) cache[id] = v
        v
    }
}
