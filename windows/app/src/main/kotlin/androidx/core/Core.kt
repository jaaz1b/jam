package androidx.core.content

import android.content.Context
import android.content.SharedPreferences

object ContextCompat {
    const val RECEIVER_EXPORTED = 2; const val RECEIVER_NOT_EXPORTED = 4; const val RECEIVER_VISIBLE_TO_INSTANT_APPS = 1
    @JvmStatic fun startForegroundService(c: Context, i: android.content.Intent) { c.startForegroundService(i) }
    @JvmStatic fun registerReceiver(c: Context, r: android.content.BroadcastReceiver?, f: android.content.IntentFilter, flags: Int): android.content.Intent? = c.registerReceiver(r, f)
    @JvmStatic fun registerReceiver(c: Context, r: android.content.BroadcastReceiver?, f: android.content.IntentFilter, perm: String?, h: android.os.Handler?, flags: Int): android.content.Intent? = c.registerReceiver(r, f)
    @JvmStatic fun getColor(c: Context, id: Int): Int = c.getColor(id)
    @JvmStatic fun getDrawable(c: Context, id: Int): android.graphics.drawable.Drawable? = android.graphics.drawable.Drawable.fromRes(id)
    @JvmStatic fun checkSelfPermission(c: Context, p: String): Int = 0
    @JvmStatic fun getMainExecutor(c: Context): java.util.concurrent.Executor = java.util.concurrent.Executor { app.jam.desktop.shim.Desk.post(it) }
    @JvmStatic fun <T> getSystemService(c: Context, cls: Class<T>): T? = c.getSystemService(cls)
    @JvmStatic fun getExternalFilesDirs(c: Context, t: String?): Array<java.io.File?> = c.getExternalFilesDirs(t)
}
object FileProvider { @JvmStatic fun getUriForFile(c: Context, authority: String, f: java.io.File): android.net.Uri = android.net.Uri.fromFile(f) }
inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) { val e = edit(); e.action(); if (commit) e.commit() else e.apply() }

object IntentCompat {
    @JvmStatic fun <T> getParcelableExtra(i: android.content.Intent, name: String, cls: Class<T>): T? = i.getParcelableExtra(name, cls)
}
