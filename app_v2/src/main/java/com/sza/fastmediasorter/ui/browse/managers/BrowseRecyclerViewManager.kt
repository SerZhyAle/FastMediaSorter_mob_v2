package com.sza.fastmediasorter.ui.browse.managers

import android.content.res.Resources
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
        showVideoThumbnails: Boolean
    ) {
        Timber.d("updateDisplayMode: mode=$mode, iconSize=$iconSize")
        
        // Save current scroll position before changing layout
        val currentLayoutManager = recyclerView.layoutManager
        val scrollPosition = when (currentLayoutManager) {
            is LinearLayoutManager -> currentLayoutManager.findFirstVisibleItemPosition()
            is GridLayoutManager -> currentLayoutManager.findFirstVisibleItemPosition()
            else -> 0
        }
        Timber.d("updateDisplayMode: Saved scroll position=$scrollPosition")
        
        // Update adapter mode
        adapter.setGridMode(
            enabled = mode == DisplayMode.GRID,
            iconSize = iconSize
        )
        Timber.d("updateDisplayMode: Updated adapter gridMode=${mode == DisplayMode.GRID}")
        
        // Update toggle button icon
        val iconResId = when (mode) {
            DisplayMode.LIST -> R.drawable.ic_view_grid // Show grid icon when in list mode
            DisplayMode.GRID -> R.drawable.ic_view_list // Show list icon when in grid mode
        }
        callbacks.updateToggleButtonIcon(iconResId)
        
        // Update layout manager
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        
        val newLayoutManager = when (mode) {
            DisplayMode.LIST -> {
                // Use multi-column grid for tablets and landscape
                // Tablets (sw600dp+): minimum 3 columns for better space utilization
                // Landscape: 2 columns
                val useMultiColumn = isLandscape || screenWidthDp >= 600f
                if (useMultiColumn) {
                    val columnCount = if (screenWidthDp >= 600f) {
                        // Tablet: 3 columns minimum (from integers.xml: grid_column_count_list = 3)
                        resources.getInteger(R.integer.grid_column_count_list)
                    } else {
                        // Landscape phone: 2 columns
                        2
                    }
                    Timber.d("updateDisplayMode: Using $columnCount-column Grid for LIST mode (landscape=$isLandscape, widthDp=$screenWidthDp)")
                    GridLayoutManager(recyclerView.context, columnCount)
                } else {
                    Timber.d("updateDisplayMode: Using Linear for LIST mode (widthDp=$screenWidthDp)")
                    LinearLayoutManager(recyclerView.context)
                }
            }
            DisplayMode.GRID -> {
                // Calculate span count dynamically based on screen width and icon size
                val cardPaddingDp = 8f // 4dp padding on each side (from card layout)
                
                // Fixed cell width calculation
                val itemWidthDp = if (showVideoThumbnails) {
                    // With thumbnails: use icon size from settings
                    iconSize.toFloat() + cardPaddingDp
                } else {
                    // Without thumbnails: use icon size from settings (user selected size)
                    iconSize.toFloat() + cardPaddingDp
                }
                
                // Calculate span count with minimum for tablets
                val calculatedSpanCount = (screenWidthDp / itemWidthDp).toInt()
                val spanCount = if (screenWidthDp >= 600f) {
                    // Tablet: minimum 3 columns for better space utilization
                    calculatedSpanCount.coerceAtLeast(3)
                } else {
                    // Phone: minimum 1 column
                    calculatedSpanCount.coerceAtLeast(1)
                }
                
                Timber.d("updateDisplayMode: Grid calculation - screenWidth=${screenWidthDp}dp, showThumbnails=$showVideoThumbnails, itemWidth=${itemWidthDp}dp, spanCount=$spanCount (calculated=$calculatedSpanCount)")
                GridLayoutManager(recyclerView.context, spanCount)
            }
        }
        recyclerView.layoutManager = newLayoutManager
        
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
}
