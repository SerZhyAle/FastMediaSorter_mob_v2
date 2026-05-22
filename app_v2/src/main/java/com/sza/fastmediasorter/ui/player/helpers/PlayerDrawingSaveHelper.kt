package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

enum class DrawSaveMode { SAVE, SAVE_AND_CLOSE, SAVE_AND_SHARE }

/**
 * Helper class that handles draw overlay actions and saving logic extracted from PlayerActivity.
 * Reduced PlayerActivity size by consolidating image drawing, merging, and file persistency routines.
 */
class PlayerDrawingSaveHelper(private val activity: PlayerActivity) {

    private val saveDrawingUseCase by lazy {
        com.sza.fastmediasorter.domain.usecase.SaveDrawingUseCase(
            fileOperationUseCase = activity.fileOperationUseCase,
            stagingRegistry = activity.textNoteStagingRegistry,
            resourceRepository = activity.resourceRepository,
        )
    }

    private val imageDrawOverlayManager get() = activity.imageDrawOverlayManager

    private val isDrawOverlayManagerReady: Boolean get() = activity.isDrawOverlayManagerReady

    fun syncDrawOverlayCurrentFile() {
        if (isDrawOverlayManagerReady) {
            imageDrawOverlayManager.currentFile = activity.viewModel.state.value.currentFile
        }
    }

    fun setupDrawOverlayActionCallbacks() {
        imageDrawOverlayManager.setShareActionsVisible(hasImageShareTargets())
        imageDrawOverlayManager.actionCallback =
            object : ImageDrawOverlayManager.DrawOverlayActionCallback {
                override fun onSaveRequested(overlayBitmap: Bitmap) {
                    requestDrawSave(DrawSaveMode.SAVE, overlayBitmap)
                }

                override fun onSaveAndCloseRequested(overlayBitmap: Bitmap) {
                    requestDrawSave(DrawSaveMode.SAVE_AND_CLOSE, overlayBitmap)
                }

                override fun onSaveAndShareRequested(overlayBitmap: Bitmap) {
                    requestDrawSave(DrawSaveMode.SAVE_AND_SHARE, overlayBitmap)
                }

                override fun onShareRequested(overlayBitmap: Bitmap) {
                    shareCurrentDrawing(overlayBitmap)
                }

                override fun onCancelRequested() {
                    cancelDrawSession()
                }
            }
    }

    private fun requestDrawSave(mode: DrawSaveMode, overlayBitmap: Bitmap) {
        val currentFile = activity.viewModel.state.value.currentFile ?: run {
            Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        if (lookupStagedDrawing(currentFile) != null) {
            promptForDrawingName(currentFile.name) { chosenName ->
                commitStagedDrawing(mode, overlayBitmap, currentFile, chosenName)
            }
        } else {
            saveExistingDrawingInPlace(mode, overlayBitmap, currentFile)
        }
    }

    private fun commitStagedDrawing(
        mode: DrawSaveMode,
        overlayBitmap: Bitmap,
        currentFile: MediaFile,
        chosenName: String,
    ) {
        if (!currentFile.path.startsWith("/")) {
            Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        activity.lifecycleScope.launch {
            val bytes = buildMergedDrawingBytes(overlayBitmap, currentFile) ?: return@launch
            val oldPath = currentFile.path
            val result = saveDrawingUseCase(
                currentLocalFile = File(oldPath),
                intendedName = chosenName,
                imageBytes = bytes,
                keepEditableCopy = mode != DrawSaveMode.SAVE_AND_CLOSE,
            )

            val outcome = result.getOrElse { error ->
                Timber.e(error, "S0191: staged drawing save failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                outcome.editableLocalPath
                    ?.takeIf { it != oldPath }
                    ?.let { newPath ->
                        activity.viewModel.updateRenamedFilePath(oldPath, newPath)
                    }

                syncDrawOverlayCurrentFile()

                if (outcome.remoteFailureMessage != null) {
                    Toast.makeText(activity, R.string.draw_save_remote_failed, Toast.LENGTH_LONG).show()
                    return@withContext
                }

                activity.setResult(Activity.RESULT_OK)
                imageDrawOverlayManager.markCurrentStateSaved()
                Toast.makeText(activity, R.string.draw_save_ok_toast, Toast.LENGTH_SHORT).show()

                if (mode == DrawSaveMode.SAVE_AND_SHARE) {
                    shareDrawingBytes(bytes, outcome.finalName)
                }
                if (mode == DrawSaveMode.SAVE_AND_CLOSE) {
                    activity.finish()
                }
            }
        }
    }

    private fun saveExistingDrawingInPlace(
        mode: DrawSaveMode,
        overlayBitmap: Bitmap,
        currentFile: MediaFile,
    ) {
        activity.lifecycleScope.launch {
            val bytes = buildMergedDrawingBytes(overlayBitmap, currentFile) ?: return@launch
            val writeOk = writeDrawingBytesInPlace(currentFile.path, bytes)

            withContext(Dispatchers.Main) {
                if (!writeOk) {
                    Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                activity.setResult(Activity.RESULT_OK)
                imageDrawOverlayManager.markCurrentStateSaved()
                syncDrawOverlayCurrentFile()
                Toast.makeText(activity, R.string.draw_save_ok_toast, Toast.LENGTH_SHORT).show()

                if (mode == DrawSaveMode.SAVE_AND_SHARE) {
                    shareDrawingBytes(bytes, currentFile.name)
                }
                if (mode == DrawSaveMode.SAVE_AND_CLOSE) {
                    activity.finish()
                }
            }
        }
    }

    private fun shareCurrentDrawing(overlayBitmap: Bitmap) {
        val currentFile = activity.viewModel.state.value.currentFile ?: run {
            Toast.makeText(activity, R.string.error_share_failed, Toast.LENGTH_SHORT).show()
            return
        }

        activity.lifecycleScope.launch {
            val bytes = buildMergedDrawingBytes(overlayBitmap, currentFile) ?: return@launch
            withContext(Dispatchers.Main) {
                shareDrawingBytes(bytes, currentFile.name)
            }
        }
    }

    private suspend fun buildMergedDrawingBytes(
        overlayBitmap: Bitmap,
        currentFile: MediaFile,
    ): ByteArray? {
        val baseBitmap = activity.viewModel.currentDisplayedBitmap ?: run {
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
            }
            return null
        }

        val ext = currentFile.name.substringAfterLast('.', "").lowercase()
        val outputFormat = if (ext == "jpg" || ext == "jpeg") {
            Bitmap.CompressFormat.JPEG
        } else {
            Bitmap.CompressFormat.PNG
        }
        val displayRect = activity.activityBinding.photoView.displayRect
        val croppedOverlay = cropOverlayToImage(
            overlay = overlayBitmap,
            imageRect = displayRect,
            targetW = baseBitmap.width,
            targetH = baseBitmap.height,
        )
        val mergeResult = activity.mergeDrawOverlayUseCase.execute(baseBitmap, croppedOverlay, outputFormat)
        return mergeResult.getOrElse { error ->
            Timber.e(error, "S0191: draw overlay merge failed")
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private suspend fun writeDrawingBytesInPlace(path: String, bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    path.startsWith("content://") -> {
                        val uri = Uri.parse(path)
                        val stream = activity.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                        stream.use { it.write(bytes) }
                        true
                    }

                    path.startsWith("/") -> {
                        FileOutputStream(File(path)).use { it.write(bytes) }
                        true
                    }

                    else -> false
                }
            } catch (e: Throwable) {
                Timber.e(e, "S0191: draw in-place write failed for $path")
                false
            }
        }
    }

    fun cancelDrawSession() {
        val currentFile = activity.viewModel.state.value.currentFile
        val stagedDrawing = currentFile?.let(::lookupStagedDrawing)

        if (currentFile == null || stagedDrawing == null) {
            imageDrawOverlayManager.exitDrawMode(save = false)
            return
        }

        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val localFile = File(currentFile.path)
                activity.textNoteStagingRegistry.unregister(localFile)
                if (localFile.exists() && !localFile.delete()) {
                    Timber.w("S0191: failed to delete staged drawing %s", localFile.absolutePath)
                }
                val sessionDir = localFile.parentFile
                if (sessionDir != null && sessionDir.name.startsWith("drawing_session_") && sessionDir.list().isNullOrEmpty()) {
                    sessionDir.delete()
                }
            }
            imageDrawOverlayManager.exitDrawMode(save = false)
            activity.finish()
        }
    }

    private fun lookupStagedDrawing(
        currentFile: MediaFile,
    ): LocalStagingRegistry.StagedEntry? {
        if (!currentFile.path.startsWith("/")) return null
        return activity.textNoteStagingRegistry.lookup(File(currentFile.path))
            ?.takeIf { it.kind == com.sza.fastmediasorter.data.local.staging.StagedKind.DRAWING }
    }

    private fun promptForDrawingName(defaultName: String, onConfirm: (String) -> Unit) {
        val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        val input = EditText(activity).apply {
            setSingleLine(true)
            setText(defaultName)
            hint = activity.getString(R.string.draw_overlay_filename_hint)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.text_editor_action_save)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm(input.text?.toString()?.trim().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        val validate = {
            val text = input.text?.toString()?.trim().orEmpty()
            positiveButton.isEnabled = text.isNotEmpty() && text.none { it in forbiddenChars }
        }
        validate()

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = validate()
        })
        input.setSelection(input.text?.length ?: 0)
    }

    private fun hasImageShareTargets(): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "image/*" }
        return activity.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()
    }

    private suspend fun shareDrawingBytes(bytes: ByteArray, fileName: String) {
        val shareFile = runCatching {
            withContext(Dispatchers.IO) {
                val shareDir = File(activity.cacheDir, "draw_share").apply { mkdirs() }
                val normalizedName = if (fileName.contains('.')) fileName else "$fileName.jpg"
                File(shareDir, normalizedName.ifBlank { "drawing.jpg" }).apply {
                    writeBytes(bytes)
                }
            }
        }.getOrElse { error ->
            Timber.e(error, "S0191: failed to prepare drawing share file")
            Toast.makeText(activity, R.string.error_share_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", shareFile)
        val sent = SystemShareInvoker.invoke(
            context = activity,
            payload = SharePayload.Image(uri = uri, mime = mimeForFileName(shareFile.name)),
            chooserTitle = activity.getString(R.string.share),
        )
        if (!sent) {
            Toast.makeText(activity, R.string.error_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mimeForFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "jpg").lowercase()
        return if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/png"
    }

    /**
     * S0192 Phase 06 - in-place overwrite callback for the `[Save]` button.
     *
     * Pipeline: merge overlay onto base bitmap -> if resource is local + writable,
     * overwrite `currentFile.path`; otherwise silently fall through to the
     * legacy "save as new" pipeline (ADR-4 - no error shown to user).
     */
    fun setupDrawOverlayInPlaceSaveCallback() {
        imageDrawOverlayManager.inPlaceSaveCallback =
            object : ImageDrawOverlayManager.DrawOverlayInPlaceSaveCallback {
                override fun onInPlaceSaveRequested(overlayBitmap: Bitmap) {
                    val baseBitmap = activity.viewModel.currentDisplayedBitmap ?: run {
                        Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                        return
                    }
                    val currentFile = activity.viewModel.state.value.currentFile ?: run {
                        Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                        return
                    }
                    val ext = currentFile.name.substringAfterLast('.', "").lowercase()
                    val outputFormat = if (ext == "jpg" || ext == "jpeg") {
                        Bitmap.CompressFormat.JPEG
                    } else {
                        Bitmap.CompressFormat.PNG
                    }

                    // "Save" always attempts in-place overwrite of currentFile.
                    // No filename dialog under any circumstances - the prompt-based
                    // "Save as.." flow is a separate command (draw_overflow_save_new).
                    // If the write fails (read-only resource, cloud/SMB write denied,
                    // permission revoked), we surface a single failure toast and stay
                    // in draw mode so the user can decide what to do next.
                    val displayRect = activity.activityBinding.photoView.displayRect

                    activity.lifecycleScope.launch {
                        val croppedOverlay = cropOverlayToImage(
                            overlayBitmap, displayRect, baseBitmap.width, baseBitmap.height
                        )
                        val mergeResult = activity.mergeDrawOverlayUseCase.execute(baseBitmap, croppedOverlay, outputFormat)
                        val bytes = mergeResult.getOrElse { e ->
                            Timber.e(e, "Draw overlay merge failed (in-place save)")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val writeOk = withContext(Dispatchers.IO) {
                            try {
                                val path = currentFile.path
                                if (path.startsWith("content://")) {
                                    val uri = Uri.parse(path)
                                    val stream = activity.contentResolver.openOutputStream(uri, "wt")
                                        ?: return@withContext false
                                    stream.use { it.write(bytes) }
                                } else {
                                    FileOutputStream(File(path)).use { it.write(bytes) }
                                }
                                true
                            } catch (e: Throwable) {
                                Timber.e(e, "Draw overlay in-place write failed for ${currentFile.path}")
                                false
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (writeOk) {
                                Toast.makeText(activity, R.string.draw_save_ok_toast, Toast.LENGTH_SHORT).show()
                                imageDrawOverlayManager.exitDrawMode(save = false)
                                // Notify the view model so any cached bitmap for currentFile is refreshed
                                val refreshed = MediaFile(
                                    path = currentFile.path,
                                    name = currentFile.name,
                                    type = currentFile.type,
                                    size = bytes.size.toLong(),
                                    createdDate = currentFile.createdDate
                                )
                                activity.viewModel.onFileCreatedInCurrentDirectory(refreshed)
                            } else {
                                Toast.makeText(activity, R.string.draw_save_failed_toast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
    }

    /**
     * Wires the DrawOverlaySaveCallback to the merge + write + navigate pipeline.
     * Called from PlayerManagerInitializer after bindToolbar().
     */
    fun setupDrawOverlaySaveCallback() {
        imageDrawOverlayManager.saveCallback =
            object : ImageDrawOverlayManager.DrawOverlaySaveCallback {
                override fun onSaveRequested(overlayBitmap: Bitmap, filename: String) {
                    val baseBitmap = activity.viewModel.currentDisplayedBitmap ?: run {
                        Toast.makeText(activity, activity.getString(R.string.draw_overlay_saved_to_downloads), Toast.LENGTH_SHORT).show()
                        return
                    }
                    val currentFile = activity.viewModel.state.value.currentFile ?: return
                    val ext = currentFile.name.substringAfterLast('.', "").lowercase()
                    val outputFormat = if (ext == "jpg" || ext == "jpeg") {
                        Bitmap.CompressFormat.JPEG
                    } else {
                        Bitmap.CompressFormat.PNG
                    }
                    val isReadOnly = activity.viewModel.state.value.resource?.isReadOnly ?: false
                    val isLocalFile = currentFile.path.startsWith("/")

                    // Append extension when original file has none (e.g. /3DVR/ assets without ext)
                    val effectiveFilename = if (!filename.contains('.')) {
                        filename + if (outputFormat == Bitmap.CompressFormat.JPEG) ".jpg" else ".png"
                    } else filename

                    // Read image rect on main thread - PhotoView shifts up when toolbar is visible;
                    // crop must reflect actual image position at draw time, not full canvas size
                    val displayRect = activity.activityBinding.photoView.displayRect

                    activity.lifecycleScope.launch {
                        // Crop overlay to image region and scale to base bitmap dimensions
                        val croppedOverlay = cropOverlayToImage(overlayBitmap, displayRect, baseBitmap.width, baseBitmap.height)
                        val result = activity.mergeDrawOverlayUseCase.execute(baseBitmap, croppedOverlay, outputFormat)
                        result.onFailure { e ->
                            Timber.e(e, "S0107: overlay merge failed")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, R.string.draw_overlay_save_failed, Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        val bytes = result.getOrNull() ?: return@launch

                        val targetPath = withContext(Dispatchers.IO) {
                            if (!isReadOnly && isLocalFile) {
                                val parentDir = File(currentFile.path).parentFile
                                    ?: android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val target = File(parentDir, effectiveFilename)
                                FileOutputStream(target).use { it.write(bytes) }
                                target.absolutePath
                            } else {
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                                )
                                val target = File(downloadsDir, effectiveFilename)
                                FileOutputStream(target).use { it.write(bytes) }
                                target.absolutePath
                            }
                        }

                        withContext(Dispatchers.Main) {
                            imageDrawOverlayManager.exitDrawMode(save = false)
                            if (!isReadOnly && isLocalFile) {
                                // Step 4.5: navigate to newly created file
                                val newMediaFile = MediaFile(
                                    path = targetPath,
                                    name = effectiveFilename,
                                    type = MediaType.IMAGE,
                                    size = bytes.size.toLong(),
                                    createdDate = System.currentTimeMillis()
                                )
                                activity.viewModel.onFileCreatedInCurrentDirectory(newMediaFile)
                            } else {
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.draw_overlay_saved_to_downloads, targetPath),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
    }

    /**
     * Crops the draw canvas overlay to the image display region (imageRect) and scales
     * the result to the base bitmap dimensions for pixel-accurate compositing.
     * Required because the canvas covers the full container; when the draw toolbar is
     * visible the image is offset upward, so canvas coordinates != image coordinates.
     */
    private fun cropOverlayToImage(
        overlay: Bitmap,
        imageRect: RectF,
        targetW: Int,
        targetH: Int
    ): Bitmap {
        val left = imageRect.left.toInt().coerceAtLeast(0)
        val top = imageRect.top.toInt().coerceAtLeast(0)
        val right = imageRect.right.toInt().coerceAtMost(overlay.width)
        val bottom = imageRect.bottom.toInt().coerceAtMost(overlay.height)
        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(overlay, left, top, cropW, cropH)
        return if (cropW == targetW && cropH == targetH) cropped
        else Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    }
}
