package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * S1066: realises a 16:9 photo selection by cropping the full 4:3 sensor frame the capture delivered,
 * because the photo stream is always requested at 4:3 (see `CameraUseCaseFactory.PHOTO_ASPECT_RATIO`).
 *
 * S1478: shared by both capture routes - the camera screen and the headless edge-gesture shot. It used
 * to be a private method of the session manager, which left the headless path with no way to reach it;
 * copying it would have created a second implementation of the very geometry this ticket unifies.
 */
internal object CapturedPhotoAspectCropper {

    /**
     * Centre-crops a captured JPEG to the 16:9 result frame inside the full 4:3 sensor frame,
     * overwriting [file]. The sensor JPEG is stored landscape, so 16:9 (wider than 4:3) keeps the full
     * long edge and trims the short edge; the pixels stay in their stored orientation, so
     * TAG_ORIENTATION and the other tags remain valid and are re-applied via [restoreExif] (S0765).
     * A frame already at or below 16:9 is left untouched, and any failure keeps the saved photo -
     * wrong proportions beat losing the shot.
     */
    fun cropToSixteenNine(file: File) {
        runCatching {
            val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()

            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return
            val w = decoder.width
            val h = decoder.height
            val longEdge = maxOf(w, h)
            val shortEdge = minOf(w, h)
            val targetShort = (longEdge.toLong() * SIXTEEN_NINE_SHORT / SIXTEEN_NINE_LONG).toInt()
            if (targetShort >= shortEdge) {
                decoder.recycle()
                return
            }
            val rect = if (w >= h) {
                val top = (h - targetShort) / 2
                Rect(0, top, w, top + targetShort)
            } else {
                val left = (w - targetShort) / 2
                Rect(left, 0, left + targetShort, h)
            }
            val cropped = decoder.decodeRegion(rect, null)
            decoder.recycle()
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            cropped.recycle()
            originalExif?.let { restoreExif(it, file) }
        }.onFailure { Timber.w(it, "CapturedPhotoAspectCropper: aspect crop failed") }
    }

    private const val SIXTEEN_NINE_LONG = 16
    private const val SIXTEEN_NINE_SHORT = 9
}

/** S0765: JPEG quality every re-encode on the capture paths writes with. */
internal const val JPEG_QUALITY = 95

/**
 * S0765: re-applies the [source] EXIF tags onto [target] after a re-encode that drops metadata.
 * Orientation is copied unchanged (the crop did not physically rotate pixels), so a viewer rotates
 * the cropped shot exactly like the original full-frame capture. Top-level because it reads no state
 * of either caller - a pure tag copy between two files (S1185 decomposition).
 */
internal fun restoreExif(source: ExifInterface, target: File) {
    runCatching {
        val dest = ExifInterface(target.absolutePath)
        PRESERVED_EXIF_TAGS.forEach { tag ->
            source.getAttribute(tag)?.let { value -> dest.setAttribute(tag, value) }
        }
        dest.saveAttributes()
    }.onFailure { Timber.w(it, "CapturedPhotoAspectCropper: EXIF restore after crop failed") }
}

/**
 * S0765: EXIF tags carried across a re-encode so a cropped shot keeps the same rotation, capture time
 * and place as the original full-frame JPEG CameraX produced.
 */
private val PRESERVED_EXIF_TAGS = listOf(
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_SOFTWARE,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
)
