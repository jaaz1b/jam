package com.bumptech.glide.integration.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

@RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class ExperimentalGlideComposeApi

/** What a GlideImage shows while it loads or when it fails: a drawable, a painter or composable content. */
sealed class Placeholder {
    internal class Res(val id: Int) : Placeholder()
    internal class Paint(val painter: Painter) : Placeholder()
    internal class Content(val content: @Composable () -> Unit) : Placeholder()
    internal class Drawable(val d: android.graphics.drawable.Drawable?) : Placeholder()
    @Composable internal fun Show(modifier: Modifier, contentScale: ContentScale, alignment: Alignment, alpha: Float, colorFilter: ColorFilter?) {
        when (this) {
            is Res -> Image(painterResource(id), null, modifier, alignment, contentScale, alpha, colorFilter)
            is Paint -> Image(painter, null, modifier, alignment, contentScale, alpha, colorFilter)
            is Content -> content()
            is Drawable -> d?.painter()?.let { Image(it, null, modifier, alignment, contentScale, alpha, colorFilter) }
        }
    }
}
fun placeholder(resourceId: Int): Placeholder = Placeholder.Res(resourceId)
fun placeholder(painter: Painter): Placeholder = Placeholder.Paint(painter)
fun placeholder(drawable: android.graphics.drawable.Drawable?): Placeholder = Placeholder.Drawable(drawable)
fun placeholder(composable: @Composable () -> Unit): Placeholder = Placeholder.Content(composable)

interface Transition { interface Factory }
object CrossFade : Transition.Factory

@Composable
fun GlideImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    loading: Placeholder? = null,
    failure: Placeholder? = null,
    transition: Transition.Factory? = null,
    requestBuilderTransform: (com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable>) -> com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> = { it },
) {
    val size = requestBuilderTransform(com.bumptech.glide.RequestBuilder(android.graphics.drawable.Drawable::class.java)).width
    SubcomposeAsyncImage(
        model = app.jam.desktop.shim.Images.request(model, size),
        contentDescription = contentDescription,
        imageLoader = app.jam.desktop.shim.Images.loader,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
    ) {
        val state = painter.state.collectAsStateCompat()
        when (state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            is AsyncImagePainter.State.Error -> (failure ?: loading)?.Show(Modifier.matchParentSizeCompat(), contentScale, alignment, alpha, colorFilter)
            else -> loading?.Show(Modifier.matchParentSizeCompat(), contentScale, alignment, alpha, colorFilter)
        }
    }
}
@Composable private fun kotlinx.coroutines.flow.StateFlow<AsyncImagePainter.State>.collectAsStateCompat(): AsyncImagePainter.State = collectAsState().value
private fun Modifier.matchParentSizeCompat(): Modifier = fillMaxSize()
