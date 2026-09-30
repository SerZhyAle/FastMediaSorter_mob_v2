package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.domain.usecase.WriteCaptureFileUseCase
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.util.CaptureFileNamer.CaptureKind
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Filesystem work for the Camera-OCR-Translate flow: temp capture files, FileProvider URIs,
 * saving the captured photo to the device camera folder and exporting the OCR/translation result as
 * a `.txt` file. Both outputs follow the CAPTURE-OUTPUT contract: the photo is kind `photo` in
 * DCIM/Camera, the text is `ocr_text` or `translation` in Documents, each named by [CaptureFileNamer]
 * and written through [WriteCaptureFileUseCase] (MediaStore-aware, never overwriting), with
 * Downloads as the one fallback. Keeps all storage logic out of the Activity (Strict Rule 3).
 */
class CameraOcrStorageManager(
    private val context: Context,
    private val writeCaptureFile: WriteCaptureFileUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun contextForCaptureIntent(): Context = context

    /** True when the device exposes any camera hardware for the in-app CameraX flow. */
    fun isCameraAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    suspend fun createTempPhotoFile(captureMillis: Long): File? = withContext(ioDispatcher) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            File(dir, "CAP_$captureMillis.jpg").also { it.createNewFile() }
        } catch (e: IOException) {
            Timber.e(e, "CameraOcrStorageManager: Create temp file failed")
            null
        }
    }

    fun buildCaptureUri(tempFile: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "CameraOcrStorageManager: FileProvider generation failed")
        null
    }

    /**
     * Saves [bitmap] (the cropped region, or the full captured frame when no crop was applied) as a
     * `photo_<yyMMdd>_<HHmmss>.jpg` in DCIM/Camera, falling back to Downloads.
     */
    suspend fun saveBitmapToGallery(bitmap: Bitmap, captureMillis: Long): Boolean =
        withContext(ioDispatcher) {
            val name = CaptureFileNamer.shared.allocate(CaptureKind.PHOTO, ".jpg", captureMillis)
            Timber.d("S3746: ocr photo name=%s", name)
            val temp = File(context.cacheDir, name)
            try {
                FileOutputStream(temp).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        throw IOException("Bitmap.compress returned false for $name")
                    }
                }
                writeWithFallback(temp, CaptureDestinationPolicy.resolveCameraDestination(null), name) != null
            } catch (e: IOException) {
                Timber.e(e, "CameraOcrStorageManager: Save bitmap to gallery failed")
                false
            } finally {
                temp.delete()
            }
        }

    /**
     * Writes the result as UTF-8 text with LF line ends (CAPTURE-OUTPUT rules 14-15) to Documents,
     * falling back to Downloads. A result with a translation uses the two-marker layout and the
     * `translation` kind; recognition alone is plain `ocr_text`.
     * @return `<folder>/<file name>` on success, null on failure.
     */
    suspend fun exportResultToTxt(
        captureMillis: Long,
        originalText: String,
        translationText: String,
        ocrOnly: Boolean
    ): String? = withContext(ioDispatcher) {
        val textOnly = ocrOnly || translationText.isEmpty()
        val kind = if (textOnly) CaptureKind.OCR_TEXT else CaptureKind.TRANSLATION
        val name = CaptureFileNamer.shared.allocate(kind, ".txt", captureMillis)
        Timber.d("S3746: ocr text name=%s", name)
        val content = if (textOnly) {
            originalText
        } else {
            "=== TRANSLATION ===\n$translationText\n\n=== ORIGINAL ===\n$originalText"
        }
        val temp = File(context.cacheDir, name)
        try {
            temp.writeText(content.replace("\r\n", "\n"), Charsets.UTF_8)
            writeWithFallback(temp, CaptureDestinationPolicy.resolveDocumentsDestination(), name)
        } catch (e: IOException) {
            Timber.e(e, "CameraOcrStorageManager: Exporting text file failed")
            null
        } finally {
            temp.delete()
        }
    }

    fun cleanupTempFile(file: File?) {
        file?.let { if (it.exists()) it.delete() }
    }

    /**
     * Writes [temp] into [dir], then into Downloads when [dir] refuses the write (rule 9's single
     * fallback). Returns `<folder>/<final name>` - the caller shows it, so a fallback is never silent.
     */
    private suspend fun writeWithFallback(temp: File, dir: File, name: String): String? {
        val targets = listOf(dir, CaptureDestinationPolicy.downloadsDirectory()).distinct()
        for (target in targets) {
            val saved = writeCaptureFile(temp, target.absolutePath, name)
                .onFailure { e -> Timber.w(e, "CameraOcrStorageManager: write to %s failed", target) }
                .getOrNull()
            if (saved != null) {
                // A plain-file write (pre-Q, or Documents) is not indexed yet; a MediaStore URI already is
                // and the notifier skips it.
                MediaStoreNotifier.notifyFile(context, saved.location, "camera-ocr")
                Timber.i("CameraOcrStorageManager: saved %s to %s", saved.displayName, target)
                return "${target.name}/${saved.displayName}"
            }
        }
        return null
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
