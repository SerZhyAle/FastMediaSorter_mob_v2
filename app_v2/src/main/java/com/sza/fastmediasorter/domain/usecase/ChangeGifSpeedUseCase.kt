package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.util.InPlaceFileReplacer
import com.sza.fastmediasorter.util.gif.AnimatedGifEncoder
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject

/**
 * UseCase for changing GIF animation speed by adjusting frame delays
 * Uses AnimatedGifEncoder to rebuild GIF with new delays
 * 
 * Speed multiplier range: 0.25x (slower) to 4.0x (faster)
 * The original is replaced only after the new GIF is fully written (InPlaceFileReplacer)
 */
class ChangeGifSpeedUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        const val MIN_SPEED_MULTIPLIER = 0.25f  // 4x slower
        const val MAX_SPEED_MULTIPLIER = 4.0f   // 4x faster
        private const val MIN_FRAME_DELAY_MS = 10
    }

    /**
     * Change GIF animation speed
     * @param gifPath absolute path to GIF file
     * @param speedMultiplier speed factor (0.25 = 4x slower, 4.0 = 4x faster)
     * @param saveToDownloads if true, saves to Downloads with suffix (for network files). If false, overwrites original
     * @return Result with output file path
     */
    suspend fun execute(
        gifPath: String, 
        speedMultiplier: Float,
        saveToDownloads: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate speed multiplier
            val clampedSpeed = speedMultiplier.coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER)
            
            val gifFile = File(gifPath)
            if (!gifFile.exists() || !gifFile.canWrite()) {
                return@withContext Result.failure(Exception("GIF file not found or not writable: $gifPath"))
            }

            Timber.d("ChangeGifSpeed: Loading GIF from $gifPath, speed multiplier: $clampedSpeed")
            
            val bitmapProvider = GifBitmapProvider(Glide.get(context).bitmapPool)
            val gifDecoder: GifDecoder = StandardGifDecoder(bitmapProvider)
            gifDecoder.read(gifFile.readBytes())
            
            if (gifDecoder.frameCount == 0) {
                gifDecoder.clear()
                return@withContext Result.failure(Exception("GIF file contains no frames"))
            }
            
            Timber.d("ChangeGifSpeed: Found ${gifDecoder.frameCount} frames")
            
            val outputFile = if (saveToDownloads) {
                val speedFormatted = String.format(Locale.US, "%.1f", clampedSpeed).replace(".", "_")
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                File(downloadsDir, "${gifFile.nameWithoutExtension}_speed_${speedFormatted}x.gif")
            } else {
                gifFile
            }

            // S3967: the original is swapped out only after the whole GIF is written; a GIF with no
            // frame written is reported as a failed write and leaves the original untouched.
            try {
                InPlaceFileReplacer.replace(outputFile, "speed") { out ->
                    // Not closed here: that would close [out] before the replacer syncs it.
                    reencodeFrames(gifDecoder, clampedSpeed, out.buffered()) > 0
                }
            } finally {
                gifDecoder.clear()
            }

            Timber.d("ChangeGifSpeed: changed speed to ${clampedSpeed}x, output: ${outputFile.absolutePath}")
            MediaStoreNotifier.notifyFile(context, outputFile.absolutePath, "gif-speed")
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            // An Error, not an Exception: without this branch a huge GIF crashes the caller.
            Timber.e(e, "ChangeGifSpeed: out of memory for $gifPath")
            Result.failure(IllegalStateException("Not enough memory to re-encode GIF", e))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "ChangeGifSpeed: Failed to change speed for $gifPath")
            Result.failure(e)
        }
    }

    /**
     * Re-encodes every frame of [decoder] into [out] with its delay divided by [speed], and returns
     * the number of frames written.
     *
     * Each frame goes to the encoder the moment it is decoded: `addFrame` reads the pixels at once
     * and keeps no reference, so the decoder may reuse its bitmap and memory stays at one frame
     * instead of growing with the frame count.
     */
    internal fun reencodeFrames(decoder: GifDecoder, speed: Float, out: OutputStream): Int {
        val encoder = AnimatedGifEncoder()
        encoder.start(out)
        encoder.setRepeat(0) // Loop indefinitely (same as original GIF behavior)
        var written = 0
        for (i in 0 until decoder.frameCount) {
            val originalDelay = decoder.getDelay(i)
            val newDelay = (originalDelay / speed).toInt().coerceAtLeast(MIN_FRAME_DELAY_MS)
            decoder.advance()
            val frame = decoder.nextFrame
            if (frame == null) {
                Timber.w("ChangeGifSpeed: Frame $i is null, skipping")
                continue
            }
            encoder.setDelay(newDelay)
            if (encoder.addFrame(frame)) written++
        }
        if (written > 0) encoder.finish()
        return written
    }
}
