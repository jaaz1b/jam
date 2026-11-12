package app.jam

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myspoti.engine.R

/** Jam: what Jam adds to the settings (both looks): YouTube Music mode and Discord's presence. */
object JamSettings {
    /** Jam's own settings page (Jam for Android's): the Discord sign-in, the status card, the mode. */
    fun openDiscord(context: Context) {
        context.startActivity(Intent(context, app.jam.android.SettingsActivity::class.java)
            .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun activity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}

/** The confirmation before Jam restarts in YouTube Music mode. */
@Composable
fun JamYtmModeConfirm(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jam_ytm_mode_title)) },
        text = { Text(stringResource(R.string.jam_ytm_mode_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                JamSettings.activity(ctx)?.let { app.jam.android.JamMode.switchTo(it, ytm = true) }
            }) { Text(stringResource(R.string.jam_ytm_mode_switch)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
