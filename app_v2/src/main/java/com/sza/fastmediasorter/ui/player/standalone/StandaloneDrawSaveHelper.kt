package com.sza.fastmediasorter.ui.player.standalone

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import com.sza.fastmediasorter.ui.player.helpers.DrawCropCompositor
import com.sza.fastmediasorter.ui.player.helpers.DrawKeepExportHelper
import com.sza.fastmediasorter.ui.player.helpers.ImageCropManager
import com.sza.fastmediasorter.ui.player.helpers.ImageDrawOverlayManager
import com.sza.fastmediasorter.ui.player.helpers.ImageEditorFileNamer
import com.sza.fastmediasorter.ui.player.helpers.ScreenRotationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S0410: drives the shared [ImageDrawOverlayManager] for the standalone image viewer. The standalone
 * host has no resource/folder context, so by default every draw save is a save-as: the base image and
 * the draw overlay are merged and written to a NEW file in Pictures (the source URI is never touched).
 * Mirrors the in-app PlayerDrawingSaveHelper merge/crop pipeline without its staging/resource paths.
 *
 * S0837: for the screenshot OPEN_IN_DRAW gesture the host supplies [getOverwriteTargetUri]. When it
 * returns a URI a DEFAULT save (no chosen filename) overwrites that source in place instead of writing
 * a new file; an explicit "Save as.." (chosen filename) still writes a new file.
 */
class StandaloneDrawSaveHelper(
    private val activity: AppCompatActivity,
    imageContainer: ViewGroup,
    toolbarRoot: View,
    screenRotationManager: ScreenRotationManager,
    hasAccelerometer: Boolean,
    keepExportHelper: DrawKeepExportHelper,
    private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase,
    private val imageCropManager: ImageCropManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val getCurrentFile: () -> MediaFile?,
    private val getDisplayedBitmap: () -> Bitmap?,
    private val getImageDisplayRect: () -> RectF,
    // S0679 - swap the standalone image view to the cropped working composite.
    private val setDisplayedBitmap: (Bitmap) -> Unit,
    private val onDelete: () -> Unit,
    // S0676: notify the host when draw mode enters (true) / leaves (false) so it can hide / restore the
    // Copy/Move bottom panels, mirroring the in-app PlayerImmersiveModeManager. No-op by default.
    private val onDrawModeChanged: (Boolean) -> Unit = {},
    // S0837: source URI to overwrite on a default save (screenshot flow); null keeps save-as-new.
    private val getOverwriteTargetUri: () -> Uri? = { null },
) {
    private val manager = ImageDrawOverlayManager(
        activity = activity,
        imageContainer = imageContainer,
        screenRotationManager = screenRotationManager,
        hasAccelerometer = hasAccelerometer,
        keepExportHelper = keepExportHelper,
    )

    init {
        manager.baseBitmapProvider = getDisplayedBitmap
        manager.bindToolbar(toolbarRoot)
        manager.editModeCallback = { mode ->
            onDrawModeChanged(mode == com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.DRAW)
        }
        manager.actionCallback = object : ImageDrawOverlayManager.DrawOverlayActionCallback {
            override fun onSaveRequested(overlayBitmap: Bitmap) = save(overlayBitmap, null, close = false)
            override fun onSaveAndCloseRequested(overlayBitmap: Bitmap) = save(overlayBitmap, null, close = true)
            // The standalone draw toolbar has no share / save-and-share button; if ever reached, fall
            // back to a plain save so the drawing is never silently lost.
            override fun onSaveAndShareRequested(overlayBitmap: Bitmap) = save(overlayBitmap, null, close = false)
            override fun onShareRequested(overlayBitmap: Bitmap) = save(overlayBitmap, null, close = false)
            override fun onCancelRequested() {
                manager.exitDrawMode(save = false)
            }
            override fun onDeleteRequested() {
                onDelete()
            }
        }
        manager.saveCallback = object : ImageDrawOverlayManager.DrawOverlaySaveCallback {
            override fun onSaveRequested(overlayBitmap: Bitmap, filename: String) =
                save(overlayBitmap, filename, close = false)
        }
        // S0679 - draw-editor crop tool (mirrors the in-app PlayerDrawingSaveHelper wiring).
        val cropCompositor = DrawCropCompositor(imageCropManager, mergeDrawOverlayUseCase)
        manager.cropApplyCallback = { normalizedRect, viewW, viewH ->
            applyCrop(cropCompositor, normalizedRect, viewW, viewH)
        }
    }

    fun enterDrawMode() {
        manager.currentFile = getCurrentFile()
        manager.enterDrawMode()
    }

    /** True if draw mode was active and consumed the back press. */
    fun handleBackPress(): Boolean = manager.handleBackPress()

    private fun save(overlay: Bitmap, filename: String?, close: Boolean) {
        val base = getDisplayedBitmap() ?: run {
            // Base bitmap unavailable (image not yet loaded or load failed). Exit draw mode so
            // the user is not left stuck in the overlay with no way to dismiss it.
            Timber.w("Standalone draw save: base bitmap null, exiting draw mode")
            manager.exitDrawMode(save = false)
            Toast.makeText(activity, R.string.draw_overlay_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val current = getCurrentFile()
        val ext = current?.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val format = if (ext == "jpg" || ext == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val baseName = current?.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "drawing"
        val defaultExt = ext.ifEmpty { if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png" }
        val chosen = filename?.takeIf { it.isNotBlank() }
            ?: ImageEditorFileNamer.buildName(baseName, defaultExt, ImageEditorFileNamer.DRAW)
        val finalName = ImageEditorFileNamer.ensureExtension(
            chosen,
            if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png",
        )
        val displayRect = getImageDisplayRect()
        val mime = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
        // S0837: a default save (no chosen filename) overwrites the launched screenshot in place;
        // "Save as.." (filename != null) always falls through to the new-file path below.
        val overwriteUri = if (filename == null) getOverwriteTargetUri() else null
        lifecycleScope.launch {
            // Wrap the whole pipeline: a crop-geometry edge case, a merge failure, or a scoped-storage
            // write denial must surface a toast, never crash the activity. Default target is MediaStore
            // Pictures (scoped-storage-correct, no storage permission needed for our own insert;
            // mirrors the host's saveCurrentFrame). S0837 overwrite writes back to the source URI.
            val saved = try {
                val cropped = cropOverlayToImage(overlay, displayRect, base.width, base.height)
                val bytes = mergeDrawOverlayUseCase.execute(base, cropped, format).getOrThrow()
                withContext(Dispatchers.IO) {
                    if (overwriteUri != null) {
                        // "wt" truncates then writes; keeps the original MediaStore entry, name and path.
                        activity.contentResolver.openOutputStream(overwriteUri, "wt")
                            ?.use { it.write(bytes) } ?: return@withContext false
                        true
                    } else {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, finalName)
                            put(MediaStore.Images.Media.MIME_TYPE, mime)
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        }
                        val uri = activity.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        ) ?: return@withContext false
                        activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        true
                    }
                }
            } catch (e: Exception) {
                e.errorUnlessCancellation("Standalone draw save failed")
                false
            }
            manager.exitDrawMode(save = false)
            Toast.makeText(
                activity,
                if (saved) R.string.draw_save_ok_toast else R.string.draw_overlay_save_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (saved && close) activity.finish()
        }
    }

    // S0679 - apply the draw-editor crop: compose the cropped working image and swap it in, keeping
    // the crop undoable (ADR-4). No resource context in standalone, so currentResource is null.
    private fun applyCrop(
        compositor: DrawCropCompositor,
        normalizedRect: RectF,
        viewW: Int,
        viewH: Int,
    ) {
        val base = getDisplayedBitmap() ?: run {
            Toast.makeText(activity, R.string.draw_crop_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val current = getCurrentFile() ?: run {
            Toast.makeText(activity, R.string.draw_crop_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val overlay = manager.getOverlayBitmap()
        val displayRect = getImageDisplayRect()
        val selectionRect = RectF(
            normalizedRect.left * viewW,
            normalizedRect.top * viewH,
            normalizedRect.right * viewW,
            normalizedRect.bottom * viewH,
        )
        lifecycleScope.launch {
            val cropped = try {
                compositor.composeCroppedWorkingImage(
                    baseBitmap = base,
                    overlayBitmap = overlay,
                    displayRect = displayRect,
                    selectionRect = selectionRect,
                    canvasWidth = viewW,
                    canvasHeight = viewH,
                    currentFile = current,
                    currentResource = null,
                )
            } catch (e: Exception) {
                e.errorUnlessCancellation("Standalone draw crop failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, R.string.draw_crop_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            } finally {
                // S0679: getOverlayBitmap() hands back a fresh throwaway snapshot; the compositor copies
                // only the region it needs and does not take ownership, so release it here to avoid
                // leaking one overlay-size bitmap per crop within a draw session.
                overlay?.recycle()
            }
            withContext(Dispatchers.Main) {
                setDisplayedBitmap(cropped)
                manager.beginCropUndo {
                    setDisplayedBitmap(base)
                }
            }
        }
    }

    /**
     * Crop the full-canvas overlay to the image display rect and scale to the base bitmap so the
     * composite is pixel-aligned (the canvas covers the whole container; the image is offset while
     * the draw toolbar is visible). Same mapping as the in-app PlayerDrawingSaveHelper.
     */
    private fun cropOverlayToImage(overlay: Bitmap, imageRect: RectF, targetW: Int, targetH: Int): Bitmap {
        val w = overlay.width
        val h = overlay.height
        // Clamp so left + cropW <= w and top + cropH <= h hold for any displayRect (it can fall partly
        // outside the canvas, or be empty before the image is laid out) - otherwise createBitmap throws.
        val left = imageRect.left.toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
        val top = imageRect.top.toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
        val right = imageRect.right.toInt().coerceIn(left + 1, w)
        val bottom = imageRect.bottom.toInt().coerceIn(top + 1, h)
        val cropW = right - left
        val cropH = bottom - top
        val cropped = Bitmap.createBitmap(overlay, left, top, cropW, cropH)
        return if (cropW == targetW && cropH == targetH) cropped
        else Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    }
}
