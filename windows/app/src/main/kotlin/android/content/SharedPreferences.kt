package android.content

import app.jam.desktop.shim.Desk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Android's SharedPreferences, kept as one JSON file per name in Jam's data folder. */
interface SharedPreferences {
    val all: Map<String, *>
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

/** The files behind [SharedPreferences], one instance per name (as on Android). */
object Prefs {
    private val all = ConcurrentHashMap<String, FilePrefs>()
    fun of(name: String): SharedPreferences = all.getOrPut(name) { FilePrefs(File(Desk.prefsDir, "${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")) }
    fun names(): List<String> = Desk.prefsDir.listFiles().orEmpty().filter { it.extension == "json" }.map { it.nameWithoutExtension }
    fun delete(name: String): Boolean { all.remove(name); return File(Desk.prefsDir, "$name.json").delete() }
}

internal class FilePrefs(private val file: File) : SharedPreferences {
    private val map = ConcurrentHashMap<String, Any>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    init { load() }

    private fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        for ((k, v) in o) fromJson(v)?.let { map[k] = it }
    }

    private fun fromJson(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
        is JsonObject -> {
            // {"t":"long","v":5} keeps the type an Android pref had.
            val t = (e["t"] as? JsonPrimitive)?.content; val v = e["v"] as? JsonPrimitive
            when (t) {
                "int" -> v?.longOrNull?.toInt(); "long" -> v?.longOrNull; "float" -> v?.doubleOrNull?.toFloat()
                "bool" -> v?.booleanOrNull; else -> v?.content
            }
        }
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull
    }

    private fun toJson(v: Any): JsonElement = when (v) {
        is String -> JsonPrimitive(v)
        is Set<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
        is Int -> JsonObject(mapOf("t" to JsonPrimitive("int"), "v" to JsonPrimitive(v)))
        is Long -> JsonObject(mapOf("t" to JsonPrimitive("long"), "v" to JsonPrimitive(v)))
        is Float -> JsonObject(mapOf("t" to JsonPrimitive("float"), "v" to JsonPrimitive(v)))
        is Boolean -> JsonObject(mapOf("t" to JsonPrimitive("bool"), "v" to JsonPrimitive(v)))
        else -> JsonPrimitive(v.toString())
    }

    @Synchronized private fun save() {
        val o = JsonObject(map.mapValues { toJson(it.value) })
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching { tmp.writeText(o.toString()); java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
            .onFailure { runCatching { file.writeText(o.toString()) } }
    }

    override val all: Map<String, *> get() = HashMap(map)
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = (map[key] as? Set<String>)?.toSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = (map[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String, defValue: Long) = (map[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float) = (map[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += listener }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= listener }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { puts[key] = values?.toSet(); return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { puts[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { puts[key] = null; return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { write(); return true }
        override fun apply() = write()
        private fun write() {
            val changed = ArrayList<String>()
            synchronized(this@FilePrefs) {
                if (clear) { changed += map.keys; map.clear() }
                for ((k, v) in puts) { if (v == null) map.remove(k) else map[k] = v; changed += k }
                save()
            }
            if (listeners.isNotEmpty()) {
                val notify = { changed.distinct().forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this@FilePrefs, k) } } }
                if (Desk.isMain()) notify() else Desk.post { notify() }
            }
        }
    }
}
