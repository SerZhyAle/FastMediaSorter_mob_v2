package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.common.widget.ActionBarView
import timber.log.Timber

/**
 * Manages scroll button (FAB) visibility and safe RecyclerView notification for BrowseActivity.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 *
 * S3249: the four controls became two floating [ActionBarView] strips - the backward pair anchored
 * to the top of the list, the forward pair to its bottom - so a strip is hidden alongside its own
 * actions and stops reserving space no visible control needs.
 *
 * S4482: the list itself reserves an end gutter as wide as the strips while it scrolls, so no row
 * button sits under a strip.
 */
class BrowseScrollButtonManager(
    private val activity: Activity,
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val barScrollTop: ActionBarView,
    private val barScrollBottom: ActionBarView
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
        val atTop = fileCount == 0 || allVisible || firstVisible <= 0
        val atBottom = fileCount == 0 || allVisible || lastVisible >= itemCount - 1

        applyStripState(barScrollTop, R.id.actionBrowseScrollToTop, R.id.actionBrowsePageUp, !atTop)
        applyStripState(barScrollBottom, R.id.actionBrowsePageDown, R.id.actionBrowseScrollToBottom, !atBottom)
        applyListGutter(reserve = fileCount > 0 && !allVisible)
    }

    /**
     * The strips float over the end edge of the list, where each row keeps its play and overflow
     * buttons, so a row under a strip could not be tapped. Rows therefore end where the strips begin.
     * The gutter is held for as long as the list scrolls, not only while a strip is shown - otherwise
     * every row would reflow each time the list reaches its top or bottom.
     */
    private fun applyListGutter(reserve: Boolean) {
        val target = if (reserve) stripGutterPx() else 0
        if (recyclerView.paddingEnd != target) {
            recyclerView.updatePaddingRelative(end = target)
        }
    }

    /** One strip's width plus the clearance it keeps from the end edge, read from the live layout. */
    private fun stripGutterPx(): Int {
        val endMargin = (barScrollTop.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        return activity.resources.getDimensionPixelSize(R.dimen.browse_scroll_button_size) + endMargin
    }

    private fun applyStripState(bar: ActionBarView, firstAction: Int, secondAction: Int, visible: Boolean) {
        bar.setActionVisible(firstAction, visible)
        bar.setActionVisible(secondAction, visible)
        bar.isVisible = visible
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
