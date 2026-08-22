package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherMediaDocumentWindowBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S1754: the newest document of a resource, readable inside its own desktop cell.
 *
 * Plain text only, on purpose. PDF and EPUB need their own renderers, which live in the reader this cell
 * opens on a tap - a window that showed their raw bytes would be worse than one that names the file and
 * says where to read it.
 */
class DocumentWindowGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_MEDIA_DOCUMENT_WINDOW
    override val defaultSpanW: Int = MediaWindow.SPAN
    override val defaultSpanH: Int = MediaWindow.SPAN
    override val labelRes: Int = R.string.launcher_gadget_media_document_window
    override val iconRes: Int = R.drawable.ic_book
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        DocumentWindowGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class DocumentWindowGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherMediaDocumentWindowBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.mediaDocumentWindowTitle.setOnClickListener {
            resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.PLAY)) }
        }
    }

    override suspend fun CoroutineScope.onActive() {
        val loaded = resourceId?.let {
            loadFiles(it, limit = MediaWindow.SCAN_LIMIT, sortMode = SortMode.DATE_DESC)
        } as? LoadLauncherGadgetFilesUseCase.Result.Files
        Timber.d("S1754: document window bound to resource %s", resourceId)
        val document = loaded?.files?.firstOrNull { it.type.isDocumentFile() }
        if (document == null) {
            showMessage()
        } else {
            binding.mediaDocumentWindowTitle.text = document.name
            binding.mediaDocumentWindowText.text = body(document)
        }
    }

    /**
     * The opening of the file, or the invitation to open it in the reader.
     *
     * Capped rather than streamed: a cell shows a paragraph, and reading a whole book into a home-screen
     * view to display its first lines is the cost this cap exists to refuse.
     */
    private suspend fun body(document: MediaFile): CharSequence = when {
        document.type != MediaType.TEXT -> context.getString(R.string.launcher_gadget_media_document_open)
        else -> readHead(document.path)
            ?: context.getString(R.string.launcher_gadget_media_document_open)
    }

    private suspend fun readHead(path: String): String? = withContext(Dispatchers.IO) {
        if (!path.startsWith("/")) {
            return@withContext null
        }
        runCatching {
            File(path).inputStream().use { stream ->
                val buffer = ByteArray(PREVIEW_BYTES)
                val read = stream.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
            }
        }.onFailure {
            Timber.w(it, "Document window: %s unreadable", path)
        }.getOrNull()
    }

    private fun showMessage() {
        binding.mediaDocumentWindowTitle.setText(R.string.launcher_gadget_media_document_window)
        binding.mediaDocumentWindowText.setText(R.string.launcher_home_cell_unavailable)
    }

    private companion object {
        /** Roughly a screenful of prose - far more than a 2x2 cell scrolls through, and bounded. */
        const val PREVIEW_BYTES = 4_096
    }
}
