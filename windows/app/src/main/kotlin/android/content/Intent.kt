package android.content

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable

/** Android's Intent: an action, data, extras. On the PC some are carried out (see shim.Intents). */
open class Intent() : Cloneable, Parcelable {
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var flags: Int = 0
    var component: ComponentName? = null
    var `package`: String? = null
    val categories = LinkedHashSet<String>()
    var extras: Bundle? = null
        private set
    var clipData: ClipData? = null
    var selector: Intent? = null

    constructor(action: String?) : this() { this.action = action }
    constructor(action: String?, uri: Uri?) : this() { this.action = action; this.data = uri }
    constructor(context: Context, cls: Class<*>) : this() { component = ComponentName(context, cls) }
    constructor(action: String?, uri: Uri?, context: Context, cls: Class<*>) : this() { this.action = action; data = uri; component = ComponentName(context, cls) }
    constructor(o: Intent) : this() { action = o.action; data = o.data; type = o.type; flags = o.flags; component = o.component; extras = o.extras?.let { Bundle(it) } }

    private fun b(): Bundle = extras ?: Bundle().also { extras = it }
    fun setAction(a: String?): Intent { action = a; return this }
    fun setData(u: Uri?): Intent { data = u; return this }
    fun setDataAndType(u: Uri?, t: String?): Intent { data = u; type = t; return this }
    fun setType(t: String?): Intent { type = t; return this }
    fun setPackage(p: String?): Intent { `package` = p; return this }
    fun setClass(c: Context, cls: Class<*>): Intent { component = ComponentName(c, cls); return this }
    fun setClassName(pkg: String, cls: String): Intent { component = ComponentName(pkg, cls); return this }
    fun setClassName(c: Context, cls: String): Intent { component = ComponentName(c, cls); return this }
    fun setComponent(c: ComponentName?): Intent { component = c; return this }
    fun setFlags(f: Int): Intent { flags = f; return this }
    fun addFlags(f: Int): Intent { flags = flags or f; return this }
    fun addCategory(c: String): Intent { categories += c; return this }
    fun hasCategory(c: String) = c in categories
    fun putExtra(k: String, v: String?): Intent { b().putString(k, v); return this }
    fun putExtra(k: String, v: CharSequence?): Intent { b().putCharSequence(k, v); return this }
    fun putExtra(k: String, v: Int): Intent { b().putInt(k, v); return this }
    fun putExtra(k: String, v: Long): Intent { b().putLong(k, v); return this }
    fun putExtra(k: String, v: Float): Intent { b().putFloat(k, v); return this }
    fun putExtra(k: String, v: Double): Intent { b().putDouble(k, v); return this }
    fun putExtra(k: String, v: Boolean): Intent { b().putBoolean(k, v); return this }
    fun putExtra(k: String, v: Parcelable?): Intent { b().putParcelable(k, v); return this }
    fun putExtra(k: String, v: java.io.Serializable?): Intent { b().putSerializable(k, v); return this }
    fun putExtra(k: String, v: Array<String>?): Intent { b().putStringArray(k, v); return this }
    fun putExtra(k: String, v: Bundle?): Intent { b().putBundle(k, v); return this }
    fun putExtra(k: String, v: ByteArray?): Intent { b().putByteArray(k, v); return this }
    fun putExtras(e: Bundle?): Intent { e?.let { b().putAll(it) }; return this }
    fun putStringArrayListExtra(k: String, v: ArrayList<String>?): Intent { b().putStringArrayList(k, v); return this }
    fun removeExtra(k: String) { extras?.remove(k) }
    fun hasExtra(k: String) = extras?.containsKey(k) == true
    fun getStringExtra(k: String): String? = extras?.getString(k)
    fun getCharSequenceExtra(k: String): CharSequence? = extras?.getCharSequence(k)
    fun getIntExtra(k: String, d: Int) = extras?.getInt(k, d) ?: d
    fun getLongExtra(k: String, d: Long) = extras?.getLong(k, d) ?: d
    fun getFloatExtra(k: String, d: Float) = extras?.getFloat(k, d) ?: d
    fun getDoubleExtra(k: String, d: Double) = extras?.getDouble(k, d) ?: d
    fun getBooleanExtra(k: String, d: Boolean) = extras?.getBoolean(k, d) ?: d
    fun getByteArrayExtra(k: String): ByteArray? = extras?.getByteArray(k)
    fun getStringArrayExtra(k: String): Array<String>? = extras?.getStringArray(k)
    fun getStringArrayListExtra(k: String): ArrayList<String>? = extras?.getStringArrayList(k)
    fun getBundleExtra(k: String): Bundle? = extras?.getBundle(k)
    @Suppress("UNCHECKED_CAST") fun <T : Parcelable> getParcelableExtra(k: String): T? = extras?.getParcelable(k)
    fun <T> getParcelableExtra(k: String, cls: Class<T>): T? = extras?.getParcelable(k, cls)
    fun getSerializableExtra(k: String): java.io.Serializable? = extras?.getSerializable(k)
    fun <T : java.io.Serializable> getSerializableExtra(k: String, cls: Class<T>): T? = extras?.getSerializable(k, cls)
    fun resolveActivity(pm: android.content.pm.PackageManager): ComponentName? = component
    fun toUri(flags: Int): String = "intent:${data ?: ""}#Intent;action=$action;end"
    public override fun clone(): Any = Intent(this)
    override fun toString() = "Intent { act=$action dat=$data cmp=$component }"

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_SEARCH = "android.intent.action.SEARCH"
        const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
        const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
        const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
        const val ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT"
        const val ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val ACTION_CHOOSER = "android.intent.action.CHOOSER"
        const val ACTION_DIAL = "android.intent.action.DIAL"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
        const val ACTION_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED"
        const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
        const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
        const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"
        const val ACTION_TIME_TICK = "android.intent.action.TIME_TICK"
        const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val CATEGORY_HOME = "android.intent.category.HOME"
        const val CATEGORY_APP_MUSIC = "android.intent.category.APP_MUSIC"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_INTENT = "android.intent.extra.INTENT"
        const val EXTRA_KEY_EVENT = "android.intent.extra.KEY_EVENT"
        const val EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES"
        const val EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE"
        const val EXTRA_LOCAL_ONLY = "android.intent.extra.LOCAL_ONLY"
        const val EXTRA_INITIAL_INTENTS = "android.intent.extra.INITIAL_INTENTS"
        const val EXTRA_EXCLUDE_COMPONENTS = "android.intent.extra.EXCLUDE_COMPONENTS"
        const val EXTRA_CHOOSER_TARGETS = "android.intent.extra.CHOOSER_TARGETS"
        const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val EXTRA_REFERRER = "android.intent.extra.REFERRER"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TASK = 0x00008000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
        const val FLAG_ACTIVITY_NO_HISTORY = 0x40000000
        const val FLAG_ACTIVITY_NO_ANIMATION = 0x00010000
        const val FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000
        const val FLAG_ACTIVITY_NO_USER_ACTION = 0x00040000
        const val FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = 0x00800000
        const val FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000
        const val FLAG_ACTIVITY_REQUIRE_NON_BROWSER = 0x00000400
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x40
        const val FLAG_RECEIVER_FOREGROUND = 0x10000000
        const val FLAG_INCLUDE_STOPPED_PACKAGES = 0x20
        const val URI_INTENT_SCHEME = 1
        fun createChooser(target: Intent, title: CharSequence?): Intent = Intent(ACTION_CHOOSER).putExtra(EXTRA_INTENT, target as Parcelable?).putExtra(EXTRA_TITLE, title)
        fun createChooser(target: Intent, title: CharSequence?, sender: android.content.IntentSender?): Intent = createChooser(target, title)
        fun makeRestartActivityTask(c: ComponentName?): Intent = Intent(ACTION_MAIN).setComponent(c)
        fun makeMainActivity(c: ComponentName?): Intent = Intent(ACTION_MAIN).setComponent(c)
        fun parseUri(uri: String, flags: Int): Intent = Intent(ACTION_VIEW, Uri.parse(uri))
    }
}

class IntentSender
