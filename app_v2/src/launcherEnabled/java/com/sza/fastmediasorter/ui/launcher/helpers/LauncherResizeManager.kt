package com.sza.fastmediasorter.ui.launcher.helpers

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * S1093: turns a drag on a gadget's bottom-right handle into a resize. The real cell view is never
 * touched during the drag - a translucent preview rectangle shows the candidate footprint, and only
 * the authoritative cells Flow changes the real cell once the resize commits. A rejected (colliding)
 * resize therefore needs no snap-back: the cell simply never moved.
 *
 * Floor is the gadget's own seed size (clock 2x1, others 2x2); ceiling is full width x viewport height.
 * Only one resize runs at a time (a modal gesture), so the in-flight state lives in fields.
 */
class LauncherResizeManager(
    private val container: LauncherDesktopLayout,
    private val viewport: View,
    private val gadgetRegistry: LauncherGadgetRegistry,
    private val viewModel: LauncherHomeViewModel,
) {

    private var preview: View? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var baseW = 1
    private var baseH = 1
    private var candW = 1
    private var candH = 1
    private var cellSize = 1
    private var floorW = 1
    private var floorH = 1
    private var ceilingW = 1
    private var ceilingH = 1
    private var anchorRow = 0
    private var anchorCol = 0
    private var activeCellId = 0L

    // A resize handle is a drag target, not a click target; it carries its own contentDescription for
    // TalkBack, so the ClickableViewAccessibility lint (which wants performClick) does not apply.
    @SuppressLint("ClickableViewAccessibility")
    fun attachHandle(handle: View, cellUi: LauncherCellUi) {
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onDown(event, cellUi)
                MotionEvent.ACTION_MOVE -> onMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onUp()
                else -> false
            }
        }
    }

    private fun onDown(event: MotionEvent, cellUi: LauncherCellUi): Boolean {
        val cell = cellUi.cell
        val (seedW, seedH) = seedFloor(cell.target, cell.spanW, cell.spanH)
        cellSize = container.currentCellSize().coerceAtLeast(1)
        floorW = seedW
        floorH = seedH
        ceilingW = container.columns.coerceAtLeast(floorW)
        ceilingH = (viewport.height / cellSize).coerceAtLeast(floorH)
        downRawX = event.rawX
        downRawY = event.rawY
        baseW = cell.spanW
        baseH = cell.spanH
        candW = baseW
        candH = baseH
        anchorRow = cell.rowIndex
        anchorCol = cell.colIndex
        activeCellId = cell.id
        addPreview()
        // Stop the scroll container from stealing the vertical drag as a scroll.
        (viewport as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun onMove(event: MotionEvent): Boolean {
        val deltaW = ((event.rawX - downRawX) / cellSize).roundToInt()
        val deltaH = ((event.rawY - downRawY) / cellSize).roundToInt()
        val newW = (baseW + deltaW).coerceIn(floorW, ceilingW)
        val newH = (baseH + deltaH).coerceIn(floorH, ceilingH)
        if (newW != candW || newH != candH) {
            candW = newW
            candH = newH
            preview?.layoutParams =
                LauncherDesktopLayout.CellLayoutParams(anchorRow, anchorCol, candW, candH)
            container.requestLayout()
        }
        return true
    }

    private fun onUp(): Boolean {
        (viewport as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
        preview?.let { container.removeView(it) }
        preview = null
        if (candW != baseW || candH != baseH) {
            Timber.d("S1093: resize commit ${baseW}x$baseH -> ${candW}x$candH")
            viewModel.resizeCell(activeCellId, candW, candH)
        }
        return true
    }

    private fun addPreview() {
        val view = View(container.context).apply {
            setBackgroundResource(R.drawable.bg_launcher_resize_preview)
        }
        container.addView(
            view,
            LauncherDesktopLayout.CellLayoutParams(anchorRow, anchorCol, baseW, baseH),
        )
        preview = view
    }

    private fun seedFloor(target: String, fallbackW: Int, fallbackH: Int): Pair<Int, Int> {
        val key = gadgetRegistry.decodeTarget(target)?.first
        val gadget = key?.let { gadgetRegistry.byKey(it) }
        return (gadget?.minSpanW ?: fallbackW) to (gadget?.minSpanH ?: fallbackH)
    }
}
