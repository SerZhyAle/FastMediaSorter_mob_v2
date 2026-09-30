package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages high-quality Tesseract offline language models (tessdata_best).
 * Handles installation verification, downloads with progress tracking, and file cleanup.
 *
 * Hilt-injectable (S0386 audit); flavor source sets that need it before a Hilt graph exists may
 * still construct it directly with an application context.
 */
@Singleton
class TesseractModelManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        private const val TESS_DATA_DIR = "tessdata"
        private const val BEST_DIR_NAME = "tesseract_best"

        // Pin the passive data files to the published release so checksum validation stays stable.
        private const val TESS_DATA_BEST_URL_BASE =
            "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/4.1.0/"

        // Minimum expected sizes for tessdata_best models to prevent corrupted/empty files
        private const val MIN_RUS_SIZE = 14_000_000L // rus.traineddata best is ~15 MB
        private const val MIN_UKR_SIZE = 10_000_000L // ukr.traineddata best is ~11.6 MB

        private const val PREFS_NAME = "tesseract_models_prefs"
        private const val SHA256_RUS =
            "b617eb6830ffabaaa795dd87ea7fd251adfe9cf0efe05eb9a2e8128b7728d6b6"
        private const val SHA256_UKR =
            "1277f6e3b6f707063a92d40e7678e7f57154e8414e328e340be9ee9275eea9c8"
    }

    private val validatedModels = java.util.concurrent.ConcurrentHashMap<String, String>()

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
     * Retains validated status in memory and preferences to avoid re-hashing 10-15 MB on each check.
     * Never performs heavy SHA-256 hashing on the main thread.
     * @param language Language code ("rus" or "ukr").
     */
    fun isModelInstalled(language: String): Boolean {
        val modelFile = File(getTessdataDir(), "$language.traineddata")
        val fileSize = if (modelFile.isFile) modelFile.length() else -1L

        if (fileSize < 0L || fileSize < minRequiredSize(language)) {
            clearValidationStamp(language)
            return false
        }

        val currentStamp = "${fileSize}_${modelFile.lastModified()}"
        return when {
            isValidated(language, currentStamp) -> true
            // Avoid blocking main thread with heavy SHA-256 calculation
            isMainThread() -> {
                Timber.d("TesseractModelManager: Model $language unvalidated on main thread, skipping sync hash")
                false
            }
            else -> validateAndStamp(modelFile, language, currentStamp)
        }
    }

    private fun minRequiredSize(language: String): Long = when (language) {
        "rus" -> MIN_RUS_SIZE
        "ukr" -> MIN_UKR_SIZE
        else -> 0L
    }

    private fun validateAndStamp(modelFile: File, language: String, currentStamp: String): Boolean {
        val isValid = hasExpectedSha256(modelFile, language)
        if (isValid) {
            recordValidationStamp(language, currentStamp)
        } else {
            Timber.w(
                "TesseractModelManager: Model %s failed integrity validation (size: %d bytes)",
                language,
                modelFile.length()
            )
            clearValidationStamp(language)
        }
        return isValid
    }

    /**
     * Verify model integrity on IO dispatcher and retain validated status if valid.
     */
    suspend fun verifyModelIntegrity(language: String): Boolean = withContext(Dispatchers.IO) {
        val tessDir = getTessdataDir()
        val modelFile = File(tessDir, "$language.traineddata")

        if (!modelFile.exists() || !modelFile.isFile) {
            clearValidationStamp(language)
            return@withContext false
        }

        val fileSize = modelFile.length()
        val minRequiredSize = when (language) {
            "rus" -> MIN_RUS_SIZE
            "ukr" -> MIN_UKR_SIZE
            else -> 0L
        }

        if (fileSize < minRequiredSize) {
            clearValidationStamp(language)
            return@withContext false
        }

        val currentStamp = "${fileSize}_${modelFile.lastModified()}"
        if (isValidated(language, currentStamp)) {
            return@withContext true
        }

        val isValid = hasExpectedSha256(modelFile, language)
        if (isValid) {
            recordValidationStamp(language, currentStamp)
        } else {
            Timber.w("TesseractModelManager: Model $language failed integrity validation (size: $fileSize bytes)")
            clearValidationStamp(language)
        }
        isValid
    }

    /**
     * Delete the installed high-quality model.
     * @param language Language code ("rus" or "ukr").
     * @return true if file does not exist after the call.
     */
    fun deleteModel(language: String): Boolean {
        clearValidationStamp(language)
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
                        ensureActive()
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

            if (downloadedSize < minRequiredSize || !hasExpectedSha256(tmpFile, language)) {
                Timber.e(
                    "TesseractModelManager: Model integrity verification failed for $language. " +
                        "Downloaded: $downloadedSize bytes, expected at least: $minRequiredSize bytes"
                )
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
                recordValidationStamp(language, "${finalFile.length()}_${finalFile.lastModified()}")
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
            e.errorUnlessCancellation("TesseractModelManager: Unexpected error during download of model for $language")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun validationKey(language: String) = "validated_model_$language"

    private fun isValidated(language: String, expectedStamp: String): Boolean {
        if (validatedModels[language] == expectedStamp) return true
        val matches = getPrefs()?.getString(validationKey(language), null) == expectedStamp
        if (matches) validatedModels[language] = expectedStamp
        return matches
    }

    private fun recordValidationStamp(language: String, stamp: String) {
        validatedModels[language] = stamp
        getPrefs()?.edit()?.putString(validationKey(language), stamp)?.apply()
    }

    private fun clearValidationStamp(language: String) {
        validatedModels.remove(language)
        getPrefs()?.edit()?.remove(validationKey(language))?.apply()
    }

    private fun getPrefs(): android.content.SharedPreferences? = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private fun isMainThread(): Boolean {
        val looper = runCatching { android.os.Looper.getMainLooper() }.getOrNull() ?: return false
        return runCatching { android.os.Looper.myLooper() == looper }.getOrDefault(false)
    }

    private fun hasExpectedSha256(file: File, language: String): Boolean {
        val expected = expectedSha256(language) ?: return false
        val actual = file.sha256()
        val matches = actual.equals(expected, ignoreCase = true)
        if (!matches) {
            Timber.w("TesseractModelManager: SHA-256 mismatch for $language model")
        }
        return matches
    }

    private fun expectedSha256(language: String): String? {
        return when (language) {
            "rus" -> SHA256_RUS
            "ukr" -> SHA256_UKR
            else -> null
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
    }
}
