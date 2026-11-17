package android.content

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Looper
import app.jam.desktop.shim.Desk
import app.jam.desktop.shim.Res
import app.jam.desktop.shim.Services
import java.io.File

/**
 * Android's Context on the PC: one for the whole app ([app.jam.desktop.shim.DesktopApp]). Storage
 * goes to Jam's data folder, resources come from the Android app's res/, "starting an activity"
 * does what it means on a PC (a web page opens in the browser, a share copies the text...).
 */
abstract class Context {
    open val applicationContext: Context get() = app.jam.desktop.shim.DesktopApp.context
    open val packageName: String get() = "app.jam.android"
    open val resources: Resources get() = Resources.instance
    open val assets: AssetManager get() = AssetManager
    open val filesDir: File get() = Desk.filesDir
    open val cacheDir: File get() = Desk.cacheDir
    open val codeCacheDir: File get() = File(Desk.cacheDir, "code").apply { mkdirs() }
    open val noBackupFilesDir: File get() = File(Desk.dataDir, "no_backup").apply { mkdirs() }
    open val dataDir: File get() = Desk.dataDir
    open val externalCacheDir: File? get() = File(Desk.cacheDir, "external").apply { mkdirs() }
    open val mainLooper: Looper get() = Looper.getMainLooper()
    open val contentResolver: ContentResolver get() = ContentResolver
    open val packageManager: PackageManager get() = PackageManager.instance
    open val applicationInfo: ApplicationInfo get() = ApplicationInfo.instance
    open val theme: Resources.Theme get() = Resources.Theme
    open val classLoader: ClassLoader get() = Context::class.java.classLoader

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = Prefs.of(name)
    open fun deleteSharedPreferences(name: String): Boolean = Prefs.delete(name)
    open fun getString(resId: Int): String = Res.string(resId)
    open fun getString(resId: Int, vararg formatArgs: Any?): String = Res.format(resId, formatArgs)
    open fun getText(resId: Int): CharSequence = Res.string(resId)
    open fun getColor(id: Int): Int = Res.color(id)
    open fun getExternalFilesDir(type: String?): File? = File(Desk.dataDir, "external/${type ?: ""}").apply { mkdirs() }
    open fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(getExternalFilesDir(type))
    open fun getDir(name: String, mode: Int): File = File(Desk.dataDir, "app_$name").apply { mkdirs() }
    open fun getDatabasePath(name: String): File = File(File(Desk.dataDir, "databases").apply { mkdirs() }, name)
    open fun getFileStreamPath(name: String): File = File(filesDir, name)
    open fun openFileOutput(name: String, mode: Int): java.io.FileOutputStream = java.io.FileOutputStream(File(filesDir, name), mode and MODE_APPEND != 0)
    open fun openFileInput(name: String): java.io.FileInputStream = java.io.FileInputStream(File(filesDir, name))
    open fun deleteFile(name: String): Boolean = File(filesDir, name).delete()

    open fun startActivity(intent: Intent) = app.jam.desktop.shim.Intents.start(intent)
    open fun startActivity(intent: Intent, options: android.os.Bundle?) = startActivity(intent)
    open fun startService(intent: Intent): ComponentName? = app.jam.desktop.shim.Intents.service(intent)
    open fun startForegroundService(intent: Intent): ComponentName? = startService(intent)
    open fun stopService(intent: Intent): Boolean = true
    open fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean = false
    open fun unbindService(conn: ServiceConnection) {}
    open fun sendBroadcast(intent: Intent) = app.jam.desktop.shim.Intents.broadcast(intent)
    open fun sendBroadcast(intent: Intent, permission: String?) = sendBroadcast(intent)
    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? = app.jam.desktop.shim.Intents.register(receiver, filter)
    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = registerReceiver(receiver, filter)
    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, handler: android.os.Handler?): Intent? = registerReceiver(receiver, filter)
    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, handler: android.os.Handler?, flags: Int): Intent? = registerReceiver(receiver, filter)
    open fun unregisterReceiver(receiver: BroadcastReceiver?) = app.jam.desktop.shim.Intents.unregister(receiver)

    open fun getSystemService(name: String): Any? = Services.get(name)
    @Suppress("UNCHECKED_CAST")
    open fun <T> getSystemService(serviceClass: Class<T>): T = Services.get(serviceClass) as T
    open fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
    open fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
    open fun grantUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) {}
    open fun createConfigurationContext(c: android.content.res.Configuration): Context = this
    open fun obtainStyledAttributes(attrs: IntArray): android.content.res.TypedArray = android.content.res.TypedArray()

    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_APPEND = 0x8000
        const val MODE_MULTI_PROCESS = 4
        const val BIND_AUTO_CREATE = 1
        const val RECEIVER_EXPORTED = 2
        const val RECEIVER_NOT_EXPORTED = 4
        const val CLIPBOARD_SERVICE = "clipboard"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val AUDIO_SERVICE = "audio"
        const val POWER_SERVICE = "power"
        const val VIBRATOR_SERVICE = "vibrator"
        const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
        const val NOTIFICATION_SERVICE = "notification"
        const val WIFI_SERVICE = "wifi"
        const val ACTIVITY_SERVICE = "activity"
        const val UI_MODE_SERVICE = "uimode"
        const val NSD_SERVICE = "servicediscovery"
        const val BATTERY_SERVICE = "batterymanager"
        const val SEARCH_SERVICE = "search"
        const val WINDOW_SERVICE = "window"
        const val TELEPHONY_SERVICE = "phone"
        const val INPUT_METHOD_SERVICE = "input_method"
        const val MEDIA_SESSION_SERVICE = "media_session"
        const val STORAGE_SERVICE = "storage"
        const val DOWNLOAD_SERVICE = "download"
        const val KEYGUARD_SERVICE = "keyguard"
        const val DISPLAY_SERVICE = "display"
        const val ALARM_SERVICE = "alarm"
        const val LAYOUT_INFLATER_SERVICE = "layout_inflater"
        const val MEDIA_ROUTER_SERVICE = "media_router"
        const val BLUETOOTH_SERVICE = "bluetooth"
    }
}

open class ContextWrapper(base: Context?) : Context() {
    private val wrapped: Context? = base
    val baseContext: Context get() = wrapped ?: app.jam.desktop.shim.DesktopApp.context
}

interface ServiceConnection {
    fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?)
    fun onServiceDisconnected(name: ComponentName?)
}

data class ComponentName(val packageName: String, val className: String) {
    constructor(context: Context, cls: Class<*>) : this(context.packageName, cls.name)
    constructor(context: Context, className: String) : this(context.packageName, className)
    fun flattenToString(): String = "$packageName/$className"
    val shortClassName: String get() = className.substringAfterLast('.')
}


class IntentFilter() {
    val actions = ArrayList<String>()
    var priority: Int = 0
    constructor(action: String) : this() { actions += action }
    fun addAction(action: String) { actions += action }
    fun addCategory(c: String) {}
    fun addDataScheme(s: String) {}
    fun hasAction(a: String) = a in actions
    fun matchAction(a: String?) = a != null && a in actions
    fun countActions() = actions.size
    fun getAction(i: Int) = actions[i]
    companion object { const val SYSTEM_HIGH_PRIORITY = 1000 }
}

object ContentResolver {
    const val SCHEME_CONTENT = "content"
    const val SCHEME_FILE = "file"
    const val SCHEME_ANDROID_RESOURCE = "android.resource"
    fun openInputStream(uri: android.net.Uri): java.io.InputStream? = app.jam.desktop.shim.Files.open(uri)
    fun openOutputStream(uri: android.net.Uri): java.io.OutputStream? = app.jam.desktop.shim.Files.openOut(uri, false)
    fun openOutputStream(uri: android.net.Uri, mode: String): java.io.OutputStream? = app.jam.desktop.shim.Files.openOut(uri, mode.contains('a'))
    fun openFileDescriptor(uri: android.net.Uri, mode: String): android.os.ParcelFileDescriptor? = app.jam.desktop.shim.Files.file(uri)?.let { android.os.ParcelFileDescriptor(it, mode) }
    fun getType(uri: android.net.Uri): String? = app.jam.desktop.shim.Files.file(uri)?.let { java.nio.file.Files.probeContentType(it.toPath()) }
    fun takePersistableUriPermission(uri: android.net.Uri, flags: Int) {}
    fun releasePersistableUriPermission(uri: android.net.Uri, flags: Int) {}
    val persistedUriPermissions: List<UriPermission> get() = emptyList()
    fun query(uri: android.net.Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): android.database.Cursor? = null
    fun query(uri: android.net.Uri, projection: Array<String>?, queryArgs: android.os.Bundle?, cancellationSignal: Any?): android.database.Cursor? = null
    fun delete(uri: android.net.Uri, where: String?, selectionArgs: Array<String>?): Int = if (app.jam.desktop.shim.Files.file(uri)?.delete() == true) 1 else 0
    fun insert(uri: android.net.Uri, values: ContentValues?): android.net.Uri? = null
    fun update(uri: android.net.Uri, values: ContentValues?, where: String?, selectionArgs: Array<String>?): Int = 0
    fun notifyChange(uri: android.net.Uri, observer: Any?) {}
    fun registerContentObserver(uri: android.net.Uri, notifyForDescendants: Boolean, observer: android.database.ContentObserver) {}
    fun unregisterContentObserver(observer: android.database.ContentObserver) {}
}

class UriPermission(val uri: android.net.Uri, val isReadPermission: Boolean = true, val isWritePermission: Boolean = true)

class ContentValues {
    private val m = LinkedHashMap<String, Any?>()
    fun put(key: String, value: String?) { m[key] = value }
    fun put(key: String, value: Int?) { m[key] = value }
    fun put(key: String, value: Long?) { m[key] = value }
    fun put(key: String, value: Boolean?) { m[key] = value }
    fun get(key: String): Any? = m[key]
}

object ContentUris {
    fun withAppendedId(contentUri: android.net.Uri, id: Long): android.net.Uri = android.net.Uri.parse("$contentUri/$id")
    fun parseId(uri: android.net.Uri): Long = uri.lastPathSegment?.toLongOrNull() ?: -1
}

/** The clipboard: the PC's. */
class ClipboardManager {
    fun setPrimaryClip(clip: ClipData) {
        val text = clip.getItemAt(0).text?.toString() ?: return
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
    }
    val primaryClip: ClipData? get() = runCatching {
        val t = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
        t?.let { ClipData.newPlainText("", it) }
    }.getOrNull()
    fun hasPrimaryClip(): Boolean = primaryClip != null
    fun clearPrimaryClip() = setPrimaryClip(ClipData.newPlainText("", ""))
    fun addPrimaryClipChangedListener(l: OnPrimaryClipChangedListener) {}
    fun interface OnPrimaryClipChangedListener { fun onPrimaryClipChanged() }
}

class ClipData(val label: CharSequence?, private val items: List<Item>) {
    class Item(val text: CharSequence?) { val uri: android.net.Uri? get() = null; fun coerceToText(c: Context?): CharSequence = text ?: "" }
    fun getItemAt(i: Int) = items[i]
    val itemCount: Int get() = items.size
    var description: ClipDescription = ClipDescription()
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?) = ClipData(label, listOf(Item(text)))
        fun newRawUri(label: CharSequence?, uri: android.net.Uri) = ClipData(label, listOf(Item(uri.toString())))
    }
}
class ClipDescription { val extras: android.os.PersistableBundle? = null; var extrasValue: android.os.PersistableBundle? = null }
