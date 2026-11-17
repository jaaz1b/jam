package app.jam.desktop.media

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Speed without a pitch change (WSOLA), on interleaved stereo float frames: what media3's Sonic does
 * for the speed setting and AutoMix's tempo matching. At speed 1 it passes the audio as it is.
 */
class Stretch(private val channels: Int = 2, rate: Int = Ffmpeg.RATE) {
    private val n = (rate * 0.040).roundToInt().let { it - it % 2 }   // 40 ms windows
    private val ho = n / 2                                              // output hop
    private val search = (rate * 0.012).roundToInt()                   // ±12 ms search
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
    private var input = FloatArray(0); private var inFrames = 0
    private var pos = 0.0          // analysis position (frames into input)
    private var prev = -1          // start of the last segment used
    private val tail = FloatArray(ho * channels)
    var speed = 1f

    fun reset() { inFrames = 0; pos = 0.0; prev = -1; tail.fill(0f) }

    /** Takes [frames] frames and returns what is ready (maybe nothing yet). */
    fun process(src: FloatArray, frames: Int): FloatArray {
        if (speed == 1f && inFrames == 0) return src.copyOf(frames * channels)
        append(src, frames)
        val out = ArrayList<FloatArray>()
        val ha = speed * ho
        while (true) {
            val base = pos.toInt()
            if (base + n + search >= inFrames) break
            var best = base
            if (prev >= 0 && base - search >= 0) {
                val natural = prev + ho
                var bestScore = Float.NEGATIVE_INFINITY
                var d = -search
                while (d <= search) {
                    val c = base + d
                    if (c >= 0 && natural + ho <= inFrames) {
                        var s = 0f; var i = 0
                        while (i < ho) { s += mono(c + i) * mono(natural + i); i += 4 }
                        if (s > bestScore) { bestScore = s; best = c }
                    }
                    d += 4
                }
            }
            val o = FloatArray(ho * channels)
            for (i in 0 until ho) for (ch in 0 until channels) {
                o[i * channels + ch] = tail[i * channels + ch] + input[(best + i) * channels + ch] * window[i]
                tail[i * channels + ch] = input[(best + ho + i) * channels + ch] * window[ho + i]
            }
            out += o
            prev = best
            pos += ha
        }
        // Frames no longer reachable are dropped.
        val keep = (minOf(pos.toInt(), if (prev >= 0) prev else Int.MAX_VALUE) - search).coerceAtLeast(0)
        if (keep > 0) { System.arraycopy(input, keep * channels, input, 0, (inFrames - keep) * channels); inFrames -= keep; pos -= keep; if (prev >= 0) prev -= keep }
        if (out.isEmpty()) return FloatArray(0)
        val all = FloatArray(out.sumOf { it.size }); var at = 0
        for (o in out) { System.arraycopy(o, 0, all, at, o.size); at += o.size }
        return all
    }
    /** What is left, at the end of the stream. */
    fun drain(): FloatArray { val r = input.copyOfRange((pos.toInt().coerceAtMost(inFrames)) * channels, inFrames * channels); reset(); return r }
    private fun mono(f: Int): Float { var s = 0f; for (c in 0 until channels) s += input[f * channels + c]; return s }
    private fun append(src: FloatArray, frames: Int) {
        val need = (inFrames + frames) * channels
        if (input.size < need) input = input.copyOf(maxOf(need, input.size * 2))
        System.arraycopy(src, 0, input, inFrames * channels, frames * channels)
        inFrames += frames
    }
}
