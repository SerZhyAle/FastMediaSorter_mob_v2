package com.sza.fastmediasorter.ui.browse

import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Owns the drag-to-reorder state for MediaFileAdapter (MANUAL sort mode).
 * The adapter keeps a thin delegating API; all state lives here.
 *
 * `dragOrderedList` is a shadow copy maintained during drag so visual order is tracked
 * without mutating the ListAdapter's currentList.
 */
class AdapterDragController {

    interface DragStartListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    var listener: DragStartListener? = null

    var showHandles: Boolean = false
        private set

    private val dragOrderedList = mutableListOf<MediaFile>()

    /** Returns true if state actually changed (caller should rebind items). */
    fun setShowHandles(show: Boolean, currentList: List<MediaFile>): Boolean {
        if (showHandles == show) return false
        showHandles = show
        if (show) {
            dragOrderedList.clear()
            dragOrderedList.addAll(currentList)
        }
        return true
    }

    /** Returns true if the indices were valid and the shadow list was reordered. */
    fun moveItem(from: Int, to: Int): Boolean {
        if (from < 0 || to < 0 || from >= dragOrderedList.size || to >= dragOrderedList.size) return false
        if (from < to) {
            for (i in from until to) dragOrderedList.add(i + 1, dragOrderedList.removeAt(i))
        } else {
            for (i in from downTo to + 1) dragOrderedList.add(i - 1, dragOrderedList.removeAt(i))
        }
        return true
    }

    /** Keep the shadow list in sync when a new ListAdapter submission is committed. */
    fun syncWithCurrentList(currentList: List<MediaFile>) {
        if (!showHandles) return
        dragOrderedList.clear()
        dragOrderedList.addAll(currentList)
    }

    fun getOrderedPaths(): List<String> = dragOrderedList.map { it.path }.toList()
}
