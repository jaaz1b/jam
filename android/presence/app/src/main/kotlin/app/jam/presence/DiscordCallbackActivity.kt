package app.jam.presence

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives jampresence://oauth2/callback after signing in to Discord, then returns to the app. */
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
