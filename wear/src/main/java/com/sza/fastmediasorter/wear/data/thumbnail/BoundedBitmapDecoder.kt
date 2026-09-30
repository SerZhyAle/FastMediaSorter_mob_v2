package com.sza.fastmediasorter.wear.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget

/**
 * S3797: decodes picture bytes the watch did not produce - embedded cover art, a phone-sent
 * thumbnail - without ever holding the full-resolution bitmap.
 *
 * A bounds-only pass reads the dimensions, a power-of-two sample size brings the decode close to
 * the cell edge, and the final downscale lands on it exactly. Decoding first and shrinking after
 * would allocate the whole picture, and a phone photo used as cover art is tens of megabytes of
 * pixels on a device with a few hundred megabytes of heap.
 */
object BoundedBitmapDecoder {

    /** Null when the bytes are not a picture BitmapFactory can read. */
    fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = WearThumbnailBudget.sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::downscale)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val edge = WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val ratio = edge.toFloat() / longest
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
