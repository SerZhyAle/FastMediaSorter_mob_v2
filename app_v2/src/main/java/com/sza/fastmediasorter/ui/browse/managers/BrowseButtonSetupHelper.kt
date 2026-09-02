package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

/**
 * Sets up button click listeners and scroll FAB handlers for BrowseActivity.
 *
 * Extracted from BrowseActivity.setupViews() (Wave 1.5 decomposition - IV.1).
 */
class BrowseButtonSetupHelper(
    private val binding: ActivityBrowseBinding,
    private val adapter: MediaFileAdapter,
    private val scrollButtonManager: BrowseScrollButtonManager
) {

    interface ButtonCallbacks {
        fun onFilterClicked()
        fun onSearchQueryChanged(query: String)
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
        fun onCreateFolderClicked()
        fun onCreateTextNoteClicked()
        fun onCreateDrawingClicked()
        fun isAudioOnlyResource(): Boolean
        fun onMicRecordTouchDown()
        fun onMicRecordTouchUp()
        fun onMicRecordSingleTap()
    }

    fun setupAllButtons(callbacks: ButtonCallbacks) {
        binding.btnFilter.setOnClickListener {
            UserActionLogger.logButtonClick("Filter", "BrowseActivity")
            callbacks.onFilterClicked()
        }

        binding.btnSearch.setOnClickListener {
            UserActionLogger.logButtonClick("Search", "BrowseActivity")
            showSearchContainer()
        }

        binding.btnSearchClose.setOnClickListener {
            UserActionLogger.logButtonClick("SearchClose", "BrowseActivity")
            hideSearchContainer()
        }

        binding.etSearchQuery.doOnTextChanged { text, _, _, _ ->
            callbacks.onSearchQueryChanged(text?.toString().orEmpty())
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

        binding.btnCreateFolder?.setOnClickListener {
            UserActionLogger.logButtonClick("CreateFolder", "BrowseActivity")
            callbacks.onCreateFolderClicked()
        }

        binding.btnCreateTextFile?.setOnClickListener {
            UserActionLogger.logButtonClick("CreateTextNote", "BrowseActivity")
            callbacks.onCreateTextNoteClicked()
        }

        binding.btnCreateDrawing?.setOnClickListener {
            UserActionLogger.logButtonClick("CreateDrawing", "BrowseActivity")
            callbacks.onCreateDrawingClicked()
        }

        var micTouchDownTime = 0L
        binding.btnMicRecord?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    micTouchDownTime = System.currentTimeMillis()
                    callbacks.onMicRecordTouchDown()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - micTouchDownTime
                    callbacks.onMicRecordTouchUp()
                    if (duration < 300) callbacks.onMicRecordSingleTap()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    callbacks.onMicRecordTouchUp()
                    true
                }
                else -> false
            }
        }

        setupScrollButtons()
    }

    /**
     * S2171 ADR-1: the search row is an elevated overlay sharing layoutControls' bar position,
     * not a replacement screen - layoutSearch carries its own elevation so it draws and receives
     * touch above layoutControls. layoutControls goes INVISIBLE rather than GONE so
     * layoutResourceInfo's constraintTop_toBottomOf anchor keeps its measured height, and so the
     * covered command buttons drop out of D-pad/keyboard focus search (only VISIBLE views are
     * focus targets on Android).
     */
    private fun showSearchContainer() {
        binding.layoutControls.visibility = View.INVISIBLE
        binding.layoutSearch.visibility = View.VISIBLE
        binding.etSearchQuery.requestFocus()
        val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearchContainer() {
        // Clearing the text fires the doOnTextChanged listener above, which resets nameContains.
        binding.etSearchQuery.text?.clear()
        binding.layoutSearch.visibility = View.GONE
        binding.layoutControls.visibility = View.VISIBLE
        val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearchQuery.windowToken, 0)
    }

    /** True while the live search overlay is open. */
    fun isSearchActive(): Boolean = binding.layoutSearch.visibility == View.VISIBLE

    /**
     * Closes the search overlay if it is open, clearing its query (S2171 §3.3: search does not
     * survive closing). Returns whether it consumed the close, so a Back/Escape handler can close
     * search first instead of navigating up.
     */
    fun closeSearchIfActive(): Boolean {
        if (!isSearchActive()) return false
        hideSearchContainer()
        return true
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
     * Show or hide text labels on toolbar buttons depending on orientation, and re-apply the rest of
     * the landscape command-bar delta.
     *
     * S1549: BrowseActivity declares android:configChanges, so layout-land/activity_browse.xml is
     * inflated on a landscape cold start but never on a rotation - the wide bar has to be produced
     * here as well. Labels alone left the icon-only metrics, the bar elevation, the scroll-button
     * tints and the empty-state colours frozen at whatever the first inflation resolved.
     */
    fun updateToolbarButtonLabels(config: Configuration) {
        val isWide = config.isWideLayout()
        Timber.d("updateToolbarButtonLabels: isWide=$isWide")
        val ctx = binding.root.context
        applyCommandBarMetrics(isWide)

        if (isWide) {
            binding.btnBack.text = ctx.getString(R.string.back)
            binding.btnFilter.text = ctx.getString(R.string.filter)
            binding.btnSearch.text = ctx.getString(R.string.search)
            binding.btnRefresh.text = ctx.getString(R.string.refresh)
            binding.btnToggleView.text = ctx.getString(R.string.toggle_view_short)
            binding.btnSelectAll.text = ctx.getString(R.string.select_all_short)
            binding.btnPlay.text = ctx.getString(R.string.slideshow)
            binding.btnPlayRandom?.text = ctx.getString(R.string.play_random_short)
            binding.btnCreateFolder?.text = ctx.getString(R.string.action_create_folder)
            binding.btnCreateTextFile?.text = ctx.getString(R.string.action_create_text_file)
            binding.btnCreateDrawing?.text = ctx.getString(R.string.action_create_drawing)
        } else {
            binding.btnBack.text = null
            binding.btnFilter.text = null
            binding.btnSearch.text = null
            binding.btnRefresh.text = null
            binding.btnToggleView.text = null
            binding.btnSelectAll.text = null
            binding.btnPlay.text = null
            binding.btnPlayRandom?.text = null
            binding.btnResourceOps?.text = null
            binding.btnCreateFolder?.text = null
            binding.btnCreateTextFile?.text = null
            binding.btnCreateDrawing?.text = null
        }
    }

    /**
     * Commands that carry a label in the wide bar. btnPath stays icon-only in every variant (S1316)
     * and btnDeselectAll keeps the icon style with its own width, so both are handled apart.
     */
    private val labelledCommandButtons: List<MaterialButton>
        get() = listOfNotNull(
            binding.btnBack, binding.btnFilter, binding.btnSearch, binding.btnRefresh, binding.btnToggleView,
            binding.btnSelectAll, binding.btnCreateFolder, binding.btnCreateTextFile,
            binding.btnCreateDrawing, binding.btnResourceOps, binding.btnMicRecord,
            binding.btnPlayRandom, binding.btnPlay
        )

    /**
     * The individual attributes are written rather than the style swapped, because a View's style is
     * read once at inflation and cannot be replaced afterwards. Metric values come from qualified
     * dimens, so the resource bucket - not this code - decides which variant they belong to; only the
     * two enum-valued attributes below branch on [isWide], the same predicate that picks the bucket.
     */
    private fun applyCommandBarMetrics(isWide: Boolean) {
        val res = binding.root.resources
        val paddingStart = res.getDimensionPixelSize(R.dimen.browse_cmd_button_padding_start)
        val paddingEnd = res.getDimensionPixelSize(R.dimen.browse_cmd_button_padding_end)
        val insetVertical = res.getDimensionPixelSize(R.dimen.browse_cmd_button_inset_vertical)
        val iconPadding = res.getDimensionPixelSize(R.dimen.browse_cmd_button_icon_padding)
        val textColor = AppCompatResources.getColorStateList(binding.root.context, R.color.command_button_text)
        labelledCommandButtons.forEach { button ->
            button.setPaddingRelative(paddingStart, button.paddingTop, paddingEnd, button.paddingBottom)
            button.insetTop = insetVertical
            button.insetBottom = insetVertical
            button.iconPadding = iconPadding
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.gravity = commandGravity(isWide)
            textColor?.let(button::setTextColor)
        }
        applyDeselectAllMetrics(isWide, paddingStart, paddingEnd)
        binding.layoutControls.elevation = res.getDimension(R.dimen.browse_controls_elevation)
        applyScrollButtonTints()
        applyEmptyStateColors()
    }

    /** Labels are left-aligned in the wide bar; the icon-only bar centres its single glyph. */
    private fun commandGravity(isWide: Boolean): Int =
        if (isWide) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER

    /** The only command whose declared width differs: a square cell in the wide bar, wrapped otherwise. */
    private fun applyDeselectAllMetrics(isWide: Boolean, paddingStart: Int, paddingEnd: Int) {
        val button = binding.btnDeselectAll
        button.setPaddingRelative(paddingStart, button.paddingTop, paddingEnd, button.paddingBottom)
        button.gravity = commandGravity(isWide)
        val target = if (isWide) {
            binding.root.resources.getDimensionPixelSize(R.dimen.browse_cmd_button_size)
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val params = button.layoutParams
        if (params.width != target) {
            params.width = target
            button.layoutParams = params
        }
    }

    private fun applyScrollButtonTints() {
        val tint = AppCompatResources.getColorStateList(binding.root.context, R.color.browse_scroll_button_tint)
        listOf(
            binding.fabScrollToTop,
            binding.fabPageUp,
            binding.fabPageDown,
            binding.fabScrollToBottom
        ).forEach { ImageViewCompat.setImageTintList(it, tint) }
    }

    private fun applyEmptyStateColors() {
        val tint = AppCompatResources.getColorStateList(binding.root.context, R.color.browse_empty_state_tint)
            ?: return
        // The empty-state icon carries no id in any layout variant, so it is reached by position.
        (binding.emptyStateView.getChildAt(0) as? ImageView)?.let { ImageViewCompat.setImageTintList(it, tint) }
        binding.tvEmptyStateMessage.setTextColor(tint)
        binding.tvEmptyStateHint.setTextColor(tint)
    }
}
