package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

/**
 * S0404: owns the desktop's edit mode - the drag-to-move gesture, the Done affordance, and the one-shot
 * orientation hint. Keeps the activity thin (Rule 3): no placement logic lives here, only the mapping
 * from a gesture to an intent. The placement itself - overlap rejection and the equal-footprint trade -
 * is the repository's, reached through [LauncherHomeViewModel.moveCell].
 *
 * NOT `ItemTouchHelper`: that is RecyclerView-only, and the desktop is a 2D `ViewGroup` (ADR-9). Drag is
 * a plain [View.OnDragListener] on the container plus [View.startDragAndDrop] on a cell; the drop target
 * is arithmetic via [LauncherDesktopLayout.cellAt], not adapter bookkeeping.
 */
class LauncherEditModeManager(
    private val lifecycleOwner: LifecycleOwner,
    private val desktop: LauncherDesktopLayout,
    private val doneButton: View,
    private val snackbarAnchor: View,
    private val viewModel: LauncherHomeViewModel,
) {

    fun attach() {
        desktop.setOnDragListener(dragListener)
        // S1090: long-press on empty desktop enters edit mode. The listener sits on the container, so a
        // press that lands on a cell or an interactive gadget is consumed by that child first and never
        // reaches here. While the desktop is locked the gesture is a silent no-op by owner's decision.
        desktop.setOnLongClickListener {
            Timber.d("S1090: desktop long-press, locked=%s", viewModel.desktopLocked.value)
            if (viewModel.desktopLocked.value || viewModel.editMode.value) return@setOnLongClickListener false
            viewModel.setEditMode(true)
            true
        }
        doneButton.setOnClickListener { viewModel.setEditMode(false) }
        // The Done affordance exists only while editing; the desktop stays clean otherwise.
        lifecycleOwner.collectOnLifecycle(viewModel.editMode) { editing ->
            doneButton.isVisible = editing
        }
    }

    /** Called by the binder when a cell is long-pressed in edit mode: lift it into a drag. */
    fun startCellDrag(cellView: View, cellUi: LauncherCellUi) {
        val id = cellUi.cell.id
        val clip = ClipData.newPlainText(DRAG_LABEL, id.toString())
        // localState carries the id in-process, so the drop needs no ClipData parsing.
        cellView.startDragAndDrop(clip, View.DragShadowBuilder(cellView), id, 0)
    }

    /**
     * First rotation while the desktop holds at least one user cell shows the one-shot hint that the two
     * orientations are arranged separately (risk 6). Independent of edit mode - the surprise is the same
     * either way. The "shown" flag is persisted through settings, so it survives a process kill.
     */
    fun onOrientationChanged() {
        if (viewModel.rotationHintShown.value) return
        if (viewModel.cells.value.isEmpty()) return
        viewModel.markRotationHintShown()
        Snackbar.make(snackbarAnchor, R.string.launcher_edit_rotation_hint, Snackbar.LENGTH_LONG).show()
    }

    private val dragListener = View.OnDragListener { _, event ->
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val id = event.localState as? Long ?: return@OnDragListener false
                val target = desktop.cellAt(event.x, event.y)
                viewModel.moveCell(id, target.row, target.col)
                true
            }
            // Every other drag event must be accepted, or the framework stops routing DROP to us.
            else -> true
        }
    }

    private companion object {
        const val DRAG_LABEL = "launcher_cell"
    }
}
