package app.jam.presence

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** All settings in one JSON object (the same keys as Jam's Discord settings where they apply). */
object Prefs {
    const val APPLE_MUSIC = "com.apple.android.music"
    /** Shown when a song has no cover Discord can load: Jam's jar, from this repo. */
    const val JAM_IMAGE = "https://raw.githubusercontent.com/jaaz1b/jam/main/desktop/build/discord-asset-jam.png"

    private lateinit var sp: SharedPreferences
    private var cache: JSONObject? = null

    private val DEFAULTS = JSONObject(
        """
        {
          "rpcEnabled": true,
          "discordAppId": "",
          "players": ["$APPLE_MUSIC"],
          "keepAlive": true,
          "stayOnline": false,
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
          "lookupCovers": true,
          "fallbackImage": "$JAM_IMAGE",
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
        sp = context.getSharedPreferences("jam_presence", Context.MODE_PRIVATE)
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
    fun arr(key: String): JSONArray = all().optJSONArray(key) ?: JSONArray()

    /** The players whose songs go to Discord (package names). */
    fun players(): Set<String> = arr("players").let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() }

    @Synchronized
    fun setPlayer(pkg: String, on: Boolean) {
        val now = players().toMutableSet()
        if (on) now += pkg else now -= pkg
        set("players", JSONArray(now.sorted()))
    }

    /** Players seen with a media session on this phone, newest last (for the Players list). */
    @Synchronized
    fun seen(): List<String> = runCatching { JSONArray(sp.getString("seenPlayers", "[]")) }.getOrDefault(JSONArray())
        .let { a -> (0 until a.length()).map { a.optString(it) } }

    @Synchronized
    fun addSeen(pkg: String) {
        val list = seen()
        if (pkg in list) return
        sp.edit().putString("seenPlayers", JSONArray((list + pkg).takeLast(30)).toString()).apply()
    }

    @Synchronized
    fun resetPresence() {
        val keep = setOf("discordAppId", "rpcEnabled", "players", "keepAlive", "stayOnline")
        val all = all()
        DEFAULTS.keys().forEach { k -> if (k !in keep) all.put(k, DEFAULTS.get(k)) }
        sp.edit().putString("settings", all.toString()).apply()
    }

    // ---- secrets (Discord tokens), stored apart and never shown in settings
    fun secret(key: String): String? = sp.getString("secret.$key", null)
    fun setSecret(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove("secret.$key") else putString("secret.$key", value) }.apply()
    }
}
