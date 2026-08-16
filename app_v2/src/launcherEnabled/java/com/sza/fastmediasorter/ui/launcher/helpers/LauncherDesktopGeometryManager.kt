package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import timber.log.Timber

/**
 * S1541: the desktop's geometry answers - column count, viewport rows, current orientation - plus the
 * single render path and the first-run seed, extracted from the activity so a grid change no longer
 * means opening it.
 *
 * The last-seen orientation lives here rather than in the activity because every reader of it also
 * reads one of the answers above: splitting the two would let a rotation update one and not the other.
 */
class LauncherDesktopGeometryManager(
    private val resources: Resources,
    private val desktop: LauncherDesktopLayout,
    private val viewport: View,
    private val cellBinder: LauncherCellViewBinder,
    private val viewModel: LauncherHomeViewModel,
) {

    private var lastOrientation: LauncherOrientation = LauncherOrientation.PORTRAIT

    /**
     * Rotation and density changes re-resolve the column count and re-place every cell. Rebinding IS
     * the re-layout here, so there is no bound state that can go stale behind a changed column count -
     * the trap the RecyclerView renderer had, where requestLayout() never re-ran a bind (ADR-9).
     */
    fun applyGridGeometry() {
        Timber.d("S1541: geometry manager applying grid")
        val orientation = currentOrientation()
        val columns = currentColumns()
        viewModel.setOrientation(orientation)
        renderDesktop()
        viewModel.persistColumns(orientation, columns)
    }

    /**
     * Single desktop render path. All three triggers - cells changing, edit mode toggling, and a grid
     * re-derive on rotation/density - funnel here so edit mode is never dropped by one of them binding
     * without it. The binder's own (cells, columns, editMode) guard makes redundant calls free.
     */
    fun renderDesktop() {
        cellBinder.bind(
            desktop,
            viewModel.cells.value,
            currentColumns(),
            viewModel.editMode.value,
            currentViewportRows(),
            viewModel.sections.collapsed.value,
        )
    }

    fun currentColumns(): Int = LauncherGridGeometry.columns(
        availableWidthDp = resources.configuration.screenWidthDp.toFloat(),
        densityFactor = viewModel.densityFactor.value,
    )

    /**
     * S1288: how many rows the visible desktop covers, so edit mode can fill it with empty slots.
     *
     * The scroll container is asked first because it is the exact answer - it already accounts for
     * insets, its own padding and whatever the taskbar leaves. Before the first layout pass it has no
     * size yet, and a rotation while editing rebinds in precisely that state, so the fallback derives
     * the same figure from the configuration - the source the column count above already trusts -
     * minus the taskbar the container stops above.
     */
    fun currentViewportRows(): Int {
        val density = resources.displayMetrics.density
        val widthPx = if (viewport.width > 0) {
            viewport.width
        } else {
            (resources.configuration.screenWidthDp * density).toInt()
        }
        val heightPx = if (viewport.height > 0) {
            viewport.height
        } else {
            (resources.configuration.screenHeightDp * density).toInt() -
                resources.getDimensionPixelSize(R.dimen.launcher_taskbar_height)
        }
        val contentWidthPx = widthPx - viewport.paddingStart - viewport.paddingEnd
        return LauncherGridGeometry.rowsForViewport(
            availableHeightPx = heightPx,
            cellSizePx = LauncherGridGeometry.cellSizePx(contentWidthPx, currentColumns()),
        )
    }

    fun currentOrientation(): LauncherOrientation =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }

    /**
     * S0404: seed the starter desktop once, on first entry. The display metrics are read here
     * (current axis = screenWidthDp, other = screenHeightDp); the ViewModel converts them to per-
     * orientation column counts INSIDE its coroutine, using the real persisted density rather than the
     * StateFlow default that may still be in place synchronously here (audit 2026-07-17, P1). The
     * ViewModel and the repository both guard against re-seeding a live desktop.
     */
    fun seedDesktopIfNeeded() {
        val config = resources.configuration
        viewModel.seedDesktopIfNeeded(
            widthDp = config.screenWidthDp.toFloat(),
            heightDp = config.screenHeightDp.toFloat(),
            startedPortrait = currentOrientation() == LauncherOrientation.PORTRAIT,
        )
    }

    /** Records the orientation the desktop was last laid out for, without reporting a change. */
    fun syncOrientation() {
        lastOrientation = currentOrientation()
    }

    /**
     * True exactly once per rotation: the caller re-lays out edit mode on a true change, and a second
     * configuration callback for the same orientation must not repeat that work.
     */
    fun consumeOrientationChange(): Boolean {
        val now = currentOrientation()
        if (now == lastOrientation) {
            return false
        }
        lastOrientation = now
        return true
    }
}
