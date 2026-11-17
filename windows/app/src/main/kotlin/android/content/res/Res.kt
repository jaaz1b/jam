package android.content.res

import app.jam.desktop.shim.Res

class Resources private constructor() {
    fun getString(id: Int): String = Res.string(id)
    fun getString(id: Int, vararg args: Any?): String = Res.format(id, args)
    fun getText(id: Int): CharSequence = Res.string(id)
    fun getQuantityString(id: Int, quantity: Int): String = Res.plural(id, quantity)
    fun getQuantityString(id: Int, quantity: Int, vararg args: Any?): String = runCatching { String.format(Res.locale, Res.plural(id, quantity), *args) }.getOrElse { Res.plural(id, quantity) }
    fun getQuantityText(id: Int, quantity: Int): CharSequence = Res.plural(id, quantity)
    fun getStringArray(id: Int): Array<String> = Res.array(id).toTypedArray()
    fun getTextArray(id: Int): Array<CharSequence> = Res.array(id).toTypedArray()
    fun getIntArray(id: Int): IntArray = Res.array(id).mapNotNull { it.toIntOrNull() }.toIntArray()
    fun getColor(id: Int): Int = Res.color(id)
    fun getColor(id: Int, theme: Theme?): Int = Res.color(id)
    fun getDimension(id: Int): Float = Res.dimen(id) * displayMetrics.density
    fun getDimensionPixelSize(id: Int): Int = getDimension(id).toInt()
    fun getDimensionPixelOffset(id: Int): Int = getDimension(id).toInt()
    fun getInteger(id: Int): Int = Res.integer(id)
    fun getBoolean(id: Int): Boolean = Res.bool(id)
    fun getIdentifier(name: String, defType: String?, defPackage: String?): Int {
        val n = name.substringAfter(':'); val type = defType ?: n.substringBefore('/'); return Res.id(type, n.substringAfter('/'))
    }
    fun getResourceEntryName(id: Int): String = Res.name(id)?.second ?: throw NotFoundException("$id")
    fun getResourceName(id: Int): String = Res.name(id)?.let { "app.jam.android:${it.first}/${it.second}" } ?: throw NotFoundException("$id")
    fun getResourceTypeName(id: Int): String = Res.name(id)?.first ?: throw NotFoundException("$id")
    fun openRawResource(id: Int): java.io.InputStream = Res.file(id)?.let { Res.open(it) } ?: throw NotFoundException("$id")
    fun getDrawable(id: Int, theme: Theme?): android.graphics.drawable.Drawable? = android.graphics.drawable.Drawable.fromRes(id)
    val displayMetrics: android.util.DisplayMetrics get() = metrics
    val configuration: Configuration get() = Configuration.current
    @Suppress("DEPRECATION") fun updateConfiguration(c: Configuration?, m: android.util.DisplayMetrics?) {}
    class NotFoundException(msg: String? = null) : RuntimeException(msg)
    object Theme { fun obtainStyledAttributes(attrs: IntArray) = TypedArray(); fun resolveAttribute(id: Int, v: android.util.TypedValue?, b: Boolean) = false }
    companion object {
        val instance = Resources()
        private val metrics = android.util.DisplayMetrics().also {
            runCatching {
                val s = java.awt.Toolkit.getDefaultToolkit().screenSize
                it.widthPixels = s.width; it.heightPixels = s.height
            }
        }
        @JvmStatic fun getSystem(): Resources = instance
        const val ID_NULL = 0
    }
}

class Configuration() {
    @JvmField var orientation: Int = ORIENTATION_LANDSCAPE
    @JvmField var uiMode: Int = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL
    @JvmField var screenWidthDp: Int = 1280
    @JvmField var screenHeightDp: Int = 800
    @JvmField var smallestScreenWidthDp: Int = 800
    @JvmField var densityDpi: Int = 160
    @JvmField var fontScale: Float = 1f
    @JvmField var screenLayout: Int = SCREENLAYOUT_SIZE_XLARGE
    @JvmField var keyboard: Int = KEYBOARD_QWERTY
    @JvmField var navigation: Int = NAVIGATION_NONAV
    @JvmField var touchscreen: Int = TOUCHSCREEN_FINGER
    @JvmField var locale: java.util.Locale = Res.locale
    val locales: android.os.LocaleList get() = android.os.LocaleList(Res.locale)
    constructor(o: Configuration) : this() { orientation = o.orientation; uiMode = o.uiMode; screenWidthDp = o.screenWidthDp; screenHeightDp = o.screenHeightDp; fontScale = o.fontScale }
    fun setLocale(l: java.util.Locale?) { if (l != null) locale = l }
    fun setLocales(l: android.os.LocaleList?) {}
    val isNightModeActive: Boolean get() = true
    val isScreenRound: Boolean get() = false
    companion object {
        val current = Configuration()
        const val ORIENTATION_UNDEFINED = 0; const val ORIENTATION_PORTRAIT = 1; const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_MASK = 0x30; const val UI_MODE_NIGHT_NO = 0x10; const val UI_MODE_NIGHT_YES = 0x20; const val UI_MODE_NIGHT_UNDEFINED = 0
        const val UI_MODE_TYPE_MASK = 0x0f; const val UI_MODE_TYPE_NORMAL = 1; const val UI_MODE_TYPE_TELEVISION = 4; const val UI_MODE_TYPE_CAR = 3; const val UI_MODE_TYPE_WATCH = 6; const val UI_MODE_TYPE_DESK = 2
        const val SCREENLAYOUT_SIZE_MASK = 0x0f; const val SCREENLAYOUT_SIZE_LARGE = 3; const val SCREENLAYOUT_SIZE_XLARGE = 4; const val SCREENLAYOUT_SIZE_NORMAL = 2; const val SCREENLAYOUT_SIZE_SMALL = 1
        const val KEYBOARD_QWERTY = 2; const val KEYBOARD_NOKEYS = 1; const val NAVIGATION_NONAV = 1; const val NAVIGATION_DPAD = 2; const val TOUCHSCREEN_FINGER = 3; const val TOUCHSCREEN_NOTOUCH = 1
        const val SCREEN_WIDTH_DP_UNDEFINED = 0; const val SCREENLAYOUT_ROUND_YES = 0x200
    }
}

class TypedArray { fun getColor(i: Int, d: Int) = d; fun getDimension(i: Int, d: Float) = d; fun getResourceId(i: Int, d: Int) = d; fun getBoolean(i: Int, d: Boolean) = d; fun recycle() {}; fun close() {} }

object AssetManager {
    fun open(name: String): java.io.InputStream = AssetManager::class.java.classLoader.getResourceAsStream("assets/$name") ?: throw java.io.FileNotFoundException(name)
    fun open(name: String, mode: Int): java.io.InputStream = open(name)
    fun list(path: String): Array<String> = app.jam.desktop.shim.Res.files.size.let { emptyArray() }
    fun openFd(name: String): AssetFileDescriptor = throw java.io.FileNotFoundException(name)
    const val ACCESS_STREAMING = 2
}
class AssetFileDescriptor
class ColorStateList private constructor(val defaultColor: Int) { companion object { @JvmStatic fun valueOf(c: Int) = ColorStateList(c) } }
