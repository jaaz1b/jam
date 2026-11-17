package android.widget

open class LinearLayout(context: android.content.Context) : android.view.ViewGroup(context) { var orientation = 1; companion object { const val VERTICAL = 1; const val HORIZONTAL = 0 } }
open class ImageView(context: android.content.Context) : android.view.View(context) { fun setImageBitmap(b: android.graphics.Bitmap?) {}; fun setImageResource(r: Int) {} }
open class TextView(context: android.content.Context) : android.view.View(context) { var text: CharSequence? = null }
class RemoteViews(val packageName: String, val layoutId: Int) {
    fun setTextViewText(id: Int, t: CharSequence?) {}; fun setImageViewBitmap(id: Int, b: android.graphics.Bitmap?) {}; fun setImageViewResource(id: Int, r: Int) {}
    fun setOnClickPendingIntent(id: Int, p: android.app.PendingIntent?) {}; fun setViewVisibility(id: Int, v: Int) {}; fun setInt(id: Int, m: String, v: Int) {}; fun setProgressBar(id: Int, max: Int, p: Int, ind: Boolean) {}
    fun setContentDescription(id: Int, t: CharSequence?) {}; fun setFloat(id: Int, m: String, v: Float) {}; fun setColorStateList(id: Int, m: String, c: android.content.res.ColorStateList?) {}
}
