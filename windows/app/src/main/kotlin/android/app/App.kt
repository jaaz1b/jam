package android.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import app.jam.desktop.shim.DesktopApp

open class Application : ContextWrapper(null) {
    init { DesktopApp.context = this }
    open fun onCreate() {}
    open fun onTerminate() {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}
    override val applicationContext: Context get() = this
    fun registerActivityLifecycleCallbacks(cb: ActivityLifecycleCallbacks) {}
    interface ActivityLifecycleCallbacks {
        fun onActivityCreated(a: Activity, b: Bundle?) {}; fun onActivityStarted(a: Activity) {}; fun onActivityResumed(a: Activity) {}
        fun onActivityPaused(a: Activity) {}; fun onActivityStopped(a: Activity) {}; fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}; fun onActivityDestroyed(a: Activity) {}
    }
    companion object { @JvmStatic fun getProcessName(): String = "app.jam.android" }
}

/**
 * An Activity on the PC: Jam's window. Its content (setContentView / setContent) is drawn by the
 * window (app.jam.desktop.Window); finish() closes it.
 */
open class Activity : ContextWrapper(null), androidx.lifecycle.LifecycleOwner, androidx.lifecycle.ViewModelStoreOwner,
    androidx.lifecycle.HasDefaultViewModelProviderFactory, androidx.activity.OnBackPressedDispatcherOwner {
    open var intent: Intent = Intent(Intent.ACTION_MAIN)
    val window: Window = Window(this)
    var requestedOrientation: Int = -1
    val isInMultiWindowMode: Boolean get() = false
    val isInPictureInPictureMode: Boolean get() = false
    val isFinishing: Boolean get() = finished
    val isDestroyed: Boolean get() = destroyed
    val isChangingConfigurations: Boolean get() = false
    private var finished = false
    private var destroyed = false
    /** What the window draws: a view tree (setContentView) whose ComposeViews hold the UI. */
    val content = androidx.compose.runtime.mutableStateOf<android.view.View?>(null)
    val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory get() = app.jam.desktop.shim.ReflectiveViewModels
    override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher { finishFromBack() }
    open fun finishFromBack() {}
    val fragmentManager: Any? get() = null

    open fun onCreate(savedInstanceState: Bundle?) { lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
    open fun onStart() { lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
    open fun onResume() { lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
    open fun onPause() { lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
    open fun onStop() { lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
    open fun onDestroy() { destroyed = true; lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED }
    open fun onNewIntent(intent: Intent) {}
    open fun onUserLeaveHint() {}
    open fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {}
    open fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
    open fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    open fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    open fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = if (event.action == android.view.KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event) else onKeyUp(event.keyCode, event)
    open fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean = false
    open fun onSaveInstanceState(outState: Bundle) {}
    open fun onWindowFocusChanged(hasFocus: Boolean) {}
    open fun onTrimMemory(level: Int) {}
    @Deprecated("") open fun onBackPressed() { onBackPressedDispatcher.onBackPressed() }
    open fun setContentView(view: android.view.View) { content.value = view }
    open fun setContentView(view: android.view.View, params: android.view.ViewGroup.LayoutParams?) = setContentView(view)
    open fun finish() { finished = true; app.jam.desktop.shim.Windows.finish(this) }
    open fun finishAndRemoveTask() = finish()
    open fun moveTaskToBack(nonRoot: Boolean): Boolean { app.jam.desktop.shim.Windows.minimize(this); return true }
    open fun recreate() { app.jam.desktop.shim.Windows.recreate(this) }
    open fun enterPictureInPictureMode(params: PictureInPictureParams): Boolean = false
    open fun setPictureInPictureParams(params: PictureInPictureParams) {}
    open fun startActivityForResult(intent: Intent, code: Int) = startActivity(intent)
    open fun runOnUiThread(r: Runnable) { if (app.jam.desktop.shim.Desk.isMain()) r.run() else app.jam.desktop.shim.Desk.post { r.run() } }
    open fun setShowWhenLocked(b: Boolean) {}
    open fun setTurnScreenOn(b: Boolean) {}
    open fun overridePendingTransition(a: Int, b: Int) {}
    open fun setTaskDescription(d: Any?) {}
    open fun reportFullyDrawn() {}
    open fun shouldShowRequestPermissionRationale(p: String): Boolean = false
    open fun requestPermissions(p: Array<String>, code: Int) {}
    open val callingActivity: ComponentName? get() = null
    open val componentName: ComponentName get() = ComponentName("app.jam.android", javaClass.name)
    open val currentFocus: android.view.View? get() = null
    val layoutInflater: Any? get() = null
    val title: CharSequence get() = "Jam"
    companion object { const val RESULT_OK = -1; const val RESULT_CANCELED = 0 }
}

class Window(val activity: Activity) {
    var attributes: android.view.WindowManager.LayoutParams = android.view.WindowManager.LayoutParams()
    var statusBarColor: Int = 0
    var navigationBarColor: Int = 0
    var isNavigationBarContrastEnforced: Boolean = false
    var isStatusBarContrastEnforced: Boolean = false
    val decorView: android.view.View get() = activity.content.value ?: android.view.View(activity)
    var colorMode: Int = 0
    fun addFlags(f: Int) { attributes.flags = attributes.flags or f; app.jam.desktop.shim.Windows.flagsChanged(activity) }
    fun clearFlags(f: Int) { attributes.flags = attributes.flags and f.inv(); app.jam.desktop.shim.Windows.flagsChanged(activity) }
    fun setFlags(f: Int, mask: Int) { attributes.flags = (attributes.flags and mask.inv()) or (f and mask) }
    fun setBackgroundDrawable(d: android.graphics.drawable.Drawable?) {}
    fun setSoftInputMode(m: Int) {}
    fun setDecorFitsSystemWindows(b: Boolean) {}
    val insetsController: android.view.WindowInsetsController? get() = android.view.WindowInsetsController()
    fun setNavigationBarColor(c: Int) { navigationBarColor = c }
    fun setStatusBarColor(c: Int) { statusBarColor = c }
    fun setSustainedPerformanceMode(b: Boolean) {}
    fun setHideOverlayWindows(b: Boolean) {}
    fun setFrameRateBoostOnTouchEnabled(b: Boolean) {}
}

/** A Service: created when first started by intent, its commands delivered on the main thread. */
abstract class Service : ContextWrapper(null) {
    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    open fun onBind(intent: Intent?): android.os.IBinder? = null
    open fun onUnbind(intent: Intent?): Boolean = false
    open fun onDestroy() {}
    open fun onTaskRemoved(rootIntent: Intent?) {}
    open fun onTimeout(startId: Int) {}
    open fun onTimeout(startId: Int, fgsType: Int) {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}
    fun startForeground(id: Int, n: Notification) { (app.jam.desktop.shim.Services.get(NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) }
    fun startForeground(id: Int, n: Notification, type: Int) = startForeground(id, n)
    fun stopForeground(flags: Int) {}
    @Deprecated("") fun stopForeground(remove: Boolean) {}
    fun stopSelf() { app.jam.desktop.shim.ServiceHost.stop(this) }
    fun stopSelf(startId: Int) = stopSelf()
    fun stopSelfResult(startId: Int): Boolean { stopSelf(); return true }
    val foregroundServiceType: Int get() = 0
    companion object {
        const val START_STICKY = 1; const val START_NOT_STICKY = 2; const val START_REDELIVER_INTENT = 3
        const val STOP_FOREGROUND_REMOVE = 1; const val STOP_FOREGROUND_DETACH = 2
    }
}

class PendingIntent private constructor(val intent: Intent, val kind: Int) : android.os.Parcelable {
    fun send() { when (kind) { 0 -> DesktopApp.context.startActivity(intent); 1 -> DesktopApp.context.sendBroadcast(intent); else -> DesktopApp.context.startService(intent) } }
    fun send(code: Int) = send()
    fun send(ctx: Context?, code: Int, fill: Intent?) = send()
    fun cancel() {}
    val intentSender: android.content.IntentSender get() = android.content.IntentSender()
    class CanceledException : Exception()
    companion object {
        const val FLAG_UPDATE_CURRENT = 0x08000000; const val FLAG_IMMUTABLE = 0x04000000; const val FLAG_MUTABLE = 0x02000000; const val FLAG_CANCEL_CURRENT = 0x10000000; const val FLAG_ONE_SHOT = 0x40000000; const val FLAG_NO_CREATE = 0x20000000
        @JvmStatic fun getActivity(c: Context, code: Int, i: Intent, flags: Int): PendingIntent = PendingIntent(i, 0)
        @JvmStatic fun getActivity(c: Context, code: Int, i: Intent, flags: Int, o: Bundle?): PendingIntent = PendingIntent(i, 0)
        @JvmStatic fun getBroadcast(c: Context, code: Int, i: Intent, flags: Int): PendingIntent = PendingIntent(i, 1)
        @JvmStatic fun getService(c: Context, code: Int, i: Intent, flags: Int): PendingIntent = PendingIntent(i, 2)
        @JvmStatic fun getForegroundService(c: Context, code: Int, i: Intent, flags: Int): PendingIntent = PendingIntent(i, 2)
    }
}

/** A notification: shown as a Windows toast (title and text) when it is not the ongoing media one. */
open class Notification {
    @JvmField var title: CharSequence? = null
    @JvmField var text: CharSequence? = null
    @JvmField var ongoing: Boolean = false
    @JvmField var contentIntent: PendingIntent? = null
    @JvmField var flags: Int = 0
    @JvmField var extras: Bundle = Bundle()
    @JvmField var channelId: String? = null
    @JvmField var when_: Long = System.currentTimeMillis()
    @JvmField var category: String? = null
    class Action(icon: Int, val title: CharSequence?, val actionIntent: PendingIntent?) {
        class Builder(icon: Any?, val title: CharSequence?, val intent: PendingIntent?) { fun build() = Action(0, title, intent); fun addExtras(b: Bundle) = this; fun setAuthenticationRequired(b: Boolean) = this }
    }
    open class Style
    class BigTextStyle : Style() { fun bigText(t: CharSequence?) = this; fun setBigContentTitle(t: CharSequence?) = this; fun setSummaryText(t: CharSequence?) = this }
    class MediaStyle : Style() { fun setMediaSession(t: Any?) = this; fun setShowActionsInCompactView(vararg i: Int) = this }
    class Builder(val context: Context, val channel: String?) {
        constructor(context: Context) : this(context, null)
        private val n = Notification().also { it.channelId = channel }
        fun setContentTitle(t: CharSequence?) = apply { n.title = t }
        fun setContentText(t: CharSequence?) = apply { n.text = t }
        fun setSmallIcon(i: Int) = this
        fun setSmallIcon(i: android.graphics.drawable.Icon?) = this
        fun setLargeIcon(b: android.graphics.Bitmap?) = this
        fun setOngoing(b: Boolean) = apply { n.ongoing = b }
        fun setContentIntent(p: PendingIntent?) = apply { n.contentIntent = p }
        fun setAutoCancel(b: Boolean) = this
        fun setStyle(s: Style?) = this
        fun setCategory(c: String?) = apply { n.category = c }
        fun setVisibility(v: Int) = this
        fun setShowWhen(b: Boolean) = this
        fun setOnlyAlertOnce(b: Boolean) = this
        fun setPriority(p: Int) = this
        fun setSilent(b: Boolean) = this
        fun setProgress(max: Int, p: Int, ind: Boolean) = this
        fun addAction(a: Action) = this
        fun addAction(i: Int, t: CharSequence?, p: PendingIntent?) = this
        fun setDeleteIntent(p: PendingIntent?) = this
        fun setColor(c: Int) = this
        fun setForegroundServiceBehavior(b: Int) = this
        fun setSubText(t: CharSequence?) = this
        fun setTicker(t: CharSequence?) = this
        fun setGroup(g: String?) = this
        fun setWhen(w: Long) = apply { n.when_ = w }
        fun setExtras(b: Bundle?) = apply { if (b != null) n.extras = b }
        fun build(): Notification = n
    }
    companion object {
        const val FLAG_ONGOING_EVENT = 2; const val FLAG_FOREGROUND_SERVICE = 64; const val CATEGORY_TRANSPORT = "transport"; const val CATEGORY_SERVICE = "service"; const val CATEGORY_PROGRESS = "progress"
        const val VISIBILITY_PUBLIC = 1; const val PRIORITY_LOW = -1; const val PRIORITY_DEFAULT = 0; const val PRIORITY_HIGH = 1; const val EXTRA_MEDIA_SESSION = "android.mediaSession"
        const val FOREGROUND_SERVICE_IMMEDIATE = 1
    }
}
class NotificationChannel(val id: String, var name: CharSequence?, val importance: Int) {
    var description: String? = null
    fun setShowBadge(b: Boolean) {}; fun setSound(u: android.net.Uri?, a: Any?) {}; fun enableVibration(b: Boolean) {}; fun enableLights(b: Boolean) {}
    var lockscreenVisibility: Int = 0
}
class NotificationManager {
    fun createNotificationChannel(c: NotificationChannel) {}
    fun deleteNotificationChannel(id: String) {}
    fun getNotificationChannel(id: String): NotificationChannel? = null
    fun notify(id: Int, n: Notification) = app.jam.desktop.shim.Notices.notify(null, id, n)
    fun notify(tag: String?, id: Int, n: Notification) = app.jam.desktop.shim.Notices.notify(tag, id, n)
    fun cancel(id: Int) = app.jam.desktop.shim.Notices.cancel(null, id)
    fun cancel(tag: String?, id: Int) = app.jam.desktop.shim.Notices.cancel(tag, id)
    fun cancelAll() {}
    fun areNotificationsEnabled(): Boolean = true
    val activeNotifications: Array<Any> get() = emptyArray()
    companion object { const val IMPORTANCE_LOW = 2; const val IMPORTANCE_DEFAULT = 3; const val IMPORTANCE_HIGH = 4; const val IMPORTANCE_MIN = 1; const val IMPORTANCE_NONE = 0 }
}
class ActivityManager {
    class RunningAppProcessInfo { @JvmField var importance: Int = IMPORTANCE_FOREGROUND; @JvmField var processName: String = "app.jam.android"; @JvmField var lastTrimLevel = 0
        companion object { const val IMPORTANCE_FOREGROUND = 100; const val IMPORTANCE_VISIBLE = 200; const val IMPORTANCE_FOREGROUND_SERVICE = 125; const val IMPORTANCE_CACHED = 400; const val IMPORTANCE_SERVICE = 300 } }
    class MemoryInfo { @JvmField var availMem: Long = Runtime.getRuntime().maxMemory(); @JvmField var totalMem: Long = Runtime.getRuntime().maxMemory() * 4; @JvmField var lowMemory = false; @JvmField var threshold: Long = 0 }
    fun getMemoryInfo(i: MemoryInfo) {}
    val isLowRamDevice: Boolean get() = false
    val memoryClass: Int get() = 512
    val largeMemoryClass: Int get() = 1024
    val runningAppProcesses: List<RunningAppProcessInfo> get() = listOf(RunningAppProcessInfo())
    fun getHistoricalProcessExitReasons(pkg: String?, pid: Int, max: Int): List<ApplicationExitInfo> = emptyList()
    fun isBackgroundRestricted(): Boolean = false
    val appTasks: List<Any> get() = emptyList()
    companion object { @JvmStatic fun getMyMemoryState(i: RunningAppProcessInfo) {}; @JvmStatic fun isUserAMonkey() = false }
}
class ApplicationExitInfo { val reason: Int = 0; val timestamp: Long = 0; val description: String? = null; val status: Int = 0; val importance: Int = 0; val pss: Long = 0; val rss: Long = 0
    fun getTraceInputStream(): java.io.InputStream? = null
    companion object { const val REASON_UNKNOWN = 0; const val REASON_EXIT_SELF = 1; const val REASON_SIGNALED = 2; const val REASON_LOW_MEMORY = 3; const val REASON_CRASH = 4; const val REASON_CRASH_NATIVE = 5
        const val REASON_ANR = 6; const val REASON_INITIALIZATION_FAILURE = 7; const val REASON_PERMISSION_CHANGE = 8; const val REASON_EXCESSIVE_RESOURCE_USAGE = 9; const val REASON_USER_REQUESTED = 10
        const val REASON_USER_STOPPED = 11; const val REASON_DEPENDENCY_DIED = 12; const val REASON_OTHER = 13; const val REASON_FREEZER = 14; const val REASON_PACKAGE_STATE_CHANGE = 15; const val REASON_PACKAGE_UPDATED = 16 } }
class UiModeManager { val currentModeType: Int get() = android.content.res.Configuration.UI_MODE_TYPE_DESK; var nightMode: Int = MODE_NIGHT_YES
    fun setApplicationNightMode(m: Int) {}
    companion object { const val MODE_NIGHT_YES = 2; const val MODE_NIGHT_NO = 1; const val MODE_NIGHT_AUTO = 0; const val MODE_NIGHT_CUSTOM = 3 } }
class PictureInPictureParams private constructor() { class Builder { fun setAspectRatio(r: android.util.Rational?) = this; fun setActions(a: List<RemoteAction>) = this; fun setAutoEnterEnabled(b: Boolean) = this
    fun setSeamlessResizeEnabled(b: Boolean) = this; fun setSourceRectHint(r: android.graphics.Rect?) = this; fun setTitle(t: CharSequence?) = this; fun setSubtitle(t: CharSequence?) = this; fun build() = PictureInPictureParams() } }
class RemoteAction(icon: android.graphics.drawable.Icon, title: CharSequence, desc: CharSequence, intent: PendingIntent) { var isEnabled = true; fun setShouldShowIcon(b: Boolean) {} }
class AlarmManager { fun set(type: Int, at: Long, p: PendingIntent) = setExact(type, at, p); fun setExact(type: Int, at: Long, p: PendingIntent) { app.jam.desktop.shim.Desk.timer.schedule({ app.jam.desktop.shim.Desk.post { p.send() } }, (at - System.currentTimeMillis()).coerceAtLeast(0), java.util.concurrent.TimeUnit.MILLISECONDS) }
    fun setAndAllowWhileIdle(type: Int, at: Long, p: PendingIntent) = setExact(type, at, p); fun setExactAndAllowWhileIdle(type: Int, at: Long, p: PendingIntent) = setExact(type, at, p)
    fun setInexactRepeating(type: Int, at: Long, interval: Long, p: PendingIntent) {}; fun cancel(p: PendingIntent) {}; fun canScheduleExactAlarms() = true
    companion object { const val RTC_WAKEUP = 0; const val RTC = 1; const val ELAPSED_REALTIME_WAKEUP = 2; const val ELAPSED_REALTIME = 3; const val INTERVAL_DAY = 86_400_000L; const val INTERVAL_HOUR = 3_600_000L } }
class KeyguardManager { val isKeyguardLocked: Boolean get() = false; val isDeviceLocked: Boolean get() = false }
class SearchManager { companion object { const val QUERY = "query" } }
open class Dialog(ctx: Context) { fun show() {}; fun dismiss() {} }
