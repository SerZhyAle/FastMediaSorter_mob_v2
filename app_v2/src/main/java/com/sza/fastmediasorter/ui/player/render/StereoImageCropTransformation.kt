package com.sza.fastmediasorter.ui.player.render

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.sza.fastmediasorter.domain.model.StereoMode
import java.security.MessageDigest

/**
 * Glide BitmapTransformation that crops a stereoscopic image to show
 * the left-eye frame only.
 *
 * - SBS (Side-by-Side): crops to the left 50% of the image width.
 * - OU (Over-Under): crops to the top 50% of the image height.
 * - MONO / AUTO: no-op passthrough (returns original bitmap).
 *
 * The cropped result is cached by Glide under a unique key that includes
 * the stereo mode, so switching modes triggers a new decode.
 */
class StereoImageCropTransformation(
    private val stereoMode: StereoMode
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val w = toTransform.width
        val h = toTransform.height

        return when (stereoMode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> {
                // Left eye = left half of the frame
                val halfW = w / 2
                if (halfW <= 0) toTransform
                else Bitmap.createBitmap(toTransform, 0, 0, halfW, h)
            }
            StereoMode.OU -> {
                // Left eye = top half of the frame
                val halfH = h / 2
                if (halfH <= 0) toTransform
                else Bitmap.createBitmap(toTransform, 0, 0, w, halfH)
            }
            else -> toTransform // MONO / AUTO / UNKNOWN — no crop
        }
    }

    override fun equals(other: Any?): Boolean =
        other is StereoImageCropTransformation && other.stereoMode == stereoMode

    override fun hashCode(): Int = ID.hashCode() + stereoMode.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + stereoMode.name).toByteArray(CHARSET))
    }

    companion object {
        private const val ID = "com.sza.fastmediasorter.StereoImageCropTransformation"
        private val CHARSET = Charsets.UTF_8
    }
}
