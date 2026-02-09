package com.sza.fastmediasorter.ui.browse.managers

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * Manages state persistence for BrowseActivity.
 * Handles saving/restoring last viewed file position and scroll position.
 */
class BrowseStateManager(
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val callbacks: StateCallbacks
) {
    
    interface StateCallbacks {
        fun saveLastViewedFile(filePath: String)
        fun saveScrollPosition(position: Int)
    }
    
    /**
     * Gets current focus position in RecyclerView.
     * Returns first visible item position or 0 if unavailable.
     */
    fun getCurrentFocusPosition(): Int {
        val layoutManager = recyclerView.layoutManager
        return when (layoutManager) {
            is LinearLayoutManager -> {
                layoutManager.findFirstVisibleItemPosition()
            }
            else -> 0
        }
    }
    
    /**
     * Saves current first visible file as last viewed position.
     * Used for restoring scroll position when returning to activity.
     */
    fun saveLastViewedFile() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        
        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            val currentFile = adapter.currentList.getOrNull(firstVisiblePosition)
            if (currentFile != null) {
                callbacks.saveLastViewedFile(currentFile.path)
                Timber.d("Saved last viewed file: ${currentFile.path}")
            }
        }
    }
    
    /**
     * Saves current scroll position (first visible item) when leaving Browse screen.
     * Position is restored on next resource open.
     */
    fun saveScrollPosition() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        
        if (firstVisiblePosition != RecyclerView.NO_POSITION && firstVisiblePosition >= 0) {
            callbacks.saveScrollPosition(firstVisiblePosition)
            Timber.d("Saved scroll position: $firstVisiblePosition")
        }
    }
}
