package android.text

object TextUtils {
    enum class TruncateAt { START, MIDDLE, END, MARQUEE }
    @JvmStatic fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()
    @JvmStatic fun join(d: CharSequence, parts: Iterable<*>): String = parts.joinToString(d)
    @JvmStatic fun htmlEncode(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    @JvmStatic fun ellipsize(t: CharSequence, p: TextPaint, avail: Float, w: TruncateAt): CharSequence {
        if (p.measureText(t.toString()) <= avail) return t
        var s = t.toString(); while (s.isNotEmpty() && p.measureText("$s…") > avail) s = s.dropLast(1); return "$s…"
    }
    @JvmStatic fun equals(a: CharSequence?, b: CharSequence?): Boolean = a?.toString() == b?.toString()
}
open class TextPaint(flags: Int = 0) : android.graphics.Paint(flags)
open class Layout(val text: CharSequence, val paint: TextPaint, val width: Int) {
    protected val lines: List<String> by lazy {
        val out = ArrayList<String>()
        for (para in text.toString().split('\n')) {
            var line = ""
            for (word in para.split(' ')) { val tryLine = if (line.isEmpty()) word else "$line $word"; if (paint.measureText(tryLine) <= width || line.isEmpty()) line = tryLine else { out += line; line = word } }
            out += line
        }
        out
    }
    open val lineCount: Int get() = lines.size
    val height: Int get() = (lineCount * lineHeight).toInt()
    private val lineHeight: Float get() = paint.fontMetrics.let { it.descent + it.ascent * -1 + it.leading }.let { if (it <= 0) paint.textSize * 1.2f else it * spacingMult + spacingAdd }
    internal var spacingMult = 1f; internal var spacingAdd = 0f
    fun getLineWidth(i: Int): Float = paint.measureText(lines[i])
    fun getLineTop(i: Int): Int = (i * lineHeight).toInt()
    fun getLineBottom(i: Int): Int = ((i + 1) * lineHeight).toInt()
    fun getLineBaseline(i: Int): Int = (getLineTop(i) - paint.fontMetrics.ascent).toInt()
    fun getLineStart(i: Int): Int = lines.take(i).sumOf { it.length + 1 }
    fun getLineEnd(i: Int): Int = getLineStart(i) + lines[i].length
    fun draw(c: android.graphics.Canvas) { lines.forEachIndexed { i, l -> c.drawText(l, 0f, getLineBaseline(i).toFloat(), paint) } }
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
}
class StaticLayout private constructor(t: CharSequence, p: TextPaint, w: Int, private val maxLines: Int) : Layout(t, p, w) {
    override val lineCount: Int get() = minOf(super.lineCount, maxLines)
    class Builder private constructor(private val t: CharSequence, private val p: TextPaint, private val w: Int) {
        private var max = Int.MAX_VALUE; private var mult = 1f; private var add = 0f
        fun setMaxLines(n: Int) = apply { max = n }
        fun setEllipsize(e: TextUtils.TruncateAt?) = this
        fun setAlignment(a: Layout.Alignment) = this
        fun setLineSpacing(add: Float, mult: Float) = apply { this.add = add; this.mult = mult }
        fun setIncludePad(b: Boolean) = this
        fun setBreakStrategy(b: Int) = this
        fun setHyphenationFrequency(h: Int) = this
        fun setTextDirection(d: Any?) = this
        fun build(): StaticLayout = StaticLayout(t, p, w, max).also { it.spacingMult = mult; it.spacingAdd = add }
        companion object { @JvmStatic fun obtain(t: CharSequence, start: Int, end: Int, p: TextPaint, w: Int) = Builder(t.subSequence(start, end), p, w) }
    }
}
interface Spanned : CharSequence
object Html { @JvmStatic fun fromHtml(s: String, flags: Int): CharSequence = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'"); const val FROM_HTML_MODE_LEGACY = 0; const val FROM_HTML_MODE_COMPACT = 63 }
