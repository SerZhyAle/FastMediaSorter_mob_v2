package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherMediaImageWindowBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * S1754: a resource's pictures, cycling inside their own desktop cell.
 *
 * Complements [FolderPreviewGadget] rather than replacing it: that one answers "what is in this folder"
 * with four thumbnails at once, while this is a photo frame showing one picture at cell size.
 */
class ImageWindowGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_MEDIA_IMAGE_WINDOW
    override val defaultSpanW: Int = MediaWindow.SPAN
    override val defaultSpanH: Int = MediaWindow.SPAN
    override val labelRes: Int = R.string.launcher_gadget_media_image_window
    override val iconRes: Int = R.drawable.ic_image
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        ImageWindowGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class ImageWindowGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherMediaImageWindowBinding.inflate(LayoutInflater.from(context), this)

    /** A paused frame stays on screen and only the timer stops, so a tap never blanks the cell. */
    private var paused = false

    init {
        binding.mediaImageWindowTitle.setOnClickListener {
            resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.SLIDESHOW)) }
        }
        binding.mediaImageWindowFrame.setOnClickListener { paused = !paused }
    }

    override suspend fun CoroutineScope.onActive() {
        val loaded = resourceId?.let {
            loadFiles(it, limit = MediaWindow.SCAN_LIMIT, sortMode = SortMode.DATE_DESC)
        } as? LoadLauncherGadgetFilesUseCase.Result.Files
        val frames = loaded?.files
            ?.filter { it.type == MediaType.IMAGE || it.type == MediaType.GIF }
            ?.mapNotNull(MediaWindow::localModel)
            .orEmpty()
        if (loaded == null || frames.isEmpty()) {
            showMessage()
        } else {
            binding.mediaImageWindowTitle.text = loaded.resourceName
            cycle(frames)
        }
    }

    /**
     * Re-checks the pause flag on every tick rather than around the whole wait: a frame resumes on the
     * next tick after the tap, not one full interval later.
     */
    private suspend fun CoroutineScope.cycle(frames: List<Uri>) {
        var index = 0
        while (isActive) {
            show(frames[index % frames.size])
            delay(MediaWindow.TICK_MS)
            if (!paused) {
                index++
            }
        }
    }

    private fun show(model: Uri) {
        val view = binding.mediaImageWindowFrame
        val width = view.width.coerceAtLeast(MediaWindow.MIN_DECODE_PX)
        val height = view.height.coerceAtLeast(MediaWindow.MIN_DECODE_PX)
        Glide.with(view)
            // Decoded at cell size, never source size (audit protocol, Glide ownership): a desktop can
            // carry several of these at once, and a full-resolution decode per cell is what kills it.
            .load(model)
            .override(width, height)
            .centerCrop()
            .into(view)
    }

    override fun onDetachedFromWindow() {
        runCatching { Glide.with(binding.mediaImageWindowFrame).clear(binding.mediaImageWindowFrame) }
        super.onDetachedFromWindow()
    }

    private fun showMessage() {
        binding.mediaImageWindowTitle.setText(R.string.launcher_gadget_media_image_window)
        binding.mediaImageWindowMessage.setText(R.string.launcher_home_cell_unavailable)
        binding.mediaImageWindowMessage.isVisible = true
        binding.mediaImageWindowFrame.isVisible = false
    }
}
