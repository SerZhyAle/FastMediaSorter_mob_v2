package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * S1066: realises a narrower photo selection by centre-cropping the frame the capture delivered.
 *
 * S1478: shared by both capture routes - the camera screen and the headless edge-gesture shot. It used
 * to be a private method of the session manager, which left the headless path with no way to reach it;
 * copying it would have created a second implementation of the very geometry this ticket unifies.
 */
internal object CapturedPhotoAspectCropper {

    /**
     * Centre-crops a captured JPEG to [targetRatio] - the wanted long-edge / short-edge ratio -
     * overwriting [file]. The sensor JPEG is stored landscape, so a wider target keeps the full long
     * edge and trims the short edge; the pixels stay in their stored orientation, so TAG_ORIENTATION
     * and the other tags remain valid and are re-applied via [restoreExif] (S0765). A frame already
     * at or narrower than the target is left untouched, and any failure keeps the saved photo -
     * wrong proportions beat losing the shot.
     */
    fun cropToRatio(file: File, targetRatio: Float) {
        runCatching {
            val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()

            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return
            val w = decoder.width
            val h = decoder.height
            val crop = CaptureCropGeometry.cropRectForRatio(w, h, targetRatio)
            if (crop.right - crop.left == w && crop.bottom - crop.top == h) {
                decoder.recycle()
                return
            }
            val rect = Rect(crop.left, crop.top, crop.right, crop.bottom)
            val cropped = decoder.decodeRegion(rect, null)
            decoder.recycle()
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            cropped.recycle()
            originalExif?.let { restoreExif(it, file) }
        }.onFailure { Timber.w(it, "CapturedPhotoAspectCropper: aspect crop failed") }
    }

    /**
     * S0753: centre-crops a captured JPEG by [factor] and rescales it to the original size, matching
     * the saved photo to the digital (soft) zoom, overwriting [file].
     *
     * S1658: moved here from the session manager for the reason S1478 moved its sibling - the two
     * capture-path crops are one concern, and neither reads any session state. A crop keeps the pixels
     * in their stored orientation, so the snapshotted tags stay valid and are re-applied via
     * [restoreExif] (S0765): `Bitmap.compress` emits a bare JPEG, which used to drop
     * orientation/datetime/GPS from every digital-zoom shot.
     */
    fun cropCenter(file: File, factor: Float) {
        runCatching {
            val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()

            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return
            val w = decoder.width
            val h = decoder.height
            val crop = CaptureCropGeometry.centreCropByFactor(w, h, factor)
            val region = decoder.decodeRegion(Rect(crop.left, crop.top, crop.right, crop.bottom), null)
            decoder.recycle()
            val scaled = Bitmap.createScaledBitmap(region, w, h, true)
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (scaled != region) region.recycle()
            scaled.recycle()
            originalExif?.let { restoreExif(it, file) }
        }.onFailure { Timber.w(it, "CapturedPhotoAspectCropper: digital-zoom crop failed") }
    }

    /** The 16:9 target, for the callers whose selection is the ratio itself rather than the screen. */
    const val SIXTEEN_NINE = 16f / 9f

    /**
     * S1658: the shape of a screen as a long-edge / short-edge ratio, so a caller can ask for "crop to
     * what the viewfinder filled" without repeating the arithmetic. An unmeasured screen (either edge
     * zero) answers [SIXTEEN_NINE], which is the stream the full-screen selection is built from.
     */
    fun ratioOfScreen(width: Int, height: Int): Float {
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        return if (shortEdge > 0) longEdge.toFloat() / shortEdge.toFloat() else SIXTEEN_NINE
    }
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
