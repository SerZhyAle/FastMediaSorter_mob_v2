package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for saving first frame of GIF animation as static PNG image
 * Saves to Downloads folder with naming: [original_name]_first_frame.png
 *
 * Writes through the shared MediaStore-aware local destination writer (S0527) so the
 * save succeeds under scoped storage on API 29+ instead of failing with EACCES on a
 * direct FileOutputStream into the public Downloads directory.
 */
class SaveGifFirstFrameUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
    private val statsSink: StatsSink,
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
            
            val baseFileName = gifFile.nameWithoutExtension
            val outputFileName = "${baseFileName}_first_frame.png"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetPath = File(downloadsDir, outputFileName).absolutePath

            val category = destinationClassifier.classify(targetPath)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                gifDecoder.clear()
                return@withContext Result.failure(e)
            }
            try {
                // commit() flushes and closes the sink stream, so do not wrap it in use().
                firstFrame.compress(Bitmap.CompressFormat.PNG, 100, sink.outputStream)
            } catch (e: Exception) {
                sink.abort()
                gifDecoder.clear()
                return@withContext Result.failure(e)
            }
            val savedPath = sink.commit().getOrElse { e ->
                gifDecoder.clear()
                return@withContext Result.failure(e)
            }

            gifDecoder.clear()

            Timber.d("SaveGifFirstFrame: Saved to $savedPath")

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Pre-Q the writer lands a plain file the system has not indexed; on API 29+ the
                // MediaStore publish indexes it, so the manual scan is only needed on legacy storage.
                MediaStoreNotifier.notifyFile(context, savedPath, "save-frame")
            }

            statsSink.record(StatsEvent.GifFrameSaved)
            Result.success(savedPath)

        } catch (e: Exception) {
            Timber.e(e, "SaveGifFirstFrame: Failed to save first frame from $gifPath")
            Result.failure(e)
        }
    }
}
