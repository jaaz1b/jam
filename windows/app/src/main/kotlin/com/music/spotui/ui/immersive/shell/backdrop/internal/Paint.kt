/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Jam for Windows: the Skia (Compose for Desktop) side of this file, as the library has it for
 * its skiko targets; replaces the Android file of the same name (android-only.txt).
 */
package com.music.spotui.ui.immersive.shell.backdrop.internal

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import com.music.spotui.ui.immersive.shell.backdrop.RuntimeShader
import com.music.spotui.ui.immersive.shell.backdrop.asSkiaShaderBuilder
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

internal fun Paint.blur(radius: Float) {
    // Android's blur radius is about 1.73 sigma (BlurMaskFilter); Skia takes the sigma.
    this.skiaPaint.maskFilter = if (radius > 0f) MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius * 0.57735f + 0.5f, true) else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    this.skiaPaint.shader = runtimeShader?.asSkiaShaderBuilder()?.makeShader(null)
}
