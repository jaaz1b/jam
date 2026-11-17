/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Jam for Windows: the Skia (Compose for Desktop) side of this file, as the library has it for
 * its skiko targets; replaces the Android file of the same name (android-only.txt).
 */
package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** AGSL is SkSL with a few names of Android's; the shaders here use the shared subset. */
fun RuntimeShader(shaderString: String): RuntimeShader = SkiaRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderString)))

fun RuntimeShader.asSkiaShaderBuilder(): RuntimeShaderBuilder = (this as SkiaRuntimeShader).builder

internal class SkiaRuntimeShader(val builder: RuntimeShaderBuilder) : RuntimeShader {
    override fun setFloatUniform(name: String, value: Float) = builder.uniform(name, value)
    override fun setFloatUniform(name: String, value1: Float, value2: Float) = builder.uniform(name, value1, value2)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) = builder.uniform(name, value1, value2, value3)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) = builder.uniform(name, value1, value2, value3, value4)
    override fun setFloatUniform(name: String, values: FloatArray) = builder.uniform(name, values)
    override fun setIntUniform(name: String, value: Int) = builder.uniform(name, value)
    override fun setIntUniform(name: String, value1: Int, value2: Int) = builder.uniform(name, value1, value2)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) = builder.uniform(name, value1, value2, value3)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) = builder.uniform(name, value1, value2, value3, value4)
    override fun setIntUniform(name: String, values: IntArray) = builder.uniform(name, values.map { it.toFloat() }.toFloatArray())
    override fun setColorUniform(name: String, color: Color) = builder.uniform(name, color.red, color.green, color.blue, color.alpha)
}

interface RuntimeShader {
    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)
    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)
    fun setColorUniform(name: String, color: Color)
}
