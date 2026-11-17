/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Jam for Windows: the Skia (Compose for Desktop) side of this file, as the library has it for
 * its skiko targets; replaces the Android file of the same name (android-only.txt).
 */
package com.music.spotui.ui.immersive.shell.backdrop.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.skiaImageFilter
import com.music.spotui.ui.immersive.shell.backdrop.RuntimeShader
import com.music.spotui.ui.immersive.shell.backdrop.asSkiaShaderBuilder
import org.jetbrains.skia.ImageFilter

internal fun RenderEffect?.chain(other: RenderEffect): RenderEffect =
    if (this != null) ImageFilter.makeCompose(other.skiaImageFilter, this.skiaImageFilter).asComposeRenderEffect() else other

internal fun RuntimeShaderEffect(runtimeShader: RuntimeShader, uniformShaderName: String): RenderEffect =
    ImageFilter.makeRuntimeShader(runtimeShader.asSkiaShaderBuilder(), uniformShaderName, null).asComposeRenderEffect()

internal fun ColorFilterEffect(renderEffect: RenderEffect?, colorFilter: ColorFilter): RenderEffect =
    ImageFilter.makeColorFilter(colorFilter.asSkiaColorFilter(), renderEffect?.skiaImageFilter, null).asComposeRenderEffect()
