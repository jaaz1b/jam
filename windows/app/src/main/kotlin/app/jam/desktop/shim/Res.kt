package app.jam.desktop.shim

import org.w3c.dom.Element
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Android app's resources (its res/ folder, copied into the jar as res/…) by R id: strings in
 * the PC's language (values-<lang>[-r<REGION>], else values/), plurals, arrays, colours, drawables.
 */
object Res {
    private val loader: ClassLoader = Res::class.java.classLoader

    /** id → (type, name), from rnames.txt. */
    private val byId: Map<Int, Pair<String, String>> by lazy {
        val m = HashMap<Int, Pair<String, String>>()
        loader.getResourceAsStream("rnames.txt")?.bufferedReader()?.forEachLine { line ->
            val p = line.split(' ')
            if (p.size == 3) m[p[2].toInt()] = p[0] to p[1]
        }
        m
    }
    private val byName: Map<String, Int> by lazy { byId.entries.associate { (id, tn) -> "${tn.first}/${tn.second}" to id } }
    val files: List<String> by lazy { loader.getResourceAsStream("resfiles.txt")?.bufferedReader()?.readLines().orEmpty() }

    fun name(id: Int): Pair<String, String>? = byId[id]
    fun id(type: String, name: String): Int = byName["$type/$name"] ?: 0

    @Volatile var locale: Locale = Locale.getDefault()

    /** values folders to read, most specific first: values-fr-rCA, values-fr, values. */
    private fun valueDirs(l: Locale): List<String> {
        val all = files.map { it.substringBefore('/') }.filter { it == "values" || it.startsWith("values-") }.toSet()
        val out = ArrayList<String>()
        val lang = if (l.language == "iw") "he" else if (l.language == "in") "id" else l.language
        if (l.country.isNotEmpty()) "values-$lang-r${l.country}".let { if (it in all) out += it }
        "values-b+$lang".let { if (it in all) out += it }
        "values-$lang".let { if (it in all) out += it }
        out += "values"
        return out
    }

    private class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val arrays: Map<String, List<String>>, val colors: Map<String, String>, val dimens: Map<String, String>, val ints: Map<String, String>, val bools: Map<String, String>)
    private val tables = ConcurrentHashMap<String, Table>()

    private fun table(dir: String): Table = tables.getOrPut(dir) {
        val strings = HashMap<String, String>(); val plurals = HashMap<String, Map<String, String>>(); val arrays = HashMap<String, List<String>>()
        val colors = HashMap<String, String>(); val dimens = HashMap<String, String>(); val ints = HashMap<String, String>(); val bools = HashMap<String, String>()
        for (f in files.filter { it.startsWith("$dir/") && it.endsWith(".xml") && it.count { c -> c == '/' } == 1 }) {
            val doc = runCatching { loader.getResourceAsStream("res/$f")!!.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) } }.getOrNull() ?: continue
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.getAttribute("name").ifEmpty { continue }
                when (e.tagName) {
                    "string" -> strings[name] = text(e)
                    "plurals" -> plurals[name] = children(e, "item").associate { it.getAttribute("quantity") to text(it) }
                    "string-array", "array", "integer-array" -> arrays[name] = children(e, "item").map { text(it) }
                    "color" -> colors[name] = e.textContent.trim()
                    "dimen" -> dimens[name] = e.textContent.trim()
                    "integer" -> ints[name] = e.textContent.trim()
                    "bool" -> bools[name] = e.textContent.trim()
                    "item" -> when (e.getAttribute("type")) { "color" -> colors[name] = e.textContent.trim(); "dimen" -> dimens[name] = e.textContent.trim() }
                }
            }
        }
        Table(strings, plurals, arrays, colors, dimens, ints, bools)
    }

    private fun children(e: Element, tag: String): List<Element> {
        val out = ArrayList<Element>(); val n = e.childNodes
        for (i in 0 until n.length) (n.item(i) as? Element)?.takeIf { it.tagName == tag }?.let(out::add)
        return out
    }

    /** An Android string's text: quotes and escapes resolved, markup dropped. */
    private fun text(e: Element): String {
        var s = e.textContent
        val quoted = s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")
        if (quoted) s = s.substring(1, s.length - 1)
        else s = s.replace(Regex("\\s+"), " ").trim()
        val b = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> b.append('\n'); 't' -> b.append('\t')
                    'u' -> if (i + 5 < s.length) { b.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 } else b.append(n)
                    else -> b.append(n)
                }
                i += 2; continue
            }
            if (c == '"' && !quoted) { i++; continue }
            b.append(c); i++
        }
        return b.toString()
    }

    private fun <T> lookup(pick: (Table) -> T?): T? { for (d in valueDirs(locale)) pick(table(d))?.let { return it }; return null }

    private fun resolve(v: String?): String? {
        if (v == null) return null
        if (v.startsWith("@string/")) return lookup { it.strings[v.removePrefix("@string/")] }
        return v
    }

    fun string(id: Int): String {
        val n = byId[id]?.second ?: return "#$id"
        return resolve(lookup { it.strings[n] }) ?: n
    }
    fun string(name: String): String? = resolve(lookup { it.strings[name] })

    fun format(id: Int, args: Array<out Any?>): String = runCatching { String.format(locale, string(id), *args) }.getOrElse { string(id) }

    fun plural(id: Int, quantity: Int): String {
        val n = byId[id]?.second ?: return "#$id"
        val forms = lookup { it.plurals[n] } ?: return n
        val q = pluralCategory(quantity)
        return resolve(forms[q] ?: forms["other"] ?: forms.values.firstOrNull()) ?: n
    }

    /** CLDR plural category for the PC's language (the common cases). */
    fun pluralCategory(n: Int): String = when (locale.language) {
        "fr", "pt" -> if (n == 0 || n == 1) "one" else if (n != 0 && n % 1_000_000 == 0) "many" else "other"
        "ja", "zh", "ko", "th", "vi", "id", "ms", "tr" -> "other"
        "ru", "uk", "be" -> when { n % 10 == 1 && n % 100 != 11 -> "one"; n % 10 in 2..4 && n % 100 !in 12..14 -> "few"; else -> "many" }
        "pl" -> when { n == 1 -> "one"; n % 10 in 2..4 && n % 100 !in 12..14 -> "few"; else -> "many" }
        "cs", "sk" -> when (n) { 1 -> "one"; in 2..4 -> "few"; else -> "other" }
        "ar" -> when { n == 0 -> "zero"; n == 1 -> "one"; n == 2 -> "two"; n % 100 in 3..10 -> "few"; n % 100 in 11..99 -> "many"; else -> "other" }
        else -> if (n == 1) "one" else "other"
    }

    fun array(id: Int): List<String> { val n = byId[id]?.second ?: return emptyList(); return lookup { it.arrays[n] }?.map { resolve(it) ?: it }.orEmpty() }

    fun color(id: Int): Int = colorValue(byId[id]?.second ?: return 0)
    fun colorValue(name: String): Int {
        val v = lookup { it.colors[name] } ?: return 0
        if (v.startsWith("@color/")) return colorValue(v.removePrefix("@color/"))
        return parseColor(v)
    }
    fun parseColor(v: String): Int {
        val h = v.removePrefix("#")
        return when (h.length) {
            3 -> (0xFF000000 or h.map { "$it$it" }.joinToString("").toLong(16)).toInt()
            6 -> (0xFF000000 or h.toLong(16)).toInt()
            8 -> h.toLong(16).toInt()
            else -> 0
        }
    }
    fun dimen(id: Int): Float = (lookup { it.dimens[byId[id]?.second ?: ""] } ?: "0").filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull() ?: 0f
    fun integer(id: Int): Int = lookup { it.ints[byId[id]?.second ?: ""] }?.toIntOrNull() ?: 0
    fun bool(id: Int): Boolean = lookup { it.bools[byId[id]?.second ?: ""] } == "true"

    /** The file of a drawable / mipmap / raw / font id: res/drawable/name.xml, …, best density first. */
    fun file(id: Int): String? {
        val (type, name) = byId[id] ?: return null
        val candidates = files.filter { it.substringAfter('/').substringBeforeLast('.') == name && it.substringBefore('/').substringBefore('-') == type }
        val order = listOf("-anydpi", "-xxxhdpi", "-xxhdpi", "-xhdpi", "-hdpi", "-mdpi", "")
        return candidates.minByOrNull { c -> val d = c.substringBefore('/'); order.indexOfFirst { d.endsWith(it) && (it.isNotEmpty() || d == type) }.let { if (it < 0) 99 else it } }
    }

    fun open(path: String) = loader.getResourceAsStream("res/$path")
}
