package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import com.sza.fastmediasorter.domain.stats.EditKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
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
    suspend fun execute(imagePath: String, direction: FlipDirection, recordStats: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists() || !file.canWrite()) {
                return@withContext Result.failure(Exception("File not found or not writable: $imagePath"))
            }

            // Read original bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(imagePath, options)
                ?: return@withContext Result.failure(Exception("Failed to decode image"))

            val matrix = Matrix().apply {
                when (direction) {
                    FlipDirection.HORIZONTAL -> {
                        // Flip horizontally (mirror on Y axis)
                        postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                    }
                    FlipDirection.VERTICAL -> {
                        // Flip vertically (mirror on X axis)
                        postScale(1f, -1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                    }
                }
            }

            val flippedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )

            // Read EXIF data before overwriting
            val exif = try {
                ExifInterface(imagePath)
            } catch (e: Exception) {
                Timber.w(e, "Failed to read EXIF from $imagePath")
                null
            }

            // Save flipped bitmap to file
            FileOutputStream(file).use { out ->
                @Suppress("DEPRECATION")
                val format = when (file.extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 95
                flippedBitmap.compress(format, quality, out)
            }

            // Preserve EXIF metadata
            exif?.let {
                try {
                    val newExif = ExifInterface(imagePath)
                    
                    // Copy all EXIF attributes
                    val attributes = listOf(
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
                    
                    attributes.forEach { tag ->
                        it.getAttribute(tag)?.let { value ->
                            newExif.setAttribute(tag, value)
                        }
                    }
                    
                    // Reset orientation to normal since we physically flipped the image
                    newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    
                    newExif.saveAttributes()
                    Timber.d("EXIF metadata preserved for $imagePath")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to preserve EXIF for $imagePath")
                }
            }

            // Cleanup bitmaps
            originalBitmap.recycle()
            flippedBitmap.recycle()

            Timber.d("Successfully flipped image $direction: $imagePath")
            MediaStoreNotifier.notifyFile(context, imagePath, "modification")
            if (recordStats) statsSink.record(StatsEvent.Edit(EditKind.IMAGE_EDIT))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to flip image: $imagePath")
            Result.failure(e)
        }
    }
}
