package androidx.core.graphics

object ColorUtils {
    @JvmStatic fun colorToHSL(color: Int, out: FloatArray) = hsl(color, out)
    private fun hsl(rgb: Int, out: FloatArray) {
        val r = (rgb shr 16 and 255) / 255f; val g = (rgb shr 8 and 255) / 255f; val b = (rgb and 255) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn; val l = (mx + mn) / 2f
        val h: Float; val s: Float
        if (mx == mn) { h = 0f; s = 0f } else { h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }; s = d / (1f - kotlin.math.abs(2f * l - 1f)) }
        out[0] = (h * 60f + 360f) % 360f; out[1] = s.coerceIn(0f, 1f); out[2] = l.coerceIn(0f, 1f)
    }
    @JvmStatic fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        val c = (1f - kotlin.math.abs(2 * l - 1f)) * s; val m = l - 0.5f * c; val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
        val hi = (h.toInt() / 60).coerceIn(0, 5)
        val (r, g, b) = when (hi) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        fun ch(v: Float) = (255f * (v + m)).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(ch(r), ch(g), ch(b))
    }
    @JvmStatic fun blendARGB(a: Int, b: Int, ratio: Float): Int { val inv = 1f - ratio
        return android.graphics.Color.argb((android.graphics.Color.alpha(a) * inv + android.graphics.Color.alpha(b) * ratio).toInt(), (android.graphics.Color.red(a) * inv + android.graphics.Color.red(b) * ratio).toInt(),
            (android.graphics.Color.green(a) * inv + android.graphics.Color.green(b) * ratio).toInt(), (android.graphics.Color.blue(a) * inv + android.graphics.Color.blue(b) * ratio).toInt()) }
    @JvmStatic fun calculateLuminance(c: Int): Double = android.graphics.Color.luminance(c).toDouble()
    @JvmStatic fun setAlphaComponent(c: Int, a: Int): Int = (c and 0x00ffffff) or (a shl 24)
    @JvmStatic fun calculateContrast(fg: Int, bg: Int): Double { val l1 = calculateLuminance(fg) + 0.05; val l2 = calculateLuminance(bg) + 0.05; return maxOf(l1, l2) / minOf(l1, l2) }
    @JvmStatic fun compositeColors(fg: Int, bg: Int): Int { val a = android.graphics.Color.alpha(fg) / 255f; return blendARGB(bg, fg or (0xFF shl 24), a) }
    @JvmStatic fun RGBToHSL(r: Int, g: Int, b: Int, out: FloatArray) = hsl(android.graphics.Color.rgb(r, g, b), out)
}
class Insets private constructor(@JvmField val left: Int, @JvmField val top: Int, @JvmField val right: Int, @JvmField val bottom: Int) {
    companion object { @JvmField val NONE = Insets(0, 0, 0, 0); @JvmStatic fun of(l: Int, t: Int, r: Int, b: Int) = Insets(l, t, r, b) }
}
fun android.graphics.Bitmap.scale(w: Int, h: Int, filter: Boolean = true): android.graphics.Bitmap = android.graphics.Bitmap.createScaledBitmap(this, w, h, filter)
fun createBitmap(w: Int, h: Int, config: android.graphics.Bitmap.Config = android.graphics.Bitmap.Config.ARGB_8888): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(w, h, config)
fun android.graphics.Bitmap.get(x: Int, y: Int): Int = getPixel(x, y)
fun Int.toColorInt(): Int = this
fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
