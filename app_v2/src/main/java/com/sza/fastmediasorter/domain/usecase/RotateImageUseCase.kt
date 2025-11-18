package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Use case for rotating images by specified angle
 * Preserves EXIF metadata and adjusts orientation tag
 */
class RotateImageUseCase @Inject constructor() {

    /**
     * Rotate image by specified angle
     * @param imagePath absolute path to image file
     * @param angle rotation angle in degrees (90, 180, -90)
     * @return Result with success/failure
     */
    suspend fun execute(imagePath: String, angle: Float): Result<Unit> = withContext(Dispatchers.IO) {
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

            // Create rotation matrix
            val matrix = Matrix().apply {
                postRotate(angle)
            }

            // Create rotated bitmap
            val rotatedBitmap = Bitmap.createBitmap(
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

            // Save rotated bitmap to file
            FileOutputStream(file).use { out ->
                @Suppress("DEPRECATION")
                val format = when (file.extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 95
                rotatedBitmap.compress(format, quality, out)
            }

            // Preserve EXIF metadata and update orientation
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
                    
                    // Reset orientation to normal since we physically rotated the image
                    newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    
                    newExif.saveAttributes()
                    Timber.d("EXIF metadata preserved for $imagePath")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to preserve EXIF for $imagePath")
                }
            }

            // Cleanup bitmaps
            originalBitmap.recycle()
            rotatedBitmap.recycle()

            Timber.d("Successfully rotated image by $angle degrees: $imagePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate image: $imagePath")
            Result.failure(e)
        }
    }
}
