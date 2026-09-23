package com.sza.fastmediasorter.ui.browse.sheets

import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetBinaryFileBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileMenuAction
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet

/**
 * Recreation-safe replacement for the raw `BottomSheetDialog` menu `BrowseBinaryFileHandler` used
 * to build (S3244). Callbacks are set by [newInstance] rather than serialized into arguments - the
 * sheet is a modal action menu tied to the file the host has selected, never restored from the back
 * stack, the same convention `SendToBottomSheet` uses for the same reason.
 */
class BrowseBinaryFileBottomSheet : BaseAppBottomSheet() {

    class Callbacks(
        val onShare: () -> Unit,
        val onOpenWith: () -> Unit,
        val onCopy: () -> Unit,
        val onMove: () -> Unit,
        val onRename: () -> Unit,
        val onDelete: () -> Unit,
    )

    override val contentLayout: Int = R.layout.bottom_sheet_binary_file
    override val requestKey: String = REQUEST_KEY

    private var mediaFile: MediaFile? = null
    private var callbacks: Callbacks? = null
    private var menuActions: Set<BrowseBinaryFileMenuAction> = emptySet()

    companion object {
        private const val REQUEST_KEY = "browse_binary_file_sheet"

        fun newInstance(
            mediaFile: MediaFile,
            callbacks: Callbacks,
            menuActions: Set<BrowseBinaryFileMenuAction> = emptySet(),
        ): BrowseBinaryFileBottomSheet = BrowseBinaryFileBottomSheet().also {
            it.mediaFile = mediaFile
            it.callbacks = callbacks
            it.menuActions = menuActions
        }
    }

    override fun bindContent(content: View) {
        val file = mediaFile ?: run {
            dismiss()
            return
        }
        val binding = BottomSheetBinaryFileBinding.bind(content)
        binding.tvFileName.text = file.name

        bindRow(binding.btnShare, Callbacks::onShare)
        bindRow(binding.btnOpenWith, Callbacks::onOpenWith)
        bindRow(binding.btnCopy, Callbacks::onCopy)
        bindRow(binding.btnMove, Callbacks::onMove)
        bindRow(binding.btnRename, Callbacks::onRename)
        bindRow(binding.btnDelete, Callbacks::onDelete)

        menuActions.forEach { action -> action.bind(content, file) { dismiss() } }
    }

    private fun bindRow(row: View, action: (Callbacks) -> () -> Unit) {
        row.setOnClickListener {
            callbacks?.let(action)?.invoke()
            dismiss()
        }
    }
}
