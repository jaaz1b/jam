package androidx.core.os
class LocaleListCompat private constructor(private val locales: List<java.util.Locale>) {
    fun get(i: Int): java.util.Locale? = locales.getOrNull(i)
    fun size(): Int = locales.size
    val isEmpty: Boolean get() = locales.isEmpty()
    fun toLanguageTags(): String = locales.joinToString(",") { it.toLanguageTag() }
    companion object {
        @JvmStatic fun getEmptyLocaleList() = LocaleListCompat(emptyList())
        @JvmStatic fun forLanguageTags(t: String?) = LocaleListCompat(t.orEmpty().split(',').filter { it.isNotBlank() }.map { java.util.Locale.forLanguageTag(it.trim()) })
        @JvmStatic fun create(vararg l: java.util.Locale) = LocaleListCompat(l.toList())
        @JvmStatic fun getDefault() = LocaleListCompat(listOf(java.util.Locale.getDefault()))
        @JvmStatic fun getAdjustedDefault() = getDefault()
    }
}
fun bundleOf(vararg pairs: Pair<String, Any?>): android.os.Bundle = android.os.Bundle().apply { for ((k, v) in pairs) putAny(k, v) }
object BuildCompat { @JvmStatic fun isAtLeastV(): Boolean = true; @JvmStatic fun isAtLeastU(): Boolean = true }
object HandlerCompat { @JvmStatic fun createAsync(l: android.os.Looper): android.os.Handler = android.os.Handler(l); @JvmStatic fun postDelayed(h: android.os.Handler, r: Runnable, token: Any?, d: Long): Boolean = h.postDelayed(r, token, d) }
