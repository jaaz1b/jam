package android.graphics.drawable

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap

abstract class Drawable {
    open val intrinsicWidth: Int get() = -1
    open val intrinsicHeight: Int get() = -1
    var bounds: android.graphics.Rect = android.graphics.Rect()
    open var alpha: Int = 255
    fun setBounds(l: Int, t: Int, r: Int, b: Int) { bounds = android.graphics.Rect(l, t, r, b) }
    open fun draw(c: android.graphics.Canvas) {}
    fun setTint(c: Int) {}
    fun mutate(): Drawable = this
    /** How Compose draws it on the PC. */
    abstract fun painter(): Painter?
    companion object {
        fun fromRes(id: Int): Drawable? = (androidx.compose.ui.res.AndroidDrawables.load(id, androidx.compose.ui.unit.Density(1f)))?.let { ResDrawable(id) }
        @JvmStatic fun createFromStream(s: java.io.InputStream?, name: String?): Drawable? = android.graphics.BitmapFactory.decodeStream(s)?.let { BitmapDrawable(null, it) }
        @JvmStatic fun createFromPath(p: String): Drawable? = android.graphics.BitmapFactory.decodeFile(p)?.let { BitmapDrawable(null, it) }
    }
}
class ResDrawable(val id: Int) : Drawable() { override fun painter(): Painter? = (androidx.compose.ui.res.AndroidDrawables.load(id, androidx.compose.ui.unit.Density(1f)) as? androidx.compose.ui.graphics.ImageBitmap)?.let { BitmapPainter(it) } }
open class BitmapDrawable(res: android.content.res.Resources?, val bitmap: android.graphics.Bitmap) : Drawable() {
    override val intrinsicWidth: Int get() = bitmap.width
    override val intrinsicHeight: Int get() = bitmap.height
    override fun painter(): Painter = BitmapPainter(bitmap.image.toComposeImageBitmap())
}
class ColorDrawable(var color: Int = 0) : Drawable() { override fun painter(): Painter = ColorPainter(androidx.compose.ui.graphics.Color(color)) }
class GradientDrawable : Drawable() { var cornerRadius = 0f; fun setColor(c: Int) {}; var shape = 0; override fun painter(): Painter? = null }
class AnimatedVectorDrawable : Drawable() { override fun painter(): Painter? = null }
class Icon private constructor(val resId: Int, val bitmap: android.graphics.Bitmap?) {
    companion object {
        @JvmStatic fun createWithResource(c: android.content.Context, id: Int) = Icon(id, null)
        @JvmStatic fun createWithResource(pkg: String, id: Int) = Icon(id, null)
        @JvmStatic fun createWithBitmap(b: android.graphics.Bitmap) = Icon(0, b)
        @JvmStatic fun createWithAdaptiveBitmap(b: android.graphics.Bitmap) = Icon(0, b)
    }
}
