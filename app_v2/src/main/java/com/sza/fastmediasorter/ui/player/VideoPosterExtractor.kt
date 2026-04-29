package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.MediaMetadataRetriever
import android.os.Debug
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.browse.AdapterThumbnailLoader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Resilient poster-frame extraction for the dynamic-background plane behind the player.
 *
 * Three tiers, evaluated in order:
 *   1. Preventive skip (native heap low / ExoPlayer decoder busy) → straight to fallback.
 *   2. `MediaMetadataRetriever.getFrameAtTime` for the file's first sync frame.
 *   3. Fallback chain — Glide memory cache (Browse thumbnail) — added in phase 02.
 */
class VideoPosterExtractor {

    enum class Source { FRAME_AT_TIME, GLIDE_MEMORY, EXOPLAYER_LAST, PLACEHOLDER, NONE }

    enum class SkipReason { OOM, DECODER_BUSY, UNSUPPORTED, UNKNOWN }

    data class Result(
        val bitmap: Bitmap?,
        val source: Source,
        val reason: SkipReason?
    )

    @Volatile
    private var lastDeliveredBitmap: Bitmap? = null

    fun rememberDelivered(bitmap: Bitmap) {
        lastDeliveredBitmap = bitmap
    }

    fun reset() {
        lastDeliveredBitmap = null
    }

    suspend fun extract(context: Context, path: String, isPlayerBusy: Boolean): Result {
        val nativeFreeBytes = Debug.getNativeHeapFreeSize()
        Timber.d(
            "VideoPlayerManager: getFrameAtTime attempt path=$path " +
                "nativeFreeMb=${nativeFreeBytes / (1024 * 1024)} playerBusy=$isPlayerBusy"
        )
        if (isPlayerBusy) {
            return runFallback(context, path, SkipReason.DECODER_BUSY, isPreventiveSkip = true)
        }
        if (nativeFreeBytes < NATIVE_HEAP_LOW_THRESHOLD_BYTES) {
            return runFallback(context, path, SkipReason.OOM, isPreventiveSkip = true)
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                Timber.d(
                    "VideoPlayerManager: getFrameAtTime succeeded path=$path " +
                        "size=${bitmap.width}x${bitmap.height}"
                )
                return Result(bitmap, Source.FRAME_AT_TIME, null)
            }
            return runFallback(context, path, classifyNullReason(retriever), isPreventiveSkip = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: getFrameAtTime threw exception path=$path")
            return runFallback(context, path, SkipReason.UNKNOWN, isPreventiveSkip = false)
        } finally {
            retriever.release()
        }
    }

    private fun runFallback(
        context: Context,
        path: String,
        reason: SkipReason,
        isPreventiveSkip: Boolean
    ): Result {
        val cached = tryGlideMemoryCache(context, path)
        if (cached != null) {
            logFallback(path, reason, "glide-memory", isPreventiveSkip)
            return Result(cached, Source.GLIDE_MEMORY, reason)
        }
        lastDeliveredBitmap?.let {
            logFallback(path, reason, "exoplayer-last", isPreventiveSkip)
            return Result(it, Source.EXOPLAYER_LAST, reason)
        }
        logFallback(path, reason, "placeholder", isPreventiveSkip)
        return Result(buildPlaceholder(context), Source.PLACEHOLDER, reason)
    }

    private fun logFallback(path: String, reason: SkipReason, fallbackName: String, isPreventiveSkip: Boolean) {
        if (isPreventiveSkip) {
            Timber.d("VideoPlayerManager: getFrameAtTime skipped path=$path reason=$reason fallback=$fallbackName")
        } else {
            Timber.w("VideoPlayerManager: getFrameAtTime returned null path=$path reason=$reason fallback=$fallbackName")
        }
    }

    private fun buildPlaceholder(context: Context): Bitmap {
        cachedPlaceholder?.let { return it }
        val size = PLACEHOLDER_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PLACEHOLDER_BG_COLOR)
        ContextCompat.getDrawable(context, R.drawable.ic_video)?.let { drawable ->
            val iconSize = size / 3
            val left = (size - iconSize) / 2
            val top = (size - iconSize) / 2
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.colorFilter = PorterDuffColorFilter(PLACEHOLDER_ICON_COLOR, PorterDuff.Mode.SRC_IN)
            drawable.draw(canvas)
        }
        cachedPlaceholder = bitmap
        return bitmap
    }

    private fun tryGlideMemoryCache(context: Context, path: String): Bitmap? {
        val file = File(path)
        if (!file.canRead()) return null
        return try {
            Glide.with(context)
                .asBitmap()
                .load(file)
                .signature(ObjectKey("${path}_${file.length()}"))
                .override(AdapterThumbnailLoader.CACHED_THUMBNAIL_SIZE, AdapterThumbnailLoader.CACHED_THUMBNAIL_SIZE)
                .centerCrop()
                .onlyRetrieveFromCache(true)
                .submit()
                .get(50, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        }
    }

    private fun classifyNullReason(retriever: MediaMetadataRetriever): SkipReason {
        if (Debug.getNativeHeapFreeSize() < NATIVE_HEAP_LOW_THRESHOLD_BYTES) {
            return SkipReason.OOM
        }
        val mime = try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (_: Exception) {
            null
        }
        return if (mime == null) SkipReason.UNSUPPORTED else SkipReason.UNKNOWN
    }

    companion object {
        private const val NATIVE_HEAP_LOW_THRESHOLD_BYTES = 50L * 1024 * 1024
        private const val PLACEHOLDER_SIZE_PX = 256
        private val PLACEHOLDER_BG_COLOR = Color.rgb(0x22, 0x22, 0x22)
        private val PLACEHOLDER_ICON_COLOR = Color.rgb(0x88, 0x88, 0x88)
        @Volatile
        private var cachedPlaceholder: Bitmap? = null
    }
}
