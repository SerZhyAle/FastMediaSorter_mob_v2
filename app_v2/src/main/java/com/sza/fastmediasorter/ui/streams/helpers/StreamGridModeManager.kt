package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.Resources
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
) {

    private var currentMode: DisplayMode = DisplayMode.LIST
    private var latestList: List<StreamSourceEntity> = emptyList()
    private var refreshJob: Job? = null

    init {
        snapshotManager.onCaptured = { url -> gridAdapter.repaintUrl(url) }
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
        Timber.d("S0675: applyMode mode=%s items=%d", mode.name, currentList.size)
        onToggleIconChanged(mode)
        when (mode) {
            DisplayMode.LIST -> {
                snapshotManager.cancelAll()
                stopPeriodicRefresh()
                swipeRefresh.isEnabled = false
                swipeRefresh.isRefreshing = false
                recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
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

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
        const val MIN_TILE_WIDTH_DP = 160f
    }
}
