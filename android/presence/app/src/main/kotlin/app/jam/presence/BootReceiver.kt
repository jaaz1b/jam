package app.jam.presence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or an update: back to work (the app object connects Discord; this starts the service). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAliveService.sync(context)
    }
}
