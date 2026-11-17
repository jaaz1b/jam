package android.graphics

import java.awt.image.BufferedImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toAwtImage

/** A Bitmap: a java.awt BufferedImage (ARGB). */
class Bitmap internal constructor(@JvmField val image: BufferedImage) {
    val width: Int get() = image.width
    val height: Int get() = image.height
    val config: Config get() = Config.ARGB_8888
    val isRecycled: Boolean get() = recycled
    private var recycled = false
    val byteCount: Int get() = width * height * 4
    val allocationByteCount: Int get() = byteCount
    val rowBytes: Int get() = width * 4
    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)
    fun setPixel(x: Int, y: Int, c: Int) = image.setRGB(x, y, c)
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) { image.getRGB(x, y, w, h, pixels, offset, stride) }
    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) { image.setRGB(x, y, w, h, pixels, offset, stride) }
    fun recycle() { recycled = true }
    fun hasAlpha() = true
    fun copy(c: Config?, mutable: Boolean): Bitmap = Bitmap(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { it.graphics.drawImage(image, 0, 0, null) })
    fun compress(format: CompressFormat, quality: Int, out: java.io.OutputStream): Boolean {
        val fmt = if (format == CompressFormat.PNG) "png" else "jpg"
        val img = if (fmt == "jpg") BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { it.graphics.drawImage(image, 0, 0, java.awt.Color.BLACK, null) } else image
        return javax.imageio.ImageIO.write(img, fmt, out)
    }
    fun eraseColor(c: Int) { val g = image.createGraphics(); g.composite = java.awt.AlphaComposite.Src; g.color = java.awt.Color(c, true); g.fillRect(0, 0, width, height); g.dispose() }
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
    companion object {
        @JvmStatic fun createBitmap(w: Int, h: Int, c: Config): Bitmap = Bitmap(BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))
        @JvmStatic fun createBitmap(src: Bitmap): Bitmap = src.copy(Config.ARGB_8888, true)
        @JvmStatic fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap = Bitmap(src.image.getSubimage(x, y, w, h).let { s -> BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.graphics.drawImage(s, 0, 0, null) } })
        @JvmStatic fun createBitmap(pixels: IntArray, w: Int, h: Int, c: Config): Bitmap = createBitmap(w, h, c).also { it.image.setRGB(0, 0, w, h, pixels, 0, w) }
        @JvmStatic fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
            val out = BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics(); g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(src.image, 0, 0, w, h, null); g.dispose()
            return Bitmap(out)
        }
        fun of(img: BufferedImage): Bitmap = Bitmap(if (img.type == BufferedImage.TYPE_INT_ARGB) img else BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB).also { it.graphics.drawImage(img, 0, 0, null) })
    }
}

object BitmapFactory {
    class Options { @JvmField var inJustDecodeBounds = false; @JvmField var inSampleSize = 1; @JvmField var outWidth = 0; @JvmField var outHeight = 0; @JvmField var inPreferredConfig: Bitmap.Config? = null; @JvmField var outMimeType: String? = null; @JvmField var inMutable = false }
    private fun read(bytes: ByteArray, o: Options?): Bitmap? {
        val img = runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) }.getOrNull()
            ?: runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap().toAwtImage() }.getOrNull()
            ?: return null
        o?.outWidth = img.width; o?.outHeight = img.height
        if (o?.inJustDecodeBounds == true) return null
        val s = (o?.inSampleSize ?: 1).coerceAtLeast(1)
        val b = Bitmap.of(img)
        return if (s > 1) Bitmap.createScaledBitmap(b, img.width / s, img.height / s, true) else b
    }
    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = read(data.copyOfRange(offset, offset + length), null)
    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int, o: Options?): Bitmap? = read(data.copyOfRange(offset, offset + length), o)
    @JvmStatic fun decodeStream(s: java.io.InputStream?): Bitmap? = s?.readBytes()?.let { read(it, null) }
    @JvmStatic fun decodeStream(s: java.io.InputStream?, pad: Rect?, o: Options?): Bitmap? = s?.readBytes()?.let { read(it, o) }
    @JvmStatic fun decodeFile(path: String): Bitmap? = runCatching { read(java.io.File(path).readBytes(), null) }.getOrNull()
    @JvmStatic fun decodeFile(path: String, o: Options?): Bitmap? = runCatching { read(java.io.File(path).readBytes(), o) }.getOrNull()
    @JvmStatic fun decodeResource(r: android.content.res.Resources, id: Int): Bitmap? = runCatching { read(r.openRawResource(id).readBytes(), null) }.getOrNull()
    @JvmStatic fun decodeFileDescriptor(fd: java.io.FileDescriptor): Bitmap? = runCatching { read(java.io.FileInputStream(fd).readBytes(), null) }.getOrNull()
}

object Color {
    const val BLACK = -0x1000000; const val WHITE = -0x1; const val TRANSPARENT = 0; const val RED = -0x10000; const val GREEN = -0xff0100; const val BLUE = -0xffff01
    const val GRAY = -0x777778; const val DKGRAY = -0xbbbbbc; const val LTGRAY = -0x333334; const val YELLOW = -0x100; const val CYAN = -0xff0001; const val MAGENTA = -0xff01
    @JvmStatic fun parseColor(s: String): Int = app.jam.desktop.shim.Res.parseColor(s).takeIf { it != 0 || s.trim('#') == "00000000" } ?: when (s.lowercase()) { "black" -> BLACK; "white" -> WHITE; "red" -> RED; else -> throw IllegalArgumentException("Unknown color $s") }
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun rgb(r: Float, g: Float, b: Float): Int = rgb((r * 255 + .5f).toInt(), (g * 255 + .5f).toInt(), (b * 255 + .5f).toInt())
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Float, r: Float, g: Float, b: Float): Int = argb((a * 255 + .5f).toInt(), (r * 255 + .5f).toInt(), (g * 255 + .5f).toInt(), (b * 255 + .5f).toInt())
    @JvmStatic fun alpha(c: Int) = c ushr 24
    @JvmStatic fun red(c: Int) = (c shr 16) and 0xFF
    @JvmStatic fun green(c: Int) = (c shr 8) and 0xFF
    @JvmStatic fun blue(c: Int) = c and 0xFF
    @JvmStatic fun luminance(c: Int): Float { fun l(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }; return (0.2126 * l(red(c)) + 0.7152 * l(green(c)) + 0.0722 * l(blue(c))).toFloat() }
    @JvmStatic fun colorToHSV(c: Int, hsv: FloatArray) { val f = java.awt.Color.RGBtoHSB(red(c), green(c), blue(c), null); hsv[0] = f[0] * 360; hsv[1] = f[1]; hsv[2] = f[2] }
    @JvmStatic fun HSVToColor(hsv: FloatArray): Int = java.awt.Color.HSBtoRGB(hsv[0] / 360f, hsv[1], hsv[2])
    @JvmStatic fun HSVToColor(a: Int, hsv: FloatArray): Int = (HSVToColor(hsv) and 0xFFFFFF) or (a shl 24)
    @JvmStatic fun RGBToHSV(r: Int, g: Int, b: Int, hsv: FloatArray) = colorToHSV(rgb(r, g, b), hsv)
}

open class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    fun width() = right - left; fun height() = bottom - top; fun centerX() = (left + right) / 2; fun centerY() = (top + bottom) / 2
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
}
open class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    fun width() = right - left; fun height() = bottom - top; fun centerX() = (left + right) / 2; fun centerY() = (top + bottom) / 2
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
}
data class Point(@JvmField var x: Int = 0, @JvmField var y: Int = 0)
data class PointF(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f)
open class Matrix { fun setScale(sx: Float, sy: Float) {}; fun postScale(sx: Float, sy: Float): Boolean = true; fun postTranslate(dx: Float, dy: Float): Boolean = true; fun setRotate(d: Float) {}; fun postRotate(d: Float): Boolean = true; fun reset() {} }

/** A Canvas over a Bitmap (Java2D), for the few drawings the app makes into bitmaps (share cards, QR codes). */
open class Canvas(private val bitmap: Bitmap? = null) {
    val g: java.awt.Graphics2D? = bitmap?.image?.createGraphics()?.also { it.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON); it.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON) }
    val width: Int get() = bitmap?.width ?: 0
    val height: Int get() = bitmap?.height ?: 0
    private fun paint(p: Paint?) { g?.color = java.awt.Color(p?.color ?: Color.BLACK, true) }
    fun drawColor(c: Int) { g?.color = java.awt.Color(c, true); g?.fillRect(0, 0, width, height) }
    fun drawColor(c: Int, mode: PorterDuff.Mode) = drawColor(c)
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { paint(p); g?.fill(java.awt.geom.Rectangle2D.Float(l, t, r - l, b - t)) }
    fun drawRect(r: RectF, p: Paint) = drawRect(r.left, r.top, r.right, r.bottom, p)
    fun drawRect(r: Rect, p: Paint) = drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), p)
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) { paint(p); g?.fill(java.awt.geom.RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2)) }
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) = drawRoundRect(RectF(l, t, r, b), rx, ry, p)
    fun drawCircle(cx: Float, cy: Float, radius: Float, p: Paint) { paint(p); g?.fill(java.awt.geom.Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2)) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { paint(p); g?.draw(java.awt.geom.Line2D.Float(x0, y0, x1, y1)) }
    fun drawBitmap(b: Bitmap, left: Float, top: Float, p: Paint?) { g?.drawImage(b.image, left.toInt(), top.toInt(), null) }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: Rect, p: Paint?) { g?.drawImage(b.image, dst.left, dst.top, dst.width(), dst.height(), null) }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, p: Paint?) { g?.drawImage(b.image, dst.left.toInt(), dst.top.toInt(), dst.width().toInt(), dst.height().toInt(), null) }
    fun drawBitmap(b: Bitmap, m: Matrix, p: Paint?) { g?.drawImage(b.image, 0, 0, null) }
    fun drawText(text: String, x: Float, y: Float, p: Paint) { paint(p); g?.font = p.awtFont(); g?.drawString(text, x, y) }
    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, p: Paint) = drawText(text.substring(start, end), x, y, p)
    fun drawPath(path: Path, p: Paint) { paint(p); g?.fill(path.awt) }
    fun drawOval(r: RectF, p: Paint) { paint(p); g?.fill(java.awt.geom.Ellipse2D.Float(r.left, r.top, r.width(), r.height())) }
    fun save(): Int = 0; fun restore() {}; fun restoreToCount(c: Int) {}
    fun translate(dx: Float, dy: Float) { g?.translate(dx.toDouble(), dy.toDouble()) }
    fun scale(sx: Float, sy: Float) { g?.scale(sx.toDouble(), sy.toDouble()) }
    fun rotate(d: Float) { g?.rotate(Math.toRadians(d.toDouble())) }
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { g?.clip(java.awt.geom.Rectangle2D.Float(l, t, r - l, b - t)); return true }
    fun clipPath(p: Path): Boolean { g?.clip(p.awt); return true }
    fun saveLayer(b: RectF?, p: Paint?): Int = 0
}
open class Path { internal val awt = java.awt.geom.Path2D.Float()
    fun moveTo(x: Float, y: Float) = awt.moveTo(x, y); fun lineTo(x: Float, y: Float) = awt.lineTo(x, y); fun close() = awt.closePath(); fun reset() = awt.reset()
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = awt.quadTo(x1, y1, x2, y2); fun cubicTo(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) = awt.curveTo(a, b, c, d, e, f)
    fun addRoundRect(r: RectF, rx: Float, ry: Float, d: Direction) = awt.append(java.awt.geom.RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), false)
    fun addRect(r: RectF, d: Direction) = awt.append(java.awt.geom.Rectangle2D.Float(r.left, r.top, r.width(), r.height()), false)
    fun addCircle(x: Float, y: Float, r: Float, d: Direction) = awt.append(java.awt.geom.Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false)
    enum class Direction { CW, CCW } }

open class Paint(flags: Int = 0) {
    var color: Int = Color.BLACK
    var alpha: Int get() = Color.alpha(color); set(v) { color = (color and 0xFFFFFF) or (v shl 24) }
    var textSize: Float = 12f
    var typeface: Typeface? = null
    var isAntiAlias: Boolean = true
    var isFilterBitmap: Boolean = true
    var isDither: Boolean = false
    var isFakeBoldText: Boolean = false
    var strokeWidth: Float = 0f
    var style: Style = Style.FILL
    var strokeCap: Cap = Cap.BUTT
    var textAlign: Align = Align.LEFT
    var shader: Shader? = null
    var xfermode: Xfermode? = null
    var maskFilter: MaskFilter? = null
    var colorFilter: ColorFilter? = null
    var letterSpacing: Float = 0f
    constructor(p: Paint) : this() { color = p.color; textSize = p.textSize; typeface = p.typeface }
    fun setShadowLayer(r: Float, dx: Float, dy: Float, c: Int) {}
    fun clearShadowLayer() {}
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    internal fun awtFont(): java.awt.Font = java.awt.Font(typeface?.family ?: "Segoe UI", if ((typeface?.style ?: 0) and Typeface.BOLD != 0 || isFakeBoldText) java.awt.Font.BOLD else java.awt.Font.PLAIN, textSize.toInt().coerceAtLeast(1))
    private val metricsG by lazy { BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics() }
    fun measureText(s: String): Float = metricsG.getFontMetrics(awtFont()).stringWidth(s).toFloat()
    fun measureText(s: CharSequence, start: Int, end: Int): Float = measureText(s.substring(start, end))
    fun getTextBounds(s: String, start: Int, end: Int, b: Rect) { val fm = metricsG.getFontMetrics(awtFont()); b.set(0, -fm.ascent, fm.stringWidth(s.substring(start, end)), fm.descent) }
    val fontMetrics: FontMetrics get() = metricsG.getFontMetrics(awtFont()).let { FontMetrics().apply { ascent = -it.ascent.toFloat(); descent = it.descent.toFloat(); top = ascent; bottom = descent; leading = it.leading.toFloat() } }
    fun breakText(text: String, measureForwards: Boolean, maxWidth: Float, measuredWidth: FloatArray?): Int { var i = 0; while (i < text.length && measureText(text.substring(0, i + 1)) <= maxWidth) i++; return i }
    class FontMetrics { @JvmField var ascent = 0f; @JvmField var descent = 0f; @JvmField var top = 0f; @JvmField var bottom = 0f; @JvmField var leading = 0f }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }
    companion object { const val ANTI_ALIAS_FLAG = 1; const val FILTER_BITMAP_FLAG = 2; const val DITHER_FLAG = 4; const val FAKE_BOLD_TEXT_FLAG = 32 }
}
class Typeface private constructor(val family: String, val style: Int) {
    companion object {
        const val NORMAL = 0; const val BOLD = 1; const val ITALIC = 2; const val BOLD_ITALIC = 3
        @JvmField val DEFAULT = Typeface("Segoe UI", 0); @JvmField val DEFAULT_BOLD = Typeface("Segoe UI", 1); @JvmField val SANS_SERIF = Typeface("Segoe UI", 0)
        @JvmField val SERIF = Typeface("Serif", 0); @JvmField val MONOSPACE = Typeface("Consolas", 0)
        @JvmStatic fun create(family: Typeface?, style: Int) = Typeface(family?.family ?: "Segoe UI", style)
        @JvmStatic fun create(family: String?, style: Int) = Typeface(family ?: "Segoe UI", style)
        @JvmStatic fun create(family: Typeface?, weight: Int, italic: Boolean) = Typeface(family?.family ?: "Segoe UI", if (weight >= 600) BOLD else NORMAL)
        @JvmStatic fun defaultFromStyle(style: Int) = Typeface("Segoe UI", style)
    }
}
open class Shader { enum class TileMode { CLAMP, REPEAT, MIRROR, DECAL } }
class LinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?, tile: Shader.TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)
}
class RadialGradient(cx: Float, cy: Float, r: Float, colors: IntArray, positions: FloatArray?, tile: Shader.TileMode) : Shader()
class BitmapShader(b: Bitmap, x: Shader.TileMode, y: Shader.TileMode) : Shader()
open class Xfermode
class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()
object PorterDuff { enum class Mode { CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN, MULTIPLY, SCREEN, ADD, OVERLAY } }
open class MaskFilter
class BlurMaskFilter(radius: Float, style: Blur) : MaskFilter() { enum class Blur { NORMAL, SOLID, OUTER, INNER } }
open class ColorFilter
class PorterDuffColorFilter(c: Int, m: PorterDuff.Mode) : ColorFilter()
class ColorMatrix(val array: FloatArray = FloatArray(20)) { fun setSaturation(s: Float) {} }
class ColorMatrixColorFilter(m: ColorMatrix) : ColorFilter()
/** Android 12+ render effects (blurs): the Compose side uses its own; these exist so the code compiles. */
class RenderEffect private constructor() {
    companion object {
        @JvmStatic fun createBlurEffect(rx: Float, ry: Float, mode: Shader.TileMode) = RenderEffect()
        @JvmStatic fun createRuntimeShaderEffect(s: RuntimeShader, name: String) = RenderEffect()
        @JvmStatic fun createChainEffect(a: RenderEffect, b: RenderEffect) = RenderEffect()
        @JvmStatic fun createColorFilterEffect(f: ColorFilter) = RenderEffect()
        @JvmStatic fun createOffsetEffect(x: Float, y: Float) = RenderEffect()
    }
}
class RuntimeShader(val sksl: String) : Shader() {
    fun setFloatUniform(name: String, v: Float) {}
    fun setFloatUniform(name: String, a: Float, b: Float) {}
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float) {}
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float, d: Float) {}
    fun setFloatUniform(name: String, v: FloatArray) {}
    fun setIntUniform(name: String, v: Int) {}
    fun setColorUniform(name: String, c: Int) {}
    fun setInputShader(name: String, s: Shader) {}
}
class ImageDecoder private constructor() { companion object { @JvmStatic fun createSource(f: java.io.File) = Source(); @JvmStatic fun decodeBitmap(s: Source): Bitmap = throw java.io.IOException("unsupported") } class Source }
class SurfaceTexture(tex: Int) { fun setDefaultBufferSize(w: Int, h: Int) {}; fun release() {} }
