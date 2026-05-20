package com.sza.fastmediasorter.ui.browse.managers

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * RecyclerView scroll listener that pauses thumbnail loading during scroll
 * and resumes it when scroll stops, loading visible items immediately.
 *
 * Extracted from BrowseActivity.setupViews() (Wave 1.5 decomposition - IV.1).
 */
class BrowseScrollThumbnailListener(
    private val adapter: MediaFileAdapter,
    private val onScrollButtonsUpdate: (Int) -> Unit
) : RecyclerView.OnScrollListener() {

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        Timber.d("BrowseActivity: onScrollStateChanged newState=$newState")
        when (newState) {
            RecyclerView.SCROLL_STATE_IDLE -> {
                adapter.setScrolling(false)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.let {
                    val firstVisible = it.findFirstVisibleItemPosition()
                    val lastVisible = it.findLastVisibleItemPosition()
                    if (firstVisible >= 0 && lastVisible >= 0) {
                        Timber.d("Scroll ended: loading visible thumbnails [$firstVisible..$lastVisible]")
                        adapter.loadVisibleThumbnails(firstVisible, lastVisible)
                        adapter.loadVisibleAudioMetadata(firstVisible, lastVisible)
                    }
                }
                onScrollButtonsUpdate(adapter.itemCount)
            }
            RecyclerView.SCROLL_STATE_DRAGGING,
            RecyclerView.SCROLL_STATE_SETTLING -> {
                adapter.setScrolling(true)
            }
        }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy != 0 || dx != 0) {
            adapter.setScrolling(true)
        }
        onScrollButtonsUpdate(adapter.itemCount)
    }
}
