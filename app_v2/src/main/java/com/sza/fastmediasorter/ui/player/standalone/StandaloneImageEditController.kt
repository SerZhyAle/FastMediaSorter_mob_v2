package com.sza.fastmediasorter.ui.player.standalone

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.player.helpers.ImageCropManager
import com.sza.fastmediasorter.ui.player.views.CropOverlayView
import kotlinx.coroutines.launch

/**
 * S0390: drives the generic [ImageCropManager] from a standalone host - the standalone-side
 * equivalent of the PlayerActivity-bound `PlayerCropDelegate`, without a `MediaResource` context.
 *
 * The standalone file is always treated as a writable local image (`resource = null`): the host only
 * hands an editable file here once [StandalonePlayerViewModel.editableImageFile] has resolved the
 * external URI to a real filesystem path, so [ImageCropManager.ensureLocalSource]'s local branch is
 * valid. CROP overwrites that path in place; CROP_TO_FILE / COMPRESS_COPY save a copy (Downloads when
 * no writable resource is supplied), mirroring the in-app behaviour. The engine itself is untouched.
 */
class StandaloneImageEditController(
    private val activity: AppCompatActivity,
    private val mediaContentArea: ViewGroup,
    private val pinchTarget: View,
    private val imageCropManager: ImageCropManager,
    private val getEditFile: () -> MediaFile?,
    private val onInPlaceCropSaved: () -> Unit,
) {

    private var cropOverlayView: View? = null

    fun enterCropMode(mode: ImageCropManager.CropMode) {
        val file = getEditFile() ?: return
        imageCropManager.enterCropMode(mode, file, currentResource = null, callback = cropCallback)
        // Match the in-app crop alignment: the overlay maps a 0..1 rect over a FIT_CENTER image.
        (pinchTarget as? ImageView)?.scaleType = ImageView.ScaleType.FIT_CENTER
        showCropOverlay(mode)
    }

    fun startCompressedCopy() {
        val file = getEditFile() ?: return
        imageCropManager.showCropFilenameDialog(
            activity,
            ImageCropManager.CropMode.COMPRESS_COPY,
            file,
            isReadOnly = false,
        ) { fileName ->
            imageCropManager.lifecycleScope.launch {
                imageCropManager.performCompressedCopy(
                    file, currentResource = null, fileName, saveTo = null, cropCallback,
                )
            }
        }
    }

    private fun showCropOverlay(mode: ImageCropManager.CropMode) {
        val overlay = activity.layoutInflater
            .inflate(R.layout.player_crop_overlay_content, mediaContentArea, false)
        cropOverlayView = overlay
        mediaContentArea.addView(overlay)

        val cropView = overlay.findViewById<CropOverlayView>(R.id.crop_overlay_view)
        cropView.pinchPassthroughTarget = pinchTarget
        overlay.findViewById<View>(R.id.btn_crop_cancel).setOnClickListener {
            imageCropManager.exitCropMode()
        }
        overlay.findViewById<View>(R.id.btn_crop_confirm).setOnClickListener {
            val file = getEditFile() ?: run {
                imageCropManager.exitCropMode()
                return@setOnClickListener
            }
            val rect = cropView.getCropRectNormalized()
            val vw = cropView.width
            val vh = cropView.height
            when (mode) {
                ImageCropManager.CropMode.CROP -> activity.lifecycleScope.launch {
                    imageCropManager.performCrop(rect, vw, vh, file, null, cropCallback)
                }
                ImageCropManager.CropMode.CROP_TO_FILE -> imageCropManager.showCropFilenameDialog(
                    activity, ImageCropManager.CropMode.CROP_TO_FILE, file, isReadOnly = false,
                ) { fileName ->
                    activity.lifecycleScope.launch {
                        imageCropManager.performCropToFile(
                            rect, vw, vh, file, null, fileName, saveTo = null, cropCallback,
                        )
                    }
                }
                ImageCropManager.CropMode.COMPRESS_COPY -> imageCropManager.exitCropMode()
            }
        }
    }

    private fun hideCropOverlay() {
        cropOverlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        cropOverlayView = null
    }

    private val cropCallback = object : ImageCropManager.Callback {
        override fun onCropModeEntered(mode: ImageCropManager.CropMode) = Unit

        override fun onCropModeExited() = hideCropOverlay()

        override fun onSuccess(savedPath: String, mode: ImageCropManager.CropMode) {
            hideCropOverlay()
            if (mode == ImageCropManager.CropMode.CROP) {
                onInPlaceCropSaved()
            } else {
                val fileName = savedPath.substringAfterLast('/')
                Toast.makeText(
                    activity,
                    activity.getString(R.string.crop_file_created, fileName),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        override fun onError(message: String) {
            hideCropOverlay()
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }
}
