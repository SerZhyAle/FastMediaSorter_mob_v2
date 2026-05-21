package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages high-quality Tesseract offline language models (tessdata_best).
 * Handles installation verification, downloads with progress tracking, and file cleanup.
 */
class TesseractModelManager(private val context: Context) {

    companion object {
        private const val TESS_DATA_DIR = "tessdata"
        private const val BEST_DIR_NAME = "tesseract_best"
        
        // URL source for high quality models (tessdata_best)
        private const val TESS_DATA_BEST_URL_BASE = "https://github.com/tesseract-ocr/tessdata_best/raw/main/"
        
        // Minimum expected sizes for tessdata_best models to prevent corrupted/empty files
        private const val MIN_RUS_SIZE = 14_000_000L // rus.traineddata best is ~15 MB
        private const val MIN_UKR_SIZE = 10_000_000L // ukr.traineddata best is ~11.6 MB
    }

    /**
     * Get parent directory for best quality models.
     */
    fun getBestDataDir(): File {
        return File(context.filesDir, BEST_DIR_NAME)
    }

    /**
     * Get target tessdata directory where models are located.
     */
    fun getTessdataDir(): File {
        return File(getBestDataDir(), TESS_DATA_DIR)
    }

    /**
     * Check if high-quality model is fully installed and valid on disk.
     * @param language Language code ("rus" or "ukr").
     */
    fun isModelInstalled(language: String): Boolean {
        val tessDir = getTessdataDir()
        val modelFile = File(tessDir, "$language.traineddata")
        
        if (!modelFile.exists() || !modelFile.isFile) {
            return false
        }
        
        val fileSize = modelFile.length()
        val minRequiredSize = when (language) {
            "rus" -> MIN_RUS_SIZE
            "ukr" -> MIN_UKR_SIZE
            else -> 0L
        }
        
        val isValid = fileSize >= minRequiredSize
        if (!isValid) {
            Timber.w("TesseractModelManager: Model $language is corrupted or too small (size: $fileSize bytes)")
        }
        return isValid
    }

    /**
     * Delete the installed high-quality model.
     * @param language Language code ("rus" or "ukr").
     * @return true if file does not exist after the call.
     */
    fun deleteModel(language: String): Boolean {
        val tessDir = getTessdataDir()
        val modelFile = File(tessDir, "$language.traineddata")
        
        if (modelFile.exists()) {
            val deleted = modelFile.delete()
            Timber.i("TesseractModelManager: Deleted model $language: $deleted")
            return deleted
        }
        return true
    }

    /**
     * Dynamic HTTP downloader that retrieves the high-quality model file from the remote repository.
     * Staged into a temporary .tmp file during download, then validated and renamed to final file.
     *
     * @param language Language code ("rus" or "ukr").
     * @param onProgress Progress callback invoked on IO block writes: (percent, bytesDownloaded, totalBytes).
     * @return true if successfully downloaded, validated, and installed.
     */
    suspend fun downloadModel(
        language: String,
        onProgress: (percent: Int, bytes: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tessDir = getTessdataDir()
        if (!tessDir.exists()) {
            tessDir.mkdirs()
        }

        val tmpFile = File(tessDir, "$language.traineddata.tmp")
        val finalFile = File(tessDir, "$language.traineddata")
        
        // Clean up any stale temp file before download
        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        val downloadUrl = "$TESS_DATA_BEST_URL_BASE$language.traineddata"
        var connection: HttpURLConnection? = null
        
        try {
            Timber.d("TesseractModelManager: Downloading high-quality model from $downloadUrl")
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Timber.e("TesseractModelManager: HTTP status $responseCode received for URL: $downloadUrl")
                return@withContext false
            }
            
            val contentLength = connection.contentLength.toLong()
            Timber.d("TesseractModelManager: Model size to download: $contentLength bytes")

            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val percent = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt()
                        } else {
                            0
                        }
                        
                        onProgress(percent, totalBytesRead, contentLength)
                    }
                }
            }
            
            // Check download completeness and size bounds
            val downloadedSize = tmpFile.length()
            val minRequiredSize = when (language) {
                "rus" -> MIN_RUS_SIZE
                "ukr" -> MIN_UKR_SIZE
                else -> 0L
            }
            
            if (downloadedSize < minRequiredSize) {
                Timber.e("TesseractModelManager: File size verification failed for $language. " +
                        "Downloaded: $downloadedSize bytes, expected at least: $minRequiredSize bytes")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                return@withContext false
            }
            
            // Safe replacement of the final file
            if (finalFile.exists()) {
                finalFile.delete()
            }
            
            val renameSuccess = tmpFile.renameTo(finalFile)
            if (renameSuccess) {
                Timber.i("TesseractModelManager: Successfully installed high-quality model for $language")
                true
            } else {
                Timber.e("TesseractModelManager: Failed to rename tmp file to $finalFile")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                false
            }
        } catch (e: IOException) {
            Timber.e(e, "TesseractModelManager: Network error downloading high-quality model for $language")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "TesseractModelManager: Unexpected error during download of model for $language")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            false
        } finally {
            connection?.disconnect()
        }
    }
}
