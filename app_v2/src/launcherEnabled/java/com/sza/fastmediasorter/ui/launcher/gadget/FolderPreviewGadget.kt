package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherPreviewBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S0404: the newest few files of a resource, as a peek at the folder.
 */
class FolderPreviewGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_FOLDER_PREVIEW
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 2
    override val labelRes: Int = R.string.launcher_gadget_folder_preview
    override val iconRes: Int = R.drawable.ic_view_grid
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        FolderPreviewGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class FolderPreviewGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherPreviewBinding.inflate(LayoutInflater.from(context), this)

    private val thumbs: List<ImageView> = listOf(
        binding.gadgetPreviewThumb0,
        binding.gadgetPreviewThumb1,
        binding.gadgetPreviewThumb2,
        binding.gadgetPreviewThumb3,
    )

    init {
        binding.gadgetPreviewTitle.setOnClickListener {
            resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.BROWSE)) }
        }
        thumbs.forEach { thumb ->
            // Every tile opens the same thing: the show starts at the folder, not at the tapped file
            // (per-file positioning is not iteration-1). The label says so rather than naming the file,
            // which would promise a jump this does not make.
            thumb.contentDescription = context.getString(R.string.launcher_gadget_folder_preview_open)
            thumb.setOnClickListener {
                resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.SLIDESHOW)) }
            }
        }
    }

    override suspend fun CoroutineScope.onActive() {
        val id = resourceId
        if (id == null) {
            showMessage()
            return
        }
        // DATE_DESC sorts by createdDate, which is the field the scanners actually populate -
        // lastModified is 0L for every network scanner, so "newest" by it would be meaningless.
        when (val result = loadFiles(id, limit = thumbs.size, sortMode = SortMode.DATE_DESC)) {
            is LoadLauncherGadgetFilesUseCase.Result.Unavailable -> showMessage()
            is LoadLauncherGadgetFilesUseCase.Result.Files -> {
                binding.gadgetPreviewTitle.text = result.resourceName
                binding.gadgetPreviewMessage.isVisible = false
                binding.gadgetPreviewGrid.isVisible = true
                thumbs.forEachIndexed { index, thumb -> bindThumb(thumb, result.files.getOrNull(index)) }
            }
        }
    }

    override fun onDetachedFromWindow() {
        // Glide ownership (audit protocol): release every target when the cell goes away, or a rebuilt
        // desktop leaves decoded bitmaps bound to views nobody will ever show again.
        thumbs.forEach { thumb -> runCatching { Glide.with(thumb).clear(thumb) } }
        super.onDetachedFromWindow()
    }

    private fun bindThumb(thumb: ImageView, file: MediaFile?) {
        // Fewer files than tiles: the spare tiles go away rather than showing a placeholder each, which
        // would read as "four files, two broken".
        thumb.isVisible = file != null
        if (file == null) {
            runCatching { Glide.with(thumb).clear(thumb) }
            return
        }
        val model = localModel(file)
        if (model == null) {
            runCatching { Glide.with(thumb).clear(thumb) }
            thumb.setImageResource(R.drawable.ic_folder)
            return
        }
        val size = thumb.resources.getDimensionPixelSize(R.dimen.launcher_gadget_thumb_size)
        Glide.with(thumb)
            .load(model)
            // Decode at display size, not at source size (audit protocol Glide ownership).
            .override(size, size)
            .centerCrop()
            // S1317: still preview tile - a still frame is correct, and it also avoids keeping an
            // animated-drawable decoder alive behind the home screen.
            .dontAnimate()
            .placeholder(R.drawable.ic_folder)
            .error(R.drawable.ic_folder)
            .into(thumb)
    }

    /**
     * Only device-local sources are decoded: Glide is registered for the typed `NetworkFileData` /
     * `CloudThumbnailData` models that `AdapterThumbnailLoader` builds, never for a bare path string,
     * so an SMB/cloud file would silently fail to load. Such a file gets the generic mark instead of a
     * blank tile; reimplementing that branching is not iteration-1.
     */
    private fun localModel(file: MediaFile): Any? = when {
        file.contentUri?.startsWith("content://") == true -> Uri.parse(file.contentUri)
        file.path.startsWith("/") -> file.path
        else -> null
    }

    private fun showMessage() {
        binding.gadgetPreviewTitle.setText(R.string.launcher_gadget_folder_preview)
        binding.gadgetPreviewMessage.setText(R.string.launcher_home_cell_unavailable)
        binding.gadgetPreviewMessage.isVisible = true
        binding.gadgetPreviewGrid.isVisible = false
    }
}
