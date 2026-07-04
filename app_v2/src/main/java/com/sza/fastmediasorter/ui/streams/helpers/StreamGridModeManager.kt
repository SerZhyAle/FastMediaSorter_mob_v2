package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.Resources
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.ui.streams.StreamGridAdapter
import com.sza.fastmediasorter.ui.streams.StreamSourceAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0675: owns all stream grid-mode behavior so [com.sza.fastmediasorter.ui.streams.StreamsActivity]
 * stays a thin delegate (Rule 3/5). Swaps adapter + layout manager on [applyMode], enables pull-to-
 * refresh only in GRID, drives a periodic re-capture of stale visible frames, and tears the snapshot
 * engine down when leaving GRID. The grid adapter enqueues captures itself during bind; this manager
 * re-requests captures for the visible tiles on a timer / scroll / pull.
 *
 * S0784: the refresh re-captures each visible tile in place - the fresh frame swaps in via `repaintUrl`
 * when it lands - instead of invalidating the cache entry and rebinding to the favicon first. The old
 * invalidate+resweep reverted every visible tile to the atlas icon for the whole ~12 s capture window;
 * with the cache now holding the last frame until a live one replaces it, a tile never blanks.
 */
// S0712: one wiring dependency over the threshold - the manager glues the grid's view, adapters and the
// in-memory + on-disk frame stores together; splitting it would add indirection without clarity.
@Suppress("LongParameterList")
@UnstableApi
class StreamGridModeManager(
    private val recyclerView: RecyclerView,
    private val swipeRefresh: SwipeRefreshLayout,
    private val listAdapter: StreamSourceAdapter,
    private val gridAdapter: StreamGridAdapter,
    private val snapshotManager: StreamFrameSnapshotManager,
    private val cache: StreamFrameCache,
    // S0712: cold-start source of last-session thumbnails, pre-warmed into [cache] on entering GRID.
    private val persistentStore: StreamFramePersistentStore,
    private val lifecycleOwner: LifecycleOwner,
    private val resources: Resources,
    private val onToggleIconChanged: (mode: DisplayMode) -> Unit,
    // S0700: report a video tile's capture outcome (ok = first frame decoded) as its green/red status.
    private val onStreamOutcome: (id: String, ok: Boolean) -> Unit = { _, _ -> },
) {

    private var currentMode: DisplayMode = DisplayMode.LIST
    private var latestList: List<StreamSourceEntity> = emptyList()
    private var refreshJob: Job? = null
    private var prewarmJob: Job? = null

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
                stopPrewarm()
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
                prewarmPersistedFrames(currentList)
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
        if (currentMode == DisplayMode.GRID) {
            gridAdapter.submitList(currentList)
            prewarmPersistedFrames(currentList)
        } else {
            listAdapter.submitList(currentList)
        }
    }

    /**
     * A visible-range scroll in GRID: re-request captures for the now-visible tiles. S0784: binds no
     * longer blank a stale tile (the cache keeps its last frame), so the scroll hook - not the bind -
     * drives re-capture of stale tiles scrolled into view; fresh ones are skipped inside `request`.
     */
    fun onVisibleRangeChanged() {
        requestVisibleCaptures(force = false)
    }

    /** Cancel in-flight captures and stop the timer (Activity onStop / leaving the screen). */
    fun stop() {
        snapshotManager.cancelAll()
        stopPeriodicRefresh()
        stopPrewarm()
        swipeRefresh.isRefreshing = false
    }

    /**
     * S0712: seed the in-memory cache from disk so known channels show their last-session frame at once.
     * Runs once per submit, sequentially over the small (<=64) set of persisted files; entries already in
     * the cache (a live capture or earlier restore) are skipped, and [StreamFrameCache.putRestored] keeps
     * the disk frame from clobbering a fresher live one. The frame is shown but not "fresh", so the bind's
     * own [StreamFrameSnapshotManager.request] still captures a current frame and overwrites it.
     */
    // The early break on cancel plus per-item skip continues is the clearest shape for a cancellable,
    // re-checked prewarm; flattening it would hurt readability more than the extra jump statements do.
    @Suppress("LoopWithTooManyJumpStatements")
    private fun prewarmPersistedFrames(currentList: List<StreamSourceEntity>) {
        stopPrewarm()
        if (currentMode != DisplayMode.GRID) return
        prewarmJob = lifecycleOwner.lifecycleScope.launch {
            for (source in currentList) {
                if (!isActive || currentMode != DisplayMode.GRID) break
                if (!isCaptureableVideo(source) || cache.hasEntry(source.url)) continue
                val bitmap = persistentStore.load(source.url) ?: continue
                if (!isActive || currentMode != DisplayMode.GRID || cache.hasEntry(source.url)) continue
                cache.putRestored(source.url, bitmap)
                gridAdapter.repaintUrl(source.url)
            }
        }
    }

    private fun stopPrewarm() {
        prewarmJob?.cancel()
        prewarmJob = null
    }

    // Mirrors StreamGridAdapter.isCaptureableVideo: only http(s) VIDEO sources have a persisted frame.
    private fun isCaptureableVideo(source: StreamSourceEntity): Boolean =
        source.mediaKind == "VIDEO" &&
            (source.url.startsWith("http://") || source.url.startsWith("https://"))

    private fun onPullToRefresh() {
        // Explicit user pull: re-capture every visible tile, even still-fresh ones.
        requestVisibleCaptures(force = true)
        swipeRefresh.isRefreshing = false
    }

    /**
     * S0700: re-capture the thumbnails of the currently-visible tiles (the grid arm of the refresh
     * action). No-op unless in GRID mode. Mirrors pull-to-refresh's frame sweep without the spinner.
     */
    fun refreshVisibleFrames() {
        if (currentMode == DisplayMode.GRID) requestVisibleCaptures(force = true)
    }

    private fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        refreshJob = lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                if (currentMode != DisplayMode.GRID) break
                // S0784: refresh stale visible frames in place - the destructive invalidate+rebind that
                // reverted tiles to the favicon for the whole ~12 s re-capture window is gone.
                requestVisibleCaptures(force = false)
            }
        }
    }

    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * S0784: re-request captures for the currently-visible captureable tiles. Each fresh frame is swapped
     * into its tile in place when it lands (via [StreamFrameSnapshotManager.onCaptured] -> `repaintUrl`),
     * so the tile keeps its last frame throughout and never blanks to the favicon. [force] re-captures
     * even still-fresh tiles (an explicit pull / refresh action); the automatic timer leaves them alone.
     */
    private fun requestVisibleCaptures(force: Boolean) {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (position in first..last) {
            val source = latestList.getOrNull(position) ?: continue
            if (isCaptureableVideo(source)) snapshotManager.request(source.url, force)
        }
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
