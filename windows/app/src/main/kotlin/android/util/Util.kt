package android.util

import app.jam.desktop.shim.Desk

object Log {
    const val VERBOSE = 2; const val DEBUG = 3; const val INFO = 4; const val WARN = 5; const val ERROR = 6; const val ASSERT = 7
    @JvmStatic fun v(tag: String?, msg: String): Int { Desk.log('V', tag, msg); return 0 }
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('V', tag, msg, tr); return 0 }
    @JvmStatic fun d(tag: String?, msg: String): Int { Desk.log('D', tag, msg); return 0 }
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('D', tag, msg, tr); return 0 }
    @JvmStatic fun i(tag: String?, msg: String): Int { Desk.log('I', tag, msg); return 0 }
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('I', tag, msg, tr); return 0 }
    @JvmStatic fun w(tag: String?, msg: String): Int { Desk.log('W', tag, msg); return 0 }
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('W', tag, msg, tr); return 0 }
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int { Desk.log('W', tag, null, tr); return 0 }
    @JvmStatic fun e(tag: String?, msg: String): Int { Desk.log('E', tag, msg); return 0 }
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('E', tag, msg, tr); return 0 }
    @JvmStatic fun wtf(tag: String?, msg: String?): Int { Desk.log('A', tag, msg); return 0 }
    @JvmStatic fun wtf(tag: String?, msg: String?, tr: Throwable?): Int { Desk.log('A', tag, msg, tr); return 0 }
    @JvmStatic fun println(priority: Int, tag: String?, msg: String): Int { Desk.log('I', tag, msg); return 0 }
    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = level >= INFO
    @JvmStatic fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
}

object Base64 {
    const val DEFAULT = 0; const val NO_PADDING = 1; const val NO_WRAP = 2; const val CRLF = 4; const val URL_SAFE = 8; const val NO_CLOSE = 16
    private fun enc(flags: Int): java.util.Base64.Encoder {
        var e = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else if (flags and NO_WRAP != 0) java.util.Base64.getEncoder() else java.util.Base64.getMimeEncoder(76, if (flags and CRLF != 0) "\r\n".toByteArray() else "\n".toByteArray())
        if (flags and NO_PADDING != 0) e = e.withoutPadding()
        return e
    }
    @JvmStatic fun encodeToString(input: ByteArray, flags: Int): String = enc(flags).encodeToString(input).let { if (flags and (NO_WRAP or URL_SAFE) == 0 && it.isNotEmpty()) it + "\n" else it }
    @JvmStatic fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String = encodeToString(input.copyOfRange(offset, offset + len), flags)
    @JvmStatic fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray()
    @JvmStatic fun decode(str: String, flags: Int): ByteArray {
        val s = str.filterNot { it.isWhitespace() }
        val url = flags and URL_SAFE != 0 || s.contains('-') || s.contains('_')
        val padded = if (s.length % 4 == 0) s else s + "=".repeat(4 - s.length % 4)
        return if (url) java.util.Base64.getUrlDecoder().decode(padded) else java.util.Base64.getDecoder().decode(padded)
    }
    @JvmStatic fun decode(input: ByteArray, flags: Int): ByteArray = decode(String(input), flags)
}

class AtomicFile(val baseFile: java.io.File) {
    fun startWrite(): java.io.FileOutputStream = java.io.FileOutputStream(java.io.File(baseFile.path + ".new"))
    fun finishWrite(s: java.io.FileOutputStream?) { runCatching { s?.fd?.sync(); s?.close() }; java.nio.file.Files.move(java.io.File(baseFile.path + ".new").toPath(), baseFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    fun failWrite(s: java.io.FileOutputStream?) { runCatching { s?.close() }; java.io.File(baseFile.path + ".new").delete() }
    fun openRead(): java.io.FileInputStream = java.io.FileInputStream(baseFile)
    fun readFully(): ByteArray = baseFile.readBytes()
    fun delete() { baseFile.delete(); java.io.File(baseFile.path + ".new").delete() }
    fun exists() = baseFile.exists()
}

class Rational(val numerator: Int, val denominator: Int) { fun toFloat() = numerator.toFloat() / denominator; fun toDouble() = numerator.toDouble() / denominator }
data class Size(val width: Int, val height: Int)
data class SizeF(val width: Float, val height: Float)
data class Range<T : Comparable<T>>(val lower: T, val upper: T) { fun contains(v: T) = v >= lower && v <= upper }

class DisplayMetrics { @JvmField var density = 1f; @JvmField var densityDpi = 160; @JvmField var scaledDensity = 1f; @JvmField var widthPixels = 1920; @JvmField var heightPixels = 1080; @JvmField var xdpi = 160f; @JvmField var ydpi = 160f
    companion object { const val DENSITY_DEFAULT = 160 } }

object TypedValue {
    const val COMPLEX_UNIT_PX = 0; const val COMPLEX_UNIT_DIP = 1; const val COMPLEX_UNIT_SP = 2
    @JvmStatic fun applyDimension(unit: Int, value: Float, metrics: DisplayMetrics?): Float = when (unit) { COMPLEX_UNIT_DIP -> value * (metrics?.density ?: 1f); COMPLEX_UNIT_SP -> value * (metrics?.scaledDensity ?: 1f); else -> value }
}

open class LruCache<K : Any, V : Any>(private var max: Int) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true); private var size = 0
    protected open fun sizeOf(key: K, value: V): Int = 1
    protected open fun create(key: K): V? = null
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}
    @Synchronized fun get(key: K): V? = map[key] ?: create(key)?.also { put(key, it) }
    @Synchronized fun put(key: K, value: V): V? { val prev = map.put(key, value); size += sizeOf(key, value); if (prev != null) size -= sizeOf(key, prev); trimToSize(max); return prev }
    @Synchronized fun remove(key: K): V? = map.remove(key)?.also { size -= sizeOf(key, it) }
    @Synchronized fun trimToSize(m: Int) { val it = map.entries.iterator(); while (size > m && it.hasNext()) { val e = it.next(); it.remove(); size -= sizeOf(e.key, e.value); entryRemoved(true, e.key, e.value, null) } }
    @Synchronized fun evictAll() = trimToSize(-1)
    @Synchronized fun size() = size
    @Synchronized fun maxSize() = max
    @Synchronized fun resize(m: Int) { max = m; trimToSize(m) }
    @Synchronized fun snapshot(): Map<K, V> = LinkedHashMap(map)
}

open class SparseArray<E>(capacity: Int = 10) { private val m = java.util.TreeMap<Int, E>()
    fun get(k: Int): E? = m[k]; fun get(k: Int, d: E): E = m[k] ?: d; fun put(k: Int, v: E) { m[k] = v }; fun remove(k: Int) { m.remove(k) }; fun delete(k: Int) { m.remove(k) }
    fun size() = m.size; fun keyAt(i: Int) = m.keys.elementAt(i); fun valueAt(i: Int): E = m.values.elementAt(i); fun clear() = m.clear(); fun indexOfKey(k: Int) = m.keys.indexOf(k) }
class SparseIntArray { private val m = java.util.TreeMap<Int, Int>(); fun get(k: Int, d: Int = 0) = m[k] ?: d; fun put(k: Int, v: Int) { m[k] = v }; fun size() = m.size; fun keyAt(i: Int) = m.keys.elementAt(i); fun valueAt(i: Int) = m.values.elementAt(i) }
class SparseBooleanArray { private val m = java.util.TreeMap<Int, Boolean>(); fun get(k: Int, d: Boolean = false) = m[k] ?: d; fun put(k: Int, v: Boolean) { m[k] = v } }
class ArrayMap<K, V> : java.util.LinkedHashMap<K, V>()
class ArraySet<E> : java.util.LinkedHashSet<E>()
class Pair<F, S>(@JvmField val first: F, @JvmField val second: S) { companion object { @JvmStatic fun <A, B> create(a: A, b: B) = Pair(a, b) } }
object Patterns { @JvmField val WEB_URL: java.util.regex.Pattern = java.util.regex.Pattern.compile("https?://[^\\s]+"); @JvmField val EMAIL_ADDRESS: java.util.regex.Pattern = java.util.regex.Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    @JvmField val IP_ADDRESS: java.util.regex.Pattern = java.util.regex.Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}") }
object Xml {
    @JvmStatic fun newPullParser(): org.xmlpull.v1.XmlPullParser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
    @JvmStatic fun asAttributeSet(p: org.xmlpull.v1.XmlPullParser): AttributeSet = object : AttributeSet {}
}
interface AttributeSet
class Printer
class MutableBoolean(@JvmField var value: Boolean)
