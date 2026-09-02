package com.sza.fastmediasorter.ui.browse.managers

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import timber.log.Timber

/**
 * Applies edge-to-edge window insets to BrowseActivity layout.
 * Pushes top bar below status bar and bottom bar above navigation bar.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
object BrowseEdgeToEdgeHelper {

    fun apply(binding: ActivityBrowseBinding) {
        val topBarOrigPaddingLeft   = binding.layoutControls.paddingLeft
        val topBarOrigPaddingTop    = binding.layoutControls.paddingTop
        val topBarOrigPaddingRight  = binding.layoutControls.paddingRight
        val topBarOrigPaddingBottom = binding.layoutControls.paddingBottom

        // S2368: layoutSearch is constrained to the same parent top as layoutControls and swaps
        // places with it, so it needs the same status-bar inset. Without it the overlay drew from
        // y=0 while the bar it replaces sat at y=147 - its close button landed inside the status
        // bar's own window, which consumed the touch, leaving no way to dismiss the search.
        val searchOrigPaddingLeft   = binding.layoutSearch.paddingLeft
        val searchOrigPaddingTop    = binding.layoutSearch.paddingTop
        val searchOrigPaddingRight  = binding.layoutSearch.paddingRight
        val searchOrigPaddingBottom = binding.layoutSearch.paddingBottom

        val fabOrigBottomMargin = (binding.fabScrollToBottom.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.bottomMargin ?: binding.root.resources.getDimensionPixelSize(R.dimen.margin_small)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getStatusBarHeightSafe(binding.root.resources)
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.layoutControls.setPadding(
                topBarOrigPaddingLeft,
                topBarOrigPaddingTop + statusBarHeight,
                topBarOrigPaddingRight,
                topBarOrigPaddingBottom
            )

            binding.layoutSearch.setPadding(
                searchOrigPaddingLeft,
                searchOrigPaddingTop + statusBarHeight,
                searchOrigPaddingRight,
                searchOrigPaddingBottom
            )
            Timber.d("S2368: search overlay top inset applied - statusBarHeight=$statusBarHeight")

            val fabBottomMargin = fabOrigBottomMargin + navBar.bottom
            (binding.fabScrollToBottom.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = fabBottomMargin
                binding.fabScrollToBottom.layoutParams = it
            }

            applyBottomInsets(binding)

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /**
     * Settles every navigation-bar inset below the file list: which bottom strip carries it, and how
     * much height the list itself must reserve. Call after any bottom-bar visibility change - the
     * insets listener alone does not fire on those.
     */
    fun applyBottomInsets(binding: ActivityBrowseBinding) {
        val navBottom = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        applyBottomStripInsets(binding, navBottom)
        applyListBottomInset(binding, navBottom)
    }

    /**
     * Bottom strips stack upward from the parent bottom: filter warning, transfer indicator (S1227),
     * operations bar. Only the lowest *visible* strip may carry the nav-bar inset - handing it to all
     * of them leaves a nav-bar-high empty band inside every strip that has another one below it.
     * Original bottom padding is re-read from the same dimens the layouts declare, so re-applying on
     * a visibility change never accumulates.
     */
    private fun applyBottomStripInsets(binding: ActivityBrowseBinding, navBottom: Int) {
        val resources = binding.root.resources
        val stripPadding = resources.getDimensionPixelSize(R.dimen.padding_small)
        val barPadding = resources.getDimensionPixelSize(R.dimen.control_bar_padding)
        // Lowest first: the first visible entry owns the inset.
        val strips = listOf(
            binding.tvFilterWarning to stripPadding,
            binding.tvTransferIndicator to stripPadding,
            binding.layoutOperations to barPadding,
        )
        var ownerFound = false
        strips.forEach { (view, originalBottom) ->
            val ownsInset = !ownerFound && view.isVisible
            if (ownsInset) ownerFound = true
            val target = originalBottom + if (ownsInset) navBottom else 0
            if (view.paddingBottom != target) {
                view.updatePadding(bottom = target)
            }
        }
    }

    /**
     * Reserve navigation-bar height at the bottom of the file list so its last row can scroll clear
     * of the translucent system nav bar and stay tappable (checkbox / overflow). Applied only while
     * no opaque bottom strip is shown - a visible strip already lifts the list above the nav bar and
     * owns that inset, so a second reservation would leave an empty band. clipToPadding stays false
     * in XML, so rows still draw under the bar while scrolling. Owns rvMediaFiles' bottom padding.
     */
    private fun applyListBottomInset(binding: ActivityBrowseBinding, navBottom: Int) {
        val bottomBarShown = binding.layoutOperations.isVisible ||
            binding.tvTransferIndicator.isVisible ||
            binding.tvFilterWarning.isVisible
        val target = if (bottomBarShown) 0 else navBottom
        if (binding.rvMediaFiles.paddingBottom != target) {
            binding.rvMediaFiles.updatePadding(bottom = target)
        }
    }
}
