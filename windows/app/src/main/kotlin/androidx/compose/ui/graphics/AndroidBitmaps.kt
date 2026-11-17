package androidx.compose.ui.graphics

/** android.graphics.Bitmap ⇄ Compose's ImageBitmap, as on Android. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = image.toComposeImageBitmap()
fun ImageBitmap.asAndroidBitmap(): android.graphics.Bitmap = android.graphics.Bitmap.of(toAwtImage())
