package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * Manages the submitList callback logic for BrowseActivity:
 * - Deferred thumbnail loading (visible range detection + notifyItemRangeChanged)
 * - Empty state visibility
 * - Scroll restoration (lastViewedFile / lastScrollPosition)
 * - Heap protection (Glide memory cache trimming)
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseListSubmitManager(
    private val activity: Activity,
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val emptyStateView: View,
    private val scrollButtonManager: BrowseScrollButtonManager,
    private val isLoadingProvider: () -> Boolean
) {
    /** Flag for scroll restoration after PlayerActivity return. */
    var shouldScrollToLastViewed = false
    /** Flag for first-load scroll restoration. */
    var pendingScrollRestore = true

    /**
     * Called inside the submitList {} callback. Handles thumbnail triggering,
     * empty state, scroll restoration, and heap protection.
     */
    fun onListSubmitted(state: BrowseState) {
        if (activity.isDestroyed || activity.isFinishing) {
            Timber.w("submitList callback: Activity destroyed/finishing, skipping")
            return
        }

        Timber.d("=== submitList CALLBACK START ===")
        Timber.d("Adapter list submitted, itemCount=${adapter.itemCount}")

        triggerDeferredThumbnails()
        updateEmptyState()
        restoreScrollPosition(state)

        // Update scroll buttons after layout settles
        recyclerView.post { scrollButtonManager.updateScrollButtonsVisibility(adapter.itemCount) }

        // Heap protection: if heap >85% used, trim Glide memory cache
        val maxMem = Runtime.getRuntime().maxMemory()
        val usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val heapPct = (usedMem * 100L / maxMem).toInt()
        if (heapPct > 85) {
            Timber.w("HEAP_MONITOR: ${heapPct}% used - clearing Glide memory cache")
            com.bumptech.glide.Glide.get(activity).clearMemory()
        }
    }

    // ── Thumbnail deferred loading ──────────────────────────────────────────

    private fun triggerDeferredThumbnails() {
        if (adapter.itemCount <= 0) {
            Timber.d("Empty list, resetting skipInitialThumbnailLoad")
            adapter.setSkipInitialThumbnailLoad(false)
            return
        }

        if (recyclerView.isLaidOut && recyclerView.childCount > 0) {
            triggerThumbnailsImmediate("submitList immediate")
            adapter.setSkipInitialThumbnailLoad(false)
        } else {
            recyclerView.post {
                if (activity.isDestroyed || activity.isFinishing) return@post
                if (recyclerView.childCount > 0) {
                    triggerThumbnailsImmediate("submitList post")
                } else {
                    retryOnFirstChildAttach()
                }
                adapter.setSkipInitialThumbnailLoad(false)
            }
        }
    }

    private fun triggerThumbnailsImmediate(source: String) {
        notifyVisibleThumbnailRange(source)
    }

    private fun notifyVisibleThumbnailRange(source: String) {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return

        val (first, last) = getVisibleRange()
        if (first < 0 || last < first) return

        // RecyclerView can report pre-submit positions immediately after a list shrink.
        // Skipping stale ranges avoids an out-of-bounds notify before layout catches up.
        if (first >= itemCount) {
            Timber.d("$source: visible range stale start=$first itemCount=$itemCount")
            return
        }

        val boundedLast = minOf(last, itemCount - 1)
        scrollButtonManager.notifyItemRangeChangedSafely(
            start = first,
            count = boundedLast - first + 1,
            payload = "LOAD_THUMBNAILS",
            source = source
        )
    }

    private fun retryOnFirstChildAttach() {
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                recyclerView.removeOnChildAttachStateChangeListener(this)
                if (activity.isDestroyed || activity.isFinishing) return
                val (f, l) = getVisibleRange()
                if (f >= 0 && l >= f) {
                    notifyVisibleThumbnailRange("submitList childAttach")
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }

    private fun getVisibleRange(): Pair<Int, Int> {
        val lm = recyclerView.layoutManager
        val first = when (lm) {
            is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
            is GridLayoutManager -> lm.findFirstVisibleItemPosition()
            else -> 0
        }
        val last = when (lm) {
            is LinearLayoutManager -> lm.findLastVisibleItemPosition()
            is GridLayoutManager -> lm.findLastVisibleItemPosition()
            else -> minOf(20, adapter.itemCount - 1)
        }
        return first to last
    }

    // ── Empty state ─────────────────────────────────────────────────────────

    private fun updateEmptyState() {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            emptyStateView.isVisible = false
        } else {
            emptyStateView.isVisible = !isLoadingProvider()
        }
    }

    // ── Scroll restoration ──────────────────────────────────────────────────

    private fun restoreScrollPosition(state: BrowseState) {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return

        // Priority 1: Restore to lastViewedFile (return from PlayerActivity)
        if (shouldScrollToLastViewed) {
            shouldScrollToLastViewed = false
            val lastViewedPath = state.resource?.lastViewedFile ?: return
            val position = state.mediaFiles.indexOfFirst { it.path == lastViewedPath }
            if (position >= 0) {
                scrollToPosition(position)
                Timber.i("submitList: restored to lastViewedFile at $position")
            } else {
                Timber.w("submitList: lastViewedFile not found: $lastViewedPath")
            }
            return
        }

        // Priority 2: Restore to lastScrollPosition (first open)
        if (pendingScrollRestore) {
            pendingScrollRestore = false
            val position = state.resource?.lastScrollPosition ?: 0
            if (position > 0 && position < itemCount) {
                scrollToPosition(position)
                Timber.i("submitList: restored to saved position $position")
            }
        }
    }

    private fun scrollToPosition(position: Int) {
        recyclerView.post {
            if (activity.isDestroyed || activity.isFinishing) return@post
            val lm = recyclerView.layoutManager
            when (lm) {
                is LinearLayoutManager -> lm.scrollToPositionWithOffset(position, 0)
                is GridLayoutManager -> lm.scrollToPositionWithOffset(position, 0)
                else -> recyclerView.scrollToPosition(position)
            }
        }
    }
}
