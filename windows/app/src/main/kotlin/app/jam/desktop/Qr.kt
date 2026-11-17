package app.jam.desktop

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Reads the QR code in a picture (a screenshot of a phone's Jam, a pass invite). */
object Qr {
    fun read(file: java.io.File): String? {
        val img = javax.imageio.ImageIO.read(file) ?: return null
        val px = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        val src = RGBLuminanceSource(img.width, img.height, px)
        return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(src))).text }.getOrNull()
    }
}
