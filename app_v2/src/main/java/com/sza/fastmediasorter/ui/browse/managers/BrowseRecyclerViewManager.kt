package com.sza.fastmediasorter.ui.browse.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * Manages RecyclerView configuration, adapter binding, and scroll behavior.
 * Handles layout manager switching (grid/list), span count calculation, and scroll position restoration.
 */
class BrowseRecyclerViewManager(
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val resources: Resources,
    private val callbacks: RecyclerViewCallbacks
) {
    
    interface RecyclerViewCallbacks {
        fun onDisplayModeChanged(displayMode: DisplayMode)
        fun updateToggleButtonIcon(iconResId: Int)
    }

    private val gridRowSpacing = GridRowSpacingItemDecoration()
    private var gridRowSpacingAttached = false

    /** Adds a fixed vertical gap below each grid cell so rows do not touch (S0419). */
    private class GridRowSpacingItemDecoration : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.bottom = (GRID_ROW_GAP_DP * parent.resources.displayMetrics.density).toInt()
        }
    }

    private companion object {
        const val GRID_ROW_GAP_DP = 4
    }
    
    init {
        // Set adapter immediately so RecyclerView never reports "No adapter attached"
        // before Activity's setupViews() block reaches the adapter assignment.
        recyclerView.adapter = adapter
    }

    fun initialize() {
        // RecyclerView already configured in Activity setupViews
    }
    
    fun updateDisplayMode(
        mode: DisplayMode,
        iconSize: Int,
        showVideoThumbnails: Boolean,
        isCompactMode: Boolean = false,
        disableThumbnails: Boolean = false
    ) {
        Timber.d("updateDisplayMode: mode=$mode, iconSize=$iconSize, compact=$isCompactMode, noThumbs=$disableThumbnails")

        // Save current scroll position before changing layout
        val currentLayoutManager = recyclerView.layoutManager
        val scrollPosition = when (currentLayoutManager) {
            is LinearLayoutManager -> currentLayoutManager.findFirstVisibleItemPosition()
            is GridLayoutManager -> currentLayoutManager.findFirstVisibleItemPosition()
            else -> 0
        }
        Timber.d("updateDisplayMode: Saved scroll position=$scrollPosition")

        // Update adapter mode - always pass full iconSize; compact halving is done inside onBindViewHolder
        adapter.setGridMode(
            enabled = mode == DisplayMode.GRID,
            iconSize = iconSize
        )
        Timber.d("updateDisplayMode: Updated adapter gridMode=${mode == DisplayMode.GRID}")

        val iconResId = when (mode) {
            DisplayMode.LIST -> R.drawable.ic_view_grid // Show grid icon when in list mode
            DisplayMode.GRID -> R.drawable.ic_view_list // Show list icon when in grid mode
        }
        callbacks.updateToggleButtonIcon(iconResId)

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val newLayoutManager = when (mode) {
            DisplayMode.LIST -> {
                val columnCount = calculateListSpanCount(screenWidthDp, isLandscape)
                if (columnCount > 1) {
                    Timber.d(
                        "UI_LAYOUT LIST wDp=${"%.0f".format(screenWidthDp)} fs=${"%.2f".format(resources.configuration.fontScale)} span=$columnCount"
                    )
                    Timber.d("updateDisplayMode: LIST dynamic columns=$columnCount (landscape=$isLandscape, widthDp=$screenWidthDp)")
                    GridLayoutManager(recyclerView.context, columnCount)
                } else {
                    Timber.d(
                        "UI_LAYOUT LIST wDp=${"%.0f".format(screenWidthDp)} fs=${"%.2f".format(resources.configuration.fontScale)} span=1"
                    )
                    Timber.d("updateDisplayMode: Using Linear for LIST mode (widthDp=$screenWidthDp)")
                    LinearLayoutManager(recyclerView.context)
                }
            }
            DisplayMode.GRID -> {
                // In compact mode use half the icon size for span count so more columns fit;
                // the adapter's onBindViewHolder renders each thumbnail at iconSize/2 internally.
                val cardPaddingDp = 8f
                val effectiveIconSizeDp = (if (isCompactMode) iconSize / 2 else iconSize).toFloat()
                val itemWidthDp = effectiveIconSizeDp + cardPaddingDp

                // Calculate span count with minimum for tablets
                val calculatedSpanCount = (screenWidthDp / itemWidthDp).toInt()
                val baseSpanCount = if (screenWidthDp >= 600f) {
                    // Tablet: minimum 3 columns for better space utilization
                    calculatedSpanCount.coerceAtLeast(3)
                } else {
                    // Phone: minimum 1 column
                    calculatedSpanCount.coerceAtLeast(1)
                }

                // No-thumbnail grid renders horizontal "planks" (square tile + name); halving the
                // column count gives each plank double width so more of the name fits (S0419).
                val spanCount = if (disableThumbnails) (baseSpanCount / 2).coerceAtLeast(1) else baseSpanCount

                Timber.d(
                    "UI_LAYOUT GRID wDp=${"%.0f".format(screenWidthDp)} fs=${"%.2f".format(resources.configuration.fontScale)} item=${"%.0f".format(itemWidthDp)} span=$spanCount compact=$isCompactMode noThumbs=$disableThumbnails"
                )

                Timber.d("updateDisplayMode: Grid calculation - screenWidth=${screenWidthDp}dp, itemWidth=${itemWidthDp}dp, spanCount=$spanCount (base=$baseSpanCount, calculated=$calculatedSpanCount)")
                Timber.d("S0419: grid span=$spanCount noThumbs=$disableThumbnails rowGap=${GRID_ROW_GAP_DP}dp")
                GridLayoutManager(recyclerView.context, spanCount)
            }
        }
        recyclerView.layoutManager = newLayoutManager

        // Grid rows otherwise touch (card padding is 0dp); add a small vertical gap between rows (S0419).
        if (mode == DisplayMode.GRID) {
            if (!gridRowSpacingAttached) {
                recyclerView.addItemDecoration(gridRowSpacing)
                gridRowSpacingAttached = true
            }
        } else if (gridRowSpacingAttached) {
            recyclerView.removeItemDecoration(gridRowSpacing)
            gridRowSpacingAttached = false
        }

        // Restore scroll position after layout manager change
        if (scrollPosition >= 0) {
            recyclerView.post {
                newLayoutManager.scrollToPosition(scrollPosition)
                Timber.d("updateDisplayMode: Restored scroll position=$scrollPosition")
            }
        }

        Timber.d("updateDisplayMode: Layout manager updated to ${newLayoutManager::class.simpleName}")

        callbacks.onDisplayModeChanged(mode)
    }
    
    fun cleanup() {
        // Release adapter resources if needed
    }

    private fun calculateListSpanCount(screenWidthDp: Float, isLandscape: Boolean): Int {
        val configuration = resources.configuration
        val isTelevision =
            (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

        val horizontalPaddingDp = 8f
        val availableWidthDp = (screenWidthDp - horizontalPaddingDp).coerceAtLeast(0f)

        val baseMinCellWidthDp = when {
            isTelevision -> 320f
            isLandscape -> 580f
            else -> 460f
        }
        val adjustedMinCellWidthDp = baseMinCellWidthDp * configuration.fontScale.coerceIn(1.0f, 1.35f)

        val calculated = (availableWidthDp / adjustedMinCellWidthDp).toInt().coerceAtLeast(1)
        val maxColumns = if (isTelevision) 8 else 6
        return calculated.coerceIn(1, maxColumns)
    }
}
