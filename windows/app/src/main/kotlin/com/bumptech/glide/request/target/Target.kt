package com.bumptech.glide.request.target
abstract class CustomTarget<T>(val width: Int = Int.MIN_VALUE, val height: Int = Int.MIN_VALUE) {
    @Volatile var cancelled = false
    abstract fun onResourceReady(resource: T, transition: com.bumptech.glide.request.transition.Transition<in T>?)
    abstract fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?)
    open fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {}
    open fun onLoadStarted(placeholder: android.graphics.drawable.Drawable?) {}
}
interface Target<T> { companion object { const val SIZE_ORIGINAL = Int.MIN_VALUE } }
