package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaScannerConnection
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UseCase for extracting all frames from GIF animation to separate PNG files
 * Saves frames to Downloads folder with naming: [original_name]_frame_001.png
 */
class ExtractGifFramesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Extract all frames from GIF file and save to Downloads
     * @param gifPath absolute path to GIF file
     * @return Result with frame count or error
     */
    suspend fun execute(gifPath: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val gifFile = File(gifPath)
            if (!gifFile.exists() || !gifFile.canRead()) {
                return@withContext Result.failure(Exception("GIF file not found or not readable: $gifPath"))
            }

            Timber.d("ExtractGifFrames: Loading GIF from $gifPath")
            
            // Read GIF file data
            val gifData = gifFile.readBytes()
            
            // Create GIF decoder using Glide's GifDecoder
            val bitmapProvider = GifBitmapProvider(Glide.get(context).bitmapPool)
            val gifDecoder: GifDecoder = StandardGifDecoder(bitmapProvider)
            gifDecoder.read(gifData)
            
            val frameCount = gifDecoder.frameCount
            if (frameCount == 0) {
                return@withContext Result.failure(Exception("GIF file contains no frames"))
            }
            
            Timber.d("ExtractGifFrames: Found $frameCount frames")
            
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // List to collect created file paths for batched MediaStore scan
            val createdPaths = mutableListOf<String>()
            
            // Prepare output file name pattern
            val baseFileName = gifFile.nameWithoutExtension
            
            // Extract each frame
            var successCount = 0
            for (i in 0 until frameCount) {
                try {
                    gifDecoder.advance()
                    val frameBitmap = gifDecoder.nextFrame
                    
                    if (frameBitmap != null) {
                        // Create output file
                        val frameFileName = "${baseFileName}_frame_${String.format("%03d", i + 1)}.png"
                        val frameFile = File(downloadsDir, frameFileName)
                        
                        // Save frame as PNG
                        FileOutputStream(frameFile).use { fos ->
                            frameBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        
                        successCount++
                        createdPaths.add(frameFile.absolutePath)
                        Timber.d("ExtractGifFrames: Saved frame $i to ${frameFile.absolutePath}")
                    } else {
                        Timber.w("ExtractGifFrames: Frame $i is null")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ExtractGifFrames: Failed to extract frame $i")
                    // Continue with next frame
                }
            }
            
            // Clear decoder resources
            gifDecoder.clear()
            
            if (successCount == 0) {
                return@withContext Result.failure(Exception("Failed to extract any frames"))
            }
            
            Timber.d("ExtractGifFrames: Successfully extracted $successCount frames")
            
            // Batch scan created files
            if (createdPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    createdPaths.toTypedArray(),
                    null
                ) { _, uri ->
                    Timber.v("ExtractGifFrames: Scanned frame -> $uri")
                }
            }

            Result.success(successCount)
            
        } catch (e: Exception) {
            Timber.e(e, "ExtractGifFrames: Failed to extract frames from $gifPath")
            Result.failure(e)
        }
    }
}
