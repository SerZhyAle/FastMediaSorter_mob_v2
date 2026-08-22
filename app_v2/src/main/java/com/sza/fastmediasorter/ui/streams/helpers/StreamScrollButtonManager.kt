package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.utils.UserActionLogger

/**
 * S0587: scroll-navigation buttons for the Streams list, mirroring the file browser
 * (`BrowseScrollButtonManager` + `BrowseButtonSetupHelper.setupScrollButtons`). Kept Streams-local
 * because the browser manager is bound to `MediaFileAdapter`; the four actions and the show/hide
 * rule are small enough to own here without divergence. All scroll logic lives in this manager so
 * the Activity only forwards views (Rule 3).
 */
class StreamScrollButtonManager(
    private val recyclerView: RecyclerView,
    private val fabScrollToTop: View,
    private val fabPageUp: View,
    private val fabPageDown: View,
    private val fabScrollToBottom: View,
) {

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            updateVisibility()
        }
    }

    /** Wire click actions and the scroll listener. Call once after the RecyclerView is configured. */
    fun attach() {
        fabScrollToTop.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToTop", "StreamsActivity")
            scrollToPosition(0)
        }
        fabScrollToBottom.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToBottom", "StreamsActivity")
            val last = (recyclerView.layoutManager?.itemCount ?: 0) - 1
            if (last >= 0) scrollToPosition(last)
        }
        fabPageUp.setOnClickListener {
            UserActionLogger.logButtonClick("PageUp", "StreamsActivity")
            recyclerView.smoothScrollBy(0, -recyclerView.height)
        }
        fabPageDown.setOnClickListener {
            UserActionLogger.logButtonClick("PageDown", "StreamsActivity")
            recyclerView.smoothScrollBy(0, recyclerView.height)
        }
        recyclerView.addOnScrollListener(scrollListener)
        updateVisibility()
    }

    private fun scrollToPosition(position: Int) {
        val lm = recyclerView.layoutManager
        if (lm is LinearLayoutManager) {
            lm.scrollToPositionWithOffset(position, 0)
        } else {
            recyclerView.scrollToPosition(position)
        }
        recyclerView.post { updateVisibility() }
    }

    /**
     * Hide all four when every row fits on screen; otherwise hide top/page-up at the top and
     * bottom/page-down at the bottom. Mirrors `BrowseScrollButtonManager.updateScrollButtonsVisibility`.
     */
    fun updateVisibility() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val firstVisible = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        val lastVisible = lm?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
        val itemCount = lm?.itemCount ?: 0

        val allVisible = firstVisible == RecyclerView.NO_POSITION ||
            (firstVisible <= 0 && lastVisible != RecyclerView.NO_POSITION && lastVisible >= itemCount - 1)
        if (itemCount == 0 || allVisible) {
            fabScrollToTop.isVisible = false
            fabPageUp.isVisible = false
            fabPageDown.isVisible = false
            fabScrollToBottom.isVisible = false
            return
        }

        val atTop = firstVisible <= 0
        val atBottom = lastVisible >= itemCount - 1
        fabScrollToTop.isVisible = !atTop
        fabPageUp.isVisible = !atTop
        fabScrollToBottom.isVisible = !atBottom
        fabPageDown.isVisible = !atBottom
    }
}
