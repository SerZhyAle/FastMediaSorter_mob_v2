package com.sza.fastmediasorter.wear.ui.broadcast

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import timber.log.Timber
import java.util.EnumMap

/**
 * S2509: the descriptor payload as a square the listener's camera can read.
 *
 * A watch-side twin of `app_v2`'s `QrCodeEncoder` rather than a shared class: the two modules share no
 * source, and this half needs only the encode direction. Error-correction level L for the same reason
 * the phone gives it - it maximises capacity for a dense compressed payload, and a round watch face
 * has the least room of any screen in this project to spend on redundancy.
 *
 * A payload ZXing cannot encode answers `null` instead of throwing: the QR screen is a convenience on
 * top of a broadcast that is already running, and it must not be able to take that broadcast down.
 */
object WearQrCodeEncoder {

    fun encode(payload: String, sizePx: Int): Bitmap? = try {
        toBitmap(payload, sizePx)
    } catch (e: WriterException) {
        // The payload outgrew what a QR code of this size can carry. The address is still live and
        // still shareable by the paired phone, so the screen says so rather than the session failing.
        Timber.w(e, "Could not encode the broadcast descriptor as a QR code")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "The broadcast descriptor was rejected by the QR encoder")
        null
    }

    private fun toBitmap(payload: String, sizePx: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
        }
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
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

    /** One module of quiet zone. Any less and a camera loses the finder patterns against the bezel. */
    private const val QUIET_ZONE_MODULES = 1
}
