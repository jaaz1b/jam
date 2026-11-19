package app.jam.desktop.web

import app.jam.desktop.shim.Desk

/**
 * What Discord's sign-in refused, in words, with what to change in the Discord app (the
 * Application ID's settings in Discord's Developer Portal). Logged, and shown in Jam.
 */
object DiscordHelp {
    @Volatile private var lastShown = 0L

    fun refused(url: String, status: Int, body: String) {
        val o = runCatching { org.json.JSONObject(body) }.getOrNull()
        val said = listOfNotNull(
            o?.optString("message")?.takeIf { it.isNotBlank() },
            o?.optJSONObject("errors")?.toString()?.take(300),
            o?.optString("error_description")?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { body.take(200) }
        Desk.log('W', "Discord", "sign-in refused ($status) at ${url.substringBefore('?')}: $said", null)
        val hint = when {
            "redirect" in said.lowercase() -> "Add jamdiscord://oauth2/callback under OAuth2 › Redirects."
            "scope" in said.lowercase() || "social" in said.lowercase() -> "Turn on the Social SDK for the app (Discord Social SDK › Getting Started) and Public Client under OAuth2."
            "client" in said.lowercase() -> "Check the Application ID, and turn on Public Client under OAuth2."
            else -> "In the Discord Developer Portal, for your app: Public Client on (OAuth2), jamdiscord://oauth2/callback under Redirects, Social SDK turned on."
        }
        val now = System.currentTimeMillis()
        if (now - lastShown < 4000) return
        lastShown = now
        Desk.post {
            app.jam.desktop.shim.Notices.alert("Discord refused the sign-in", "Discord said: $said\n\n$hint")
        }
    }
}
