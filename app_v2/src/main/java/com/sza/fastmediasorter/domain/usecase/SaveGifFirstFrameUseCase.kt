package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UseCase for saving first frame of GIF animation as static PNG image
 * Saves to Downloads folder with naming: [original_name]_first_frame.png
 */
class SaveGifFirstFrameUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Extract and save first frame from GIF as PNG
     * @param gifPath absolute path to GIF file
     * @return Result with output file path or error
     */
    suspend fun execute(gifPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val gifFile = File(gifPath)
            if (!gifFile.exists() || !gifFile.canRead()) {
                return@withContext Result.failure(Exception("GIF file not found or not readable: $gifPath"))
            }

            Timber.d("SaveGifFirstFrame: Loading GIF from $gifPath")
            
            // Read GIF file data
            val gifData = gifFile.readBytes()
            
            val bitmapProvider = GifBitmapProvider(Glide.get(context).bitmapPool)
            val gifDecoder: GifDecoder = StandardGifDecoder(bitmapProvider)
            gifDecoder.read(gifData)
            
            if (gifDecoder.frameCount == 0) {
                gifDecoder.clear()
                return@withContext Result.failure(Exception("GIF file contains no frames"))
            }
            
            // Get first frame
            gifDecoder.advance()
            val firstFrame = gifDecoder.nextFrame
            
            if (firstFrame == null) {
                gifDecoder.clear()
                return@withContext Result.failure(Exception("Failed to extract first frame"))
            }
            
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val baseFileName = gifFile.nameWithoutExtension
            val outputFileName = "${baseFileName}_first_frame.png"
            val outputFile = File(downloadsDir, outputFileName)
            
            FileOutputStream(outputFile).use { fos ->
                firstFrame.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            
            gifDecoder.clear()
            
            Timber.d("SaveGifFirstFrame: Saved to ${outputFile.absolutePath}")
            
            MediaStoreNotifier.notifyFile(context, outputFile.absolutePath, "save-frame")
            
            Result.success(outputFile.absolutePath)
            
        } catch (e: Exception) {
            Timber.e(e, "SaveGifFirstFrame: Failed to save first frame from $gifPath")
            Result.failure(e)
        }
    }
}
