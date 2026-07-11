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
 * First successful decode fires [onDecoded] exactly once; later frames are ignored so a still-bound
 * camera cannot re-emit and cause a double import. The Y (luminance) plane alone is enough for QR,
 * so no colour conversion is done - the analyzer stays cheap on its dedicated executor.
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

    override fun analyze(image: ImageProxy) {
        if (decoded || image.format != ImageFormat.YUV_420_888) {
            image.close()
            return
        }
        try {
            val text = decode(image)
            if (text != null && !decoded) {
                decoded = true
                onDecoded(text)
            }
        } finally {
            reader.reset()
            image.close()
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
}
