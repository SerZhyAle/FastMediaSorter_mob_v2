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
 * Extracted from BrowseActivity (Wave 1.5 decomposition — IV.1).
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

        val runNotify: () -> Unit = {
            if (activity.isDestroyed || activity.isFinishing) {
                Timber.w("$source: Activity destroyed/finishing, skipping notifyItemRangeChanged")
            } else {
                val itemCount = adapter.itemCount
                if (start >= itemCount) {
                    Timber.w("$source: start=$start out of bounds for itemCount=$itemCount")
                } else {
                    val safeCount = minOf(count, itemCount - start)
                    if (safeCount <= 0) {
                        Timber.w("$source: safeCount became $safeCount, skipping notify")
                    } else {
                        Timber.d("$source: notifyItemRangeChanged($start, $safeCount, $payload)")
                        adapter.notifyItemRangeChanged(start, safeCount, payload)
                    }
                }
            }
        }

        if (recyclerView.isComputingLayout || recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            Timber.d("$source: RecyclerView busy, posting notify")
            recyclerView.post { runNotify() }
        } else {
            runNotify()
        }
    }
}
