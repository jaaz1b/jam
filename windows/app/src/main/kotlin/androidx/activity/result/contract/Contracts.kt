package androidx.activity.result.contract

import android.net.Uri
import app.jam.desktop.shim.FilePicker

abstract class ActivityResultContract<I, O> {
    /** The PC side: what the contract does here, the result handed to [done]. */
    abstract fun desktopLaunch(input: I, done: (O) -> Unit)
    open fun createIntent(context: android.content.Context, input: I): android.content.Intent = android.content.Intent()
    open fun parseResult(resultCode: Int, intent: android.content.Intent?): O = throw UnsupportedOperationException()
}

object ActivityResultContracts {
    class RequestPermission : ActivityResultContract<String, Boolean>() { override fun desktopLaunch(input: String, done: (Boolean) -> Unit) = done(true) }
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() { override fun desktopLaunch(input: Array<String>, done: (Map<String, Boolean>) -> Unit) = done(input.associateWith { true }) }
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() { override fun desktopLaunch(input: Array<String>, done: (Uri?) -> Unit) = FilePicker.open(input.toList()) { done(it?.let(Uri::fromFile)) } }
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() { override fun desktopLaunch(input: Array<String>, done: (List<Uri>) -> Unit) = FilePicker.openMany(input.toList()) { done(it.map(Uri::fromFile)) } }
    class GetContent : ActivityResultContract<String, Uri?>() { override fun desktopLaunch(input: String, done: (Uri?) -> Unit) = FilePicker.open(listOf(input)) { done(it?.let(Uri::fromFile)) } }
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() { override fun desktopLaunch(input: Uri?, done: (Uri?) -> Unit) = FilePicker.folder { done(it?.let(Uri::fromFile)) } }
    class CreateDocument(val mime: String = "*/*") : ActivityResultContract<String, Uri?>() { override fun desktopLaunch(input: String, done: (Uri?) -> Unit) = FilePicker.save(input) { done(it?.let(Uri::fromFile)) } }
    class StartActivityForResult : ActivityResultContract<android.content.Intent, androidx.activity.result.ActivityResult>() {
        override fun desktopLaunch(input: android.content.Intent, done: (androidx.activity.result.ActivityResult) -> Unit) { app.jam.desktop.shim.DesktopApp.context.startActivity(input); done(androidx.activity.result.ActivityResult(android.app.Activity.RESULT_CANCELED, null)) }
    }
    class StartIntentSenderForResult : ActivityResultContract<Any, androidx.activity.result.ActivityResult>() { override fun desktopLaunch(input: Any, done: (androidx.activity.result.ActivityResult) -> Unit) = done(androidx.activity.result.ActivityResult(android.app.Activity.RESULT_CANCELED, null)) }
    open class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        override fun desktopLaunch(input: androidx.activity.result.PickVisualMediaRequest, done: (Uri?) -> Unit) = FilePicker.open(listOf("image/*")) { done(it?.let(Uri::fromFile)) }
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        class SingleMimeType(val mimeType: String) : VisualMediaType
        companion object { @JvmStatic fun isPhotoPickerAvailable(): Boolean = true; @JvmStatic fun isPhotoPickerAvailable(c: android.content.Context): Boolean = true }
    }
    class TakePicturePreview : ActivityResultContract<Unit?, android.graphics.Bitmap?>() { override fun desktopLaunch(input: Unit?, done: (android.graphics.Bitmap?) -> Unit) = done(null) }
}
