package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.ui.main.MainState
import timber.log.Timber

/**
 * Owns the cross-cutting layout chrome for MainActivity: orientation-aware toolbar labels,
 * width-aware RecyclerView layout manager, compact toolbar sizing, and grid item spacing.
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainLayoutChromeManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val isResourceGridMode: () -> Boolean,
    private val onControlBarFreeWidth: (Int) -> Unit = {},
    private val onOverflowChanged: () -> Unit = {}
) {

    private var gridSpacingDecoration: RecyclerView.ItemDecoration? = null
    private var compactElementsEnabled = false

    /** Command ids the last plan pushed off the bar and into the "⋮" menu. */
    private var overflowedIds: Set<Int> = emptySet()

    /** Commands a feature toggle has switched off; never candidates, never shown. */
    private val ineligibleCommandIds = mutableSetOf<Int>()

    fun isOverflowed(viewId: Int): Boolean = viewId in overflowedIds

    fun hasOverflow(): Boolean = overflowedIds.isNotEmpty()

    /** Command id to label resource, in bar order - the "⋮" menu builds its entries from this list. */
    val commandLabels: List<Pair<Int, Int>>
        get() = commandCells.map { (button, labelRes) -> button.id to labelRes }

    /**
     * S1672: report whether a command is switched on at all (Favorites has a settings toggle).
     * Eligibility is held here rather than read back from view visibility, because this class's own
     * eviction writes the same GONE and would otherwise be mistaken for a feature gate - the Browse
     * bar learned that in S0374. Recomputes only when the answer actually changes.
     */
    fun setCommandEligible(viewId: Int, eligible: Boolean) {
        val changed = if (eligible) {
            ineligibleCommandIds.remove(viewId)
        } else {
            ineligibleCommandIds.add(viewId)
        }
        if (changed) applyControlBarOverflow()
    }

    /** Show or hide text labels on toolbar buttons depending on orientation. */
    fun updateToolbarButtonLabels(config: Configuration) {
        val isWide = config.isWideLayout()
        Timber.d("updateToolbarButtonLabels: isWide=$isWide")
        applyLabels(isWide)
        applyControlBarOverflow()
    }

    /**
     * S0972 / S1672: the control bar is a non-wrapping horizontal row, so a button that does not fit
     * is simply clipped by the parent. The fit is measured rather than bucketed by width, because the
     * visible-button count varies (Menu/Toggle can be GONE) and the wide layout turns every label on
     * at once, which is the widest the row ever gets.
     *
     * S1672 replaced the 2026-07-06 rule of sacrificing one named button: labels come off the whole
     * row first, and only if icon-only still does not fit are commands evicted from the right edge,
     * as many as the shortfall requires. Every evicted command stays reachable in the "⋮" menu, whose
     * anchor is therefore reserved out of the budget instead of competing for it.
     *
     * S1443: collapsed-panel chips are passengers, not commands, so they are not candidates and never
     * enter the fit sum; the width left over is reported through [onControlBarFreeWidth], and a bar
     * that evicted anything reports zero, so no chip moves in while a command is being cut.
     */
    fun applyControlBarOverflow() {
        val bar = binding.layoutControlButtons
        bar.doOnLayout {
            val available = bar.width - bar.paddingStart - bar.paddingEnd
            if (available > 0) applyControlBarPlan(bar, available)
        }
    }

    private fun applyControlBarPlan(bar: ViewGroup, availableWidthPx: Int) {
        val cells = commandCells.filterNot { (button, _) -> button.id in ineligibleCommandIds }
        // Reset first so the fit decision is made against the full eligible set, not a prior eviction.
        commandCells.forEach { (button, _) ->
            button.visibility = if (button.id in ineligibleCommandIds) View.GONE else View.VISIBLE
        }
        val labelsPreferred = activity.resources.configuration.isWideLayout()
        val candidates = measureCandidates(bar, cells, labelsPreferred)
        // The anchor is measured even while GONE: an eviction can summon it at any moment, and a
        // reserved slot that goes unused only leaves the row wider than it had to be.
        val reservedPx = measuredWidthOf(binding.layoutMainDropdownMenu, bar)
        val plan = MainCommandBarPlanner.plan(
            availableWidthPx = availableWidthPx,
            reservedWidthPx = reservedPx,
            labelsPreferred = labelsPreferred,
            candidates = candidates
        )
        applyLabels(plan.labelsVisible)
        cells.forEach { (button, _) ->
            button.visibility = if (button.id in plan.visibleIds) View.VISIBLE else View.GONE
        }
        reportFreeWidth(availableWidthPx - reservedPx, candidates, plan)
        publishOverflow(plan)
        healProbeMeasurements(bar)
        restitchControlBarFocusChain()
    }

    /** The evictable commands in bar order; the "⋮" wrapper between Refresh and Settings is the anchor. */
    private val commandCells: List<Pair<MaterialButton, Int>> by lazy {
        listOf(
            binding.btnExit to R.string.exit,
            binding.btnAddResource to R.string.add,
            binding.btnFilter to R.string.search,
            binding.btnRefresh to R.string.refresh,
            binding.btnSettings to R.string.settings,
            binding.btnToggleView to R.string.toggle_view,
            binding.btnFavorites to R.string.favorites,
            binding.btnStartPlayer to R.string.slideshow
        )
    }

    private fun applyLabels(visible: Boolean) {
        commandCells.forEach { (button, labelRes) ->
            button.text = if (visible) activity.getString(labelRes) else null
        }
    }

    /**
     * Both label modes are measured in one pass: the planner cannot choose the icon-only rollback
     * without knowing what the row costs without its labels.
     */
    private fun measureCandidates(
        bar: ViewGroup,
        cells: List<Pair<MaterialButton, Int>>,
        labelsPreferred: Boolean
    ): List<CommandCandidate> {
        applyLabels(labelsPreferred)
        val labelled = cells.map { (button, _) -> measuredWidthOf(button, bar) }
        val iconOnly = if (labelsPreferred) {
            applyLabels(false)
            cells.map { (button, _) -> measuredWidthOf(button, bar) }
        } else {
            labelled
        }
        return cells.mapIndexed { index, (button, _) ->
            CommandCandidate(button.id, labelled[index], iconOnly[index])
        }
    }

    private fun measuredWidthOf(view: View, bar: ViewGroup): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(bar.height, View.MeasureSpec.AT_MOST)
        )
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
        return view.measuredWidth + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
    }

    private fun reportFreeWidth(
        budgetPx: Int,
        candidates: List<CommandCandidate>,
        plan: CommandBarPlan
    ) {
        val consumed = candidates.filter { it.viewId in plan.visibleIds }
            .sumOf { if (plan.labelsVisible) it.labelledWidthPx else it.iconOnlyWidthPx }
        onControlBarFreeWidth(if (plan.overflowIds.isEmpty()) budgetPx - consumed else 0)
    }

    private fun publishOverflow(plan: CommandBarPlan) {
        val next = plan.overflowIds.toSet()
        if (next != overflowedIds) {
            overflowedIds = next
            onOverflowChanged()
        }
    }

    /**
     * S1258: the probe measure() above overwrites each child's measured size with its preferred one.
     * TextView centers TEXT against getMeasuredHeight() while compound-drawable ICONS center against
     * the real height, so a stale probe leaves labels riding (height-measuredHeight)/2 px high (4px
     * at 48dp, 8px at 56dp buttons). Heal via post: this block can run inside a layout pass
     * (doOnLayout), where an inline requestLayout gets superseded by later re-probes; a posted
     * forceLayout+requestLayout runs after the frame settles and the follow-up pass re-measures with
     * true specs (proven on-device: measuredHeight 40->56). S1672 measures twice, which doubles the
     * exposure this heal covers.
     */
    private fun healProbeMeasurements(bar: ViewGroup) {
        bar.post {
            for (i in 0 until bar.childCount) {
                bar.getChildAt(i).forceLayout()
            }
            bar.requestLayout()
        }
    }

    /** Picks LinearLayoutManager / GridLayoutManager + spanCount based on screen width and grid mode. */
    fun updateLayoutManagerForScreenSize() {
        val gridMode = isResourceGridMode()
        val res = activity.resources
        val isWideScreen = res.configuration.isWideLayout()

        Timber.d("updateLayoutManagerForScreenSize: isWideScreen=$isWideScreen, isGridMode=$gridMode")

        if (gridMode) {
            // Compact Grid Mode - use resource-based column counts
            val spanCount = if (isWideScreen) {
                res.getInteger(R.integer.grid_column_count_landscape)
            } else {
                res.getInteger(R.integer.grid_column_count)
            }
            val current = binding.rvResources.layoutManager
            if (current !is GridLayoutManager || current.spanCount != spanCount) {
                binding.rvResources.layoutManager = GridLayoutManager(activity, spanCount)
            }
        } else if (isWideScreen) {
            // Wide screen (tablet or rotated phone): grid with resource-driven column count
            val columnCount = res.getInteger(R.integer.resource_grid_column_count)
            val current = binding.rvResources.layoutManager
            if (current !is GridLayoutManager || current.spanCount != columnCount) {
                binding.rvResources.layoutManager = GridLayoutManager(activity, columnCount)
            }
        } else {
            // Phone portrait: list
            if (binding.rvResources.layoutManager !is LinearLayoutManager ||
                binding.rvResources.layoutManager is GridLayoutManager) {
                binding.rvResources.layoutManager = LinearLayoutManager(activity)
            }
        }
        refreshGridSpacing()
    }

    /** Compact mode: zero vertical padding + reduced button height. Normal mode: dimen-based values. */
    fun applyCompactToolbar(compact: Boolean) {
        compactElementsEnabled = compact
        val res = activity.resources
        val barPad = if (compact) 0 else res.getDimensionPixelSize(R.dimen.control_bar_padding)
        val btnH = res.getDimensionPixelSize(
            if (compact) R.dimen.control_button_size_compact else R.dimen.control_button_size
        )
        // S1672: horizontal padding carries the system-bar insets (see applyEdgeToEdgeInsets), so
        // compact mode changes the vertical padding only and must not zero the sides.
        val bar = binding.layoutControlButtons
        bar.setPadding(bar.paddingLeft, barPad, bar.paddingRight, barPad)
        for (i in 0 until binding.layoutControlButtons.childCount) {
            val child = binding.layoutControlButtons.getChildAt(i)
            val lp = child.layoutParams
            if (lp.height > 0) {
                lp.height = btnH
                child.layoutParams = lp
            }
        }
        // S1263: the Programs button sits inside the layoutMainDropdownMenu wrapper, so the
        // direct-children loop above never resizes it - in compact mode it stayed at the full
        // height inside a shorter row and its centered content sank below the row centre.
        binding.btnMainDropdownMenu.layoutParams?.let { lp ->
            if (lp.height > 0) {
                lp.height = btnH
                binding.btnMainDropdownMenu.layoutParams = lp
            }
        }
        binding.layoutControlButtons.requestLayout()
        applyControlBarOverflow()
    }

    /** Apply or remove inter-item spacing decoration for the resource grid. */
    fun refreshGridSpacing() {
        gridSpacingDecoration?.let { binding.rvResources.removeItemDecoration(it) }
        gridSpacingDecoration = null
        if (binding.rvResources.layoutManager is GridLayoutManager) {
            val spacingPx = ((if (compactElementsEnabled) 2 else 4) * activity.resources.displayMetrics.density).toInt()
            val dec = object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(spacingPx, spacingPx, spacingPx, spacingPx)
                }
            }
            gridSpacingDecoration = dec
            binding.rvResources.addItemDecoration(dec)
        }
    }

    /**
     * S0289: rebuild the horizontal focus chain across only the currently-visible control-bar buttons.
     * Skipped (GONE) buttons drop out of nextFocusLeft/nextFocusRight so the chain stays contiguous.
     */
    fun restitchControlBarFocusChain() {
        // S1443: the button list stays hard-coded rather than walked from the bar's children -
        // btnMainDropdownMenu sits inside the layoutMainDropdownMenu wrapper (S1263), so a
        // direct-children walk would put the wrapper in the chain and drop that button out of it.
        val buttons: List<View> = listOf(
            binding.btnExit,
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnMainDropdownMenu,
            binding.btnSettings,
            binding.btnToggleView,
            binding.btnFavorites,
            binding.btnStartPlayer
        )
        val inlineChips: List<View> = listOf(
            binding.chipProgramsCollapsed,
            binding.chipStreamsCollapsed,
            binding.chipFilterCollapsed
        ).filter { it.parent === binding.layoutControlButtons }
            .sortedBy { binding.layoutControlButtons.indexOfChild(it) }
        val candidates = (buttons + inlineChips).filter { it.visibility == View.VISIBLE }
        if (candidates.isEmpty()) return
        candidates.forEachIndexed { i, view ->
            val prev = if (i > 0) candidates[i - 1].id else View.NO_ID
            val next = if (i < candidates.lastIndex) candidates[i + 1].id else View.NO_ID
            view.nextFocusLeftId = prev
            view.nextFocusRightId = next
        }
        // A chip landing at the end must not swallow the vertical exit the last command used to own.
        candidates.last().nextFocusDownId = R.id.tabResourceTypes
    }

    /**
     * RecyclerView bottom inset so the last item clears the nav bar. Runs inside setupViews' post{},
     * so it re-requests insets after the initial dispatch was already missed.
     */
    fun applyEdgeToEdgeInsets() {
        // S1672: the app draws edge to edge, so the bar's own width runs under the landscape
        // navigation bar - the row then measures as fitting while its last label sits behind the
        // system strip. Padding the bar by the horizontal bars + cutout insets is what makes
        // "available width" mean usable width, and the planner is re-run because that budget changed.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.layoutControlButtons) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, view.paddingTop, safe.right, view.paddingBottom)
            applyControlBarOverflow()
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.layoutControlButtons)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvResources) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
            (view as? android.view.ViewGroup)?.clipToPadding = false
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.rvResources)
    }

    /** Filter-active banner: show a summary of the active type/media/name filters, or hide when none. */
    fun updateFilterWarning(state: MainState) {
        val hasFilters = state.filterByType != null ||
            state.filterByMediaType != null ||
            !state.filterByName.isNullOrBlank()

        if (hasFilters) {
            val parts = mutableListOf<String>()
            state.filterByType?.let { types ->
                parts.add("Type: ${types.joinToString(", ")}")
            }
            state.filterByMediaType?.let { mediaTypes ->
                parts.add("Media: ${mediaTypes.joinToString(", ")}")
            }
            state.filterByName?.takeIf { it.isNotBlank() }?.let { name ->
                parts.add("Name: '$name'")
            }
            binding.tvFilterWarning.text = activity.getString(R.string.filters_active, parts.joinToString(" | "))
            binding.tvFilterWarning.isVisible = true
        } else {
            binding.tvFilterWarning.isVisible = false
        }
    }
}
