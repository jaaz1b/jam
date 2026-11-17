package android.icu.text

/** ICU's compact numbers ("1.2M"), from the JDK's. */
class CompactDecimalFormat private constructor(private val f: java.text.NumberFormat) {
    fun format(n: Long): String = f.format(n)
    fun format(n: Double): String = f.format(n)
    fun format(n: Number): String = f.format(n)
    var maximumFractionDigits: Int get() = f.maximumFractionDigits; set(v) { f.maximumFractionDigits = v }
    enum class CompactStyle { SHORT, LONG }
    companion object {
        @JvmStatic fun getInstance(l: java.util.Locale, s: CompactStyle) = CompactDecimalFormat(java.text.NumberFormat.getCompactNumberInstance(l, if (s == CompactStyle.SHORT) java.text.NumberFormat.Style.SHORT else java.text.NumberFormat.Style.LONG).apply { maximumFractionDigits = 1 })
    }
}
