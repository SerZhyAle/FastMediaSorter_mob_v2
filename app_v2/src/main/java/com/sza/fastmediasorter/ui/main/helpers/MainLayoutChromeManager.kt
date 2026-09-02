package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.domain.model.ResourceGridCellSize
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
    // S1285: supplied rather than injected so the helper keeps taking no repository - it reads the
    // current step at the moment it recomputes the span, the same way it reads the grid mode.
    private val resourceGridCellSize: () -> ResourceGridCellSize = { ResourceGridCellSize.DEFAULT },
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

    /**
     * Show or hide text labels on toolbar buttons depending on orientation, and re-apply the rest of
     * the landscape control-bar delta.
     *
     * S1549: MainActivity declares android:configChanges, so layout-land/activity_main.xml is
     * inflated on a landscape cold start but never on a rotation. Labels alone left the button
     * metrics, the row gravity and the seven-cell separator pattern frozen at the first inflation.
     */
    fun updateToolbarButtonLabels(config: Configuration) {
        val isWide = config.isWideLayout()
        Timber.d("updateToolbarButtonLabels: isWide=$isWide")
        applyCommandBarMetrics()
        applyLabels(isWide)
        applyControlBarOverflow()
    }

    /**
     * Values come from qualified dimens, so the resource bucket - not this code - decides which
     * variant they belong to. The style itself is never swapped: a View reads it once at inflation.
     */
    private fun applyCommandBarMetrics() {
        styleCommandButton(
            button = binding.btnExit,
            minWidthRes = R.dimen.main_cmd_first_min_width,
            paddingStartRes = R.dimen.main_cmd_first_padding_start,
            paddingEndRes = R.dimen.main_cmd_first_padding_end,
            iconPaddingRes = R.dimen.main_cmd_first_icon_padding
        )
        styleCommandButton(
            button = binding.btnMainDropdownMenu,
            minWidthRes = R.dimen.main_cmd_menu_min_width,
            paddingStartRes = R.dimen.main_cmd_menu_padding_start,
            paddingEndRes = R.dimen.main_cmd_menu_padding_end,
            iconPaddingRes = R.dimen.main_cmd_icon_padding
        )
        listOf(
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnSettings,
            binding.btnToggleView,
            binding.btnFavorites,
            binding.btnStartPlayer
        ).forEach { button ->
            styleCommandButton(
                button = button,
                minWidthRes = R.dimen.main_cmd_min_width,
                paddingStartRes = R.dimen.main_cmd_padding_start,
                paddingEndRes = R.dimen.main_cmd_padding_end,
                iconPaddingRes = R.dimen.main_cmd_icon_padding
            )
        }
        binding.layoutControlButtons.gravity =
            if (activity.resources.getBoolean(R.bool.main_cmd_bar_center_vertical)) {
                Gravity.CENTER_VERTICAL
            } else {
                Gravity.TOP or Gravity.START
            }
        syncControlBarSeparators()
    }

    /**
     * iconGravity is set to textStart for every command, including Exit, whose portrait copy declares
     * plain start: with the label empty - which is the only state portrait ever shows - the two
     * resolve to the same placement, so one write covers both variants.
     */
    private fun styleCommandButton(
        button: MaterialButton,
        minWidthRes: Int,
        paddingStartRes: Int,
        paddingEndRes: Int,
        iconPaddingRes: Int
    ) {
        val res = activity.resources
        button.minWidth = res.getDimensionPixelSize(minWidthRes)
        button.setPaddingRelative(
            res.getDimensionPixelSize(paddingStartRes),
            button.paddingTop,
            res.getDimensionPixelSize(paddingEndRes),
            button.paddingBottom
        )
        button.iconPadding = res.getDimensionPixelSize(iconPaddingRes)
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.main_cmd_text_size))
        AppCompatResources.getColorStateList(activity, R.color.command_button_text)
            ?.let(button::setTextColor)
    }

    /**
     * The wide bar separates its commands with narrow Space cells that exist in no other variant, so
     * this is the one part of the delta that has to create views rather than write attributes.
     *
     * Idempotent by construction: every cell this method creates carries [SEPARATOR_TAG], and the
     * sync removes all tagged children before inserting the current set. A second rotation therefore
     * replaces the row's separators instead of stacking another five, and the tag - rather than a
     * field - is what remembers them, so the guarantee survives this manager being rebuilt over a
     * view tree that already has them.
     */
    private fun syncControlBarSeparators() {
        val bar = binding.layoutControlButtons
        for (i in bar.childCount - 1 downTo 0) {
            if (bar.getChildAt(i).tag == SEPARATOR_TAG) {
                bar.removeViewAt(i)
            }
        }
        val width = activity.resources.getDimensionPixelSize(R.dimen.main_cmd_bar_separator_width)
        if (width <= 0) {
            return
        }
        separatorAnchors.forEach { anchor ->
            val index = bar.indexOfChild(anchor)
            if (index >= 0) {
                bar.addView(newSeparator(width), index + 1)
            }
        }
    }

    /** Each command the wide bar follows with a separator; the menu wrapper carries its own. */
    private val separatorAnchors: List<View>
        get() = listOf(
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnSettings,
            binding.btnToggleView
        )

    private fun newSeparator(width: Int): Space = Space(activity).apply {
        tag = SEPARATOR_TAG
        layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT)
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
            // S1549: the separators are children of the row but never candidates, so their width has
            // to leave the budget - otherwise the planner fits a row wider than the one it measured
            // and the last label is clipped again, the very failure S1672 removed.
            val available = bar.width - bar.paddingStart - bar.paddingEnd - separatorsWidth(bar)
            if (available > 0) applyControlBarPlan(bar, available)
        }
    }

    private fun separatorsWidth(bar: ViewGroup): Int {
        var total = 0
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child.tag == SEPARATOR_TAG) {
                total += child.layoutParams.width
            }
        }
        return total
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
            val baseSpan = if (isWideScreen) {
                res.getInteger(R.integer.grid_column_count_landscape)
            } else {
                res.getInteger(R.integer.grid_column_count)
            }
            // S1285: the user's step scales the count the configuration already picked, so every width
            // and orientation qualifier keeps working; MEDIUM is the identity and changes nothing.
            val spanCount = resourceGridCellSize().spanFor(baseSpan)
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

    private companion object {
        /** Marks the Space cells this class owns, so a re-sync can tell them from layout children. */
        const val SEPARATOR_TAG = "s1549_control_bar_separator"
    }
}
