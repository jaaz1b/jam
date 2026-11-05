package app.jam

import android.app.Application

class JamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Discord.init(this)
    }
}
