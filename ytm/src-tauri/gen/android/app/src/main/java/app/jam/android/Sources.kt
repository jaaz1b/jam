package app.jam.android

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * The music services Jam plays from. Each gets its own saved login. Spotify and Apple Music have
 * their own Android apps, so Jam for Android leaves them out. Users can add their own non-DRM
 * sources and change the link of Octave / Monochrome in Settings.
 */
data class Source(
    val id: String,
    val name: String,
    val url: String,
    val domains: List<String>,
    val color: String,
    /** Present as a desktop browser (Spotify / Apple Music push phones to their own apps). */
    val desktopSite: Boolean = false,
    val custom: Boolean = false,
    val editableLink: Boolean = false,
    val customUrl: Boolean = false,
    val defaultUrl: String? = null,
    val extraDomains: List<String> = emptyList(),
    /** YouTube Music: Jam's own app (the Limusic engine), not a website. */
    val engine: Boolean = false,
    /**
     * A web app with an offline mode once installed (Octave): Jam treats it as installed and keeps
     * its service worker registered, and leaves the WebView's own user agent, which is the one the
     * service worker's requests carry (Cloudflare ties its clearance to it).
     */
    val offlineApp: Boolean = false,
)

object Sources {
    const val YTM = "ytm"

    private val BUILTIN = listOf(
        Source("octave", "Octave", "https://music.octavestreaming.com/", listOf("octavestreaming.com"), "#8b5cf6", editableLink = true, offlineApp = true),
        Source(
            "monochrome", "Monochrome", "https://monochrome.st/",
            listOf("monochrome.st", "monochrome.tf", "lossless.wtf", "if-it-runs-ship-it.lol", "last.fm"), "#e8e8ee", editableLink = true,
        ),
        // The same YouTube Music app as Jam on Windows (Limusic rebuilt into Jam), running natively.
        Source(YTM, "YouTube Music", "https://music.youtube.com/", listOf("music.youtube.com", "youtube.com", "youtu.be"), "#ff0033", engine = true),
    )
    val PALETTE = listOf("#f97316", "#eab308", "#22c55e", "#06b6d4", "#3b82f6", "#a855f7", "#ec4899", "#f43f5e", "#94a3b8")
    const val MAX_CUSTOM = 12

    /** Sign-in providers that may open inside Jam. */
    val AUTH_HOSTS = listOf(
        "accounts.google.com", "accounts.youtube.com", "discord.com", "appleid.apple.com", "idmsa.apple.com", "github.com",
        "login.microsoftonline.com", "facebook.com", "x.com", "api.twitter.com", "checkout.stripe.com", "accounts.spotify.com",
    )

    fun all(): List<Source> {
        val overrides = Prefs.obj("sourceUrls")
        val list = BUILTIN.map { b ->
            val o = overrides.optString(b.id, "")
            if (b.editableLink && o.isNotBlank()) b.copy(url = o, customUrl = true, defaultUrl = b.url, domains = b.domains + baseHost(o))
            else b.copy(defaultUrl = b.url)
        }.toMutableList()
        val custom = Prefs.arr("customSources")
        for (i in 0 until custom.length()) {
            val c = custom.optJSONObject(i) ?: continue
            val url = c.optString("url")
            val extra = c.optJSONArray("extraDomains")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            list += Source(
                id = c.optString("id"), name = c.optString("name"), url = url,
                domains = listOf(baseHost(url)) + extra, color = c.optString("color", PALETTE[0]),
                desktopSite = c.optBoolean("desktopSite", false), custom = true, editableLink = true, extraDomains = extra,
            )
        }
        return list
    }

    fun byId(id: String?): Source = all().let { l -> l.firstOrNull { it.id == id } ?: l.first() }
    fun exists(id: String?): Boolean = all().any { it.id == id }

    fun hostOf(url: String?): String = runCatching { Uri.parse(url).host?.lowercase() ?: "" }.getOrDefault("")
    fun baseHost(url: String?): String = hostOf(url).removePrefix("www.")
    private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun belongsTo(s: Source, url: String?): Boolean {
        val h = hostOf(url)
        return h.isNotEmpty() && s.domains.any { matches(h, it) }
    }

    fun isAuth(url: String?): Boolean {
        val h = hostOf(url)
        return h.isNotEmpty() && AUTH_HOSTS.any { matches(h, it) }
    }

    /** "monochrome.tf" → "https://monochrome.tf/". Null if it isn't a usable web address. */
    fun normalizeUrl(input: String?): String? {
        var s = (input ?: "").trim()
        if (s.isEmpty()) return null
        if (!Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s = "https://$s"
        val u = runCatching { Uri.parse(s) }.getOrNull() ?: return null
        if (u.scheme !in listOf("http", "https") || u.host.isNullOrBlank() || !u.host!!.contains('.')) return null
        return if (u.path.isNullOrEmpty()) "$s/" else s
    }

    private fun normalizeDomains(input: String?): List<String> =
        (input ?: "").split(Regex("[\\s,]+")).mapNotNull { raw ->
            val d = raw.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("*.").removePrefix("www.")
            if (Regex("^[a-z0-9.-]+\\.[a-z]{2,}$").matches(d)) d else null
        }.distinct().take(10)

    /** Add or update a source from the Settings form. Returns an error message, or null. */
    fun save(input: JSONObject): String? {
        val id = input.optString("id")
        val builtin = BUILTIN.firstOrNull { it.id == id }
        if (builtin != null) {
            if (!builtin.editableLink) return "${builtin.name}'s link can't be changed."
            val url = normalizeUrl(input.optString("url")) ?: return "That link doesn't look like a web address."
            Prefs.set("sourceUrls", Prefs.obj("sourceUrls").put(id, url))
            return null
        }
        val name = input.optString("name").trim().take(40)
        if (name.isEmpty()) return "Give it a name."
        val url = normalizeUrl(input.optString("url")) ?: return "That link doesn't look like a web address (e.g. https://monochrome.tf)."
        val list = Prefs.arr("customSources")
        val existing = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        val others = existing.filter { it.optString("id") != id }
        if (id.isEmpty() && others.size >= MAX_CUSTOM) return "You can add up to $MAX_CUSTOM sources."
        if ((BUILTIN.map { it.name } + others.map { it.optString("name") }).any { it.equals(name, true) }) return "There's already a source called “$name”."
        val newId = if (Regex("^c_[a-z0-9]{4,16}$").matches(id)) id else "c_" + (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val color = input.optString("color").takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: PALETTE[others.size % PALETTE.size]
        val item = JSONObject()
            .put("id", newId).put("name", name).put("url", url).put("color", color)
            .put("desktopSite", input.optBoolean("desktopSite", false))
            .put("extraDomains", JSONArray(normalizeDomains(input.optString("extraDomains"))))
        val out = JSONArray()
        var replaced = false
        existing.forEach { if (it.optString("id") == newId) { out.put(item); replaced = true } else out.put(it) }
        if (!replaced) out.put(item)
        Prefs.set("customSources", out)
        return null
    }

    fun remove(id: String) {
        val list = Prefs.arr("customSources")
        val out = JSONArray()
        for (i in 0 until list.length()) list.optJSONObject(i)?.let { if (it.optString("id") != id) out.put(it) }
        Prefs.set("customSources", out)
        if (Prefs.str("defaultSource") == id) Prefs.set("defaultSource", "octave")
        if (Prefs.str("lastSource") == id) Prefs.set("lastSource", "octave")
    }

    fun resetUrl(id: String) {
        val o = Prefs.obj("sourceUrls"); o.remove(id); Prefs.set("sourceUrls", o)
    }

    fun toJson(s: Source, active: String?): JSONObject = JSONObject()
        .put("id", s.id).put("name", s.name).put("url", s.url).put("color", s.color)
        .put("custom", s.custom).put("editableLink", s.editableLink).put("customUrl", s.customUrl)
        .put("defaultUrl", s.defaultUrl ?: JSONObject.NULL).put("desktopSite", s.desktopSite)
        .put("extraDomains", JSONArray(s.extraDomains)).put("active", s.id == active).put("engine", s.engine)
}
