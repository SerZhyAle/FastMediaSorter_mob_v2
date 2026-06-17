package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import timber.log.Timber
import java.io.File
import android.content.Context
import com.sza.fastmediasorter.domain.stats.EditKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UseCase for applying color filters to images: grayscale, sepia, negative
 * Uses Android ColorMatrix for efficient color transformations
 */
class ApplyImageFilterUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // S0473/S0482: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
) {

    enum class FilterType {
        GRAYSCALE,
        SEPIA,
        NEGATIVE
    }

    /**
     * @param recordStats S0482: record one IMAGE_EDIT metric on success. Disabled by
     *   NetworkImageEditUseCase, which counts once after the upload lands to avoid double-counting.
     */
    suspend fun execute(imagePath: String, filterType: FilterType, recordStats: Boolean = true): Result<Unit> {
        return try {
            // Applying filter
            
            val file = File(imagePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $imagePath"))
            }

            val originalBitmap = BitmapFactory.decodeFile(imagePath)
                ?: return Result.failure(Exception("Failed to decode image"))

            val filteredBitmap = when (filterType) {
                FilterType.GRAYSCALE -> applyGrayscale(originalBitmap)
                FilterType.SEPIA -> applySepia(originalBitmap)
                FilterType.NEGATIVE -> applyNegative(originalBitmap)
            }

            // Save to file
            FileOutputStream(file).use { out ->
                filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Cleanup
            if (filteredBitmap != originalBitmap) {
                filteredBitmap.recycle()
            }
            originalBitmap.recycle()

            // Filter applied successfully
            MediaStoreNotifier.notifyFile(context, imagePath, "modification")
            if (recordStats) statsSink.record(StatsEvent.Edit(EditKind.IMAGE_EDIT))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply filter: $filterType")
            Result.failure(e)
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // 0 = grayscale
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applySepia(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.set(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyNegative(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.set(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
