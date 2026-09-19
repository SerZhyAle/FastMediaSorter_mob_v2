package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * Manages scroll button (FAB) visibility and safe RecyclerView notification for BrowseActivity.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseScrollButtonManager(
    private val activity: Activity,
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val fabScrollToTop: View,
    private val fabScrollToBottom: View,
    private val fabPageUp: View,
    private val fabPageDown: View
) {

    /**
     * Update scroll buttons visibility based on file count and current scroll position.
     * All four buttons are hidden when all items fit on screen.
     * When shown, scroll-to-top/page-up are hidden at the top,
     * and scroll-to-bottom/page-down are hidden at the bottom.
     */
    fun updateScrollButtonsVisibility(fileCount: Int) {
        val layoutManager = recyclerView.layoutManager
        val firstVisible = when (layoutManager) {
            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            else -> RecyclerView.NO_POSITION
        }
        val lastVisible = when (layoutManager) {
            is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
            else -> RecyclerView.NO_POSITION
        }
        val itemCount = layoutManager?.itemCount ?: 0

        val allVisible = firstVisible == RecyclerView.NO_POSITION ||
            (firstVisible <= 0 && lastVisible != RecyclerView.NO_POSITION && lastVisible >= itemCount - 1)
        if (fileCount == 0 || allVisible) {
            fabScrollToTop.isVisible = false
            fabScrollToBottom.isVisible = false
            fabPageUp.isVisible = false
            fabPageDown.isVisible = false
            return
        }

        val atTop = firstVisible <= 0
        val atBottom = lastVisible >= itemCount - 1

        fabScrollToTop.isVisible = !atTop
        fabPageUp.isVisible = !atTop
        fabScrollToBottom.isVisible = !atBottom
        fabPageDown.isVisible = !atBottom
    }

    /**
     * Safely call notifyItemRangeChanged, deferring if RecyclerView is busy.
     */
    fun notifyItemRangeChangedSafely(start: Int, count: Int, payload: Any, source: String) {
        if (start < 0 || count <= 0) {
            Timber.w("$source: skipped notifyItemRangeChanged with invalid range start=$start, count=$count")
            return
        }
        dispatchWhenIdle(start, count, payload, source, attempt = 0)
    }

    /**
     * S3282: the deferred branch posted once and then notified unconditionally. A post landing
     * inside the next layout pass reaches RecyclerViewDataObserver.assertNotInLayoutOrScroll, and a
     * notify issued mid-fling leaves a pending adapter op for the layout step to reorder. The busy
     * state is re-read on every attempt instead, and the notify waits for an idle frame.
     */
    private fun dispatchWhenIdle(start: Int, count: Int, payload: Any, source: String, attempt: Int) {
        if (activity.isDestroyed || activity.isFinishing) {
            Timber.w("$source: Activity destroyed/finishing, skipping notifyItemRangeChanged")
            return
        }
        val busy = recyclerView.isComputingLayout ||
            recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE
        if (!busy) {
            notifyRangeNow(start, count, payload, source)
        } else if (attempt >= MAX_DEFER_ATTEMPTS) {
            Timber.w("$source: RecyclerView still busy after $attempt attempts, dropping notify")
        } else {
            Timber.d("$source: RecyclerView busy, re-posting notify (attempt ${attempt + 1})")
            recyclerView.post { dispatchWhenIdle(start, count, payload, source, attempt + 1) }
        }
    }

    private fun notifyRangeNow(start: Int, count: Int, payload: Any, source: String) {
        val itemCount = adapter.itemCount
        val safeCount = minOf(count, itemCount - start)
        if (start >= itemCount || safeCount <= 0) {
            Timber.w("$source: range start=$start count=$count no longer fits itemCount=$itemCount")
        } else {
            Timber.d("$source: notifyItemRangeChanged($start, $safeCount, $payload)")
            adapter.notifyItemRangeChanged(start, safeCount, payload)
        }
    }

    private companion object {
        /** Frames a deferred notify waits for an idle RecyclerView before it is dropped. */
        const val MAX_DEFER_ATTEMPTS = 5
    }
}
