package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

class NotificationManagerCompat private constructor() {
    private val nm get() = app.jam.desktop.shim.Services.get(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    fun notify(id: Int, n: Notification) = nm.notify(id, n)
    fun notify(tag: String?, id: Int, n: Notification) = nm.notify(tag, id, n)
    fun cancel(id: Int) = nm.cancel(id)
    fun cancel(tag: String?, id: Int) = nm.cancel(tag, id)
    fun cancelAll() {}
    fun areNotificationsEnabled(): Boolean = true
    fun createNotificationChannel(c: android.app.NotificationChannel) {}
    fun createNotificationChannel(c: NotificationChannelCompat) {}
    fun getNotificationChannel(id: String): android.app.NotificationChannel? = null
    companion object { private val inst = NotificationManagerCompat(); @JvmStatic fun from(c: Context) = inst
        const val IMPORTANCE_LOW = 2; const val IMPORTANCE_DEFAULT = 3; const val IMPORTANCE_HIGH = 4; const val IMPORTANCE_MIN = 1 }
}
class NotificationChannelCompat private constructor() { class Builder(id: String, importance: Int) { fun setName(n: CharSequence?) = this; fun setDescription(d: String?) = this; fun setShowBadge(b: Boolean) = this; fun setSound(u: android.net.Uri?, a: Any?) = this; fun setVibrationEnabled(b: Boolean) = this; fun build() = NotificationChannelCompat() } }
object NotificationCompat {
    const val PRIORITY_LOW = -1; const val PRIORITY_DEFAULT = 0; const val PRIORITY_HIGH = 1; const val PRIORITY_MIN = -2; const val PRIORITY_MAX = 2
    const val VISIBILITY_PUBLIC = 1; const val VISIBILITY_PRIVATE = 0; const val VISIBILITY_SECRET = -1
    const val CATEGORY_TRANSPORT = "transport"; const val CATEGORY_SERVICE = "service"; const val CATEGORY_PROGRESS = "progress"; const val CATEGORY_RECOMMENDATION = "recommendation"; const val CATEGORY_STATUS = "status"
    const val FOREGROUND_SERVICE_IMMEDIATE = 1; const val FOREGROUND_SERVICE_DEFAULT = 0; const val FOREGROUND_SERVICE_DEFERRED = 2
    const val EXTRA_MEDIA_SESSION = "android.mediaSession"
    class Builder(context: Context, channel: String) {
        private val b = Notification.Builder(context, channel)
        fun setContentTitle(t: CharSequence?) = apply { b.setContentTitle(t) }
        fun setContentText(t: CharSequence?) = apply { b.setContentText(t) }
        fun setSubText(t: CharSequence?) = this
        fun setSmallIcon(i: Int) = this
        fun setLargeIcon(bm: android.graphics.Bitmap?) = this
        fun setOngoing(o: Boolean) = apply { b.setOngoing(o) }
        fun setContentIntent(p: PendingIntent?) = apply { b.setContentIntent(p) }
        fun setDeleteIntent(p: PendingIntent?) = this
        fun setAutoCancel(a: Boolean) = this
        fun setStyle(s: Style?) = this
        fun setCategory(c: String?) = apply { b.setCategory(c) }
        fun setVisibility(v: Int) = this
        fun setShowWhen(s: Boolean) = this
        fun setOnlyAlertOnce(s: Boolean) = this
        fun setPriority(p: Int) = this
        fun setSilent(s: Boolean) = this
        fun setProgress(max: Int, p: Int, ind: Boolean) = this
        fun addAction(a: Action) = this
        fun addAction(i: Int, t: CharSequence?, p: PendingIntent?) = this
        fun clearActions() = this
        fun setColor(c: Int) = this
        fun setColorized(c: Boolean) = this
        fun setForegroundServiceBehavior(f: Int) = this
        fun setGroup(g: String?) = this
        fun setTicker(t: CharSequence?) = this
        fun setWhen(w: Long) = this
        fun setNumber(n: Int) = this
        fun setExtras(e: android.os.Bundle?) = this
        fun setUsesChronometer(u: Boolean) = this
        fun setDefaults(d: Int) = this
        fun setSound(u: android.net.Uri?) = this
        fun setVibrate(v: LongArray?) = this
        fun setLocalOnly(b: Boolean) = this
        fun setRequestPromotedOngoing(b: Boolean) = this
        fun setShortCriticalText(t: String?) = this
        fun build(): Notification = b.build()
    }
    class Action(icon: Int, title: CharSequence?, intent: PendingIntent?) { class Builder(icon: Int, title: CharSequence?, intent: PendingIntent?) { fun build() = Action(0, null, null); fun setShowsUserInterface(b: Boolean) = this } }
    abstract class Style
    class BigTextStyle : Style() { fun bigText(t: CharSequence?) = this; fun setBigContentTitle(t: CharSequence?) = this; fun setSummaryText(t: CharSequence?) = this }
    class BigPictureStyle : Style() { fun bigPicture(b: android.graphics.Bitmap?) = this }
    class DecoratedCustomViewStyle : Style()
}
object ServiceCompat {
    const val STOP_FOREGROUND_REMOVE = 1; const val STOP_FOREGROUND_DETACH = 2; const val START_STICKY = 1
    @JvmStatic fun stopForeground(s: android.app.Service, flags: Int) = s.stopForeground(flags)
    @JvmStatic fun startForeground(s: android.app.Service, id: Int, n: Notification, type: Int) = s.startForeground(id, n, type)
}
object ActivityCompat {
    @JvmStatic fun requestPermissions(a: android.app.Activity, p: Array<String>, code: Int) {}
    @JvmStatic fun shouldShowRequestPermissionRationale(a: android.app.Activity, p: String): Boolean = false
    @JvmStatic fun finishAffinity(a: android.app.Activity) = a.finish()
    @JvmStatic fun recreate(a: android.app.Activity) = a.recreate()
}
