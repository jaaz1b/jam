package app.jam.presence

import android.app.Application

class PresenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Player.init(this)
        Covers.init(this)
        Discord.init(this)
    }
}
