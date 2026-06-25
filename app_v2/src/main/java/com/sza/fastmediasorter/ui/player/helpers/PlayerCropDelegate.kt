package com.sza.fastmediasorter.ui.player.helpers

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
            val rect = cropView.getCropRectNormalized()
            val vw = cropView.width
            val vh = cropView.height

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
