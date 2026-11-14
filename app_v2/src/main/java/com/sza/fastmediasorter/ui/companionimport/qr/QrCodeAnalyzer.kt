package com.sza.fastmediasorter.ui.companionimport.qr

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

/**
 * S0988: decodes a companion QR payload from CameraX frames with the pure-JVM ZXing core.
 *
 * A text fires [onDecoded] exactly once, after the same text decodes in two consecutive frames; later
 * frames are ignored so a still-bound camera cannot re-emit and cause a double import. A frame that
 * decodes nothing, or something else, restarts the confirmation, and a text longer than
 * [MAX_PAYLOAD_LENGTH] is never one of ours (a QR symbol holds under 3 KiB), so it is dropped unread.
 * The Y (luminance) plane alone is enough for QR, so no colour conversion is done - the analyzer stays
 * cheap on its dedicated executor, which is also why [candidate] needs no synchronisation.
 */
class QrCodeAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            }
        )
    }

    @Volatile
    private var decoded = false

    private var candidate: String? = null

    override fun analyze(image: ImageProxy) {
        if (decoded || image.format != ImageFormat.YUV_420_888) {
            candidate = null
            image.close()
            return
        }
        try {
            accept(decode(image))
        } finally {
            reader.reset()
            image.close()
        }
    }

    /** The double-read and length guard, apart from the camera so it can be exercised without a frame. */
    internal fun accept(text: String?) {
        if (decoded) return
        if (text.isNullOrEmpty() || text.length > MAX_PAYLOAD_LENGTH) {
            candidate = null
        } else if (text == candidate) {
            decoded = true
            onDecoded(text)
        } else {
            candidate = text
        }
    }

    // ZXing signals "no QR in this frame" by throwing NotFoundException - it is control flow, not an
    // error, and it fires on most frames, so the swallow is intentional and must stay silent.
    @Suppress("SwallowedException")
    private fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val w = image.width
        val h = image.height
        // rowStride is the Y-plane stride (>= width); ZXing samples only [0,width) per row so the
        // padding tail is never read and the exact remaining() length stays in bounds.
        val source = PlanarYUVLuminanceSource(data, plane.rowStride, h, 0, 0, w, h, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (e: NotFoundException) {
            null
        }
    }

    companion object {
        const val MAX_PAYLOAD_LENGTH = 4096
    }
}
