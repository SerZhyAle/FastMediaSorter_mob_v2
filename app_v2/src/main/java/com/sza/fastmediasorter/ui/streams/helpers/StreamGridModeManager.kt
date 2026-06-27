package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.Resources
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.ui.streams.StreamGridAdapter
import com.sza.fastmediasorter.ui.streams.StreamSourceAdapter
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0675: owns all stream grid-mode behavior so [com.sza.fastmediasorter.ui.streams.StreamsActivity]
 * stays a thin delegate (Rule 3/5). Swaps adapter + layout manager on [applyMode], enables pull-to-
 * refresh only in GRID, drives a periodic refresh of expired visible frames, and tears the snapshot
 * engine down when leaving GRID. The grid adapter enqueues captures itself during bind; this manager
 * only invalidates stale frames and forces a re-bind sweep so those binds re-request.
 */
@UnstableApi
class StreamGridModeManager(
    private val recyclerView: RecyclerView,
    private val swipeRefresh: SwipeRefreshLayout,
    private val listAdapter: StreamSourceAdapter,
    private val gridAdapter: StreamGridAdapter,
    private val snapshotManager: StreamFrameSnapshotManager,
    private val cache: StreamFrameCache,
    private val lifecycleOwner: LifecycleOwner,
    private val resources: Resources,
    private val onToggleIconChanged: (mode: DisplayMode) -> Unit,
    // S0700: report a video tile's capture outcome (ok = first frame decoded) as its green/red status.
    private val onStreamOutcome: (id: String, ok: Boolean) -> Unit = { _, _ -> },
) {

    private var currentMode: DisplayMode = DisplayMode.LIST
    private var latestList: List<StreamSourceEntity> = emptyList()
    private var refreshJob: Job? = null

    init {
        snapshotManager.onCaptured = { url -> gridAdapter.repaintUrl(url) }
        // S0700: a captured frame means the video stream is reachable here -> green; a failed decode -> red.
        snapshotManager.onOutcome = { url, ok ->
            latestList.firstOrNull { it.url == url }?.let { onStreamOutcome(it.id, ok) }
        }
        swipeRefresh.setOnRefreshListener { onPullToRefresh() }
        swipeRefresh.isEnabled = false
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (currentMode == DisplayMode.GRID) onVisibleRangeChanged()
            }
        })
    }

    /** Swap adapter + layout for [mode] and re-submit [currentList] to the now-active adapter. */
    fun applyMode(mode: DisplayMode, currentList: List<StreamSourceEntity>) {
        currentMode = mode
        latestList = currentList
        onToggleIconChanged(mode)
        when (mode) {
            DisplayMode.LIST -> {
                snapshotManager.cancelAll()
                stopPeriodicRefresh()
                swipeRefresh.isEnabled = false
                swipeRefresh.isRefreshing = false
                // S0692: render the list in multiple columns in landscape (1 in portrait). A
                // GridLayoutManager with span 1 is identical to a LinearLayoutManager, so the portrait
                // path is unchanged. Recomputed here so a rotation-driven Activity recreate re-spans.
                recyclerView.layoutManager = GridLayoutManager(recyclerView.context, calculateListSpanCount(resources))
                recyclerView.adapter = listAdapter
                listAdapter.submitList(currentList)
            }
            DisplayMode.GRID -> {
                recyclerView.layoutManager = GridLayoutManager(recyclerView.context, calculateGridSpanCount(resources))
                recyclerView.adapter = gridAdapter
                gridAdapter.submitList(currentList)
                swipeRefresh.isEnabled = true
                startPeriodicRefresh()
            }
        }
    }

    /**
     * S0692: recompute the column span for the active mode after a rotation. The Activity declares
     * `configChanges="orientation|screenSize"`, so it is never recreated on rotation and [applyMode] does
     * not re-run - without this the landscape multi-column list would only appear after a mode toggle.
     */
    fun onConfigurationChanged() {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val span = when (currentMode) {
            DisplayMode.LIST -> calculateListSpanCount(resources)
            DisplayMode.GRID -> calculateGridSpanCount(resources)
        }
        if (layoutManager.spanCount != span) {
            layoutManager.spanCount = span
            layoutManager.requestLayout()
        }
    }

    /** Keep the active adapter's list current when the catalog/filter changes without flipping mode. */
    fun submitCurrentList(currentList: List<StreamSourceEntity>) {
        latestList = currentList
        if (currentMode == DisplayMode.GRID) gridAdapter.submitList(currentList) else listAdapter.submitList(currentList)
    }

    /** A visible-range scroll in GRID; the adapter re-requests on bind, so only a no-op hook is needed. */
    fun onVisibleRangeChanged() {
        // Binds triggered by scrolling already call requestCapture for non-fresh http(s) VIDEO tiles.
    }

    /** Cancel in-flight captures and stop the timer (Activity onStop / leaving the screen). */
    fun stop() {
        snapshotManager.cancelAll()
        stopPeriodicRefresh()
        swipeRefresh.isRefreshing = false
    }

    private fun onPullToRefresh() {
        invalidateAndResweepVisible()
        swipeRefresh.isRefreshing = false
    }

    /**
     * S0700: re-capture the thumbnails of the currently-visible tiles (the grid arm of the refresh
     * action). No-op unless in GRID mode. Mirrors pull-to-refresh's frame sweep without the spinner.
     */
    fun refreshVisibleFrames() {
        if (currentMode == DisplayMode.GRID) invalidateAndResweepVisible()
    }

    private fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        refreshJob = lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                if (currentMode != DisplayMode.GRID) break
                invalidateAndResweepVisible()
            }
        }
    }

    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Drop the cached frame of every currently-visible tile and force a re-bind so captures re-request. */
    private fun invalidateAndResweepVisible() {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (position in first..last) {
            latestList.getOrNull(position)?.let { cache.invalidate(it.url) }
        }
        gridAdapter.notifyItemRangeChanged(first, last - first + 1)
        Timber.i("Stream grid refresh: invalidated visible frames [%d..%d]", first, last)
    }

    private fun calculateGridSpanCount(resources: Resources): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return (screenWidthDp / MIN_TILE_WIDTH_DP).toInt().coerceAtLeast(2)
    }

    /**
     * S0692: list column count. Portrait stays single-column; landscape fits as many ~[MIN_LIST_COLUMN_WIDTH_DP]
     * columns as the width allows, at least 2 - a wide list row stays readable while filling the extra
     * horizontal space instead of leaving it blank.
     */
    private fun calculateListSpanCount(resources: Resources): Int {
        if (!resources.configuration.isWideLayout()) return 1
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return (screenWidthDp / MIN_LIST_COLUMN_WIDTH_DP).toInt().coerceAtLeast(2)
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
        const val MIN_TILE_WIDTH_DP = 160f
        const val MIN_LIST_COLUMN_WIDTH_DP = 360f
    }
}
