package com.sza.fastmediasorter.wear.complication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * S3708: turns the delivered backdrop frame into the bytes a PHOTO_IMAGE complication carries.
 *
 * Complication data crosses a binder call, and a 480x480 ARGB bitmap icon is about 0.9 MB - close to
 * the transaction ceiling. A JPEG of the frame downsampled to the face's own edge is tens of KB, and
 * the face renderer decodes it on its side.
 */
object WearFacePhotoEncoder {

    /** The watch face is a 480x480 scene; a larger frame only costs the renderer memory. */
    const val FACE_EDGE_PX = 480

    private const val JPEG_QUALITY = 85
    private const val SAMPLE_STEP = 2

    /** Null when the file is not a decodable picture; the provider then answers with no data. */
    fun encode(frame: File): ByteArray? {
        val sampled = decodeSampled(frame)
        if (sampled == null) {
            Timber.w("Backdrop frame is not a decodable picture - the face gets no photo")
            return null
        }
        val bitmap = fitToFace(sampled)
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (bitmap !== sampled) bitmap.recycle()
            sampled.recycle()
        }
    }

    private fun decodeSampled(frame: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(frame.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(frame.path, options)
    }

    /**
     * Centre-cropped to a square: the face stretches the picture over its 480x480 slot, so a
     * non-square frame would be distorted there, while the app crops it the same way.
     */
    private fun fitToFace(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side == bitmap.width && side == bitmap.height && side <= FACE_EDGE_PX) return bitmap
        val matrix = Matrix()
        if (side > FACE_EDGE_PX) {
            val scale = FACE_EDGE_PX.toFloat() / side
            matrix.setScale(scale, scale)
        }
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side, matrix, true)
    }

    /** The largest power of two that keeps both edges at or above [FACE_EDGE_PX]. */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * SAMPLE_STEP) >= FACE_EDGE_PX && height / (sample * SAMPLE_STEP) >= FACE_EDGE_PX) {
            sample *= SAMPLE_STEP
        }
        return sample
    }
}
