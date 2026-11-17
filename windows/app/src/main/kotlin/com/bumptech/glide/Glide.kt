package com.bumptech.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import app.jam.desktop.shim.Images
import com.bumptech.glide.request.target.CustomTarget
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/** Glide's API, as the app uses it, over Coil 3 (app.jam.desktop.shim.Images): the same caches as the UI's covers. */
class Glide private constructor() {
    fun clearMemory() = Images.clearMemory()
    fun clearDiskCache() = Images.clearDisk()
    companion object {
        private val inst = Glide()
        @JvmStatic fun with(c: Context): RequestManager = RequestManager
        @JvmStatic fun with(v: android.view.View): RequestManager = RequestManager
        @JvmStatic fun get(c: Context): Glide = inst
        @JvmStatic fun init(c: Context, b: GlideBuilder) {}
    }
}
class GlideBuilder
enum class Priority { IMMEDIATE, HIGH, NORMAL, LOW }

object RequestManager {
    fun asBitmap(): RequestBuilder<Bitmap> = RequestBuilder(Bitmap::class.java)
    fun asDrawable(): RequestBuilder<Drawable> = RequestBuilder(Drawable::class.java)
    fun asFile(): RequestBuilder<File> = RequestBuilder(File::class.java)
    fun downloadOnly(): RequestBuilder<File> = RequestBuilder(File::class.java)
    fun load(model: Any?): RequestBuilder<Drawable> = asDrawable().load(model)
    fun clear(target: CustomTarget<*>?) { target?.cancelled = true }
}

class RequestBuilder<T>(private val type: Class<T>) {
    var model: Any? = null; private set
    var width = 0; private set
    var height = 0; private set
    fun load(m: Any?) = apply { model = m }
    fun override(size: Int) = apply { width = size; height = size }
    fun override(w: Int, h: Int) = apply { width = w; height = h }
    fun disallowHardwareConfig() = this
    fun centerCrop() = this
    fun fitCenter() = this
    fun circleCrop() = this
    fun dontAnimate() = this
    fun dontTransform() = this
    fun skipMemoryCache(b: Boolean) = this
    fun priority(p: Priority) = this
    fun onlyRetrieveFromCache(b: Boolean) = this
    fun error(m: Any?) = this
    fun placeholder(m: Any?) = this
    fun thumbnail(m: Any?) = this
    fun sizeMultiplier(m: Float) = this
    fun encodeQuality(q: Int) = this
    fun timeout(ms: Int) = this

    @Suppress("UNCHECKED_CAST")
    private fun fetch(w: Int, h: Int): T = when (type) {
        File::class.java -> Images.file(model) as T
        Bitmap::class.java -> (Images.bitmap(model, w, h) ?: throw java.io.IOException("no image for $model")) as T
        else -> android.graphics.drawable.BitmapDrawable(null, Images.bitmap(model, w, h) ?: throw java.io.IOException("no image for $model")) as T
    }
    fun submit(): Future<T> = submit(width, height)
    fun submit(w: Int, h: Int): Future<T> = CompletableFuture.supplyAsync({ fetch(w, h) }, Images.executor)
    fun preload() { Images.executor.execute { runCatching { Images.file(model) } } }
    fun preload(w: Int, h: Int) = preload()
    fun <Y : CustomTarget<T>> into(target: Y): Y {
        Images.executor.execute {
            val r = runCatching { fetch(width, height) }
            app.jam.desktop.shim.Desk.post {
                if (target.cancelled) return@post
                r.fold({ target.onResourceReady(it, null) }, { target.onLoadFailed(null) })
            }
        }
        return target
    }
}
