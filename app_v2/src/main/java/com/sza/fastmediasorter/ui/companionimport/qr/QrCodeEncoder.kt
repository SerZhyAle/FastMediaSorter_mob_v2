package com.sza.fastmediasorter.ui.companionimport.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * S1039: encode-side mirror of [QrCodeAnalyzer] - turns a companion `.fmscfg` payload string into a
 * QR [BitMatrix] / [Bitmap] with the pure-JVM ZXing core. Stateless: no Hilt, no Android context on
 * the matrix path.
 *
 * Error-correction level L is deliberate - it maximises data capacity for the dense `FMSCFG1:`
 * payload; raising it shrinks capacity and can push a big config past scannability.
 */
object QrCodeEncoder {

    fun encodeToMatrix(payload: String, sizePx: Int): BitMatrix {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
        }
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    fun encode(payload: String, sizePx: Int): Bitmap {
        val matrix = encodeToMatrix(payload, sizePx)
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                row[x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }
}
