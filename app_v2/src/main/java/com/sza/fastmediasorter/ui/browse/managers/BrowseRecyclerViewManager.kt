package com.sza.fastmediasorter.ui.browse.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
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

        // No-thumbnail "plank" sizing (S0548): each plank spans at least four extension-cube
        // widths - one for the cube, three for the name area (which also holds the overflow
        // button). Cube dp values mirror MediaFileAdapter.GridNoThumbViewHolder.cubeSizePx().
        const val GRID_NO_THUMB_PLANK_CUBE_UNITS = 4
        const val GRID_NO_THUMB_CUBE_DP = 48f
        const val GRID_NO_THUMB_CUBE_COMPACT_DP = 24f
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

        val isWide = resources.configuration.isWideLayout()
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val newLayoutManager = when (mode) {
            DisplayMode.LIST -> {
                val columnCount = calculateListSpanCount(screenWidthDp, isWide)
                if (columnCount > 1) {
                    Timber.d(
                        "UI_LAYOUT LIST wDp=${"%.0f".format(screenWidthDp)} fs=${"%.2f".format(resources.configuration.fontScale)} span=$columnCount"
                    )
                    Timber.d("updateDisplayMode: LIST dynamic columns=$columnCount (wide=$isWide, widthDp=$screenWidthDp)")
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

                // No-thumbnail grid renders horizontal "planks" (square extension cube + name).
                // Size each plank to at least four cube-widths - one cube plus three for the name
                // area, which also hosts the optional overflow button - so the name stays readable
                // whatever the overflow-menu setting (S0548; supersedes the S0419 halving, which was
                // tied to icon size and left the name only ~5 characters wide). Cube dp mirrors
                // MediaFileAdapter.GridNoThumbViewHolder.cubeSizePx().
                val spanCount = if (disableThumbnails) {
                    val cubeDp = if (isCompactMode) GRID_NO_THUMB_CUBE_COMPACT_DP else GRID_NO_THUMB_CUBE_DP
                    val minPlankWidthDp = cubeDp * GRID_NO_THUMB_PLANK_CUBE_UNITS + cardPaddingDp
                    (screenWidthDp / minPlankWidthDp).toInt().coerceAtLeast(1)
                } else {
                    baseSpanCount
                }

                Timber.d(
                    "UI_LAYOUT GRID wDp=${"%.0f".format(screenWidthDp)} fs=${"%.2f".format(resources.configuration.fontScale)} item=${"%.0f".format(itemWidthDp)} span=$spanCount compact=$isCompactMode noThumbs=$disableThumbnails"
                )

                Timber.d("updateDisplayMode: Grid calculation - screenWidth=${screenWidthDp}dp, itemWidth=${itemWidthDp}dp, spanCount=$spanCount (base=$baseSpanCount, calculated=$calculatedSpanCount)")
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

    private fun calculateListSpanCount(screenWidthDp: Float, isWide: Boolean): Int {
        val configuration = resources.configuration
        val isTelevision =
            (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

        val horizontalPaddingDp = 8f
        val availableWidthDp = (screenWidthDp - horizontalPaddingDp).coerceAtLeast(0f)

        val baseMinCellWidthDp = when {
            isTelevision -> 320f
            // S0693: wide screens pack list rows at ~360dp so a wide-portrait tablet shows 2+ columns
            // instead of one sparse full-width row.
            isWide -> 360f
            else -> 460f
        }
        val adjustedMinCellWidthDp = baseMinCellWidthDp * configuration.fontScale.coerceIn(1.0f, 1.35f)

        val calculated = (availableWidthDp / adjustedMinCellWidthDp).toInt().coerceAtLeast(1)
        val maxColumns = if (isTelevision) 8 else 6
        return calculated.coerceIn(1, maxColumns)
    }
}
