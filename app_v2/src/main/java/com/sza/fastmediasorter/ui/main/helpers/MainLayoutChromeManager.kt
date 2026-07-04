package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
    private val isResourceGridMode: () -> Boolean
) {

    private var gridSpacingDecoration: RecyclerView.ItemDecoration? = null
    private var compactElementsEnabled = false

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
        binding.layoutControlButtons.requestLayout()
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
        val candidates = listOf(
            binding.btnExit,
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnMainDropdownMenu,
            binding.btnSettings,
            binding.btnToggleView,
            binding.btnFavorites,
            binding.btnStartPlayer
        ).filter { it.visibility == View.VISIBLE }
        if (candidates.isEmpty()) return
        candidates.forEachIndexed { i, btn ->
            val prev = if (i > 0) candidates[i - 1].id else View.NO_ID
            val next = if (i < candidates.lastIndex) candidates[i + 1].id else View.NO_ID
            btn.nextFocusLeftId = prev
            btn.nextFocusRightId = next
        }
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
