package app.jam

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** All of Jam's settings, kept in one JSON object (same shape as desktop Jam where it applies). */
object Prefs {
    private lateinit var sp: SharedPreferences
    private var cache: JSONObject? = null

    private val DEFAULTS = JSONObject(
        """
        {
          "defaultSource": "octave",
          "openLastSource": false,
          "lastSource": "octave",
          "keepWarmMinutes": 5,
          "customSources": [],
          "sourceUrls": {},
          "rpcEnabled": true,
          "discordAppId": "",
          "showWhenPaused": false,
          "hideDetails": false,
          "statusDisplay": "line1",
          "appName": "",
          "line1": "{title}",
          "line2": "{artist}",
          "line3": "{album}",
          "pausedLabel": "Paused",
          "progress": "bar",
          "showAlbumArt": true,
          "fallbackImage": "jam",
          "button1Enabled": true,
          "button1Label": "Listen on {source}",
          "button1Url": "",
          "button2Enabled": false,
          "button2Label": "",
          "button2Url": ""
        }
        """.trimIndent()
    )

    fun init(context: Context) {
        sp = context.getSharedPreferences("jam", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun all(): JSONObject {
        cache?.let { return it }
        val saved = runCatching { JSONObject(sp.getString("settings", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val merged = JSONObject(DEFAULTS.toString())
        saved.keys().forEach { k -> merged.put(k, saved.get(k)) }
        cache = merged
        return merged
    }

    @Synchronized
    fun set(patch: JSONObject) {
        val all = all()
        patch.keys().forEach { k -> if (DEFAULTS.has(k)) all.put(k, patch.get(k)) }
        sp.edit().putString("settings", all.toString()).apply()
    }

    fun set(key: String, value: Any) = set(JSONObject().put(key, value))

    fun str(key: String): String = all().optString(key, DEFAULTS.optString(key))
    fun bool(key: String): Boolean = all().optBoolean(key, DEFAULTS.optBoolean(key))
    fun int(key: String): Int = all().optInt(key, DEFAULTS.optInt(key))
    fun arr(key: String): JSONArray = all().optJSONArray(key) ?: JSONArray()
    fun obj(key: String): JSONObject = all().optJSONObject(key) ?: JSONObject()

    @Synchronized
    fun resetPresence() {
        val keep = setOf("defaultSource", "openLastSource", "lastSource", "keepWarmMinutes", "customSources", "sourceUrls", "discordAppId", "rpcEnabled")
        val all = all()
        DEFAULTS.keys().forEach { k -> if (k !in keep) all.put(k, DEFAULTS.get(k)) }
        sp.edit().putString("settings", all.toString()).apply()
    }

    // ---- secrets (Discord tokens), stored separately and never shown in settings
    fun secret(key: String): String? = sp.getString("secret.$key", null)
    fun setSecret(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove("secret.$key") else putString("secret.$key", value) }.apply()
    }
}
