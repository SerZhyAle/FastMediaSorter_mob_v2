package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry

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
     * S4492: the window width the viewport was last measured at, and the outer width it had then. The
     * pair is what lets [currentColumns] tell a measurement of this configuration from one left over
     * from before a rotation, which would size the new orientation's grid from the old one's width.
     */
    private var measuredAtScreenWidthDp: Int = UNMEASURED
    private var measuredOuterWidthPx: Int = 0

    /**
     * S4492: what the window loses beside the viewport - a side taskbar, the system bar insets. It does
     * not change with rotation, so it stands in for the measurement until the new layout pass lands.
     */
    private var chromeWidthDp: Float = 0f

    private val viewportWidthListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        val width = right - left
        val stale = measuredAtScreenWidthDp != resources.configuration.screenWidthDp
        if (width > 0 && (width != oldRight - oldLeft || stale)) {
            recordMeasurement(width)
            // Posted: this runs inside a layout pass, and the re-render it triggers requests another.
            viewport.post { applyGridGeometry() }
        }
    }

    init {
        if (viewport.width > 0) {
            recordMeasurement(viewport.width)
        }
        viewport.addOnLayoutChangeListener(viewportWidthListener)
    }

    /** Detaches the viewport listener; the host calls it once, when its views are destroyed. */
    fun release() {
        viewport.removeOnLayoutChangeListener(viewportWidthListener)
    }

    /**
     * Rotation and density changes re-resolve the column count and re-place every cell. Rebinding IS
     * the re-layout here, so there is no bound state that can go stale behind a changed column count -
     * the trap the RecyclerView renderer had, where requestLayout() never re-ran a bind (ADR-9).
     *
     * S2685: the render is skipped while the cells on hand belong to the orientation just left.
     * [LauncherHomeViewModel.cells] is the orientation flat-mapped onto a repository query, so right
     * after [LauncherHomeViewModel.setOrientation] the resolved list is still the previous
     * orientation's seating - rendering it now tears down every cell view, reinflates the lot at the
     * new column count, and draws a frame whose seating belongs to the other orientation, only to be
     * replaced when the query answers. Skipping cannot strand the desktop at the old geometry: a real
     * orientation change re-runs the query, its cells carry the new orientation and so cannot equal
     * the previous list, and the collector on that emission renders.
     */
    fun applyGridGeometry() {
        val orientation = currentOrientation()
        val columns = currentColumns()
        viewModel.setOrientation(orientation)
        if (cellsMatchOrientation(viewModel.cells.value, orientation)) {
            renderDesktop()
        }
        // S4492: an estimate made before this configuration's layout pass is never written - a persisted
        // change reflows every section, so a transient count would reflow the desktop twice.
        if (isMeasuredForCurrentConfiguration()) {
            viewModel.persistColumns(orientation, columns)
        }
    }

    /**
     * Single desktop render path. All three triggers - cells changing, edit mode toggling, and a grid
     * re-derive on rotation/density - funnel here so edit mode is never dropped by one of them binding
     * without it. The binder's own (cells, columns, editMode) guard makes redundant calls free.
     */
    fun renderDesktop(screenIndex: Int = 0) {
        val screenCount = viewModel.launcherDesktopSettings.value.launcherScreenCount
        val allCells = viewModel.cells.value
        val screenCells = if (screenCount > 1) {
            allCells.filter { it.cell.screenIndex == screenIndex }
        } else {
            allCells
        }
        cellBinder.bind(
            desktop,
            screenCells,
            currentColumns(),
            viewModel.editMode.value,
            currentViewportRows(),
            // S2317: the targets folded on the cells actually being drawn. The whole set would fold a
            // header on this screen because its twin on another screen is shut.
            viewModel.sections.collapsedTargetsFor(screenCells),
            viewModel.widgetBackdropAlpha.value,
        )
    }

    /**
     * S4492: counted from the width the desktop is drawn in, not the window's. [LauncherDesktopLayout]
     * divides only the viewport beside the taskbar by this count, so a count taken from the window gave
     * a side taskbar's desktop cells a quarter smaller than the density factor promises.
     */
    fun currentColumns(): Int = LauncherGridGeometry.columns(
        availableWidthDp = contentWidthDp(),
        densityFactor = viewModel.densityFactor.value,
    )

    private fun contentWidthDp(): Float {
        val density = resources.displayMetrics.density
        return viewportContentWidthDp(
            screenWidthDp = resources.configuration.screenWidthDp,
            measuredOuterWidthPx = measuredOuterWidthPx.takeIf { isMeasuredForCurrentConfiguration() },
            chromeWidthDp = chromeWidthDp,
            paddingPx = viewport.paddingStart + viewport.paddingEnd,
            density = density,
        )
    }

    private fun isMeasuredForCurrentConfiguration(): Boolean =
        measuredAtScreenWidthDp == resources.configuration.screenWidthDp && measuredOuterWidthPx > 0

    private fun recordMeasurement(outerWidthPx: Int) {
        val screenWidthDp = resources.configuration.screenWidthDp
        measuredAtScreenWidthDp = screenWidthDp
        measuredOuterWidthPx = outerWidthPx
        chromeWidthDp = (screenWidthDp - outerWidthPx / resources.displayMetrics.density).coerceAtLeast(0f)
    }

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
        // S4492: the other axis loses the same chrome beside the viewport, so it is subtracted from both.
        val widthDp = contentWidthDp()
        val lostDp = config.screenWidthDp - widthDp
        viewModel.seedDesktopIfNeeded(
            widthDp = widthDp,
            heightDp = config.screenHeightDp - lostDp,
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

    companion object {
        private const val UNMEASURED = -1

        /**
         * S4492: the width, in dp, the desktop's columns divide. A viewport measured at the current window
         * width answers exactly; without one, the window width minus the chrome last seen beside the
         * viewport stands in, and the viewport's own padding comes off either way.
         */
        fun viewportContentWidthDp(
            screenWidthDp: Int,
            measuredOuterWidthPx: Int?,
            chromeWidthDp: Float,
            paddingPx: Int,
            density: Float,
        ): Float {
            val safeDensity = density.takeIf { it > 0f } ?: 1f
            val outerDp = measuredOuterWidthPx?.let { it / safeDensity } ?: (screenWidthDp - chromeWidthDp)
            return (outerDp - paddingPx / safeDensity).coerceAtLeast(0f)
        }

        /**
         * S2685: whether [cells] were resolved for [orientation]. The first cell answers for the whole
         * list because the desktop is resolved one orientation at a time, so a resolved list is
         * homogeneous by construction. An empty list names no orientation and inflates nothing, so it
         * matches anything rather than blocking the render that would draw an empty desktop.
         */
        fun cellsMatchOrientation(
            cells: List<LauncherCellUi>,
            orientation: LauncherOrientation,
        ): Boolean {
            val first = cells.firstOrNull() ?: return true
            return first.cell.orientation == orientation
        }
    }
}
