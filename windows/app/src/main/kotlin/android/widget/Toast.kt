package android.widget

import android.content.Context

/** A Toast: shown at the bottom of Jam's window (app.jam.desktop.shim.Toasts). */
class Toast private constructor(private var text: CharSequence, private var duration: Int) {
    fun show() = app.jam.desktop.shim.Toasts.show(text.toString(), duration == LENGTH_LONG)
    fun cancel() {}
    fun setText(s: CharSequence) { text = s }
    fun setText(resId: Int) { text = app.jam.desktop.shim.Res.string(resId) }
    fun setDuration(d: Int) { duration = d }
    fun setGravity(g: Int, x: Int, y: Int) {}
    fun addCallback(c: Callback) {}
    abstract class Callback { open fun onToastShown() {}; open fun onToastHidden() {} }
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        @JvmStatic fun makeText(context: Context?, text: CharSequence?, duration: Int) = Toast(text ?: "", duration)
        @JvmStatic fun makeText(context: Context?, resId: Int, duration: Int) = Toast(app.jam.desktop.shim.Res.string(resId), duration)
    }
}
