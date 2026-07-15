package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.coroutines.launch
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.contracts.PlayerActionHost
import com.sza.fastmediasorter.ui.player.views.CropOverlayView

/**
 * Handles the crop overlay lifecycle. S0393: consumes the binding-agnostic [PlayerActionHost] seam
 * instead of `PlayerActivity`, so the in-app player and the standalone image host share one delegate
 * (was duplicated as `StandaloneImageEditController` in S0390). Host-specific behaviour (navigate vs
 * toast after a save-as, in-place re-render) is delegated to the host, not branched here.
 */
class PlayerCropDelegate(
    private val host: PlayerActionHost,
    private val imageCropManager: ImageCropManager,
) {

    private var cropOverlayView: View? = null

    // ── Public entry points ──────────────────────────────────────────────────

    fun enterCropMode(mode: ImageCropManager.CropMode) {
        val file = host.actionCurrentFile ?: return
        val resource = host.actionCurrentResource
        imageCropManager.enterCropMode(mode, file, resource, imageCropCallback)
        host.prepareImageSurfacesForCrop()
        showCropOverlay(mode)
    }

    fun startCompressedCopy() {
        val file = host.actionCurrentFile ?: return
        val resource = host.actionCurrentResource
        val isReadOnly = resource?.isReadOnly == true
        imageCropManager.showCropFilenameDialog(
            host.hostActivity,
            ImageCropManager.CropMode.COMPRESS_COPY,
            file,
            isReadOnly
        ) { fileName ->
            imageCropManager.lifecycleScope.launch {
                imageCropManager.performCompressedCopy(
                    file, resource, fileName,
                    if (isReadOnly) null else resource,
                    imageCropCallback
                )
            }
        }
    }

    // ── Private overlay lifecycle ────────────────────────────────────────────

    private fun showCropOverlay(mode: ImageCropManager.CropMode) {
        // Mount the overlay to the media content area (FrameLayout sized to the photo display area).
        val parent: ViewGroup = host.overlayMountTarget
        val overlay = host.hostActivity.layoutInflater
            .inflate(R.layout.player_crop_overlay_content, parent, false)
        cropOverlayView = overlay
        parent.addView(overlay)

        val cropView = overlay.findViewById<CropOverlayView>(R.id.crop_overlay_view)
        cropView.pinchPassthroughTarget = host.imagePinchTarget
        val btnConfirm = overlay.findViewById<View>(R.id.btn_crop_confirm)
        val btnCancel = overlay.findViewById<View>(R.id.btn_crop_cancel)

        btnCancel.setOnClickListener {
            imageCropManager.exitCropMode()
        }

        btnConfirm.setOnClickListener {
            val file = host.actionCurrentFile ?: run {
                imageCropManager.exitCropMode()
                return@setOnClickListener
            }
            val resource = host.actionCurrentResource
            val isReadOnly = resource?.isReadOnly == true
            val vw = cropView.width
            val vh = cropView.height
            // Map the overlay selection through the live on-screen image rect so the crop honors the
            // user's current zoom/pan (WYSIWYG) instead of the fit-to-view image. Mirrors DrawCropCompositor.
            val rect = toImageNormalizedRect(cropView.getCropRectNormalized(), vw, vh)

            when (mode) {
                ImageCropManager.CropMode.CROP -> {
                    host.hostScope.launch {
                        imageCropManager.performCrop(rect, vw, vh, file, resource, imageCropCallback)
                    }
                }
                ImageCropManager.CropMode.CROP_TO_FILE -> {
                    imageCropManager.showCropFilenameDialog(
                        host.hostActivity,
                        ImageCropManager.CropMode.CROP_TO_FILE,
                        file,
                        isReadOnly
                    ) { fileName ->
                        host.hostScope.launch {
                            imageCropManager.performCropToFile(
                                rect, vw, vh, file, resource, fileName,
                                if (isReadOnly) null else resource,
                                imageCropCallback
                            )
                        }
                    }
                }
                ImageCropManager.CropMode.COMPRESS_COPY -> {
                    imageCropManager.exitCropMode()
                }
            }
        }
    }

    /**
     * Converts an overlay-normalized selection (0..1 over the crop view) into an image-normalized rect
     * (0..1 over the displayed image), using the live on-screen image rectangle so zoom, pan, and
     * letterbox are honored. Falls back to the raw overlay-normalized rect for the non-zoomable surface
     * (empty display rect), preserving the legacy fit-to-view mapping.
     */
    private fun toImageNormalizedRect(overlayNormalized: RectF, viewW: Int, viewH: Int): RectF {
        val displayRect = host.imageDisplayRect()
        if (viewW <= 0 || viewH <= 0 || displayRect.width() <= 0f || displayRect.height() <= 0f) {
            return overlayNormalized
        }
        // Lift the overlay-normalized selection into view-pixel space (shared with displayRect),
        // then re-normalize against the image rect and clamp to the visible image.
        val selLeft = overlayNormalized.left * viewW
        val selTop = overlayNormalized.top * viewH
        val selRight = overlayNormalized.right * viewW
        val selBottom = overlayNormalized.bottom * viewH
        return RectF(
            ((selLeft - displayRect.left) / displayRect.width()).coerceIn(0f, 1f),
            ((selTop - displayRect.top) / displayRect.height()).coerceIn(0f, 1f),
            ((selRight - displayRect.left) / displayRect.width()).coerceIn(0f, 1f),
            ((selBottom - displayRect.top) / displayRect.height()).coerceIn(0f, 1f)
        )
    }

    private fun hideCropOverlay() {
        cropOverlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        cropOverlayView = null
    }

    // ── Callback ─────────────────────────────────────────────────────────────

    private val imageCropCallback = object : ImageCropManager.Callback {
        override fun onCropModeEntered(mode: ImageCropManager.CropMode) {
            // Overlay is shown from enterCropMode before this callback fires
        }

        override fun onCropModeExited() {
            hideCropOverlay()
            // Engine fires this only on cancel/exit (success path uses onSuccess), so it signals a
            // dismissed crop - hosts chaining a post-crop action drop it here.
            host.onCropFlowCancelled()
        }

        override fun onSuccess(savedPath: String, mode: ImageCropManager.CropMode) {
            hideCropOverlay()
            if (mode == ImageCropManager.CropMode.CROP) {
                // Original file was overwritten in-place - host re-decodes it.
                host.reloadCurrentImageInPlace()
                return
            }
            // A new file was created in the folder - host navigates (in-app) or toasts (standalone).
            host.onFileSavedInFolder(savedPath)
        }

        override fun onError(message: String) {
            hideCropOverlay()
            Toast.makeText(host.hostActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
