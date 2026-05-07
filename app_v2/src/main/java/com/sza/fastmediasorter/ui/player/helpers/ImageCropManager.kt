package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Environment
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Self-contained engine for image crop and compress operations in the player.
 * Handles coordinate mapping with EXIF correction, region decode, file I/O, and
 * atomic overwrite for CROP, CROP_TO_FILE, and COMPRESS_COPY modes.
 *
 * No UI logic. Caller (PlayerActivity) manages the overlay view lifecycle.
 */
class ImageCropManager(
    private val context: Context,
    val lifecycleScope: LifecycleCoroutineScope,
    private val fileOperationUseCase: FileOperationUseCase
) {

    enum class CropMode { CROP, CROP_TO_FILE, COMPRESS_COPY }

    interface Callback {
        fun onSuccess(savedPath: String, mode: CropMode)
        fun onError(message: String)
        fun onCropModeEntered(mode: CropMode)
        fun onCropModeExited()
    }

    private var currentMode: CropMode? = null
    private var activeCallback: Callback? = null

    // ── Entry / exit ────────────────────────────────────────────────────────

    fun enterCropMode(
        mode: CropMode,
        currentFile: MediaFile,
        currentResource: MediaResource?,
        callback: Callback
    ) {
        Timber.d("S0106: enterCropMode mode=$mode file=${currentFile.name}")
        currentMode = mode
        activeCallback = callback
        callback.onCropModeEntered(mode)
    }

    fun exitCropMode() {
        currentMode = null
        activeCallback?.onCropModeExited()
        activeCallback = null
    }

    // ── Coordinate mapping ──────────────────────────────────────────────────

    /**
     * Maps a normalised screen rectangle (0..1 relative to view) to pixel coordinates
     * in the original image space, applying EXIF orientation correction.
     *
     * @param screenRect  0..1 RectF relative to view dimensions.
     * @param viewWidth   View width in pixels.
     * @param viewHeight  View height in pixels.
     * @param filePath    Local file path; network URIs skip EXIF (rotation 0 assumed).
     */
    suspend fun mapScreenRectToOriginal(
        screenRect: RectF,
        viewWidth: Int,
        viewHeight: Int,
        filePath: String
    ): Rect = withContext(Dispatchers.IO) {
        // Step 1: read EXIF orientation (local files only)
        val exifRotation = if (filePath.startsWith("/") || filePath.startsWith("file://")) {
            try {
                val path = filePath.removePrefix("file://")
                val exif = ExifInterface(path)
                exifOrientationToDegrees(
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                )
            } catch (_: Exception) {
                0
            }
        } else {
            0 // Network path — no EXIF access
        }

        // Step 2: read original dimensions without decoding pixels
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(filePath.removePrefix("file://"), opts) }
        val origWidth = opts.outWidth.takeIf { it > 0 } ?: viewWidth
        val origHeight = opts.outHeight.takeIf { it > 0 } ?: viewHeight

        // Step 3: map normalised screenRect to original pixel space
        val pixLeft = (screenRect.left * origWidth).toInt()
        val pixTop = (screenRect.top * origHeight).toInt()
        val pixRight = (screenRect.right * origWidth).toInt()
        val pixBottom = (screenRect.bottom * origHeight).toInt()

        // Step 4: apply EXIF rotation matrix so the decoded region matches what the user sees
        val raw = RectF(pixLeft.toFloat(), pixTop.toFloat(), pixRight.toFloat(), pixBottom.toFloat())
        val mapped = applyExifRotationToRect(raw, origWidth, origHeight, exifRotation)

        // Step 5: clamp to image bounds
        Rect(
            mapped.left.toInt().coerceIn(0, origWidth),
            mapped.top.toInt().coerceIn(0, origHeight),
            mapped.right.toInt().coerceIn(0, origWidth),
            mapped.bottom.toInt().coerceIn(0, origHeight)
        )
    }

    private fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    private fun applyExifRotationToRect(rect: RectF, imgWidth: Int, imgHeight: Int, degrees: Int): RectF {
        if (degrees == 0) return rect
        val matrix = Matrix()
        matrix.postRotate(-degrees.toFloat(), imgWidth / 2f, imgHeight / 2f)
        val pts = floatArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        matrix.mapPoints(pts)
        return RectF(
            minOf(pts[0], pts[2]),
            minOf(pts[1], pts[3]),
            maxOf(pts[0], pts[2]),
            maxOf(pts[1], pts[3])
        )
    }

    // ── Crop (overwrite original) ───────────────────────────────────────────

    suspend fun performCrop(
        screenRect: RectF,
        viewWidth: Int,
        viewHeight: Int,
        currentFile: MediaFile,
        currentResource: MediaResource?,
        callback: Callback
    ) {
        val timestamp = System.currentTimeMillis()
        val ext = currentFile.name.substringAfterLast('.', "jpg")
        val outTemp = File(context.cacheDir, "crop_out_$timestamp.$ext")
        var srcTemp: File? = null
        try {
            val mappedRect = mapScreenRectToOriginal(screenRect, viewWidth, viewHeight, currentFile.path)
            val srcFile = ensureLocalSource(currentFile, currentResource, timestamp, ext)
                .also { srcTemp = if (it.path != currentFile.path.removePrefix("file://")) it else null }

            val bitmap = withContext(Dispatchers.IO) {
                decodeRegion(srcFile.path, mappedRect)
            }

            withContext(Dispatchers.IO) {
                compressToFile(bitmap, outTemp, ext)
                bitmap.recycle()
                copyToDestination(outTemp, currentFile.path, currentResource)
            }

            withContext(Dispatchers.Main) {
                callback.onSuccess(currentFile.path, CropMode.CROP)
            }
        } catch (e: Exception) {
            Timber.e(e, "S0106: performCrop failed")
            withContext(Dispatchers.Main) { callback.onError(e.message ?: "Crop failed") }
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { outTemp.delete() }
                srcTemp?.let { runCatching { it.delete() } }
            }
        }
    }

    // ── Crop to file (save fragment as new file) ────────────────────────────

    suspend fun performCropToFile(
        screenRect: RectF,
        viewWidth: Int,
        viewHeight: Int,
        currentFile: MediaFile,
        currentResource: MediaResource?,
        targetFileName: String,
        saveTo: MediaResource?,
        callback: Callback
    ) {
        val timestamp = System.currentTimeMillis()
        val ext = currentFile.name.substringAfterLast('.', "jpg")
        val outTemp = File(context.cacheDir, "crop_out_$timestamp.$ext")
        var srcTemp: File? = null
        try {
            val mappedRect = mapScreenRectToOriginal(screenRect, viewWidth, viewHeight, currentFile.path)
            val srcFile = ensureLocalSource(currentFile, currentResource, timestamp, ext)
                .also { srcTemp = if (it.path != currentFile.path.removePrefix("file://")) it else null }

            val bitmap = withContext(Dispatchers.IO) { decodeRegion(srcFile.path, mappedRect) }

            val targetPath = withContext(Dispatchers.IO) {
                compressToFile(bitmap, outTemp, ext)
                bitmap.recycle()
                val dest = resolveDestinationPath(saveTo, targetFileName)
                copyToDestination(outTemp, dest, saveTo)
                dest
            }

            withContext(Dispatchers.Main) { callback.onSuccess(targetPath, CropMode.CROP_TO_FILE) }
        } catch (e: Exception) {
            Timber.e(e, "S0106: performCropToFile failed")
            withContext(Dispatchers.Main) { callback.onError(e.message ?: "Crop to file failed") }
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { outTemp.delete() }
                srcTemp?.let { runCatching { it.delete() } }
            }
        }
    }

    // ── Compressed copy ─────────────────────────────────────────────────────

    suspend fun performCompressedCopy(
        currentFile: MediaFile,
        currentResource: MediaResource?,
        targetFileName: String,
        saveTo: MediaResource?,
        callback: Callback
    ) {
        val timestamp = System.currentTimeMillis()
        val outTemp = File(context.cacheDir, "crop_out_$timestamp.jpg")
        var srcTemp: File? = null
        try {
            val srcFile = ensureLocalSource(currentFile, currentResource, timestamp,
                currentFile.name.substringAfterLast('.', "jpg"))
                .also { srcTemp = if (it.path != currentFile.path.removePrefix("file://")) it else null }

            val bitmap = withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options()
                val rawOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(srcFile.path, rawOpts)
                val longSide = maxOf(rawOpts.outWidth, rawOpts.outHeight)
                opts.inSampleSize = computeSampleSize(longSide, 1024)
                BitmapFactory.decodeFile(srcFile.path, opts)
                    ?: throw IllegalStateException("Failed to decode source image")
            }

            val targetPath = withContext(Dispatchers.IO) {
                FileOutputStream(outTemp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
                bitmap.recycle()
                val dest = resolveDestinationPath(saveTo, targetFileName)
                copyToDestination(outTemp, dest, saveTo)
                dest
            }

            withContext(Dispatchers.Main) { callback.onSuccess(targetPath, CropMode.COMPRESS_COPY) }
        } catch (e: Exception) {
            Timber.e(e, "S0106: performCompressedCopy failed")
            withContext(Dispatchers.Main) { callback.onError(e.message ?: "Compressed copy failed") }
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { outTemp.delete() }
                srcTemp?.let { runCatching { it.delete() } }
            }
        }
    }

    // ── Filename dialog ─────────────────────────────────────────────────────

    fun showCropFilenameDialog(
        activity: AppCompatActivity,
        mode: CropMode,
        sourceFile: MediaFile,
        isReadOnly: Boolean,
        onConfirm: (String) -> Unit
    ) {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmm"))
        val ext = sourceFile.name.substringAfterLast('.', "jpg")
        val baseName = sourceFile.name.substringBeforeLast('.')
        val defaultName = when (mode) {
            CropMode.CROP_TO_FILE -> "${baseName}_crop_${ts}.${ext}"
            CropMode.COMPRESS_COPY -> "${baseName}_shrink_${ts}.${ext}"
            CropMode.CROP -> "${baseName}_crop_${ts}.${ext}"
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val editText = EditText(activity).apply {
            hint = activity.getString(R.string.dialog_crop_filename_hint)
            setText(defaultName)
            selectAll()
        }
        container.addView(editText)

        if (isReadOnly) {
            val note = TextView(activity).apply {
                text = activity.getString(R.string.crop_save_to_downloads_note)
                val padTop = (8 * activity.resources.displayMetrics.density).toInt()
                setPadding(0, padTop, 0, 0)
            }
            container.addView(note)
        }

        AlertDialog.Builder(activity)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { defaultName }
                onConfirm(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * If the source is a network resource, download to a local temp file.
     * Returns a File pointing to a local copy (may be original local path).
     */
    private suspend fun ensureLocalSource(
        file: MediaFile,
        resource: MediaResource?,
        timestamp: Long,
        ext: String
    ): File = withContext(Dispatchers.IO) {
        val localPath = file.path.removePrefix("file://")
        val isLocal = resource == null || resource.type.name == "LOCAL" ||
            file.path.startsWith("/") || file.path.startsWith("file://")
        if (isLocal) {
            return@withContext File(localPath)
        }
        // Network source: copy to cache
        val tempDest = File(context.cacheDir, "crop_src_$timestamp.$ext")
        val result = fileOperationUseCase.execute(
            FileOperation.Copy(
                sources = listOf(File(file.path)),
                destination = tempDest,
                overwrite = true
            )
        )
        if (result is FileOperationResult.Success) {
            tempDest
        } else {
            throw IllegalStateException("Failed to download source file: ${file.path}")
        }
    }

    private fun decodeRegion(localPath: String, rect: Rect): Bitmap {
        val stream = FileInputStream(localPath)
        return stream.use { s ->
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(s, false)
                ?: throw IllegalStateException("BitmapRegionDecoder failed for $localPath")
            val bm = decoder.decodeRegion(rect, BitmapFactory.Options())
            decoder.recycle()
            bm ?: throw IllegalStateException("decodeRegion returned null")
        }
    }

    private fun compressToFile(bitmap: Bitmap, dest: File, ext: String) {
        val format = when (ext.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == Bitmap.CompressFormat.JPEG) 95 else 100
        FileOutputStream(dest).use { out -> bitmap.compress(format, quality, out) }
    }

    private suspend fun copyToDestination(
        sourceFile: File,
        destPath: String,
        destResource: MediaResource?
    ) = withContext(Dispatchers.IO) {
        val result = fileOperationUseCase.execute(
            FileOperation.Copy(
                sources = listOf(sourceFile),
                destination = File(destPath),
                overwrite = true
            )
        )
        if (result !is FileOperationResult.Success && result !is FileOperationResult.PartialSuccess) {
            throw IllegalStateException("Copy to destination failed: $destPath")
        }
    }

    private fun resolveDestinationPath(saveTo: MediaResource?, fileName: String): String {
        if (saveTo == null || saveTo.isReadOnly) {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            return File(downloads, fileName).absolutePath
        }
        return File(saveTo.path, fileName).absolutePath
    }

    private fun computeSampleSize(longSide: Int, maxSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) > maxSide) sample *= 2
        return sample
    }
}
