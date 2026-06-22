package com.sza.fastmediasorter.ui.browse.managers

import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.common.dragselect.DragSelectTouchListener

/**
 * S0512: wires [DragSelectTouchListener] onto the Browse media RecyclerView.
 *
 * Selection flows through the existing range path ([BrowseViewModel.selectFileRange] ->
 * BrowseSelectionManager.selectRange) - no new selection store. Positions are resolved against
 * the adapter's current list; folder rows are skipped (folders are not selectable).
 *
 * Mode is implicit: touch drag-select is only allowed while a multi-select is already active
 * (selectedFiles.isNotEmpty()), mirroring the long-press range entry. Mouse band-select is
 * always allowed.
 */
class BrowseDragSelectManager(
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val viewModel: BrowseViewModel,
) {

    fun setup() {
        val listener = DragSelectTouchListener(object : DragSelectTouchListener.Callback {
            override fun isActive(): Boolean =
                viewModel.state.value.selectedFiles.isNotEmpty()

            override fun onSelectionStart(position: Int) {
                // Seeding the range on an empty selection selects exactly the start file and sets
                // BrowseSelectionManager.lastSelectedPath, so subsequent range updates extend from here.
                selectablePathAt(position)?.let { viewModel.selectFileRange(it) }
            }

            override fun onSelectionRangeChanged(start: Int, end: Int) {
                selectablePathAt(end)?.let { viewModel.selectFileRange(it) }
            }

            override fun onSelectionEnd() = Unit
        })
        listener.attach(recyclerView)
    }

    private fun selectablePathAt(position: Int): String? {
        val file = adapter.currentList.getOrNull(position) ?: return null
        if (file.isDirectory) return null
        return file.path
    }
}
