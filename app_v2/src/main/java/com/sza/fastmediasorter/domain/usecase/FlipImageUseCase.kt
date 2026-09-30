package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.domain.stats.EditKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.InPlaceFileReplacer
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Use case for flipping images horizontally or vertically
 * Preserves EXIF metadata
 */
class FlipImageUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // S0473/S0482: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
) {

    enum class FlipDirection {
        HORIZONTAL,
        VERTICAL
    }

    /**
     * Flip image in specified direction
     * @param imagePath absolute path to image file
     * @param direction flip direction (HORIZONTAL or VERTICAL)
     * @param recordStats S0482: record one IMAGE_EDIT metric on success. Disabled by
     *   NetworkImageEditUseCase, which counts once after the upload lands to avoid double-counting.
     * @return Result with success/failure
     */
    suspend fun execute(imagePath: String, direction: FlipDirection, recordStats: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (!file.exists() || !file.canWrite()) {
                    return@withContext Result.failure(Exception("File not found or not writable: $imagePath"))
                }
                flipInPlace(file, direction)
                Timber.d("Successfully flipped image $direction: $imagePath")
                MediaStoreNotifier.notifyFile(context, imagePath, "modification")
                if (recordStats) statsSink.record(StatsEvent.Edit(EditKind.IMAGE_EDIT))
                Result.success(Unit)
            } catch (e: OutOfMemoryError) {
                // An Error, not an Exception: without this arm a large photo crashes the caller.
                Timber.e(e, "Out of memory flipping image: $imagePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Failed to flip image: $imagePath")
                Result.failure(e)
            }
        }

    private fun flipInPlace(file: File, direction: FlipDirection) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var originalBitmap: Bitmap? = null
        var flippedBitmap: Bitmap? = null
        try {
            val decoded = BitmapFactory.decodeFile(file.path, options)
                ?: throw IOException("Failed to decode image")
            originalBitmap = decoded
            val matrix = flipMatrix(decoded, direction)
            val flipped = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            flippedBitmap = flipped
            // Two full-size bitmaps are the peak; drop the source before the encode allocates its buffers.
            if (flipped !== decoded) {
                decoded.recycle()
                originalBitmap = null
            }

            // Read before the replace: afterwards the path holds the re-encoded image without the tags.
            val exif = try {
                ExifInterface(file.path)
            } catch (e: IOException) {
                Timber.w(e, "Failed to read EXIF from ${file.path}")
                null
            }

            InPlaceFileReplacer.replaceWithBitmap(file, flipped, TEMP_TAG)
            exif?.let { copyExif(it, file.path) }
        } finally {
            originalBitmap?.takeUnless { it.isRecycled }?.recycle()
            flippedBitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    private fun flipMatrix(bitmap: Bitmap, direction: FlipDirection): Matrix = Matrix().apply {
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        when (direction) {
            FlipDirection.HORIZONTAL -> postScale(-1f, 1f, centerX, centerY)
            FlipDirection.VERTICAL -> postScale(1f, -1f, centerX, centerY)
        }
    }

    private fun copyExif(source: ExifInterface, imagePath: String) {
        try {
            val newExif = ExifInterface(imagePath)
            EXIF_TAGS_TO_KEEP.forEach { tag ->
                source.getAttribute(tag)?.let { value -> newExif.setAttribute(tag, value) }
            }
            // The pixels are physically flipped now, so any orientation tag would flip them again.
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            newExif.saveAttributes()
            Timber.d("EXIF metadata preserved for $imagePath")
        } catch (e: IOException) {
            Timber.w(e, "Failed to preserve EXIF for $imagePath")
        }
    }

    private companion object {
        const val TEMP_TAG = "flip"
        val EXIF_TAGS_TO_KEEP = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF
        )
    }
}
