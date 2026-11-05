package app.jam.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives jamdiscord://oauth2/callback after signing in to Discord, then returns to Settings. */
class DiscordCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { Discord.onCallback(it) }
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra("section", "discord")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
