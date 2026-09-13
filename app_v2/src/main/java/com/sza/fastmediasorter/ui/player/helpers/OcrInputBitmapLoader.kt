package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaFile
import java.io.File
import timber.log.Timber

/**
 * Decodes an image source for OCR without inheriting the player's display-size limit.
 *
 * The display bitmap remains the fallback for remote paths whose source cannot be resolved locally.
 */
internal object OcrInputBitmapLoader {
    private const val MAX_OCR_DIMENSION_PX = 2048

    fun load(context: Context, mediaFile: MediaFile?, displayBitmap: Bitmap): Bitmap {
        val sourceUri = mediaFile?.let(::sourceUri) ?: return displayBitmap.also(::logInput)
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, sourceUri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                boundedSize(info.size.width, info.size.height)?.let { (width, height) ->
                    decoder.setTargetSize(width, height)
                }
            }.also(::logInput)
        } catch (error: Exception) {
            Timber.w(error, "OCR source decode failed; using display bitmap")
            displayBitmap.also(::logInput)
        }
    }

    internal fun boundedSize(width: Int, height: Int): Pair<Int, Int>? {
        val longestSide = maxOf(width, height)
        if (longestSide <= MAX_OCR_DIMENSION_PX) return null

        val scale = MAX_OCR_DIMENSION_PX.toFloat() / longestSide
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private fun sourceUri(mediaFile: MediaFile): Uri? {
        val contentUri = mediaFile.contentUri?.takeIf { it.isNotBlank() }
        if (contentUri != null) return Uri.parse(contentUri)
        if (mediaFile.path.startsWith("content://")) return Uri.parse(mediaFile.path)

        val file = File(mediaFile.path)
        return file.takeIf(File::isFile)?.let(Uri::fromFile)
    }

    private fun logInput(bitmap: Bitmap) {
        Timber.d("S3088: OCR input ${bitmap.width}x${bitmap.height}")
    }
}
