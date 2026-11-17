package com.google.mlkit.vision.codescanner

import com.google.android.gms.tasks.Task

/**
 * The QR code scanner. A PC has no camera to point at a phone, so the code is read from a picture:
 * the screenshot or image file the user picks (zxing). Cancelled when no file is picked.
 */
object GmsBarcodeScanning {
    fun getClient(context: android.content.Context): GmsBarcodeScanner = GmsBarcodeScanner()
    fun getClient(context: android.content.Context, options: Any?): GmsBarcodeScanner = GmsBarcodeScanner()
}
class GmsBarcodeScanner {
    fun startScan(): Task<Barcode> {
        val t = com.google.android.gms.tasks.TaskCompletionSource<Barcode>()
        app.jam.desktop.shim.FilePicker.open(listOf("image/*")) { file ->
            if (file == null) { t.task.finish(null, null, true); return@open }
            Thread {
                val text = runCatching { app.jam.desktop.Qr.read(file) }.getOrNull()
                if (text != null) t.setResult(Barcode(text)) else t.setException(Exception("no QR code in the picture"))
            }.start()
        }
        return t.task
    }
}
class Barcode(val rawValue: String?) { val displayValue: String? get() = rawValue; val format: Int get() = 256 }
