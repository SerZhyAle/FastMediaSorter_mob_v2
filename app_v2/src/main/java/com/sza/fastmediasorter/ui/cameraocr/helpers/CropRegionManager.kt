package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File

/**
 * S1923: ceiling for the long side of a decoded capture. Chosen well above what OCR needs - the crop
 * step still works on this bitmap - while keeping one copy in the tens of MB rather than hundreds.
 */
private const val MAX_DECODE_LONG_SIDE_PX = 4096

/**
 * Pure image geometry for the Camera-OCR crop step: decodes a captured photo with EXIF orientation
 * applied (so preview, OCR and the saved image stay consistent) and crops a bitmap to a normalized
 * selection rectangle. No Android view or coroutine dependencies - all math is side-effect free for
 * unit testing via [toPixelRect].
 */
class CropRegionManager {

    /**
     * S1923: decodes [file] with a ceiling on the pixel count instead of at whatever the sensor
     * produced.
     *
     * A full-resolution decode is unbounded by construction: a 108 Mpx capture is about 432 MB in
     * `ARGB_8888`, and the EXIF rotation below holds a second copy while it runs. On the reporter's
     * device that pair never finished - the screen sat on "Processing photo.." past three minutes
     * with the process alive, nothing in logcat and no crash, which is what allocation pressure of
     * that size looks like from the outside rather than a thrown failure.
     *
     * [MAX_DECODE_LONG_SIDE_PX] is deliberately generous: the crop step and OCR run on this bitmap,
     * so the ceiling is set far above what text recognition needs rather than at a thumbnail size.
     */
    private fun decodeWithinBudget(file: File): Bitmap? {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(path, bounds)
        } catch (e: Exception) {
            Timber.e(e, "CropRegionManager: Read bitmap bounds failed")
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
        }
        Timber.d("S1923: decode ${bounds.outWidth}x${bounds.outHeight} sample=${options.inSampleSize}")
        return try {
            BitmapFactory.decodeFile(path, options)
        } catch (e: OutOfMemoryError) {
            // Not an Exception, so the arm below would miss it and the flow would hang on its
            // loading state rather than report anything.
            Timber.e(e, "CropRegionManager: Decode bitmap ran out of memory")
            null
        } catch (e: Exception) {
            Timber.e(e, "CropRegionManager: Decode bitmap failed")
            null
        }
    }

    /**
     * Decodes [file] and rotates/flips it to display orientation per its EXIF tag.
     * Returns null when the file cannot be decoded.
     */
    fun loadOrientedBitmap(file: File): Bitmap? {
        val decoded = decodeWithinBudget(file) ?: return null

        val orientation = try {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            Timber.w(e, "CropRegionManager: Read EXIF orientation failed")
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = orientationMatrix(orientation) ?: return decoded
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it != decoded) decoded.recycle() }
        } catch (e: Exception) {
            Timber.e(e, "CropRegionManager: Apply orientation matrix failed")
            decoded
        }
    }

    /**
     * Crops [source] to [rect], whose left/top/right/bottom are normalized to [0f, 1f] relative to
     * the bitmap. Returns [source] unchanged when the rect already covers the full image.
     */
    fun cropToNormalizedRect(source: Bitmap, rect: RectF): Bitmap {
        val pixels = toPixelRect(source.width, source.height, rect)
        if (pixels.left == 0 && pixels.top == 0 &&
            pixels.width() == source.width && pixels.height() == source.height
        ) {
            return source
        }
        return Bitmap.createBitmap(source, pixels.left, pixels.top, pixels.width(), pixels.height())
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    companion object {
        /**
         * S1923: smallest power-of-two divisor that brings [longSide] to at most
         * [MAX_DECODE_LONG_SIDE_PX]. Pure function - the unit-test entry point for the decode budget.
         */
        fun sampleSizeFor(longSide: Int): Int {
            var sample = 1
            while (longSide / sample > MAX_DECODE_LONG_SIDE_PX) {
                sample *= 2
            }
            return sample
        }

        /** Minimum side, in pixels, that a cropped region is allowed to collapse to. */
        const val MIN_SIDE_PX = 1

        /**
         * Converts a normalized [rect] (each side in [0f, 1f]) into integer pixel bounds clamped
         * inside a [width] x [height] image, enforcing a minimum side of [MIN_SIDE_PX]. Pure
         * function - the unit-test entry point for the crop geometry.
         */
        fun toPixelRect(width: Int, height: Int, rect: RectF): Rect {
            val leftN = minOf(rect.left, rect.right).coerceIn(0f, 1f)
            val topN = minOf(rect.top, rect.bottom).coerceIn(0f, 1f)
            val rightN = maxOf(rect.left, rect.right).coerceIn(0f, 1f)
            val bottomN = maxOf(rect.top, rect.bottom).coerceIn(0f, 1f)

            var left = (leftN * width).toInt().coerceIn(0, width - MIN_SIDE_PX)
            var top = (topN * height).toInt().coerceIn(0, height - MIN_SIDE_PX)
            var right = (rightN * width).toInt().coerceIn(left + MIN_SIDE_PX, width)
            var bottom = (bottomN * height).toInt().coerceIn(top + MIN_SIDE_PX, height)

            // Re-clamp the origin if the min-side push moved the far edge to the image bound.
            left = left.coerceAtMost(right - MIN_SIDE_PX)
            top = top.coerceAtMost(bottom - MIN_SIDE_PX)

            return Rect(left, top, right, bottom)
        }
    }
}
