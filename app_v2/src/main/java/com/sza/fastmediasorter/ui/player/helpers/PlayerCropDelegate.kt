package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.views.CropOverlayView

/**
 * Handles the crop overlay lifecycle for [PlayerActivity].
 * Extracted from PlayerActivity (S0106) to keep that class within the LOC budget.
 */
class PlayerCropDelegate(
    private val activity: PlayerActivity,
    private val imageCropManager: ImageCropManager,
) {

    private var cropOverlayView: View? = null

    // ── Public entry points ──────────────────────────────────────────────────

    fun enterCropMode(mode: ImageCropManager.CropMode) {
        val file = activity.viewModel.state.value.currentFile ?: return
        val resource = activity.viewModel.state.value.resource
        imageCropManager.enterCropMode(mode, file, resource, imageCropCallback)
        showCropOverlay(mode)
    }

    fun startCompressedCopy() {
        val file = activity.viewModel.state.value.currentFile ?: return
        val resource = activity.viewModel.state.value.resource
        val isReadOnly = resource?.isReadOnly == true
        imageCropManager.showCropFilenameDialog(
            activity,
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
        val rootVg = activity.activityBinding.root as? ViewGroup ?: return
        val overlay = activity.layoutInflater.inflate(R.layout.player_crop_overlay_content, rootVg, false)
        cropOverlayView = overlay
        rootVg.addView(overlay)

        val cropView = overlay.findViewById<CropOverlayView>(R.id.crop_overlay_view)
        val btnConfirm = overlay.findViewById<View>(R.id.btn_crop_confirm)
        val btnCancel = overlay.findViewById<View>(R.id.btn_crop_cancel)

        btnCancel.setOnClickListener {
            imageCropManager.exitCropMode()
        }

        btnConfirm.setOnClickListener {
            val file = activity.viewModel.state.value.currentFile ?: run {
                imageCropManager.exitCropMode()
                return@setOnClickListener
            }
            val resource = activity.viewModel.state.value.resource
            val isReadOnly = resource?.isReadOnly == true
            val rect = cropView.getCropRectNormalized()
            val vw = cropView.width
            val vh = cropView.height

            when (mode) {
                ImageCropManager.CropMode.CROP -> {
                    activity.lifecycleScope.launch {
                        imageCropManager.performCrop(rect, vw, vh, file, resource, imageCropCallback)
                    }
                }
                ImageCropManager.CropMode.CROP_TO_FILE -> {
                    imageCropManager.showCropFilenameDialog(
                        activity,
                        ImageCropManager.CropMode.CROP_TO_FILE,
                        file,
                        isReadOnly
                    ) { fileName ->
                        activity.lifecycleScope.launch {
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
        }

        override fun onSuccess(savedPath: String, mode: ImageCropManager.CropMode) {
            hideCropOverlay()
            val fileName = savedPath.substringAfterLast('/')
            if (mode == ImageCropManager.CropMode.CROP) {
                Toast.makeText(activity, activity.getString(R.string.crop_file_created, fileName), Toast.LENGTH_LONG).show()
                return
            }
            val currentParent = activity.viewModel.state.value.currentFile?.path?.substringBeforeLast('/') ?: ""
            val savedParent = savedPath.substringBeforeLast('/')
            if (currentParent.isNotEmpty() && currentParent == savedParent) {
                activity.viewModel.reloadFiles()
                activity.lifecycleScope.launch {
                    val files = withTimeoutOrNull(10_000L) {
                        activity.viewModel.state.map { it.files }.distinctUntilChanged()
                            .first { files -> files.any { it.path == savedPath } }
                    }
                    val idx = files?.indexOfFirst { it.path == savedPath } ?: -1
                    if (idx >= 0) {
                        activity.viewModel.jumpToIndex(idx, manual = true)
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.crop_file_created, fileName), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(activity, activity.getString(R.string.crop_file_created, fileName), Toast.LENGTH_LONG).show()
            }
        }

        override fun onError(message: String) {
            hideCropOverlay()
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }
}
