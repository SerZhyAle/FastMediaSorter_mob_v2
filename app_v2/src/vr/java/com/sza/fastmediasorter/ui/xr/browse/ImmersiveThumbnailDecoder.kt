package com.sza.fastmediasorter.ui.xr.browse

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Debug
import com.bumptech.glide.Glide
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Decodes browse-grid thumbnails on demand under a hard heap budget.
 *
 * S0772 traced an OOM to decoding a ~7K-image folder with no accounting; on a Quest 3 LOW tier the
 * app heap is only ~512 MB, so a full browse page of full-res decodes can exhaust it. This decoder
 * caps the total bytes it hands out and skips (rather than crashes) once the budget is reached.
 *
 * S1116: video files route through [MediaMetadataRetriever] - Glide yields no frame for a bare local
 * video path, which is why the video-heavy VR test matrix rendered as grey placeholders. Images/GIF
 * stay on Glide's decode+centerCrop path.
 */
class ImmersiveThumbnailDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val budgetBytes: Long = Runtime.getRuntime().maxMemory() / DECODE_BUDGET_DIVISOR

    // Single-writer: decode() runs sequentially on the IO decode loop, so a plain @Volatile
    // accumulate (no read-modify-write across threads) is sufficient and keeps this lock-free.
    @Volatile
    private var accountedBytes: Long = 0L

    suspend fun decode(model: String, isVideo: Boolean, cellW: Int, cellH: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (accountedBytes + estimateBytes(cellW, cellH) > budgetBytes) {
                Timber.i("ImmersiveThumbnailDecoder: skip decode, budget %d B reached", budgetBytes)
                return@withContext null
            }
            val bitmap = if (isVideo) {
                decodeVideoFrame(model, cellW, cellH)
            } else {
                decodeImage(model, cellW, cellH)
            }
            bitmap?.let { accountedBytes += estimateBytes(it.width, it.height) }
            bitmap
        }

    private fun decodeImage(model: String, cellW: Int, cellH: Int): Bitmap? = runCatching {
        Glide.with(context)
            .asBitmap()
            .load(model)
            .override(cellW, cellH)
            .centerCrop()
            .submit()
            .get()
    }.getOrElse { error ->
        Timber.w(error, "ImmersiveThumbnailDecoder: image decode failed")
        null
    }

    // Mirrors VideoPosterExtractor: a native-heap guard, then the first sync frame centre-cropped to
    // the cell. ThumbnailUtils recycles the full-res frame so only the cell-sized bitmap survives.
    private fun decodeVideoFrame(model: String, cellW: Int, cellH: Int): Bitmap? {
        if (Debug.getNativeHeapFreeSize() < NATIVE_HEAP_LOW_THRESHOLD_BYTES) {
            Timber.i("ImmersiveThumbnailDecoder: skip video frame, native heap low")
            return null
        }
        val retriever = MediaMetadataRetriever()
        return runCatching {
            if (model.startsWith("content://") || model.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(model))
            } else {
                retriever.setDataSource(model)
            }
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let { ThumbnailUtils.extractThumbnail(it, cellW, cellH, ThumbnailUtils.OPTIONS_RECYCLE_INPUT) }
        }.getOrElse { error ->
            Timber.w(error, "ImmersiveThumbnailDecoder: video frame decode failed")
            null
        }.also {
            retriever.release()
        }
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

        // Below this native-heap headroom, skip the retriever decode rather than risk an OOM (S1116).
        private const val NATIVE_HEAP_LOW_THRESHOLD_BYTES = 50L * 1024 * 1024
    }
}
