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
import timber.log.Timber
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
        Timber.d("S0127: PlayerCropDelegate forcing FIT_CENTER on photoView/imageView for crop")
        activity.activityBinding.photoView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        activity.activityBinding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
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
        // S0106 fix: mount overlay to mediaContentArea (FrameLayout sized to the photo display area).
        // Mounting to activityBinding.root (vertical LinearLayout) collapsed PhotoView height to 0
        // because a match_parent overlay without layout_weight starves the weighted media slot.
        val parent: ViewGroup = activity.activityBinding.mediaContentArea
        val overlay = activity.layoutInflater.inflate(R.layout.player_crop_overlay_content, parent, false)
        cropOverlayView = overlay
        parent.addView(overlay)
        Timber.d("S0106: showCropOverlay attached to mediaContentArea size=${parent.width}x${parent.height}")

        val cropView = overlay.findViewById<CropOverlayView>(R.id.crop_overlay_view)
        cropView.pinchPassthroughTarget = activity.activityBinding.photoView
        Timber.d("S0127: PlayerCropDelegate wired pinchPassthroughTarget=photoView")
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
