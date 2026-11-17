package android.view

import android.content.Context

class ViewPropertyAnimator(private val v: View) { fun alpha(a: Float) = apply { v.alpha = a }; fun setDuration(d: Long) = this; fun withEndAction(r: Runnable) = apply { r.run() }; fun start() {}; fun cancel() {}; fun translationY(t: Float) = this; fun setInterpolator(i: Any?) = this }
class ViewTreeObserver { fun addOnGlobalLayoutListener(l: OnGlobalLayoutListener) {}; fun removeOnGlobalLayoutListener(l: OnGlobalLayoutListener) {}; fun addOnPreDrawListener(l: Any?) {}; val isAlive = true
    fun interface OnGlobalLayoutListener { fun onGlobalLayout() } }

class MotionEvent private constructor(val action: Int, val x: Float, val y: Float) {
    val actionMasked: Int get() = action and 0xff
    val pointerCount: Int get() = 1
    fun recycle() {}
    companion object { const val ACTION_DOWN = 0; const val ACTION_UP = 1; const val ACTION_MOVE = 2; const val ACTION_CANCEL = 3; const val ACTION_SCROLL = 8
        @JvmStatic fun obtain(down: Long, at: Long, action: Int, x: Float, y: Float, meta: Int) = MotionEvent(action, x, y) }
}

class KeyEvent(val action: Int, val keyCode: Int) {
    var repeatCount: Int = 0
    var metaState: Int = 0
    val isLongPress: Boolean get() = false
    val isCanceled: Boolean get() = false
    val eventTime: Long get() = android.os.SystemClock.uptimeMillis()
    val downTime: Long get() = eventTime
    val isCtrlPressed: Boolean get() = metaState and META_CTRL_ON != 0
    val isShiftPressed: Boolean get() = metaState and META_SHIFT_ON != 0
    val isAltPressed: Boolean get() = metaState and META_ALT_ON != 0
    val unicodeChar: Int get() = 0
    fun startTracking() {}
    companion object {
        const val ACTION_DOWN = 0; const val ACTION_UP = 1; const val ACTION_MULTIPLE = 2
        const val META_SHIFT_ON = 1; const val META_ALT_ON = 2; const val META_CTRL_ON = 4096
        const val KEYCODE_UNKNOWN = 0; const val KEYCODE_BACK = 4; const val KEYCODE_DPAD_UP = 19; const val KEYCODE_DPAD_DOWN = 20; const val KEYCODE_DPAD_LEFT = 21; const val KEYCODE_DPAD_RIGHT = 22; const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_VOLUME_UP = 24; const val KEYCODE_VOLUME_DOWN = 25; const val KEYCODE_SPACE = 62; const val KEYCODE_ENTER = 66; const val KEYCODE_DEL = 67; const val KEYCODE_ESCAPE = 111; const val KEYCODE_TAB = 61
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85; const val KEYCODE_MEDIA_STOP = 86; const val KEYCODE_MEDIA_NEXT = 87; const val KEYCODE_MEDIA_PREVIOUS = 88; const val KEYCODE_MEDIA_REWIND = 89; const val KEYCODE_MEDIA_FAST_FORWARD = 90
        const val KEYCODE_MEDIA_PLAY = 126; const val KEYCODE_MEDIA_PAUSE = 127; const val KEYCODE_MEDIA_SKIP_FORWARD = 272; const val KEYCODE_MEDIA_SKIP_BACKWARD = 273; const val KEYCODE_HEADSETHOOK = 79
        const val KEYCODE_SEARCH = 84; const val KEYCODE_MENU = 82; const val KEYCODE_PAGE_UP = 92; const val KEYCODE_PAGE_DOWN = 93; const val KEYCODE_MOVE_HOME = 122; const val KEYCODE_MOVE_END = 123
        const val KEYCODE_MEDIA_RECORD = 130; const val KEYCODE_BUTTON_A = 96; const val KEYCODE_BUTTON_B = 97; const val KEYCODE_F = 34; const val KEYCODE_L = 40; const val KEYCODE_N = 42; const val KEYCODE_P = 44; const val KEYCODE_S = 47
        const val KEYCODE_MUTE = 91; const val KEYCODE_VOLUME_MUTE = 164; const val KEYCODE_GUIDE = 172; const val KEYCODE_INFO = 165
        @JvmStatic fun keyCodeToString(k: Int): String = "KEYCODE_$k"
    }
}

object HapticFeedbackConstants { const val LONG_PRESS = 0; const val VIRTUAL_KEY = 1; const val KEYBOARD_TAP = 3; const val CLOCK_TICK = 4; const val CONTEXT_CLICK = 6; const val CONFIRM = 16; const val REJECT = 17
    const val TOGGLE_ON = 21; const val TOGGLE_OFF = 22; const val SEGMENT_TICK = 26; const val SEGMENT_FREQUENT_TICK = 27; const val GESTURE_START = 12; const val GESTURE_END = 13; const val DRAG_START = 25; const val TEXT_HANDLE_MOVE = 9
    const val FLAG_IGNORE_VIEW_SETTING = 1; const val FLAG_IGNORE_GLOBAL_SETTING = 2 }

class WindowManager {
    val defaultDisplay: Display get() = Display.DEFAULT
    val currentWindowMetrics: WindowMetrics get() = WindowMetrics()
    val maximumWindowMetrics: WindowMetrics get() = WindowMetrics()
    class LayoutParams { @JvmField var flags = 0; @JvmField var preferredRefreshRate = 0f; @JvmField var screenBrightness = -1f; @JvmField var layoutInDisplayCutoutMode = 0; @JvmField var preferredDisplayModeId = 0; @JvmField var rotationAnimation = 0
        companion object { const val FLAG_KEEP_SCREEN_ON = 128; const val FLAG_SECURE = 8192; const val FLAG_FULLSCREEN = 1024; const val FLAG_LAYOUT_NO_LIMITS = 512; const val FLAG_TRANSLUCENT_NAVIGATION = 0x08000000
            const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1; const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3; const val BRIGHTNESS_OVERRIDE_NONE = -1f; const val ROTATION_ANIMATION_SEAMLESS = 3 } }
}
class WindowMetrics { val bounds: android.graphics.Rect get() = android.content.res.Resources.instance.displayMetrics.let { android.graphics.Rect(0, 0, it.widthPixels, it.heightPixels) } }
class WindowInsetsController { fun hide(t: Int) {}; fun show(t: Int) {}; var systemBarsBehavior = 0; fun setSystemBarsAppearance(a: Int, m: Int) {} }
class WindowInsets
class Display private constructor() {
    val refreshRate: Float get() = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate.toFloat().takeIf { it > 0 } }.getOrNull() ?: 60f
    val displayId: Int get() = 0
    val supportedModes: Array<Mode> get() = arrayOf(Mode())
    val mode: Mode get() = Mode()
    val rotation: Int get() = 0
    val isHdr: Boolean get() = false
    val state: Int get() = STATE_ON
    fun getRealMetrics(m: android.util.DisplayMetrics) {}
    fun getMetrics(m: android.util.DisplayMetrics) {}
    inner class Mode { val refreshRate: Float get() = this@Display.refreshRate; val modeId: Int get() = 1; val physicalWidth: Int get() = 1920; val physicalHeight: Int get() = 1080 }
    companion object { val DEFAULT = Display(); const val DEFAULT_DISPLAY = 0; const val STATE_ON = 2; const val STATE_OFF = 1 }
}
class Choreographer private constructor() {
    fun interface FrameCallback { fun doFrame(frameTimeNanos: Long) }
    fun postFrameCallback(cb: FrameCallback) { app.jam.desktop.shim.Desk.timer.schedule({ app.jam.desktop.shim.Desk.post { cb.doFrame(System.nanoTime()) } }, 16, java.util.concurrent.TimeUnit.MILLISECONDS) }
    fun removeFrameCallback(cb: FrameCallback) {}
    companion object { private val inst = Choreographer(); @JvmStatic fun getInstance() = inst }
}
open class Surface { fun release() {}; val isValid: Boolean get() = false }
open class SurfaceView(context: Context) : View(context)
open class TextureView(context: Context) : View(context)
object Gravity { const val CENTER = 17; const val TOP = 48; const val BOTTOM = 80; const val START = 8388611; const val END = 8388613; const val FILL = 119; const val CENTER_HORIZONTAL = 1; const val CENTER_VERTICAL = 16 }
/** Inflates a layout of res/layout: its root view, by the class its XML names (PlayerView, FrameLayout...). */
class LayoutInflater private constructor(private val context: android.content.Context) {
    fun inflate(id: Int, root: ViewGroup?): View = inflate(id, root, root != null)
    fun inflate(id: Int, root: ViewGroup?, attach: Boolean): View {
        val path = app.jam.desktop.shim.Res.file(id) ?: throw IllegalArgumentException("no layout $id")
        val xml = app.jam.desktop.shim.Res.open(path).use { it.readBytes().decodeToString() }
        val tag = Regex("<([A-Za-z][A-Za-z0-9_.]*)").findAll(xml).map { it.groupValues[1] }.first { !it.startsWith("?") && it != "merge" }
        val cls = when { '.' in tag -> tag; tag == "View" || tag == "ViewGroup" || tag == "SurfaceView" || tag == "TextureView" -> "android.view.$tag"; else -> "android.widget.$tag" }
        val v = Class.forName(cls).getConstructor(android.content.Context::class.java).newInstance(context) as View
        if (attach && root != null) root.addView(v)
        return v
    }
    companion object { @JvmStatic fun from(c: android.content.Context) = LayoutInflater(c) }
}
class LayoutInflaterUnused
object SoundEffectConstants { const val CLICK = 0 }
