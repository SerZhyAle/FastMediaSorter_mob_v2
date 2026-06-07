package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaScannerConnection
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
    var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null

    // ── Entry / exit ────────────────────────────────────────────────────────

    fun enterCropMode(
        mode: CropMode,
        currentFile: MediaFile,
        currentResource: MediaResource?,
        callback: Callback
    ) {
        editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.CROP)
        currentMode = mode
        activeCallback = callback
        callback.onCropModeEntered(mode)
    }

    fun exitCropMode() {
        editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
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
            0 // Network path - no EXIF access
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
            // ensureLocalSource must precede mapScreenRectToOriginal: BitmapFactory cannot decode
            // network URIs (SMB/SFTP/cloud), so origWidth/origHeight would fall back to viewWidth/
            // viewHeight and produce screen-space coords instead of image-pixel coords.
            val srcFile = ensureLocalSource(currentFile, currentResource, timestamp, ext)
                .also { srcTemp = if (it.path != currentFile.path.removePrefix("file://")) it else null }
            val mappedRect = mapScreenRectToOriginal(screenRect, viewWidth, viewHeight, srcFile.path)

            val bitmap = withContext(Dispatchers.IO) {
                // BitmapRegionDecoder returns raw (unrotated) pixels - rotate to match display orientation.
                val raw = decodeRegion(srcFile.path, mappedRect)
                rotateBitmapIfNeeded(raw, readExifDegrees(srcFile.path))
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
            Timber.e(e, "performCrop failed")
            withContext(Dispatchers.Main) { callback.onError(context.getString(R.string.crop_failed)) }
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
            // ensureLocalSource must precede mapScreenRectToOriginal: BitmapFactory cannot decode
            // network URIs (SMB/SFTP/cloud), so origWidth/origHeight would fall back to viewWidth/
            // viewHeight and produce screen-space coords instead of image-pixel coords.
            val srcFile = ensureLocalSource(currentFile, currentResource, timestamp, ext)
                .also { srcTemp = if (it.path != currentFile.path.removePrefix("file://")) it else null }
            val mappedRect = mapScreenRectToOriginal(screenRect, viewWidth, viewHeight, srcFile.path)

            val bitmap = withContext(Dispatchers.IO) {
                // BitmapRegionDecoder returns raw (unrotated) pixels - rotate to match display orientation.
                val raw = decodeRegion(srcFile.path, mappedRect)
                rotateBitmapIfNeeded(raw, readExifDegrees(srcFile.path))
            }

            val targetPath = withContext(Dispatchers.IO) {
                compressToFile(bitmap, outTemp, ext)
                bitmap.recycle()
                val dest = resolveDestinationPath(saveTo, targetFileName)
                copyToDestination(outTemp, dest, saveTo)
                dest
            }

            withContext(Dispatchers.Main) { callback.onSuccess(targetPath, CropMode.CROP_TO_FILE) }
        } catch (e: Exception) {
            Timber.e(e, "performCropToFile failed")
            withContext(Dispatchers.Main) { callback.onError(context.getString(R.string.crop_to_file_failed)) }
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
                val rawOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(srcFile.path, rawOpts)
                val longSide = maxOf(rawOpts.outWidth, rawOpts.outHeight)
                val opts = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(longSide, 1024) }
                val raw = BitmapFactory.decodeFile(srcFile.path, opts)
                    ?: throw IllegalStateException("Failed to decode source image")
                // BitmapFactory does not apply EXIF orientation - rotate to match display orientation.
                rotateBitmapIfNeeded(raw, readExifDegrees(currentFile.path))
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
            Timber.e(e, "performCompressedCopy failed")
            withContext(Dispatchers.Main) { callback.onError(context.getString(R.string.compress_copy_failed)) }
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
        val ext = sourceFile.name.substringAfterLast('.', "jpg")
        val baseName = sourceFile.name.substringBeforeLast('.')
        val defaultName = when (mode) {
            CropMode.CROP_TO_FILE -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.CROP)
            CropMode.COMPRESS_COPY -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.COMPRESS)
            CropMode.CROP -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.CROP)
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
        // Network source: copy to cache.
        // FileOperation.Copy treats destination as a directory (joinPath appends source filename),
        // so we pass a real temp dir and rename the result to the expected single-file path.
        val tempDir = File(context.cacheDir, "crop_src_dir_$timestamp")
        tempDir.mkdirs()
        val result = fileOperationUseCase.execute(
            FileOperation.Copy(
                sources = listOf(File(file.path)),
                destination = tempDir,
                overwrite = true
            )
        )
        val tempDest = File(context.cacheDir, "crop_src_$timestamp.$ext")
        if (result is FileOperationResult.Success) {
            val copiedPath = (result as FileOperationResult.Success).copiedFilePaths.firstOrNull()
                ?: File(tempDir, file.name).absolutePath
            File(copiedPath).renameTo(tempDest)
            tempDir.deleteRecursively()
            if (!tempDest.exists()) throw IllegalStateException("Failed to place downloaded file: ${file.path}")
            tempDest
        } else {
            tempDir.deleteRecursively()
            throw IllegalStateException("Failed to download source file: ${file.path}")
        }
    }

    private fun decodeRegion(localPath: String, rect: Rect): Bitmap {
        val stream = FileInputStream(localPath)
        return stream.use { s ->
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(s, false)
                ?: throw IllegalStateException("BitmapRegionDecoder failed for $localPath")
            val iw = decoder.width
            val ih = decoder.height
            // Re-clamp to actual decoder dimensions.
            // mapScreenRectToOriginal may have used wrong bounds (network-file fallback to view
            // dims, or EXIF-rotation producing negative coords that all clamp to the same edge).
            val safeLeft = rect.left.coerceIn(0, iw)
            val safeTop = rect.top.coerceIn(0, ih)
            val safeRight = rect.right.coerceIn(0, iw)
            val safeBottom = rect.bottom.coerceIn(0, ih)
            if (safeLeft >= safeRight || safeTop >= safeBottom) {
                decoder.recycle()
                // Log technical details before throwing - performCrop catches and logs the exception.
                Timber.w("crop rect collapsed to empty region after clamping: input=$rect image=${iw}x${ih}")
                throw IllegalArgumentException(context.getString(R.string.crop_error_outside_image))
            }
            val safeRect = Rect(safeLeft, safeTop, safeRight, safeBottom)
            val bm = decoder.decodeRegion(safeRect, BitmapFactory.Options())
            decoder.recycle()
            bm ?: throw IllegalStateException("decodeRegion returned null")
        }
    }

    /**
     * Reads EXIF orientation from a local file and converts it to clockwise degrees.
     * Returns 0 for network paths or unreadable EXIF.
     * Must be called on a background thread.
     */
    private fun readExifDegrees(filePath: String): Int {
        if (!filePath.startsWith("/") && !filePath.startsWith("file://")) return 0
        return try {
            val path = filePath.removePrefix("file://")
            val exif = ExifInterface(path)
            exifOrientationToDegrees(
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            )
        } catch (_: Exception) { 0 }
    }

    /**
     * Rotates [bitmap] by [degrees] clockwise. Returns original bitmap if degrees == 0.
     * Recycles the original when a new bitmap is created.
     */
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { bitmap.recycle() }
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
        // FileOperation.Copy treats `destination` as a directory and appends source.name,
        // so passing a full file path would cause Kotlin's copyTo to create a directory
        // at that path (EISDIR). For local destinations, write directly and notify MediaScanner.
        // Network detection follows FileOperationUseCase.isNetworkPath canonical forms -
        // `contains("://")` alone misses our canonical `smb:/host:port/share/..` (single slash).
        val isNetworkPath = isNetworkUri(destPath)
        if (!isNetworkPath) {
            val destFile = File(destPath)
            destFile.parentFile?.mkdirs()
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destPath), null, null)
            return@withContext
        }

        // Network destination: FileOperation.Copy uses destination as a directory and appends
        // source.name - rename source to target filename first so the remote path is correct.
        val destFile = File(destPath)
        val destDir = destFile.parentFile ?: throw IllegalStateException("No parent dir for $destPath")
        val renamedSource = File(sourceFile.parent, destFile.name)
        sourceFile.renameTo(renamedSource)
        try {
            val result = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(renamedSource),
                    destination = destDir,
                    overwrite = true
                )
            )
            if (result !is FileOperationResult.Success && result !is FileOperationResult.PartialSuccess) {
                throw IllegalStateException("Copy to destination failed: $destPath")
            }
        } finally {
            runCatching { renamedSource.delete() }
        }
    }

    private fun resolveDestinationPath(saveTo: MediaResource?, fileName: String): String {
        // Virtual paths (virtual://...) have no real filesystem location - fall back to Downloads.
        if (saveTo == null || saveTo.isReadOnly || saveTo.path.startsWith("virtual://")) {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            return File(downloads, fileName).absolutePath
        }
        // Network URIs (smb:/, sftp://, etc.) must not pass through File() - its constructor
        // collapses `scheme://` to `scheme:/`, then absolutePath prepends `/`, producing
        // `/smb:/host/..` which downstream isNetworkPath checks fail to recognise canonically.
        if (isNetworkUri(saveTo.path)) {
            return "${saveTo.path.trimEnd('/')}/$fileName"
        }
        return File(saveTo.path, fileName).absolutePath
    }

    /**
     * Mirrors [com.sza.fastmediasorter.domain.usecase.FileOperationUseCase] isNetworkPath
     * shapes: `<proto>://`, `/<proto>://`, `/<proto>:/`, `<proto>:/`. Keep in sync.
     */
    private fun isNetworkUri(path: String): Boolean {
        val protocols = listOf("smb", "sftp", "ftp", "cloud")
        return protocols.any { proto ->
            path.startsWith("$proto://") ||
                path.startsWith("/$proto://") ||
                path.startsWith("/$proto:/") ||
                path.startsWith("$proto:/")
        }
    }

    private fun computeSampleSize(longSide: Int, maxSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) > maxSide) sample *= 2
        return sample
    }
}
