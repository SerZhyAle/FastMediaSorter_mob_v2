package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import android.content.Context
import com.sza.fastmediasorter.util.gif.AnimatedGifEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject

/**
 * UseCase for changing GIF animation speed by adjusting frame delays
 * Uses AnimatedGifEncoder to rebuild GIF with new delays
 * 
 * Speed multiplier range: 0.25x (slower) to 4.0x (faster)
 * Original file is overwritten with new speed
 */
class ChangeGifSpeedUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val MIN_SPEED_MULTIPLIER = 0.25f  // 4x slower
        const val MAX_SPEED_MULTIPLIER = 4.0f   // 4x faster
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
            
            // Read original GIF data
            val originalGifData = gifFile.readBytes()
            
            // Decode GIF to get frames and delays
            val bitmapProvider = GifBitmapProvider(Glide.get(context).bitmapPool)
            val gifDecoder: GifDecoder = StandardGifDecoder(bitmapProvider)
            gifDecoder.read(originalGifData)
            
            val frameCount = gifDecoder.frameCount
            if (frameCount == 0) {
                gifDecoder.clear()
                return@withContext Result.failure(Exception("GIF file contains no frames"))
            }
            
            Timber.d("ChangeGifSpeed: Found $frameCount frames")
            
            // Extract all frames and calculate new delays
            val frames = mutableListOf<Bitmap>()
            val newDelays = mutableListOf<Int>()
            
            for (i in 0 until frameCount) {
                try {
                    val originalDelay = gifDecoder.getDelay(i)
                    val newDelay = (originalDelay / clampedSpeed).toInt().coerceAtLeast(10) // Min 10ms delay
                    
                    gifDecoder.advance()
                    val frameBitmap = gifDecoder.nextFrame
                    
                    if (frameBitmap != null) {
                        // Create copy of bitmap (decoder reuses bitmaps)
                        val frameCopy = frameBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        frames.add(frameCopy)
                        newDelays.add(newDelay)
                        
                        Timber.d("ChangeGifSpeed: Frame $i - original delay: ${originalDelay}ms, new delay: ${newDelay}ms")
                    } else {
                        Timber.w("ChangeGifSpeed: Frame $i is null, skipping")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ChangeGifSpeed: Failed to process frame $i")
                }
            }
            
            gifDecoder.clear()
            
            if (frames.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to extract any frames"))
            }
            
            // Create temporary file for new GIF
            // Note: GIF files are always downloaded to local cache before processing,
            // so parentFile should always work. But add safety check.
            val parentDir = gifFile.parentFile 
                ?: return@withContext Result.failure(Exception("Cannot access parent directory"))
            val tempFile = File(parentDir, "${gifFile.nameWithoutExtension}_temp.gif")
            
            // Encode new GIF with adjusted delays
            val encoder = AnimatedGifEncoder()
            encoder.start(FileOutputStream(tempFile))
            encoder.setRepeat(0) // Loop indefinitely (same as original GIF behavior)
            
            for (i in frames.indices) {
                encoder.setDelay(newDelays[i])
                encoder.addFrame(frames[i])
                
                // Release bitmap
                frames[i].recycle()
            }
            
            encoder.finish()
            
            // Determine output file location
            val outputFile = if (saveToDownloads) {
                // Save to Downloads with speed suffix
                val baseFileName = gifFile.nameWithoutExtension
                val speedFormatted = String.format(Locale.US, "%.1f", clampedSpeed).replace(".", "_")
                val outputFileName = "${baseFileName}_speed_${speedFormatted}x.gif"
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                File(downloadsDir, outputFileName)
            } else {
                // Overwrite original
                gifFile
            }
            
            // Save new GIF
            if (tempFile.exists() && tempFile.length() > 0) {
                if (saveToDownloads) {
                    // Copy temp to Downloads
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                } else {
                    // Replace original
                    gifFile.delete()
                    tempFile.renameTo(gifFile)
                }
                
                Timber.d("ChangeGifSpeed: Successfully changed speed to ${clampedSpeed}x, output: ${outputFile.absolutePath}")
                MediaStoreNotifier.notifyFile(context, outputFile.absolutePath, "gif-speed")
                Result.success(outputFile.absolutePath)
            } else {
                tempFile.delete()
                Result.failure(Exception("Failed to encode new GIF"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "ChangeGifSpeed: Failed to change speed for $gifPath")
            Result.failure(e)
        }
    }
}
