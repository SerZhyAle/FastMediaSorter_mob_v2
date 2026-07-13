package com.sza.fastmediasorter.ui.xr.browse

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Decodes browse-grid thumbnails on demand under a hard heap budget.
 *
 * S0772 traced an OOM to decoding a ~7K-image folder with no accounting; on a Quest 3 LOW tier the
 * app heap is only ~512 MB, so a full browse page of full-res decodes can exhaust it. This decoder
 * caps the total bytes it hands out and skips (rather than crashes) once the budget is reached.
 */
class ImmersiveThumbnailDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val budgetBytes: Long = Runtime.getRuntime().maxMemory() / DECODE_BUDGET_DIVISOR

    // Single-writer: decode() runs sequentially on the IO decode loop, so a plain @Volatile
    // accumulate (no read-modify-write across threads) is sufficient and keeps this lock-free.
    @Volatile
    private var accountedBytes: Long = 0L

    suspend fun decode(model: Any, cellW: Int, cellH: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (accountedBytes + estimateBytes(cellW, cellH) > budgetBytes) {
            Timber.i("ImmersiveThumbnailDecoder: skip decode, budget %d B reached", budgetBytes)
            return@withContext null
        }
        val bitmap = runCatching {
            Glide.with(context)
                .asBitmap()
                .load(model)
                .override(cellW, cellH)
                .centerCrop()
                .submit()
                .get()
        }.getOrElse { error ->
            Timber.w(error, "ImmersiveThumbnailDecoder: decode failed")
            null
        }
        bitmap?.let { accountedBytes += estimateBytes(it.width, it.height) }
        bitmap
    }

    fun release() {
        // Glide targets from submit().get() are transient and already collectable; only the running
        // byte tally needs resetting so the next browse session starts from a clean budget.
        accountedBytes = 0L
    }

    private fun estimateBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * BYTES_PER_PIXEL

    companion object {
        // Cap thumbnails to 1/8 of maxMemory - a browse page must never own the whole heap.
        private const val DECODE_BUDGET_DIVISOR = 8L

        // ARGB_8888: 4 bytes per pixel.
        private const val BYTES_PER_PIXEL = 4L
    }
}
