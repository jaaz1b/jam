package androidx.core.text
object HtmlCompat {
    const val FROM_HTML_MODE_LEGACY = 0; const val FROM_HTML_MODE_COMPACT = 63
    @JvmStatic fun fromHtml(s: String, flags: Int): CharSequence = android.text.Html.fromHtml(
        s.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("(?i)</p>"), "\n\n"), flags)
}
fun String.parseAsHtml(flags: Int = 0): CharSequence = HtmlCompat.fromHtml(this, flags)
