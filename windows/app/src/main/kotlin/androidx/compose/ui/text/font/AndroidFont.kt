package androidx.compose.ui.text.font

/** Android's Font(R.font.x): the TTF from res/font, loaded once. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
    val path = app.jam.desktop.shim.Res.file(resId) ?: error("font $resId")
    val bytes = app.jam.desktop.shim.Res.open(path).use { it.readBytes() }
    return androidx.compose.ui.text.platform.Font("$path-${weight.weight}-$style", bytes, weight, style)
}
fun Font(resId: Int, weight: FontWeight, style: FontStyle, loadingStrategy: FontLoadingStrategy): Font = Font(resId, weight, style)
fun Font(resId: Int, weight: FontWeight, style: FontStyle, variationSettings: FontVariation.Settings): Font = Font(resId, weight, style)
