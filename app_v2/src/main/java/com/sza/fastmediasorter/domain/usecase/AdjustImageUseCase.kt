package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.sza.fastmediasorter.domain.stats.EditKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.InPlaceFileReplacer
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for adjusting image properties: brightness, contrast, saturation
 * Uses Android ColorMatrix for efficient transformations
 */
class AdjustImageUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // S0473/S0482: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
) {

    data class Adjustments(
        val brightness: Float = 0f, // -100 to +100
        val contrast: Float = 1f, // 0.0 to 3.0 (1.0 = normal)
        val saturation: Float = 1f // 0.0 to 2.0 (1.0 = normal, 0.0 = grayscale)
    )

    /**
     * @param recordStats S0482: record one IMAGE_EDIT metric on success. Disabled by
     *   NetworkImageEditUseCase, which counts once after the upload lands to avoid double-counting.
     */
    suspend fun execute(imagePath: String, adjustments: Adjustments, recordStats: Boolean = true): Result<Unit> {
        return try {
            // Adjusting image

            val file = File(imagePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $imagePath"))
            }

            val originalBitmap = BitmapFactory.decodeFile(imagePath)
                ?: return Result.failure(Exception("Failed to decode image"))

            val adjustedBitmap = applyAdjustments(originalBitmap, adjustments)

            try {
                InPlaceFileReplacer.replaceWithBitmap(file, adjustedBitmap, "adjust")
            } finally {
                if (adjustedBitmap !== originalBitmap) adjustedBitmap.recycle()
                originalBitmap.recycle()
            }

            // Image adjustments applied
            MediaStoreNotifier.notifyFile(context, imagePath, "modification")
            if (recordStats) statsSink.record(StatsEvent.Edit(EditKind.IMAGE_EDIT))
            Result.success(Unit)
        } catch (e: OutOfMemoryError) {
            // An Error, not an Exception: without this arm a large photo crashes the caller.
            Timber.e(e, "Failed to adjust image")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust image")
            Result.failure(e)
        }
    }

    private fun applyAdjustments(source: Bitmap, adj: Adjustments): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val paint = Paint()
        val colorMatrix = ColorMatrix()

        // Apply adjustments in order: brightness -> contrast -> saturation

        // 1. Brightness adjustment
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, adj.brightness,
                0f, 1f, 0f, 0f, adj.brightness,
                0f, 0f, 1f, 0f, adj.brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        // 2. Contrast adjustment
        val contrastMatrix = ColorMatrix()
        val scale = adj.contrast
        val translate = (1f - scale) * 255f / 2f
        contrastMatrix.set(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        // 3. Saturation adjustment
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(adj.saturation)

        // Combine all matrices
        colorMatrix.postConcat(brightnessMatrix)
        colorMatrix.postConcat(contrastMatrix)
        colorMatrix.postConcat(saturationMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return result
    }
}
