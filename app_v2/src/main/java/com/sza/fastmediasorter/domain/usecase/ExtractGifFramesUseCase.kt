@file:Suppress("DEPRECATION") // android.graphics.Movie is deprecated (API 33+)
package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject

/**
 * UseCase for extracting animation frames to separate PNG files.
 * Supported inputs: GIF, WEBP, APNG.
 */
class ExtractGifFramesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) {

    /**
     * Extract all frames from an animated image and save to target directory.
     * @param animatedPath absolute path to local animated file
     * @param targetDirectoryPath optional output directory path; if null, source parent is used
     * @param isActive cancellation callback, should return false when operation must stop
     * @param onProgress optional callback with (current, total)
     */
    suspend fun execute(
        animatedPath: String,
        targetDirectoryPath: String? = null,
        isActive: () -> Boolean = { true },
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputFile = File(animatedPath)
            if (!inputFile.exists() || !inputFile.canRead()) {
                return@withContext Result.failure(Exception("Animation file not found or not readable: $animatedPath"))
            }

            val extension = inputFile.extension.lowercase(Locale.US)
            val outputDir = resolveOutputDirectory(inputFile, targetDirectoryPath)
            val baseFileName = inputFile.nameWithoutExtension

            Timber.d(
                "ExtractGifFrames: start ext=%s in=%s out=%s",
                extension,
                animatedPath,
                outputDir.absolutePath
            )

            val result = when (extension) {
                "gif" -> extractGifFrames(
                    inputFile = inputFile,
                    outputDir = outputDir,
                    baseFileName = baseFileName,
                    isActive = isActive,
                    onProgress = onProgress
                )
                "webp", "apng" -> extractMovieFrames(
                    inputFile = inputFile,
                    outputDir = outputDir,
                    baseFileName = baseFileName,
                    extension = extension,
                    isActive = isActive,
                    onProgress = onProgress
                )
                else -> Result.failure(Exception("Unsupported animated format: .$extension"))
            }
            // S0473: one frame-export completed; count carries the number of frames written.
            result.getOrNull()?.let { frameCount ->
                statsSink.record(StatsEvent.View(ViewKind.FRAME_EXPORT, count = frameCount.toLong()))
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ExtractGifFrames: failed for $animatedPath")
            Result.failure(e)
        }
    }

    fun isExtractionSupported(path: String): Boolean {
        val extension = File(path).extension.lowercase(Locale.US)
        return when (extension) {
            "gif" -> true
            "webp", "apng" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            else -> false
        }
    }

    private fun resolveOutputDirectory(inputFile: File, targetDirectoryPath: String?): File {
        val targetDir = targetDirectoryPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
            ?: inputFile.parentFile?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        if (!targetDir.exists() || !targetDir.isDirectory || !targetDir.canWrite()) {
            throw IllegalStateException("Cannot access output directory: ${targetDir.absolutePath}")
        }
        return targetDir
    }

    private fun extractGifFrames(
        inputFile: File,
        outputDir: File,
        baseFileName: String,
        isActive: () -> Boolean,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        val gifData = inputFile.readBytes()
        val bitmapProvider = GifBitmapProvider(Glide.get(context).bitmapPool)
        val gifDecoder: GifDecoder = StandardGifDecoder(bitmapProvider)
        gifDecoder.read(gifData)
        val createdPaths = mutableListOf<String>()

        val frameCount = gifDecoder.frameCount
        if (frameCount == 0) {
            gifDecoder.clear()
            return Result.failure(Exception("GIF file contains no frames"))
        }

        var successCount = 0
        for (index in 0 until frameCount) {
            if (!isActive()) {
                gifDecoder.clear()
                cleanupFiles(createdPaths)
                return Result.failure(CancellationException("Frame extraction cancelled"))
            }

            try {
                gifDecoder.advance()
                val frameBitmap = gifDecoder.nextFrame ?: continue
                val frameFile = File(
                    outputDir,
                    "${baseFileName}_frame_${String.format(Locale.US, "%03d", index + 1)}.png"
                )

                FileOutputStream(frameFile).use { stream ->
                    frameBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                successCount++
                createdPaths.add(frameFile.absolutePath)
                onProgress?.invoke(index + 1, frameCount)
            } catch (e: Exception) {
                Timber.e(e, "ExtractGifFrames: failed on GIF frame ${index + 1}")
            }
        }

        gifDecoder.clear()

        if (successCount == 0) {
            cleanupFiles(createdPaths)
            return Result.failure(Exception("Failed to extract any frames"))
        }

        scanFiles(createdPaths)
        Timber.d("ExtractGifFrames: extracted %d GIF frames", successCount)
        return Result.success(successCount)
    }

    private fun extractMovieFrames(
        inputFile: File,
        outputDir: File,
        baseFileName: String,
        extension: String,
        isActive: () -> Boolean,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return Result.failure(Exception(".$extension extraction requires API 28+"))
        }

        val movie = Movie.decodeFile(inputFile.absolutePath)
            ?: return Result.failure(Exception("Cannot decode .$extension animation"))

        val durationMs = movie.duration().takeIf { it > 0 } ?: 1000
        val estimatedFrameCount = (durationMs / 16).coerceIn(1, 500)
        val width = movie.width().coerceAtLeast(1)
        val height = movie.height().coerceAtLeast(1)
        val createdPaths = mutableListOf<String>()

        var successCount = 0
        for (index in 0 until estimatedFrameCount) {
            if (!isActive()) {
                cleanupFiles(createdPaths)
                return Result.failure(CancellationException("Frame extraction cancelled"))
            }

            val timeMs = (index * durationMs) / estimatedFrameCount
            movie.setTime(timeMs)

            var frameBitmap: Bitmap? = null
            var frameFile: File? = null
            try {
                frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frameBitmap)
                movie.draw(canvas, 0f, 0f)

                frameFile = File(
                    outputDir,
                    "${baseFileName}_frame_${String.format(Locale.US, "%03d", index + 1)}.png"
                )
                FileOutputStream(frameFile).use { stream ->
                    frameBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                successCount++
                createdPaths.add(frameFile.absolutePath)
                onProgress?.invoke(index + 1, estimatedFrameCount)
            } catch (e: Exception) {
                frameFile?.let {
                    if (it.exists()) {
                        it.delete()
                    }
                }
                Timber.e(e, "ExtractGifFrames: failed on %s frame %d", extension.uppercase(Locale.US), index + 1)
            } finally {
                frameBitmap?.recycle()
            }
        }

        if (successCount == 0) {
            cleanupFiles(createdPaths)
            return Result.failure(Exception("Failed to extract any frames from .$extension"))
        }

        scanFiles(createdPaths)
        Timber.d("ExtractGifFrames: extracted %d %s frames", successCount, extension.uppercase(Locale.US))
        return Result.success(successCount)
    }

    private fun scanFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun cleanupFiles(paths: List<String>) {
        paths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
