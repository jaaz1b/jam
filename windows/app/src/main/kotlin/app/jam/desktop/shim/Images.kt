package app.jam.desktop.shim

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import okio.Path.Companion.toOkioPath
import java.io.File
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * Images (covers, avatars, the app's own drawables by id), for GlideImage and Glide's API: Coil 3
 * with a disk cache in Jam's cache folder. The data saver stops every network fetch, like on Android.
 */
object Images {
    val executor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r, "jam-images").apply { isDaemon = true } }
    private val http by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                if (runCatching { com.music.spotui.data.DataSaver.active }.getOrDefault(false)) throw java.io.IOException("data saver")
                chain.proceed(chain.request())
            }.build()
    }
    val loader: ImageLoader by lazy {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { http }))
                add(coil3.map.Mapper<android.net.Uri, String> { data, _ ->
                    if (data.scheme == "content") Files.file(data)?.absolutePath else data.toString().let { if (it.startsWith("file://")) it.removePrefix("file://") else it }
                })
                // content:// as text (the app keeps covers as strings): the file behind it.
                add(coil3.map.Mapper<String, String> { data, _ -> if (data.startsWith("content://")) Files.file(android.net.Uri.parse(data))?.absolutePath else null })
                add(coil3.map.Mapper<Int, String> { data, _ -> Res.file(data)?.let { p -> Res::class.java.classLoader.getResource("res/$p")?.toString() } })
            }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(256L * 1024 * 1024).build() }
            .diskCache { DiskCache.Builder().directory(File(Desk.cacheDir, "image_cache").toOkioPath()).maxSizeBytes(150L * 1024 * 1024).build() }
            .build()
    }
    fun request(model: Any?, size: Int): ImageRequest = ImageRequest.Builder(PlatformContext.INSTANCE).data(normalize(model)).apply { if (size > 0) size(size) }.build()
    private fun normalize(m: Any?): Any? = when (m) {
        is android.net.Uri -> m.toString().let { if (it.startsWith("file://")) File(java.net.URI(it)) else it }
        is String -> if (m.startsWith("file://")) runCatching { File(java.net.URI(m)) }.getOrDefault(m) else m
        else -> m
    }
    fun bitmap(model: Any?, w: Int, h: Int): android.graphics.Bitmap? {
        val req = ImageRequest.Builder(PlatformContext.INSTANCE).data(normalize(model)).apply { if (w > 0 && h > 0) size(w, h) }.build()
        val r = kotlinx.coroutines.runBlocking { loader.execute(req) } as? SuccessResult ?: return null
        val sk = (r.image as? coil3.BitmapImage)?.bitmap ?: return null
        return android.graphics.Bitmap.of(org.jetbrains.skia.Image.makeFromBitmap(sk).toComposeImageBitmap().toAwtImage())
    }
    /** The image's bytes on disk (downloading it first): the disk cache's file. */
    fun file(model: Any?): File {
        val m = normalize(model)
        if (m is File) return m
        val key = m.toString()
        val req = ImageRequest.Builder(PlatformContext.INSTANCE).data(m).build()
        kotlinx.coroutines.runBlocking { loader.execute(req) }
        loader.diskCache?.openSnapshot(key)?.use { return it.data.toFile() }
        // Not cached (a local resource): a temp copy.
        val f = File(Desk.cacheDir, "glide_" + Integer.toHexString(key.hashCode()))
        if (!f.exists()) java.net.URI(key).toURL().openStream().use { s -> f.outputStream().use { s.copyTo(it) } }
        return f
    }
    fun clearMemory() { loader.memoryCache?.clear() }
    fun clearDisk() { loader.diskCache?.clear() }
}
