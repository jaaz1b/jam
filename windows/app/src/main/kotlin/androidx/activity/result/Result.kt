package androidx.activity.result

abstract class ActivityResultLauncher<I> {
    fun launch(input: I) = launch(input, null)
    abstract fun launch(input: I, options: Any?)
    abstract fun unregister()
    abstract val contract: androidx.activity.result.contract.ActivityResultContract<I, *>
}
data class ActivityResult(val resultCode: Int, val data: android.content.Intent?)
class PickVisualMediaRequest(val mediaType: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo) {
    class Builder { private var t: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
        fun setMediaType(m: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType) = apply { t = m }; fun build() = PickVisualMediaRequest(t) }
}
interface ActivityResultCallback<O> { fun onActivityResult(result: O) }
interface ActivityResultCaller
