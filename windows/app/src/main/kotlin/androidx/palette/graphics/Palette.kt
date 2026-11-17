package androidx.palette.graphics

import android.graphics.Bitmap
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * AndroidX Palette on the PC, the same algorithm: the image scaled to ~112² px, a modified median cut
 * over 5-bit RGB, swatches scored for the six targets (vibrant / muted, light / dark) as AndroidX does.
 */
class Palette private constructor(val swatches: List<Swatch>) {
    class Swatch(val rgb: Int, val population: Int) {
        val hsl: FloatArray by lazy { FloatArray(3).also { rgbToHsl(rgb, it) } }
        val titleTextColor: Int get() = if (lum(rgb) > 0.5) 0xB3000000.toInt() else 0xFFFFFFFF.toInt()
        val bodyTextColor: Int get() = if (lum(rgb) > 0.5) 0x8A000000.toInt() else 0xB3FFFFFF.toInt()
        override fun toString() = "Swatch(#${Integer.toHexString(rgb)}, $population)"
    }
    class Target internal constructor(val minSat: Float, val targetSat: Float, val maxSat: Float, val minLum: Float, val targetLum: Float, val maxLum: Float) {
        companion object {
            @JvmField val LIGHT_VIBRANT = Target(0.35f, 1f, 1f, 0.55f, 0.74f, 1f)
            @JvmField val VIBRANT = Target(0.35f, 1f, 1f, 0.3f, 0.5f, 0.7f)
            @JvmField val DARK_VIBRANT = Target(0.35f, 1f, 1f, 0f, 0.26f, 0.45f)
            @JvmField val LIGHT_MUTED = Target(0f, 0.3f, 0.4f, 0.55f, 0.74f, 1f)
            @JvmField val MUTED = Target(0f, 0.3f, 0.4f, 0.3f, 0.5f, 0.7f)
            @JvmField val DARK_MUTED = Target(0f, 0.3f, 0.4f, 0f, 0.26f, 0.45f)
        }
    }
    private val selected = HashMap<Target, Swatch?>()
    val dominantSwatch: Swatch? = swatches.maxByOrNull { it.population }
    init {
        val used = HashSet<Int>()
        val maxPop = dominantSwatch?.population?.toFloat() ?: 1f
        for (t in listOf(Target.LIGHT_VIBRANT, Target.VIBRANT, Target.DARK_VIBRANT, Target.LIGHT_MUTED, Target.MUTED, Target.DARK_MUTED)) {
            var best: Swatch? = null; var bestScore = 0f
            for (s in swatches) {
                val h = s.hsl
                if (h[1] < t.minSat || h[1] > t.maxSat || h[2] < t.minLum || h[2] > t.maxLum || s.rgb in used) continue
                val score = 0.24f * (1f - abs(h[1] - t.targetSat)) + 0.52f * (1f - abs(h[2] - t.targetLum)) + 0.24f * (s.population / maxPop)
                if (best == null || score > bestScore) { best = s; bestScore = score }
            }
            selected[t] = best
            best?.let { used += it.rgb }
        }
    }
    fun getSwatchForTarget(t: Target): Swatch? = selected[t]
    val vibrantSwatch: Swatch? get() = selected[Target.VIBRANT]
    val lightVibrantSwatch: Swatch? get() = selected[Target.LIGHT_VIBRANT]
    val darkVibrantSwatch: Swatch? get() = selected[Target.DARK_VIBRANT]
    val mutedSwatch: Swatch? get() = selected[Target.MUTED]
    val lightMutedSwatch: Swatch? get() = selected[Target.LIGHT_MUTED]
    val darkMutedSwatch: Swatch? get() = selected[Target.DARK_MUTED]
    fun getDominantColor(d: Int) = dominantSwatch?.rgb ?: d
    fun getVibrantColor(d: Int) = vibrantSwatch?.rgb ?: d
    fun getDarkVibrantColor(d: Int) = darkVibrantSwatch?.rgb ?: d
    fun getLightVibrantColor(d: Int) = lightVibrantSwatch?.rgb ?: d
    fun getMutedColor(d: Int) = mutedSwatch?.rgb ?: d
    fun getDarkMutedColor(d: Int) = darkMutedSwatch?.rgb ?: d
    fun getLightMutedColor(d: Int) = lightMutedSwatch?.rgb ?: d

    fun interface PaletteAsyncListener { fun onGenerated(palette: Palette?) }
    fun interface Filter { fun isAllowed(rgb: Int, hsl: FloatArray): Boolean }

    class Builder(private val bitmap: Bitmap?, private val given: List<Swatch>? = null) {
        private var maxColors = 16
        private var area = 112 * 112
        private val filters = mutableListOf<Filter>(Filter { _, hsl -> !(hsl[2] >= 0.95f || hsl[2] <= 0.05f || (hsl[0] in 10f..37f && hsl[1] <= 0.82f)) })
        fun maximumColorCount(n: Int) = apply { maxColors = n }
        fun resizeBitmapArea(a: Int) = apply { area = a }
        fun clearFilters() = apply { filters.clear() }
        fun addFilter(f: Filter) = apply { filters += f }
        fun clearTargets() = this
        fun addTarget(t: Target) = this
        fun setRegion(l: Int, t: Int, r: Int, b: Int) = this
        fun generate(): Palette {
            given?.let { return Palette(it) }
            val b = bitmap ?: return Palette(emptyList())
            val scale = if (area > 0 && b.width * b.height > area) sqrt(area.toDouble() / (b.width * b.height)) else 1.0
            val src = if (scale < 1.0) Bitmap.createScaledBitmap(b, max(1, (b.width * scale).toInt()), max(1, (b.height * scale).toInt()), false) else b
            val px = IntArray(src.width * src.height); src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
            return Palette(quantize(px, maxColors, filters))
        }
        fun generate(l: PaletteAsyncListener): java.util.concurrent.Future<*> =
            java.util.concurrent.CompletableFuture.supplyAsync { runCatching { generate() }.getOrNull() }.thenAccept { p -> app.jam.desktop.shim.Desk.post { l.onGenerated(p) } }
    }
    companion object {
        @JvmStatic fun from(b: Bitmap): Builder = Builder(b)
        @JvmStatic fun from(s: List<Swatch>): Palette = Palette(s)
        private fun lum(rgb: Int): Double = android.graphics.Color.luminance(rgb).toDouble()

        private fun quant(c: Int) = ((c shr 19 and 31) shl 10) or ((c shr 11 and 31) shl 5) or (c shr 3 and 31)
        private fun r5(q: Int) = q shr 10 and 31; private fun g5(q: Int) = q shr 5 and 31; private fun b5(q: Int) = q and 31
        private fun e5(v: Int) = (v shl 3) or (v shr 2)
        private fun expand(q: Int): Int { val e = ::e5; return (0xFF shl 24) or (e(r5(q)) shl 16) or (e(g5(q)) shl 8) or e(b5(q)) }

        private fun quantize(px: IntArray, maxColors: Int, filters: List<Filter>): List<Swatch> {
            val hist = IntArray(1 shl 15)
            for (p in px) if (p ushr 24 >= 128) hist[quant(p)]++
            val hsl = FloatArray(3)
            val colors = (0 until hist.size).filter { q -> hist[q] > 0 && expand(q).let { c -> rgbToHsl(c, hsl); filters.all { it.isAllowed(c, hsl) } } }.toIntArray()
            if (colors.size <= maxColors) return colors.map { Swatch(expand(it), hist[it]) }
            class Box(val lo: Int, val hi: Int) {
                var cols = colors; var pop = 0
                var minR = 0; var maxR = 0; var minG = 0; var maxG = 0; var minB = 0; var maxB = 0
                fun fit(arr: IntArray) {
                    cols = arr; minR = 31; minG = 31; minB = 31; maxR = 0; maxG = 0; maxB = 0; pop = 0
                    for (i in lo..hi) { val q = arr[i]; pop += hist[q]; minR = min(minR, r5(q)); maxR = max(maxR, r5(q)); minG = min(minG, g5(q)); maxG = max(maxG, g5(q)); minB = min(minB, b5(q)); maxB = max(maxB, b5(q)) }
                }
                val volume get() = (maxR - minR + 1) * (maxG - minG + 1) * (maxB - minB + 1)
                fun canSplit() = hi > lo
                fun split(arr: IntArray): Box {
                    val dr = maxR - minR; val dg = maxG - minG; val db = maxB - minB
                    val key: (Int) -> Int = when { dr >= dg && dr >= db -> ::r5; dg >= dr && dg >= db -> ::g5; else -> ::b5 }
                    val sorted = arr.copyOfRange(lo, hi + 1).sortedBy(key); for (i in sorted.indices) arr[lo + i] = sorted[i]
                    val mid = pop / 2; var acc = 0; var at = lo
                    for (i in lo..hi) { acc += hist[arr[i]]; if (acc >= mid) { at = min(hi - 1, i); break } }
                    val nb = Box(at + 1, hi); nb.fit(arr)
                    val me = Box(lo, at); me.fit(arr)
                    return nb.also { pending = me }
                }
                var pending: Box? = null
                fun average(arr: IntArray): Swatch {
                    var r = 0L; var g = 0L; var b = 0L; var n = 0L
                    for (i in lo..hi) { val q = arr[i]; val c = hist[q]; n += c; r += r5(q).toLong() * c; g += g5(q).toLong() * c; b += b5(q).toLong() * c }
                    val q = ((r / n).toInt() shl 10) or ((g / n).toInt() shl 5) or (b / n).toInt()
                    return Swatch(expand(q), n.toInt())
                }
            }
            val arr = colors.copyOf()
            val queue = PriorityQueue<Box>(maxColors) { a, b -> b.volume - a.volume }
            queue += Box(0, arr.size - 1).also { it.fit(arr) }
            while (queue.size < maxColors) {
                val box = queue.poll() ?: break
                if (!box.canSplit()) { queue += box; break }
                val second = box.split(arr)
                queue += box.pending!!; queue += second
            }
            return queue.map { it.average(arr) }.filter { s -> rgbToHsl(s.rgb, hsl); filters.all { it.isAllowed(s.rgb, hsl) } }
        }

        internal fun rgbToHsl(rgb: Int, out: FloatArray) {
            val r = (rgb shr 16 and 255) / 255f; val g = (rgb shr 8 and 255) / 255f; val b = (rgb and 255) / 255f
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
            val l = (mx + mn) / 2f
            val h: Float; val s: Float
            if (mx == mn) { h = 0f; s = 0f } else {
                h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
                s = d / (1f - abs(2f * l - 1f))
            }
            out[0] = (h * 60f + 360f) % 360f; out[1] = s; out[2] = l
        }
    }
}
