package com.sza.fastmediasorter.ui.welcome

import androidx.annotation.ColorRes
import com.sza.fastmediasorter.R

/**
 * Per-page tint that keeps each welcome page recognisable.
 *
 * S1234: this colour used to be the page's own background. The brand animation owns the page now, so
 * the same palette tints the translucent panel behind the copy instead - the pages stay
 * distinguishable while the waves show through around the text. Shared rather than private to the
 * activity because the pager adapter is what binds the panel, and the two must not drift apart.
 */
internal object WelcomePagePalette {

    private val PAGE_COLORS = intArrayOf(
        R.color.welcome_page_1_background,
        R.color.welcome_page_2_background,
        R.color.welcome_page_3_background,
        R.color.welcome_page_4_background,
        R.color.welcome_page_5_background,
        R.color.welcome_page_6_background,
        R.color.welcome_page_7_background,
    )

    /** Opacity of the panel - enough to carry body text, sheer enough to read as glass over the waves.
     *  Raised from 0.87: the waves showed through hard enough to fight the copy on the busiest pages. */
    const val PANEL_ALPHA = 0.95f

    @ColorRes
    fun colorResFor(position: Int): Int = PAGE_COLORS[position.coerceIn(0, PAGE_COLORS.lastIndex)]
}
