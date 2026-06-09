package com.sza.fastmediasorter.ui.player.standalone

import android.view.View
import android.widget.ImageButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel

/**
 * S0389 Phase 03: wires the shared folder-paging command-panel buttons (prev / next / random /
 * slideshow) to the [StandalonePlayerViewModel] paging actions for any standalone host.
 *
 * Why a binder: the four standalone activities carry the same paging button ids but render their
 * content differently, so the button click/visibility/icon wiring is the only truly shared part.
 * Extracting it here keeps each activity free of paging plumbing (Rule 3: no logic in the activity)
 * and guarantees identical D-pad/keyboard behaviour across hosts.
 */
class StandalonePagingControlsBinder(
    private val viewModel: StandalonePlayerViewModel,
    private val btnPrev: ImageButton,
    private val btnNext: ImageButton,
    private val btnRandom: ImageButton,
    private val btnSlideshow: ImageButton,
) {

    /** Attach click handlers once during view setup. Visibility is driven later by [applyState]. */
    fun setupClicks() {
        btnPrev.setOnClickListener { viewModel.pagePrevious() }
        btnNext.setOnClickListener { viewModel.pageNext() }
        btnRandom.setOnClickListener { viewModel.pageRandom() }
        btnSlideshow.setOnClickListener { viewModel.toggleSlideshow() }
    }

    /**
     * Reflect the latest paging state: show the controls only when the folder is pageable, and mark
     * the slideshow button's active state non-colour-wise via contentDescription + selected flag.
     */
    fun applyState(supportsFolderPaging: Boolean, isSlideshowActive: Boolean) {
        val visibility = if (supportsFolderPaging) View.VISIBLE else View.GONE
        btnPrev.visibility = visibility
        btnNext.visibility = visibility
        btnRandom.visibility = visibility
        btnSlideshow.visibility = visibility

        // Swap the icon (play -> pause) so the running state is conveyed by shape, not colour alone
        // (Rule 16/accessibility); the description switches verb so TalkBack announces the next action.
        btnSlideshow.setImageResource(
            if (isSlideshowActive) R.drawable.ic_pause else R.drawable.ic_slideshow
        )
        btnSlideshow.contentDescription = btnSlideshow.context.getString(
            if (isSlideshowActive) R.string.slideshow_stop else R.string.slideshow
        )
    }
}
