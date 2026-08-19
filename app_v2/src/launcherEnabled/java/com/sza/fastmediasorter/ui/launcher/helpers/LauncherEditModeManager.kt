package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * S0404: owns the desktop's edit mode - the drag-to-move gesture, the edit-mode affordances on the
 * taskbar, and the one-shot orientation hint. Keeps the activity thin (Rule 3): no placement logic lives
 * here, only the mapping from a gesture to an intent. The placement itself - overlap rejection and the
 * equal-footprint trade - is the repository's, reached through [LauncherHomeViewModel.moveCell].
 *
 * NOT `ItemTouchHelper`: that is RecyclerView-only, and the desktop is a 2D `ViewGroup` (ADR-9). Drag is
 * a plain [View.OnDragListener] on the container plus [View.startDragAndDrop] on a cell; the drop target
 * is arithmetic via [LauncherDesktopLayout.cellAt], not adapter bookkeeping.
 *
 * @param addCellButton the slotless-add affordance (S1209). Owned here rather than by the activity's own
 *   edit-mode collector so that both taskbar buttons appear and disappear from one place and cannot
 *   drift apart.
 * @param actions the screen's own entry points - opening a picker or a dialog is the activity's job, and
 *   this manager holds no `FragmentManager` and must not grow one.
 */
class LauncherEditModeManager(
    private val lifecycleOwner: LifecycleOwner,
    private val desktop: LauncherDesktopLayout,
    private val doneButton: View,
    private val addCellButton: View,
    private val snackbarAnchor: View,
    private val viewModel: LauncherHomeViewModel,
    private val actions: LauncherDesktopActions,
) {

    fun attach() {
        desktop.setOnDragListener(dragListener)
        // S1466: the gesture opens the quick menu; entering edit mode is now one of its items (ADR-1).
        // The listener sits on the container, so a press that lands on a cell or an interactive gadget is
        // consumed by that child first and never reaches here. While the desktop is locked, or edit mode is
        // already on, the gesture stays the silent no-op it has been since S1090.
        desktop.setOnLongClickListener {
            val locked = viewModel.desktopLocked.value
            val editing = viewModel.editMode.value
            Timber.d("S1466: desktop long press - locked=$locked editing=$editing")
            if (locked || editing) return@setOnLongClickListener false
            showQuickMenu()
            true
        }
        // S1466: a popup anchored to the desktop must not survive the window it hangs on - leaving it up
        // across a stop is the classic WindowLeaked, and home is left constantly.
        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    quickMenu?.dismiss()
                    quickMenu = null
                }
            },
        )
        doneButton.setOnClickListener { viewModel.setEditMode(false) }
        addCellButton.setOnClickListener {
            actions.addItem()
        }
        // Both affordances exist only while editing; the desktop stays clean otherwise.
        lifecycleOwner.collectOnLifecycle(viewModel.editMode) { editing ->
            doneButton.isVisible = editing
            addCellButton.isVisible = editing
        }
    }

    /**
     * S1466: built per gesture and dropped on dismiss, so no popup outlives the desktop that anchored it.
     * "Add an item" carries the pressed square; a press the grid cannot name falls back to the first free
     * position, which is exactly what the taskbar "+" does and never leaves the item unplaced.
     */
    private fun showQuickMenu() {
        val slot = desktop.slotAt(desktop.lastPressX, desktop.lastPressY)
        quickMenu = LauncherDesktopQuickMenu(
            onAddItem = {
                if (slot == null) actions.addItem() else actions.addItemAtSlot(slot.row, slot.col)
            },
            onEditDesktop = { viewModel.setEditMode(true) },
            onWallpaper = actions.wallpaper,
            onLauncherSettings = actions.launcherSettings,
        ).also {
            it.show(desktop, desktop.lastPressX.toInt(), desktop.lastPressY.toInt())
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
     *
     * S1680: the flag is awaited rather than read from a `StateFlow` value. This is a decision compared
     * against a stored value, not a render, so S1535's rule applies - a seed would answer `false` for the
     * ~100 ms before DataStore has read, and a rotation inside that window re-showed a dismissed hint.
     */
    fun onOrientationChanged() {
        // S1466: rotation re-derives the column count, so the square under the menu is no longer the one it
        // was opened for. The screen survives rotation in place (no recreate), so nothing else closes it.
        quickMenu?.dismiss()
        quickMenu = null
        lifecycleOwner.lifecycleScope.launch {
            val alreadyShown = viewModel.rotationHintShown.first()
            if (!alreadyShown && viewModel.cells.value.isNotEmpty()) {
                viewModel.markRotationHintShown()
                Snackbar.make(snackbarAnchor, R.string.launcher_edit_rotation_hint, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private var quickMenu: LauncherDesktopQuickMenu? = null

    private val dragListener = View.OnDragListener { _, event ->
        when (event.action) {
            // S1209: only DROP used to be handled, so nothing moved the desktop during a gesture and a
            // row past the bottom edge stayed unreachable for the whole drag (strategic §1).
            DragEvent.ACTION_DRAG_LOCATION -> {
                updateAutoScroll(event.y)
                true
            }
            DragEvent.ACTION_DROP -> {
                stopAutoScroll()
                val id = event.localState as? Long ?: return@OnDragListener false
                val target = desktop.cellAt(event.x, event.y)
                viewModel.moveCell(id, target.row, target.col)
                true
            }
            // ENDED fires even when the drag was cancelled or released outside the container, so it is
            // the one edge guaranteed to arrive - without it a scroll could outlive the gesture.
            DragEvent.ACTION_DRAG_ENDED -> {
                stopAutoScroll()
                true
            }
            // Every other drag event must be accepted, or the framework stops routing DROP to us.
            else -> true
        }
    }

    /**
     * S1209: signed pixels the viewport travels per frame while the drag point sits in an edge band,
     * 0 when it does not. Negative pulls the desktop up. Main thread only - both the drag callback that
     * writes it and the frame callback that reads it run there.
     */
    private var autoScrollStepPx = 0

    private val autoScroller = object : Runnable {
        override fun run() {
            val viewport = desktop.parent as? View
            val step = autoScrollStepPx
            // The detached case is the leak guard: ACTION_DRAG_ENDED tears this loop down for every drag
            // that started, but a window destroyed before that event reaches us would leave a callback
            // re-posting itself on the main-thread choreographer, holding this manager and the whole
            // view tree behind it. Refusing to re-post is enough - there is no later drag on a dead view.
            // Written as one guard rather than three early returns: they would cross detekt's ReturnCount
            // limit, the same way they did in LauncherHomeViewModel.run.
            if (viewport == null || step == 0 || !desktop.isAttachedToWindow) return
            val before = viewport.scrollY
            viewport.scrollBy(0, step)
            // The scroll container clamps inside scrollTo, so a step past either end lands on the end
            // rather than overscrolling. An unchanged offset therefore means nothing is left that way,
            // and the loop stops instead of burning a frame callback for the rest of the gesture.
            if (viewport.scrollY == before) stopAutoScroll() else desktop.postOnAnimation(this)
        }
    }

    /**
     * [desktopY] is relative to the desktop, which is taller than the viewport, so the band is measured
     * against the viewport-relative position instead. The step grows with how deep the point sits inside
     * the band - a shallow hover creeps, a deep one moves - because a constant speed makes the target
     * row impossible to stop on (strategic §7).
     */
    private fun updateAutoScroll(desktopY: Float) {
        val viewport = desktop.parent as? View
        val band = desktop.resources.getDimensionPixelSize(R.dimen.launcher_drag_autoscroll_band)
        if (viewport == null || viewport.height <= 0 || band <= 0) {
            stopAutoScroll()
            return
        }
        val viewportY = desktopY - viewport.scrollY
        val depthPx = when {
            viewportY < band -> viewportY - band
            viewportY > viewport.height - band -> viewportY - (viewport.height - band)
            else -> 0f
        }
        val step = (depthPx * AUTOSCROLL_DEPTH_FACTOR).roundToInt()
        if (step == 0) {
            stopAutoScroll()
            return
        }
        val wasIdle = autoScrollStepPx == 0
        autoScrollStepPx = step
        if (wasIdle) {
            desktop.postOnAnimation(autoScroller)
        }
    }

    private fun stopAutoScroll() {
        autoScrollStepPx = 0
        desktop.removeCallbacks(autoScroller)
    }

    private companion object {
        const val DRAG_LABEL = "launcher_cell"

        /**
         * Share of the depth inside the band covered per frame. At the deep edge of a 48dp band that is
         * roughly three cells a second at xxhdpi: enough to cross a screen while a finger is held still,
         * slow enough that the row under the shadow can still be read.
         */
        const val AUTOSCROLL_DEPTH_FACTOR = 0.12f
    }
}
