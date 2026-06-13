package com.sza.fastmediasorter.ui.player.standalone

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import com.sza.fastmediasorter.ui.player.helpers.DrawKeepExportHelper
import com.sza.fastmediasorter.ui.player.helpers.ImageDrawOverlayManager
import com.sza.fastmediasorter.ui.player.helpers.ImageEditorFileNamer
import com.sza.fastmediasorter.ui.player.helpers.ScreenRotationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S0410: drives the shared [ImageDrawOverlayManager] for the standalone image viewer. The standalone
 * host has no resource/folder context, so every draw save is a save-as: the base image and the draw
 * overlay are merged and written to a NEW file in Downloads (the source URI is never overwritten).
 * Mirrors the in-app PlayerDrawingSaveHelper merge/crop pipeline without its staging/resource paths.
 */
class StandaloneDrawSaveHelper(
    private val activity: AppCompatActivity,
    imageContainer: ViewGroup,
    toolbarRoot: View,
    screenRotationManager: ScreenRotationManager,
    hasAccelerometer: Boolean,
    keepExportHelper: DrawKeepExportHelper,
    private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val getCurrentFile: () -> MediaFile?,
    private val getDisplayedBitmap: () -> Bitmap?,
    private val getImageDisplayRect: () -> RectF,
    private val onDelete: () -> Unit,
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
    }

    fun enterDrawMode() {
        manager.currentFile = getCurrentFile()
        manager.enterDrawMode()
    }

    /** True if draw mode was active and consumed the back press. */
    fun handleBackPress(): Boolean = manager.handleBackPress()

    private fun save(overlay: Bitmap, filename: String?, close: Boolean) {
        Timber.d("S0410: standalone draw save merged to Pictures")
        val base = getDisplayedBitmap() ?: run {
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
        val finalName = if (chosen.contains('.')) chosen
            else chosen + if (format == Bitmap.CompressFormat.JPEG) ".jpg" else ".png"
        val displayRect = getImageDisplayRect()
        val mime = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
        lifecycleScope.launch {
            // Wrap the whole pipeline: a crop-geometry edge case, a merge failure, or a scoped-storage
            // write denial must surface a toast, never crash the activity. Save target is MediaStore
            // Pictures (scoped-storage-correct, no storage permission needed for our own insert;
            // mirrors the host's saveCurrentFrame).
            val saved = try {
                val cropped = cropOverlayToImage(overlay, displayRect, base.width, base.height)
                val bytes = mergeDrawOverlayUseCase.execute(base, cropped, format).getOrThrow()
                withContext(Dispatchers.IO) {
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
            } catch (e: Exception) {
                Timber.e(e, "Standalone draw save failed")
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
