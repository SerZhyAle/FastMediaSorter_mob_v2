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
    private val onControlBarFreeWidth: (Int) -> Unit = {}
) {

    private var gridSpacingDecoration: RecyclerView.ItemDecoration? = null
    private var compactElementsEnabled = false

    // S1443: a chip relocated into the bar is a passenger, not a command - counting it in the fit
    // sum would let it push btnStartPlayer out, which is the S0972 rule's decision alone to make.
    private val inlineChipIds = setOf(
        R.id.chipProgramsCollapsed,
        R.id.chipStreamsCollapsed,
        R.id.chipFilterCollapsed
    )

    /** Show or hide text labels on toolbar buttons depending on orientation. */
    fun updateToolbarButtonLabels(config: Configuration) {
        val isWide = config.isWideLayout()
        Timber.d("updateToolbarButtonLabels: isWide=$isWide")

        if (isWide) {
            binding.btnExit.text = activity.getString(R.string.exit)
            binding.btnAddResource.text = activity.getString(R.string.add)
            binding.btnFilter.text = activity.getString(R.string.search)
            binding.btnRefresh.text = activity.getString(R.string.refresh)
            binding.btnSettings.text = activity.getString(R.string.settings)
            binding.btnToggleView.text = activity.getString(R.string.toggle_view)
            binding.btnFavorites.text = activity.getString(R.string.favorites)
            binding.btnStartPlayer.text = activity.getString(R.string.slideshow)
        } else {
            binding.btnExit.text = null
            binding.btnAddResource.text = null
            binding.btnFilter.text = null
            binding.btnRefresh.text = null
            binding.btnSettings.text = null
            binding.btnToggleView.text = null
            binding.btnFavorites.text = null
            binding.btnStartPlayer.text = null
        }
        applyControlBarOverflow()
    }

    /**
     * S0972: the control bar is a non-wrapping horizontal row; on a narrow screen (or in label mode)
     * the buttons can overflow and the last one is clipped. Owner directive 2026-07-06: when the full
     * set does not fit, sacrifice the last button (Start Player) so the rest stay reachable. Measured
     * (not a static width bucket) because the visible-button count varies (Menu/Toggle can be GONE).
     * Restored to VISIBLE and re-measured whenever labels/compact change (setup + rotation).
     *
     * S1443: collapsed-panel chips relocated into the bar are excluded from the fit sum, and the
     * width left after every command has fitted is reported through [onControlBarFreeWidth]. A bar
     * that overflows reports zero free width, so no chip can move in while a command is being cut.
     */
    fun applyControlBarOverflow() {
        val bar = binding.layoutControlButtons
        bar.doOnLayout {
            val available = bar.width - bar.paddingStart - bar.paddingEnd
            if (available <= 0) return@doOnLayout
            // Reset first so the fit decision is made against the full button set, not a prior GONE.
            binding.btnStartPlayer.visibility = View.VISIBLE
            val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(bar.height, View.MeasureSpec.AT_MOST)
            var needed = 0
            for (i in 0 until bar.childCount) {
                val child = bar.getChildAt(i)
                if (child.visibility == View.GONE || child.id in inlineChipIds) continue
                child.measure(widthSpec, heightSpec)
                val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
                needed += child.measuredWidth + (lp?.marginStart ?: 0) + (lp?.marginEnd ?: 0)
            }
            val overflow = needed > available
            if (overflow) {
                binding.btnStartPlayer.visibility = View.GONE
            }
            val freeWidth = if (overflow) 0 else available - needed
            onControlBarFreeWidth(freeWidth)
            // S1258: the probe measure() above overwrites each child's measured size with its
            // preferred one. TextView centers TEXT against getMeasuredHeight() while
            // compound-drawable ICONS center against the real height, so a stale probe leaves
            // labels riding (height-measuredHeight)/2 px high (4px at 48dp, 8px at 56dp
            // buttons). Heal via post: this block can run inside a layout pass (doOnLayout),
            // where an inline requestLayout gets superseded by later re-probes; a posted
            // forceLayout+requestLayout runs after the frame settles and the follow-up pass
            // re-measures with true specs (proven on-device: measuredHeight 40->56).
            bar.post {
                for (i in 0 until bar.childCount) {
                    bar.getChildAt(i).forceLayout()
                }
                bar.requestLayout()
            }
            restitchControlBarFocusChain()
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
        binding.layoutControlButtons.setPadding(0, barPad, 0, barPad)
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
