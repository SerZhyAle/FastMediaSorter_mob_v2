package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Merges a transparent draw overlay bitmap onto a base image bitmap (S0107).
 *
 * Pure in-memory transformation — no file I/O.
 * The caller is responsible for writing the returned bytes to the appropriate DataSource.
 */
class MergeDrawOverlayUseCase @Inject constructor() {

    /**
     * Composites [overlayBitmap] onto [baseBitmap] and compresses the result.
     *
     * @param baseBitmap     Source image (read-only; a mutable ARGB_8888 copy is created internally).
     * @param overlayBitmap  Transparent draw layer produced by DrawCanvasView.
     * @param outputFormat   JPEG or PNG; caller selects based on original file extension.
     * @param quality        Compression quality (0–100, ignored for PNG).
     * @return               Compressed bytes on success, or failure with the caught exception.
     */
    suspend fun execute(
        baseBitmap: Bitmap,
        overlayBitmap: Bitmap,
        outputFormat: Bitmap.CompressFormat,
        quality: Int = 95
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Mutable ARGB_8888 copy of base image
            val merged = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)

            // 2. Composite overlay on top
            val canvas = Canvas(merged)
            canvas.drawBitmap(overlayBitmap, 0f, 0f, null)

            // 3. Compress to bytes
            val out = ByteArrayOutputStream()
            merged.compress(outputFormat, quality, out)
            merged.recycle()

            out.toByteArray()
        }
    }
}
