package android.content.pm

import android.content.ComponentName
import android.content.Intent

/** The app's own package, as Android would describe it; other packages: none on the PC. */
class PackageManager private constructor() {
    fun getPackageInfo(pkg: String, flags: Int): PackageInfo = if (pkg == "app.jam.android") PackageInfo.self else throw NameNotFoundException(pkg)
    fun getPackageInfo(pkg: String, flags: PackageInfoFlags): PackageInfo = getPackageInfo(pkg, 0)
    fun getApplicationInfo(pkg: String, flags: Int): ApplicationInfo = if (pkg == "app.jam.android") ApplicationInfo.instance else throw NameNotFoundException(pkg)
    fun getLaunchIntentForPackage(pkg: String): Intent? = if (pkg == "app.jam.android") Intent(Intent.ACTION_MAIN) else null
    fun getLeanbackLaunchIntentForPackage(pkg: String): Intent? = null
    fun hasSystemFeature(f: String): Boolean = f == FEATURE_PC || f == "android.software.webview"
    fun queryIntentActivities(i: Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun queryIntentActivities(i: Intent, flags: ResolveInfoFlags): List<ResolveInfo> = emptyList()
    fun queryIntentServices(i: Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun resolveActivity(i: Intent, flags: Int): ResolveInfo? = null
    fun getInstalledPackages(flags: Int): List<PackageInfo> = listOf(PackageInfo.self)
    fun setComponentEnabledSetting(c: ComponentName, state: Int, flags: Int) {}
    fun getComponentEnabledSetting(c: ComponentName): Int = COMPONENT_ENABLED_STATE_DEFAULT
    fun canRequestPackageInstalls(): Boolean = false
    fun getPackagesForUid(uid: Int): Array<String>? = arrayOf("app.jam.android")
    fun getInstallSourceInfo(pkg: String): InstallSourceInfo = InstallSourceInfo()
    fun getNameForUid(uid: Int): String? = "app.jam.android"
    fun getApplicationLabel(info: ApplicationInfo): CharSequence = "Jam"
    fun getInstallerPackageName(pkg: String): String? = null
    val packageInstaller: PackageInstaller get() = PackageInstaller()
    class NameNotFoundException(m: String? = null) : Exception(m)
    class PackageInfoFlags private constructor() { companion object { @JvmStatic fun of(v: Long) = PackageInfoFlags() } }
    class ResolveInfoFlags private constructor() { companion object { @JvmStatic fun of(v: Long) = ResolveInfoFlags() } }
    companion object {
        val instance = PackageManager()
        const val PERMISSION_GRANTED = 0; const val PERMISSION_DENIED = -1
        const val GET_META_DATA = 128; const val GET_SIGNATURES = 64; const val GET_SIGNING_CERTIFICATES = 0x08000000; const val MATCH_DEFAULT_ONLY = 0x10000; const val MATCH_ALL = 0x20000
        const val COMPONENT_ENABLED_STATE_DEFAULT = 0; const val COMPONENT_ENABLED_STATE_ENABLED = 1; const val COMPONENT_ENABLED_STATE_DISABLED = 2; const val DONT_KILL_APP = 1
        const val FEATURE_LEANBACK = "android.software.leanback"; const val FEATURE_TELEVISION = "android.hardware.type.television"; const val FEATURE_AUTOMOTIVE = "android.hardware.type.automotive"
        const val FEATURE_WATCH = "android.hardware.type.watch"; const val FEATURE_PC = "android.hardware.type.pc"; const val FEATURE_TOUCHSCREEN = "android.hardware.touchscreen"
        const val FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture"; const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"; const val FEATURE_BLUETOOTH = "android.hardware.bluetooth"
    }
}
class PackageInfo { @JvmField var versionName: String? = System.getProperty("jpackage.app-version") ?: "dev"; @JvmField var packageName = "app.jam.android"
    @JvmField var firstInstallTime: Long = 0; @JvmField var lastUpdateTime: Long = 0; @JvmField var applicationInfo: ApplicationInfo? = ApplicationInfo.instance
    val longVersionCode: Long get() = 1; @Deprecated("") @JvmField var versionCode: Int = 1
    companion object { val self = PackageInfo() } }
class ApplicationInfo { @JvmField var packageName = "app.jam.android"; @JvmField var flags = 0; @JvmField var targetSdkVersion = 36; @JvmField var minSdkVersion = 29
    @JvmField var dataDir: String = app.jam.desktop.shim.Desk.dataDir.path; @JvmField var nativeLibraryDir: String = ""; @JvmField var sourceDir: String = ""; @JvmField var uid = 10000
    @JvmField var metaData: android.os.Bundle? = null
    companion object { val instance = ApplicationInfo(); const val FLAG_DEBUGGABLE = 2; const val FLAG_SYSTEM = 1 } }
class ResolveInfo { @JvmField var activityInfo: ActivityInfo? = null }
class ActivityInfo { @JvmField var packageName = ""; @JvmField var name = ""
    companion object { const val SCREEN_ORIENTATION_UNSPECIFIED = -1; const val SCREEN_ORIENTATION_PORTRAIT = 1; const val SCREEN_ORIENTATION_LANDSCAPE = 0
        const val SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6; const val SCREEN_ORIENTATION_USER = 2; const val SCREEN_ORIENTATION_FULL_USER = 13; const val SCREEN_ORIENTATION_LOCKED = 14
        const val SCREEN_ORIENTATION_SENSOR = 4; const val SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7; const val SCREEN_ORIENTATION_USER_LANDSCAPE = 11; const val SCREEN_ORIENTATION_USER_PORTRAIT = 12 } }
class PackageInstaller { fun abandonSession(id: Int) {}; fun createSession(p: SessionParams): Int = throw UnsupportedOperationException("no installs on the PC"); fun openSession(id: Int): Session = throw UnsupportedOperationException()
    class SessionParams(mode: Int) { companion object { const val MODE_FULL_INSTALL = 1; const val MODE_INHERIT_EXISTING = 2 } fun setAppPackageName(p: String?) {}; fun setSize(s: Long) {}; fun setRequireUserAction(r: Int) {}; fun setInstallReason(r: Int) {} }
    class Session : java.io.Closeable { fun openWrite(n: String, o: Long, l: Long): java.io.OutputStream = throw UnsupportedOperationException(); fun fsync(o: java.io.OutputStream) {}
        fun commit(s: android.content.IntentSender) {}; fun abandon() {}; override fun close() {} }
    companion object { const val EXTRA_STATUS = "android.content.pm.extra.STATUS"; const val EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE"; const val STATUS_SUCCESS = 0
        const val STATUS_PENDING_USER_ACTION = -1; const val STATUS_FAILURE = 1; const val STATUS_FAILURE_ABORTED = 3; const val STATUS_FAILURE_BLOCKED = 2; const val STATUS_FAILURE_INVALID = 4; const val STATUS_FAILURE_CONFLICT = 5; const val STATUS_FAILURE_STORAGE = 6; const val STATUS_FAILURE_INCOMPATIBLE = 7; const val EXTRA_SESSION_ID = "android.content.pm.extra.SESSION_ID"
        object SessionParamsModes; const val USER_ACTION_NOT_REQUIRED = 2 } }
class ShortcutManager
class InstallSourceInfo { val installingPackageName: String? = null; val initiatingPackageName: String? = null; val originatingPackageName: String? = null; val installingPackageSignature: Any? = null }
object ServiceInfo { const val FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2; const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1; const val FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 16; const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000; const val FOREGROUND_SERVICE_TYPE_SHORT_SERVICE = 2048 }
