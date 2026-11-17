package android.net

import java.net.URLDecoder
import java.net.URLEncoder

/** Android's Uri: parsed the way Android does (hierarchical or opaque), immutable. */
class Uri private constructor(private val raw: String) : android.os.Parcelable, Comparable<Uri> {
    private val schemeEnd = raw.indexOf(':').takeIf { it > 0 && raw.substring(0, it).all { c -> c.isLetterOrDigit() || c in "+-." } } ?: -1
    val scheme: String? get() = if (schemeEnd < 0) null else raw.substring(0, schemeEnd)
    private val ssp: String get() = if (schemeEnd < 0) raw else raw.substring(schemeEnd + 1)
    val isHierarchical: Boolean get() = schemeEnd < 0 || ssp.startsWith("/")
    val isOpaque: Boolean get() = !isHierarchical
    val isAbsolute: Boolean get() = scheme != null
    val isRelative: Boolean get() = scheme == null
    val schemeSpecificPart: String get() = decode(encodedSchemeSpecificPart) ?: ""
    val encodedSchemeSpecificPart: String get() = ssp.substringBefore('#')
    val fragment: String? get() = encodedFragment?.let(::decode)
    val encodedFragment: String? get() = raw.indexOf('#').takeIf { it >= 0 }?.let { raw.substring(it + 1) }
    private val noFrag: String get() = ssp.substringBefore('#')
    val encodedAuthority: String? get() = if (isHierarchical && noFrag.startsWith("//")) noFrag.substring(2).takeWhile { it !in "/?" } else null
    val authority: String? get() = encodedAuthority?.let(::decode)
    val encodedUserInfo: String? get() = encodedAuthority?.takeIf { '@' in it }?.substringBefore('@')
    val userInfo: String? get() = encodedUserInfo?.let(::decode)
    val host: String? get() = encodedAuthority?.substringAfter('@')?.let { a -> if (a.startsWith("[")) a.substringBefore(']') + "]" else a.substringBefore(':') }?.takeIf { it.isNotEmpty() }
    val port: Int get() = encodedAuthority?.substringAfter('@')?.substringAfterLast(':', "")?.toIntOrNull() ?: -1
    val encodedPath: String? get() {
        if (!isHierarchical) return null
        val afterAuth = if (noFrag.startsWith("//")) noFrag.substring(2).dropWhile { it !in "/?" } else noFrag
        return afterAuth.substringBefore('?')
    }
    val path: String? get() = encodedPath?.let(::decode)
    val encodedQuery: String? get() = if (!isHierarchical) null else noFrag.indexOf('?').takeIf { it >= 0 }?.let { noFrag.substring(it + 1) }
    val query: String? get() = encodedQuery?.let(::decode)
    val pathSegments: List<String> get() = (encodedPath ?: "").split('/').filter { it.isNotEmpty() }.map { decode(it) ?: it }
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val queryParameterNames: Set<String> get() = (encodedQuery ?: "").split('&').filter { it.isNotEmpty() }.map { decode(it.substringBefore('='))!! }.toCollection(LinkedHashSet())
    fun getQueryParameter(key: String): String? = getQueryParameters(key).firstOrNull()
    fun getQueryParameters(key: String): List<String> = (encodedQuery ?: "").split('&').filter { it.isNotEmpty() && decode(it.substringBefore('=')) == key }
        .map { if ('=' in it) decodeQuery(it.substringAfter('=')) else "" }
    fun getBooleanQueryParameter(key: String, d: Boolean): Boolean = getQueryParameter(key)?.let { it != "false" && it != "0" } ?: d
    fun buildUpon(): Builder = Builder().also { b ->
        b.scheme(scheme)
        if (isHierarchical) { b.encodedAuthority(encodedAuthority); b.encodedPath(encodedPath); b.encodedQuery(encodedQuery) } else b.encodedOpaquePart(encodedSchemeSpecificPart)
        b.encodedFragment(encodedFragment)
    }
    fun normalizeScheme(): Uri = scheme?.let { s -> if (s == s.lowercase()) this else parse(s.lowercase() + raw.substring(schemeEnd)) } ?: this
    override fun toString() = raw
    override fun equals(other: Any?) = other is Uri && other.raw == raw
    override fun hashCode() = raw.hashCode()
    override fun compareTo(other: Uri) = raw.compareTo(other.raw)

    class Builder {
        private var scheme: String? = null; private var authority: String? = null; private var path: String? = null
        private val query = ArrayList<String>(); private var rawQuery: String? = null; private var fragment: String? = null; private var opaque: String? = null
        fun scheme(s: String?) = apply { scheme = s }
        fun authority(a: String?) = apply { authority = a?.let { encode(it, "@:[]") } }
        fun encodedAuthority(a: String?) = apply { authority = a }
        fun path(p: String?) = apply { path = p?.let { encode(it, "/") } }
        fun encodedPath(p: String?) = apply { path = p }
        fun appendPath(seg: String?) = apply { if (seg != null) path = (path ?: "").trimEnd('/') + "/" + encode(seg) }
        fun appendEncodedPath(seg: String?) = apply { if (seg != null) path = (path ?: "").trimEnd('/') + "/" + seg.trimStart('/') }
        fun query(q: String?) = apply { rawQuery = q?.let { encode(it, "=&") }; query.clear() }
        fun encodedQuery(q: String?) = apply { rawQuery = q; query.clear() }
        fun appendQueryParameter(k: String, v: String?) = apply { query += encode(k) + "=" + encode(v ?: "") }
        fun clearQuery() = apply { rawQuery = null; query.clear() }
        fun fragment(f: String?) = apply { fragment = f?.let { encode(it) } }
        fun encodedFragment(f: String?) = apply { fragment = f }
        fun opaquePart(o: String?) = apply { opaque = o?.let { encode(it, ":/") } }
        fun encodedOpaquePart(o: String?) = apply { opaque = o }
        fun build(): Uri {
            val sb = StringBuilder()
            scheme?.let { sb.append(it).append(':') }
            if (opaque != null) sb.append(opaque) else {
                authority?.let { sb.append("//").append(it) }
                path?.let { p -> if (authority != null && p.isNotEmpty() && !p.startsWith("/")) sb.append('/'); sb.append(p) }
                val q = listOfNotNull(rawQuery?.takeIf { it.isNotEmpty() }) + query
                if (q.isNotEmpty()) sb.append('?').append(q.joinToString("&"))
            }
            fragment?.let { sb.append('#').append(it) }
            return Uri(sb.toString())
        }
        override fun toString() = build().toString()
    }

    companion object {
        @JvmField val EMPTY: Uri = Uri("")
        @JvmStatic fun parse(s: String): Uri = Uri(s)
        @JvmStatic fun fromFile(f: java.io.File): Uri = Uri("file://" + f.absoluteFile.toURI().rawPath)
        @JvmStatic fun fromParts(scheme: String, ssp: String, fragment: String?): Uri = Uri("$scheme:${encode(ssp, "/:@")}" + (fragment?.let { "#" + encode(it) } ?: ""))
        @JvmStatic fun withAppendedPath(base: Uri, seg: String): Uri = base.buildUpon().appendEncodedPath(seg).build()
        @JvmStatic fun encode(s: String?): String? = s?.let { encode(it, null) }
        @JvmStatic fun encode(s: String?, allow: String?): String? {
            if (s == null) return null
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c.isLetterOrDigit() && c.code < 128 || c in "_-!.~'()*" || (allow != null && c in allow)) sb.append(c)
                else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
            }
            return sb.toString()
        }
        @JvmStatic fun decode(s: String?): String? = s?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }
        private fun decodeQuery(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
        @Suppress("unused") private val unusedEncoder = URLEncoder::class
    }
}
