package com.sza.fastmediasorter.ui.browse.managers

import android.content.res.Configuration
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

/**
 * Sets up button click listeners and scroll FAB handlers for BrowseActivity.
 *
 * Extracted from BrowseActivity.setupViews() (Wave 1.5 decomposition — IV.1).
 */
class BrowseButtonSetupHelper(
    private val binding: ActivityBrowseBinding,
    private val adapter: MediaFileAdapter,
    private val scrollButtonManager: BrowseScrollButtonManager
) {

    interface ButtonCallbacks {
        fun onFilterClicked()
        fun onRefreshClicked()
        fun onToggleViewClicked()
        fun onSelectAllClicked()
        fun onDeselectAllClicked()
        fun onCopyClicked()
        fun onMoveClicked()
        fun onRenameClicked()
        fun onDeleteClicked()
        fun onUndoClicked()
        fun onShareClicked()
        fun onArchiveClicked()
        fun onPlayClicked()
        fun onPlayRandomClicked()
        fun onResourceOpsClicked(anchor: android.view.View)
        fun onRetryClicked()
        fun onStopScanClicked()
        fun isAudioOnlyResource(): Boolean
    }

    fun setupAllButtons(callbacks: ButtonCallbacks) {
        binding.btnFilter.setOnClickListener {
            UserActionLogger.logButtonClick("Filter", "BrowseActivity")
            callbacks.onFilterClicked()
        }

        binding.btnRefresh.setOnClickListener {
            UserActionLogger.logButtonClick("Refresh", "BrowseActivity")
            callbacks.onRefreshClicked()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            UserActionLogger.logButtonClick("PullToRefresh", "BrowseActivity")
            callbacks.onRefreshClicked()
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.blue_500,
            R.color.teal_700
        )

        binding.btnStopScan.setOnClickListener {
            UserActionLogger.logButtonClick("StopScan", "BrowseActivity")
            callbacks.onStopScanClicked()
        }

        binding.btnToggleView.setOnClickListener {
            if (callbacks.isAudioOnlyResource()) return@setOnClickListener
            UserActionLogger.logButtonClick("ToggleView", "BrowseActivity")
            callbacks.onToggleViewClicked()
        }

        binding.btnSelectAll.setOnClickListener {
            UserActionLogger.logButtonClick("SelectAll", "BrowseActivity")
            callbacks.onSelectAllClicked()
        }

        binding.btnDeselectAll.setOnClickListener {
            UserActionLogger.logButtonClick("DeselectAll", "BrowseActivity")
            callbacks.onDeselectAllClicked()
        }

        binding.btnCopy.setOnClickListener {
            UserActionLogger.logButtonClick("Copy", "BrowseActivity - Toolbar")
            callbacks.onCopyClicked()
        }

        binding.btnMove.setOnClickListener {
            UserActionLogger.logButtonClick("Move", "BrowseActivity - Toolbar")
            callbacks.onMoveClicked()
        }

        binding.btnRename.setOnClickListener {
            UserActionLogger.logButtonClick("Rename", "BrowseActivity - Toolbar")
            callbacks.onRenameClicked()
        }

        binding.btnDelete.setOnClickListener {
            UserActionLogger.logButtonClick("Delete", "BrowseActivity - Toolbar")
            callbacks.onDeleteClicked()
        }

        binding.btnUndo.setOnClickListener {
            UserActionLogger.logButtonClick("Undo", "BrowseActivity")
            callbacks.onUndoClicked()
        }

        binding.btnShare.setOnClickListener {
            UserActionLogger.logButtonClick("Share", "BrowseActivity")
            callbacks.onShareClicked()
        }

        binding.btnArchive?.setOnClickListener {
            UserActionLogger.logButtonClick("Archive", "BrowseActivity")
            callbacks.onArchiveClicked()
        }

        binding.btnPlayRandom?.setOnClickListener {
            UserActionLogger.logButtonClick("PlayRandom", "BrowseActivity - Toolbar")
            callbacks.onPlayRandomClicked()
        }

        binding.btnPlay.setOnClickListener {
            UserActionLogger.logButtonClick("Play", "BrowseActivity - Toolbar")
            callbacks.onPlayClicked()
        }

        binding.btnResourceOps?.setOnClickListener {
            UserActionLogger.logButtonClick("ResourceOps", "BrowseActivity - Toolbar")
            callbacks.onResourceOpsClicked(it)
        }

        binding.btnRetry.setOnClickListener {
            UserActionLogger.logButtonClick("Retry", "BrowseActivity")
            callbacks.onRetryClicked()
        }

        setupScrollButtons()
    }

    private fun setupScrollButtons() {
        binding.fabScrollToTop.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToTop", "BrowseActivity")
            val layoutManager = binding.rvMediaFiles.layoutManager
            when (layoutManager) {
                is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(0, 0)
                is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(0, 0)
                else -> binding.rvMediaFiles.scrollToPosition(0)
            }
            Timber.d("Scrolled to top (position 0)")
            binding.rvMediaFiles.post { scrollButtonManager.updateScrollButtonsVisibility(adapter.itemCount) }
        }

        binding.fabScrollToBottom.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToBottom", "BrowseActivity")
            val itemCount = adapter.itemCount
            if (itemCount > 0) {
                val layoutManager = binding.rvMediaFiles.layoutManager
                when (layoutManager) {
                    is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(itemCount - 1, 0)
                    is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(itemCount - 1, 0)
                    else -> binding.rvMediaFiles.scrollToPosition(itemCount - 1)
                }
                Timber.d("Scrolled to bottom (position ${itemCount - 1})")
                binding.rvMediaFiles.post { scrollButtonManager.updateScrollButtonsVisibility(adapter.itemCount) }
            }
        }

        binding.fabPageUp.setOnClickListener {
            UserActionLogger.logButtonClick("PageUp", "BrowseActivity")
            val viewportHeight = binding.rvMediaFiles.height
            binding.rvMediaFiles.smoothScrollBy(0, -viewportHeight)
            Timber.d("Page up: smoothScrollBy(0, -$viewportHeight)")
        }

        binding.fabPageDown.setOnClickListener {
            UserActionLogger.logButtonClick("PageDown", "BrowseActivity")
            val viewportHeight = binding.rvMediaFiles.height
            binding.rvMediaFiles.smoothScrollBy(0, viewportHeight)
            Timber.d("Page down: smoothScrollBy(0, $viewportHeight)")
        }
    }

    /**
     * Show or hide text labels on toolbar buttons depending on orientation.
     */
    fun updateToolbarButtonLabels(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        Timber.d("updateToolbarButtonLabels: isLandscape=$isLandscape")
        val ctx = binding.root.context

        if (isLandscape) {
            binding.btnBack.text = ctx.getString(R.string.back)
            binding.btnFilter.text = ctx.getString(R.string.search)
            binding.btnRefresh.text = ctx.getString(R.string.refresh)
            binding.btnToggleView.text = ctx.getString(R.string.toggle_view_short)
            binding.btnSelectAll.text = ctx.getString(R.string.select_all_short)
            binding.btnPlay.text = ctx.getString(R.string.slideshow)
            binding.btnPlayRandom?.text = ctx.getString(R.string.play_random_short)
        } else {
            binding.btnBack.text = null
            binding.btnFilter.text = null
            binding.btnRefresh.text = null
            binding.btnToggleView.text = null
            binding.btnSelectAll.text = null
            binding.btnPlay.text = null
            binding.btnPlayRandom?.text = null
            binding.btnResourceOps?.text = null
        }
    }
}
