package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Turns a crop selection into a new cropped working composite for the draw editor (S0679).
 *
 * The base layer is taken at full source resolution for local files (region-decode via
 * [ImageCropManager]); network / unreadable sources fall back to cropping the on-screen base
 * bitmap (display resolution). Existing annotations are cropped to the same region, scaled to the
 * base-crop size, and baked into the result so the user keeps drawing on the cropped image.
 *
 * No UI. All bitmap work runs off the main thread; intermediates this class owns are recycled.
 */
class DrawCropCompositor(
    private val cropManager: ImageCropManager,
    private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase
) {

    /**
     * @param baseBitmap     Currently displayed base image (not recycled by this method).
     * @param overlayBitmap  Draw-canvas overlay (annotations); null/absent -> base crop only.
     * @param displayRect    Where the image is drawn, in canvas coordinates.
     * @param selectionRect  User crop selection, in canvas coordinates.
     * @param canvasWidth    Canvas / overlay width in pixels.
     * @param canvasHeight   Canvas / overlay height in pixels.
     * @return new cropped working bitmap with annotations baked in.
     */
    suspend fun composeCroppedWorkingImage(
        baseBitmap: Bitmap,
        overlayBitmap: Bitmap?,
        displayRect: RectF,
        selectionRect: RectF,
        canvasWidth: Int,
        canvasHeight: Int,
        currentFile: MediaFile,
        currentResource: MediaResource?
    ): Bitmap = withContext(Dispatchers.Default) {
        val intersection = RectF(selectionRect)
        if (!intersection.intersect(displayRect) ||
            intersection.width() < 1f || intersection.height() < 1f
        ) {
            throw IllegalArgumentException("Crop selection does not overlap the image")
        }
        val normalised = RectF(
            ((intersection.left - displayRect.left) / displayRect.width()).coerceIn(0f, 1f),
            ((intersection.top - displayRect.top) / displayRect.height()).coerceIn(0f, 1f),
            ((intersection.right - displayRect.left) / displayRect.width()).coerceIn(0f, 1f),
            ((intersection.bottom - displayRect.top) / displayRect.height()).coerceIn(0f, 1f)
        )

        val croppedBase = decodeBaseCrop(baseBitmap, normalised, canvasWidth, canvasHeight, currentFile, currentResource)
        if (overlayBitmap == null) {
            return@withContext croppedBase
        }
        val result = bakeOverlay(croppedBase, overlayBitmap, intersection)
        // Guard against the whole-image fallback where createBitmap returns baseBitmap itself.
        if (croppedBase !== baseBitmap && croppedBase !== result) croppedBase.recycle()
        result
    }

    private suspend fun decodeBaseCrop(
        baseBitmap: Bitmap,
        normalised: RectF,
        canvasWidth: Int,
        canvasHeight: Int,
        currentFile: MediaFile,
        currentResource: MediaResource?
    ): Bitmap {
        if (isLocalSource(currentFile, currentResource)) {
            try {
                return cropManager.decodeCroppedRegionBitmap(
                    normalised, canvasWidth, canvasHeight, currentFile, currentResource
                )
            } catch (e: Exception) {
                // Full-resolution path is best-effort; degrade to the on-screen composite (§6.1).
                Timber.i(e, "Draw crop: full-res region decode failed, using working composite")
            }
        }
        return cropBitmap(baseBitmap, normalised)
    }

    private suspend fun bakeOverlay(croppedBase: Bitmap, overlay: Bitmap, intersection: RectF): Bitmap {
        val ix = intersection.left.toInt().coerceIn(0, overlay.width)
        val iy = intersection.top.toInt().coerceIn(0, overlay.height)
        val iw = intersection.width().toInt().coerceAtLeast(1).coerceAtMost(overlay.width - ix)
        val ih = intersection.height().toInt().coerceAtLeast(1).coerceAtMost(overlay.height - iy)
        val overlayCrop = Bitmap.createBitmap(overlay, ix, iy, iw, ih)
        val scaledOverlay =
            if (overlayCrop.width != croppedBase.width || overlayCrop.height != croppedBase.height) {
                Bitmap.createScaledBitmap(overlayCrop, croppedBase.width, croppedBase.height, true)
            } else {
                overlayCrop
            }
        // Lossless intermediate: the working composite is re-compressed once on final save/share.
        val mergedBytes = mergeDrawOverlayUseCase
            .execute(croppedBase, scaledOverlay, Bitmap.CompressFormat.PNG)
            .getOrThrow()
        if (scaledOverlay !== overlayCrop) scaledOverlay.recycle()
        if (overlayCrop !== overlay) overlayCrop.recycle()
        return BitmapFactory.decodeByteArray(mergedBytes, 0, mergedBytes.size)
            ?: throw IllegalStateException("Failed to decode merged crop bitmap")
    }

    private fun cropBitmap(base: Bitmap, normalised: RectF): Bitmap {
        val bx = (normalised.left * base.width).toInt().coerceIn(0, base.width)
        val by = (normalised.top * base.height).toInt().coerceIn(0, base.height)
        val bw = (normalised.width() * base.width).toInt().coerceAtLeast(1).coerceAtMost(base.width - bx)
        val bh = (normalised.height() * base.height).toInt().coerceAtLeast(1).coerceAtMost(base.height - by)
        return Bitmap.createBitmap(base, bx, by, bw, bh)
    }

    // Mirrors ImageCropManager.ensureLocalSource: avoid downloading a network source for full-res.
    private fun isLocalSource(file: MediaFile, resource: MediaResource?): Boolean =
        resource == null || resource.type.name == "LOCAL" ||
            file.path.startsWith("/") || file.path.startsWith("file://")
}
