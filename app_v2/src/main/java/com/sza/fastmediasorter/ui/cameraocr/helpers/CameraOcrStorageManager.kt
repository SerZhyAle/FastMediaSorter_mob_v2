package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Filesystem work for the Camera-OCR-Translate flow: temp capture files, FileProvider URIs,
 * saving the captured photo to the device gallery (DCIM/Camera with a Downloads fallback) and
 * exporting the OCR/translation result as a `.txt` file. Keeps all storage logic out of the
 * Activity so the UI layer holds no business logic (Strict Rule 3).
 */
class CameraOcrStorageManager(private val context: Context) {

    /** True when at least one Activity can handle [MediaStore.ACTION_IMAGE_CAPTURE]. */
    fun isCameraAvailable(): Boolean =
        context.packageManager
            .queryIntentActivities(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)
            .isNotEmpty()

    fun createTempPhotoFile(timestamp: String): File? = try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        File(dir, "CAP_$timestamp.jpg").also { it.createNewFile() }
    } catch (e: Exception) {
        Timber.e(e, "CameraOcrStorageManager: Create temp file failed")
        null
    }

    fun buildCaptureUri(tempFile: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    } catch (e: Exception) {
        Timber.e(e, "CameraOcrStorageManager: FileProvider generation failed")
        null
    }

    /**
     * Saves a [bitmap] (e.g. the cropped region, or the full captured frame when no crop was
     * applied) to DCIM/Camera, falling back to Downloads.
     */
    suspend fun saveBitmapToGallery(bitmap: Bitmap, timestamp: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val dcimDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Camera"
                )
                if (!dcimDir.exists()) {
                    dcimDir.mkdirs()
                }
                writeBitmap(bitmap, File(dcimDir, "OCR_IMG_$timestamp.jpg"))
                true
            } catch (e: Exception) {
                Timber.w(e, "CameraOcrStorageManager: Save bitmap to DCIM/Camera failed, trying Downloads fallback")
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    writeBitmap(bitmap, File(downloadDir, "OCR_IMG_$timestamp.jpg"))
                    true
                } catch (ex: Exception) {
                    Timber.e(ex, "CameraOcrStorageManager: Save bitmap to gallery completely failed")
                    false
                }
            }
        }

    private fun writeBitmap(bitmap: Bitmap, targetFile: File) {
        FileOutputStream(targetFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
        }
        notifyMediaScanner(targetFile)
        Timber.i("CameraOcrStorageManager: Bitmap saved to gallery: ${targetFile.absolutePath}")
    }

    /**
     * Writes the result to `Downloads/OCR_TXT_<timestamp>.txt`.
     * @return relative path string on success, null on failure.
     */
    suspend fun exportResultToTxt(
        timestamp: String,
        originalText: String,
        translationText: String,
        ocrOnly: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val txtFile = File(downloadDir, "OCR_TXT_$timestamp.txt")
            FileOutputStream(txtFile).use { fos ->
                val writer = fos.bufferedWriter()
                if (ocrOnly || translationText.isEmpty()) {
                    writer.write(originalText)
                } else {
                    writer.write("=== TRANSLATION ===\n")
                    writer.write(translationText)
                    writer.write("\n\n=== ORIGINAL ===\n")
                    writer.write(originalText)
                }
                writer.flush()
            }
            "Downloads/OCR_TXT_$timestamp.txt"
        } catch (e: IOException) {
            Timber.e(e, "CameraOcrStorageManager: Exporting text file failed")
            null
        }
    }

    fun cleanupTempFile(file: File?) {
        file?.let { if (it.exists()) it.delete() }
    }

    @Suppress("DEPRECATION")
    private fun notifyMediaScanner(file: File) {
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
    }
}
