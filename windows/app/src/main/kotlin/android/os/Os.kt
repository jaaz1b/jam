package android.os

import app.jam.desktop.shim.Desk
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

interface Parcelable {
    fun describeContents(): Int = 0
    fun writeToParcel(dest: Parcel, flags: Int) {}
    interface Creator<T> { fun createFromParcel(source: Parcel): T; fun newArray(size: Int): Array<T?> }
    companion object { const val PARCELABLE_WRITE_RETURN_VALUE = 1 }
}
class Parcel { companion object { fun obtain() = Parcel() } fun recycle() {} fun writeString(s: String?) {} fun readString(): String? = null }

/** A key–value bag, as Android's. */
open class BaseBundle {
    protected val map = LinkedHashMap<String, Any?>()
    fun containsKey(k: String) = map.containsKey(k)
    fun remove(k: String) { map.remove(k) }
    fun keySet(): Set<String> = map.keys
    fun size() = map.size
    fun isEmpty() = map.isEmpty()
    fun clear() = map.clear()
    fun putAny(k: String, v: Any?) { map[k] = v }
    @Deprecated("") fun get(k: String): Any? = map[k]
    fun putString(k: String, v: String?) { map[k] = v }
    fun getString(k: String): String? = map[k] as? String
    fun getString(k: String, d: String): String = map[k] as? String ?: d
    fun putInt(k: String, v: Int) { map[k] = v }
    fun getInt(k: String, d: Int = 0) = (map[k] as? Number)?.toInt() ?: d
    fun putLong(k: String, v: Long) { map[k] = v }
    fun getLong(k: String, d: Long = 0) = (map[k] as? Number)?.toLong() ?: d
    fun putDouble(k: String, v: Double) { map[k] = v }
    fun getDouble(k: String, d: Double = 0.0) = (map[k] as? Number)?.toDouble() ?: d
    fun putBoolean(k: String, v: Boolean) { map[k] = v }
    fun getBoolean(k: String, d: Boolean = false) = map[k] as? Boolean ?: d
    fun putStringArray(k: String, v: Array<String>?) { map[k] = v }
    @Suppress("UNCHECKED_CAST") fun getStringArray(k: String): Array<String>? = map[k] as? Array<String>
}

class Bundle() : BaseBundle(), Parcelable, Cloneable {
    constructor(o: Bundle) : this() { map.putAll(o.map) }
    constructor(capacity: Int) : this()
    fun putAll(o: Bundle) { map.putAll(o.map) }
    fun putFloat(k: String, v: Float) { map[k] = v }
    fun getFloat(k: String, d: Float = 0f) = (map[k] as? Number)?.toFloat() ?: d
    fun putCharSequence(k: String, v: CharSequence?) { map[k] = v }
    fun getCharSequence(k: String): CharSequence? = map[k] as? CharSequence
    fun putParcelable(k: String, v: Parcelable?) { map[k] = v }
    @Suppress("UNCHECKED_CAST") fun <T : Parcelable> getParcelable(k: String): T? = map[k] as? T
    @Suppress("UNCHECKED_CAST") fun <T> getParcelable(k: String, cls: Class<T>): T? = map[k] as? T
    fun putSerializable(k: String, v: java.io.Serializable?) { map[k] = v }
    fun getSerializable(k: String): java.io.Serializable? = map[k] as? java.io.Serializable
    @Suppress("UNCHECKED_CAST") fun <T : java.io.Serializable> getSerializable(k: String, cls: Class<T>): T? = map[k] as? T
    fun putBundle(k: String, v: Bundle?) { map[k] = v }
    fun getBundle(k: String): Bundle? = map[k] as? Bundle
    fun putByteArray(k: String, v: ByteArray?) { map[k] = v }
    fun getByteArray(k: String): ByteArray? = map[k] as? ByteArray
    fun putIntArray(k: String, v: IntArray?) { map[k] = v }
    fun getIntArray(k: String): IntArray? = map[k] as? IntArray
    fun putLongArray(k: String, v: LongArray?) { map[k] = v }
    fun getLongArray(k: String): LongArray? = map[k] as? LongArray
    fun putStringArrayList(k: String, v: ArrayList<String>?) { map[k] = v }
    @Suppress("UNCHECKED_CAST") fun getStringArrayList(k: String): ArrayList<String>? = map[k] as? ArrayList<String>
    fun putParcelableArrayList(k: String, v: ArrayList<out Parcelable>?) { map[k] = v }
    @Suppress("UNCHECKED_CAST") fun <T : Parcelable> getParcelableArrayList(k: String): ArrayList<T>? = map[k] as? ArrayList<T>
    public override fun clone(): Any = Bundle(this)
    fun deepCopy() = Bundle(this)
    companion object { val EMPTY = Bundle() }
}
class PersistableBundle() : BaseBundle() { constructor(o: PersistableBundle) : this() { map.putAll(o.map) } }

/** The main thread is Swing's event thread; other loopers are plain threads ([HandlerThread]). */
class Looper private constructor(val thread: Thread?) {
    private val queue: java.util.concurrent.LinkedBlockingQueue<Runnable>? = if (thread == null) null else java.util.concurrent.LinkedBlockingQueue()
    internal fun enqueue(r: Runnable) { if (queue == null) Desk.post(r) else queue.put(r) }
    internal fun loopForever() { val q = queue ?: return; while (!Thread.currentThread().isInterrupted) { try { q.take().run() } catch (_: InterruptedException) { return } catch (t: Throwable) { Desk.log('E', "Looper", "uncaught", t) } } }
    fun quit() { thread?.interrupt() }
    fun quitSafely() = quit()
    fun isCurrentThread(): Boolean = if (thread == null) Desk.isMain() else Thread.currentThread() === thread
    companion object {
        private val main = Looper(null)
        private val mine = ThreadLocal<Looper?>()
        @JvmStatic fun getMainLooper(): Looper = main
        @JvmStatic fun myLooper(): Looper? = if (Desk.isMain()) main else mine.get()
        @JvmStatic fun prepare() { if (mine.get() == null) mine.set(Looper(Thread.currentThread())) }
        @JvmStatic fun loop() { mine.get()?.loopForever() }
    }
}

open class Handler(val looper: Looper) {
    constructor() : this(Looper.myLooper() ?: Looper.getMainLooper())
    constructor(looper: Looper, callback: Callback?) : this(looper) { this.callback = callback }
    private var callback: Callback? = null
    fun interface Callback { fun handleMessage(msg: Message): Boolean }
    private val pending = ConcurrentHashMap<Any, MutableList<ScheduledFuture<*>>>()
    private val NO_TOKEN = Any()

    open fun handleMessage(msg: Message) {}
    fun post(r: Runnable): Boolean { looper.enqueue(r); return true }
    fun postAtFrontOfQueue(r: Runnable): Boolean = post(r)
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean = schedule(r, NO_TOKEN, delayMillis)
    fun postDelayed(r: Runnable, token: Any?, delayMillis: Long): Boolean = schedule(r, token ?: NO_TOKEN, delayMillis)
    fun postAtTime(r: Runnable, uptimeMillis: Long): Boolean = schedule(r, NO_TOKEN, uptimeMillis - SystemClock.uptimeMillis())
    fun postAtTime(r: Runnable, token: Any?, uptimeMillis: Long): Boolean = schedule(r, token ?: NO_TOKEN, uptimeMillis - SystemClock.uptimeMillis())
    private fun schedule(r: Runnable, token: Any, delay: Long): Boolean {
        if (delay <= 0) return post(r)
        lateinit var f: ScheduledFuture<*>
        f = Desk.timer.schedule({ pending[r]?.remove(f); pending[token]?.remove(f); looper.enqueue(r) }, delay, TimeUnit.MILLISECONDS)
        pending.getOrPut(r) { java.util.Collections.synchronizedList(ArrayList()) } += f
        pending.getOrPut(token) { java.util.Collections.synchronizedList(ArrayList()) } += f
        return true
    }
    fun removeCallbacks(r: Runnable) { pending.remove(r)?.toList()?.forEach { it.cancel(false) } }
    fun removeCallbacks(r: Runnable, token: Any?) = removeCallbacks(r)
    fun removeCallbacksAndMessages(token: Any?) {
        if (token == null) { pending.values.flatMap { it.toList() }.forEach { it.cancel(false) }; pending.clear() }
        else pending.remove(token)?.toList()?.forEach { it.cancel(false) }
    }
    fun hasCallbacks(r: Runnable): Boolean = pending[r]?.isNotEmpty() == true
    fun sendMessage(m: Message): Boolean = post { dispatch(m) }
    fun sendEmptyMessage(what: Int): Boolean = sendMessage(Message().also { it.what = what })
    fun sendEmptyMessageDelayed(what: Int, delay: Long): Boolean = schedule({ dispatch(Message().also { it.what = what }) }, what, delay)
    fun sendMessageDelayed(m: Message, delay: Long): Boolean = schedule({ dispatch(m) }, m.what, delay)
    fun removeMessages(what: Int) { pending.remove(what)?.toList()?.forEach { it.cancel(false) } }
    fun hasMessages(what: Int): Boolean = pending[what]?.isNotEmpty() == true
    fun obtainMessage(what: Int): Message = Message().also { it.what = what; it.target = this }
    fun obtainMessage(what: Int, obj: Any?): Message = Message().also { it.what = what; it.obj = obj; it.target = this }
    private fun dispatch(m: Message) { if (callback?.handleMessage(m) != true) handleMessage(m) }
    companion object { @JvmStatic fun createAsync(looper: Looper): Handler = Handler(looper) }
}

class Message { var what = 0; var arg1 = 0; var arg2 = 0; var obj: Any? = null; var target: Handler? = null
    fun sendToTarget() { target?.sendMessage(this) }
    companion object { fun obtain() = Message(); fun obtain(h: Handler?, what: Int) = Message().also { it.target = h; it.what = what } } }

open class HandlerThread(name: String, priority: Int = 0) : Thread(name) {
    @Volatile private var lp: Looper? = null
    private val ready = java.util.concurrent.CountDownLatch(1)
    init { isDaemon = true }
    override fun run() { Looper.prepare(); lp = Looper.myLooper(); ready.countDown(); Looper.loop() }
    val looper: Looper get() { ready.await(); return lp!! }
    fun quit(): Boolean { lp?.quit(); return true }
    fun quitSafely(): Boolean = quit()
    val threadId: Int get() = id.toInt()
}

object SystemClock {
    private val start = System.nanoTime()
    @JvmStatic fun elapsedRealtime(): Long = (System.nanoTime() - start) / 1_000_000 + 10_000
    @JvmStatic fun elapsedRealtimeNanos(): Long = System.nanoTime() - start + 10_000_000_000
    @JvmStatic fun uptimeMillis(): Long = elapsedRealtime()
    @JvmStatic fun uptimeNanos(): Long = elapsedRealtimeNanos()
    @JvmStatic fun currentThreadTimeMillis(): Long = elapsedRealtime()
    @JvmStatic fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }
}

/** The "device": Windows on a PC, with Android's newest API level so every code path is the modern one. */
object Build {
    @JvmField val MODEL: String = System.getenv("COMPUTERNAME") ?: "PC"
    @JvmField val MANUFACTURER: String = "PC"
    @JvmField val BRAND: String = "Windows"
    @JvmField val DEVICE: String = "pc"
    @JvmField val PRODUCT: String = "jam-windows"
    @JvmField val HARDWARE: String = System.getProperty("os.arch") ?: "x86_64"
    @JvmField val BOARD: String = "pc"
    @JvmField val FINGERPRINT: String = "windows/jam"
    @JvmField val DISPLAY: String = System.getProperty("os.name") ?: "Windows"
    @JvmField val ID: String = "jam"
    @JvmField val TAGS: String = "release-keys"
    @JvmField val TYPE: String = "user"
    @JvmField val SUPPORTED_ABIS: Array<String> = arrayOf("x86_64")
    @JvmField val SOC_MODEL: String = "pc"
    @JvmField val SOC_MANUFACTURER: String = "pc"
    object VERSION {
        @JvmField val SDK_INT: Int = 36
        @JvmField val RELEASE: String = System.getProperty("os.version") ?: "10"
        @JvmField val INCREMENTAL: String = "0"
        @JvmField val CODENAME: String = "REL"
        @JvmField val SECURITY_PATCH: String = "2026-01-01"
    }
    object VERSION_CODES {
        const val KITKAT = 19; const val LOLLIPOP = 21; const val M = 23; const val N = 24; const val N_MR1 = 25; const val O = 26; const val O_MR1 = 27
        const val P = 28; const val Q = 29; const val R = 30; const val S = 31; const val S_V2 = 32; const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34; const val VANILLA_ICE_CREAM = 35; const val BAKLAVA = 36; const val CUR_DEVELOPMENT = 10000
    }
    @JvmStatic fun getSerial(): String = "jam"
}

object Process {
    const val THREAD_PRIORITY_AUDIO = -16; const val THREAD_PRIORITY_URGENT_AUDIO = -19; const val THREAD_PRIORITY_BACKGROUND = 10
    const val THREAD_PRIORITY_DEFAULT = 0; const val THREAD_PRIORITY_FOREGROUND = -2; const val THREAD_PRIORITY_DISPLAY = -4
    const val THREAD_PRIORITY_LOWEST = 19; const val THREAD_PRIORITY_MORE_FAVORABLE = -1; const val THREAD_PRIORITY_LESS_FAVORABLE = 1
    @JvmStatic fun myPid(): Int = ProcessHandle.current().pid().toInt()
    @JvmStatic fun myTid(): Int = Thread.currentThread().id.toInt()
    @JvmStatic fun myUid(): Int = 10000
    @JvmStatic fun setThreadPriority(p: Int) {}
    @JvmStatic fun setThreadPriority(tid: Int, p: Int) {}
    @JvmStatic fun getThreadPriority(tid: Int): Int = 0
    @JvmStatic fun killProcess(pid: Int) { if (pid == myPid()) kotlin.system.exitProcess(0) }
    @JvmStatic fun getStartUptimeMillis(): Long = 0
    @JvmStatic fun getStartElapsedRealtime(): Long = 0
    @JvmStatic fun is64Bit(): Boolean = true
}

class PowerManager {
    val isInteractive: Boolean get() = true
    val isPowerSaveMode: Boolean get() = false
    val isDeviceIdleMode: Boolean get() = false
    val currentThermalStatus: Int get() = 0
    fun isIgnoringBatteryOptimizations(pkg: String): Boolean = true
    fun newWakeLock(flags: Int, tag: String): WakeLock = WakeLock()
    fun addThermalStatusListener(l: OnThermalStatusChangedListener) {}
    fun removeThermalStatusListener(l: OnThermalStatusChangedListener) {}
    fun interface OnThermalStatusChangedListener { fun onThermalStatusChanged(status: Int) }
    class WakeLock { private var held = false
        fun acquire() { held = true }; fun acquire(timeout: Long) { held = true }; fun release() { held = false }
        val isHeld: Boolean get() = held; fun setReferenceCounted(v: Boolean) {} }
    companion object {
        const val PARTIAL_WAKE_LOCK = 1; const val SCREEN_DIM_WAKE_LOCK = 6; const val FULL_WAKE_LOCK = 26
        const val THERMAL_STATUS_NONE = 0; const val THERMAL_STATUS_LIGHT = 1; const val THERMAL_STATUS_MODERATE = 2
        const val THERMAL_STATUS_SEVERE = 3; const val THERMAL_STATUS_CRITICAL = 4
        const val ACTION_POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED"
    }
}

open class Vibrator { open fun hasVibrator() = false; open fun vibrate(e: VibrationEffect?) {}; open fun vibrate(ms: Long) {}; open fun cancel() {}; open fun hasAmplitudeControl() = false }
class VibratorManager { val defaultVibrator: Vibrator = Vibrator() }
class VibrationEffect private constructor() {
    companion object {
        const val DEFAULT_AMPLITUDE = -1; const val EFFECT_CLICK = 0; const val EFFECT_TICK = 2; const val EFFECT_HEAVY_CLICK = 5; const val EFFECT_DOUBLE_CLICK = 1
        fun createOneShot(ms: Long, amp: Int) = VibrationEffect(); fun createPredefined(id: Int) = VibrationEffect()
        fun createWaveform(t: LongArray, r: Int) = VibrationEffect(); fun createWaveform(t: LongArray, a: IntArray, r: Int) = VibrationEffect()
        fun startComposition() = Composition()
    }
    class Composition { fun addPrimitive(p: Int): Composition = this; fun addPrimitive(p: Int, s: Float): Composition = this; fun addPrimitive(p: Int, s: Float, d: Int): Composition = this; fun compose() = VibrationEffect()
        companion object { const val PRIMITIVE_CLICK = 1; const val PRIMITIVE_TICK = 7; const val PRIMITIVE_LOW_TICK = 8; const val PRIMITIVE_THUD = 2; const val PRIMITIVE_SPIN = 3; const val PRIMITIVE_QUICK_RISE = 4; const val PRIMITIVE_SLOW_RISE = 5; const val PRIMITIVE_QUICK_FALL = 6 } }
}

class BatteryManager {
    fun getIntProperty(id: Int): Int = if (id == BATTERY_PROPERTY_CAPACITY) 100 else 0
    val isCharging: Boolean get() = true
    companion object { const val BATTERY_PROPERTY_CAPACITY = 4; const val EXTRA_LEVEL = "level"; const val EXTRA_SCALE = "scale"; const val EXTRA_PLUGGED = "plugged"; const val EXTRA_STATUS = "status"
        const val BATTERY_STATUS_CHARGING = 2; const val BATTERY_STATUS_FULL = 5; const val BATTERY_PLUGGED_AC = 1 }
}

class ParcelFileDescriptor(val file: File, val mode: String) : java.io.Closeable {
    val statSize: Long get() = file.length()
    val fileDescriptor: java.io.FileDescriptor get() = java.io.RandomAccessFile(file, if (mode.contains('w')) "rw" else "r").fd
    override fun close() {}
    fun detachFd(): Int = -1
    val fd: Int get() = -1
    class AutoCloseInputStream(p: ParcelFileDescriptor) : java.io.FileInputStream(p.file)
    class AutoCloseOutputStream(p: ParcelFileDescriptor) : java.io.FileOutputStream(p.file)
    companion object {
        const val MODE_READ_ONLY = 0x10000000; const val MODE_WRITE_ONLY = 0x20000000; const val MODE_READ_WRITE = 0x30000000; const val MODE_CREATE = 0x08000000; const val MODE_TRUNCATE = 0x04000000
        fun open(file: File, mode: Int) = ParcelFileDescriptor(file, if (mode and MODE_WRITE_ONLY != 0) "rw" else "r")
    }
}

interface IBinder
open class Binder : IBinder
class CancellationSignal { var isCanceled = false; private set; fun cancel() { isCanceled = true }; fun setOnCancelListener(l: OnCancelListener?) {}
    fun interface OnCancelListener { fun onCancel() } ; fun throwIfCanceled() { if (isCanceled) throw OperationCanceledException() } }
class OperationCanceledException : RuntimeException()

object Environment {
    const val DIRECTORY_MUSIC = "Music"; const val DIRECTORY_DOWNLOADS = "Download"; const val DIRECTORY_PICTURES = "Pictures"; const val DIRECTORY_DOCUMENTS = "Documents"; const val DIRECTORY_PODCASTS = "Podcasts"
    const val MEDIA_MOUNTED = "mounted"
    @JvmStatic fun getExternalStorageDirectory(): File = File(System.getProperty("user.home"))
    @JvmStatic fun getExternalStoragePublicDirectory(type: String): File = File(System.getProperty("user.home"), if (type == DIRECTORY_DOWNLOADS) "Downloads" else type)
    @JvmStatic fun getExternalStorageState(): String = MEDIA_MOUNTED
    @JvmStatic fun getDataDirectory(): File = Desk.dataDir
    @JvmStatic fun isExternalStorageManager(): Boolean = true
}

object Trace { @JvmStatic fun beginSection(n: String) {}; @JvmStatic fun endSection() {}; @JvmStatic fun isEnabled() = false
    @JvmStatic fun beginAsyncSection(n: String, c: Int) {}; @JvmStatic fun endAsyncSection(n: String, c: Int) {} }
object Debug { @JvmStatic fun isDebuggerConnected() = false; @JvmStatic fun getNativeHeapAllocatedSize(): Long = 0
    @JvmStatic fun getMemoryInfo(m: MemoryInfo) {}
    class MemoryInfo { fun getTotalPss() = 0; val totalPss = 0 } }
object StrictMode { @JvmStatic fun allowThreadDiskReads(): ThreadPolicy = ThreadPolicy(); @JvmStatic fun allowThreadDiskWrites(): ThreadPolicy = ThreadPolicy()
    @JvmStatic fun setThreadPolicy(p: ThreadPolicy) {}; @JvmStatic fun getThreadPolicy() = ThreadPolicy(); class ThreadPolicy }
class Looper2
object UserHandle { @JvmStatic fun myUserId() = 0 }
class StatFs(path: String) { private val f = File(path); val availableBytes: Long get() = f.usableSpace; val totalBytes: Long get() = f.totalSpace; val freeBytes: Long get() = f.freeSpace
    val blockSizeLong: Long get() = 4096; val availableBlocksLong: Long get() = f.usableSpace / 4096 }
class LocaleList(vararg val locales: java.util.Locale) { fun get(i: Int) = locales[i]; val size: Int get() = locales.size; fun isEmpty() = locales.isEmpty()
    fun toLanguageTags(): String = locales.joinToString(",") { it.toLanguageTag() }
    companion object { @JvmStatic fun getDefault() = LocaleList(java.util.Locale.getDefault()); @JvmStatic fun getAdjustedDefault() = getDefault()
        @JvmStatic fun forLanguageTags(t: String?) = LocaleList(*(t ?: "").split(',').filter { it.isNotBlank() }.map { java.util.Locale.forLanguageTag(it) }.toTypedArray())
        @JvmStatic fun getEmptyLocaleList() = LocaleList() } }
