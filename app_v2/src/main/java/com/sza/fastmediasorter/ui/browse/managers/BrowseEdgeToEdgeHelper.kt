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

        val bottomBarOrigPaddingLeft   = binding.layoutOperations.paddingLeft
        val bottomBarOrigPaddingTop    = binding.layoutOperations.paddingTop
        val bottomBarOrigPaddingRight  = binding.layoutOperations.paddingRight
        val bottomBarOrigPaddingBottom = binding.layoutOperations.paddingBottom

        val filterOrigPaddingLeft   = binding.tvFilterWarning.paddingLeft
        val filterOrigPaddingTop    = binding.tvFilterWarning.paddingTop
        val filterOrigPaddingRight  = binding.tvFilterWarning.paddingRight
        val filterOrigPaddingBottom = binding.tvFilterWarning.paddingBottom

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

            binding.layoutOperations.setPadding(
                bottomBarOrigPaddingLeft,
                bottomBarOrigPaddingTop,
                bottomBarOrigPaddingRight,
                bottomBarOrigPaddingBottom + navBar.bottom
            )

            binding.tvFilterWarning.setPadding(
                filterOrigPaddingLeft,
                filterOrigPaddingTop,
                filterOrigPaddingRight,
                filterOrigPaddingBottom + navBar.bottom
            )

            val fabBottomMargin = fabOrigBottomMargin + navBar.bottom
            (binding.fabScrollToBottom.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = fabBottomMargin
                binding.fabScrollToBottom.layoutParams = it
            }

            applyListBottomInset(binding)

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /**
     * Reserve navigation-bar height at the bottom of the file list so its last row can scroll clear
     * of the translucent system nav bar and stay tappable (checkbox / overflow). Applied only while
     * no opaque bottom bar is shown - when the operations bar or filter warning is visible it already
     * lifts the list above the nav bar and owns that inset, so a second reservation would leave an
     * empty band. clipToPadding stays false in XML, so rows still draw under the bar while scrolling.
     * Owns rvMediaFiles' bottom padding; call after any bottom-bar visibility change.
     */
    fun applyListBottomInset(binding: ActivityBrowseBinding) {
        val navBottom = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val bottomBarShown = binding.layoutOperations.isVisible || binding.tvFilterWarning.isVisible
        val target = if (bottomBarShown) 0 else navBottom
        if (binding.rvMediaFiles.paddingBottom != target) {
            binding.rvMediaFiles.updatePadding(bottom = target)
        }
    }
}
