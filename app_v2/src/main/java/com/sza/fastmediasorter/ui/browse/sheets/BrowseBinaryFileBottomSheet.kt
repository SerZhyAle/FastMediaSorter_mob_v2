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

    override val contentLayout: Int = R.layout.bottom_sheet_binary_file
    override val requestKey: String = REQUEST_KEY

    private var mediaFile: MediaFile? = null
    private var onShare: (() -> Unit)? = null
    private var onOpenWith: (() -> Unit)? = null
    private var onCopy: (() -> Unit)? = null
    private var onMove: (() -> Unit)? = null
    private var onRename: (() -> Unit)? = null
    private var onDelete: (() -> Unit)? = null
    private var menuActions: Set<BrowseBinaryFileMenuAction> = emptySet()

    companion object {
        private const val REQUEST_KEY = "browse_binary_file_sheet"

        fun newInstance(
            mediaFile: MediaFile,
            onShare: () -> Unit,
            onOpenWith: () -> Unit,
            onCopy: () -> Unit,
            onMove: () -> Unit,
            onRename: () -> Unit,
            onDelete: () -> Unit,
            menuActions: Set<BrowseBinaryFileMenuAction> = emptySet(),
        ): BrowseBinaryFileBottomSheet = BrowseBinaryFileBottomSheet().also {
            it.mediaFile = mediaFile
            it.onShare = onShare
            it.onOpenWith = onOpenWith
            it.onCopy = onCopy
            it.onMove = onMove
            it.onRename = onRename
            it.onDelete = onDelete
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

        binding.btnShare.setOnClickListener { onShare?.invoke(); dismiss() }
        binding.btnOpenWith.setOnClickListener { onOpenWith?.invoke(); dismiss() }
        binding.btnCopy.setOnClickListener { onCopy?.invoke(); dismiss() }
        binding.btnMove.setOnClickListener { onMove?.invoke(); dismiss() }
        binding.btnRename.setOnClickListener { onRename?.invoke(); dismiss() }
        binding.btnDelete.setOnClickListener { onDelete?.invoke(); dismiss() }

        menuActions.forEach { action -> action.bind(content, file) { dismiss() } }
    }
}
