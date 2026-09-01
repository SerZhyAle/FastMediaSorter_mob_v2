package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S2301: which launcher screen the desktop is drawing, and the dots that say so.
 *
 * One owner for the active index. Before this, the swipe branch, the dot's click listener and the
 * settings collector each moved the field and re-rendered on their own, and a fourth caller - the paging
 * gesture action this ticket adds - would have been the fourth copy of the same three lines.
 *
 * Lifted out of `LauncherHomeActivity` (Rule 3): building the dot views is view work, not activity work,
 * and the activity kept nothing here but the field the three callers shared.
 */
class LauncherScreenPagingManager(
    private val indicatorContainer: LinearLayout,
    private val screenCount: () -> Int,
    private val onScreenChanged: () -> Unit,
) {

    var activeScreenIndex: Int = 0
        private set

    /** Draws the next screen, stopping at the last one. */
    fun next() = step(FORWARD)

    /** Draws the previous screen, stopping at the first one. */
    fun previous() = step(BACKWARD)

    /**
     * Moves [delta] screens, stopping at the ends.
     *
     * Stopping rather than wrapping: the paging S2251 shipped stops, and one gesture that stops at one
     * edge and wraps at the other reads as a bug rather than as two rules.
     */
    fun step(delta: Int) {
        val count = screenCount()
        if (count <= SINGLE_SCREEN) return
        show((activeScreenIndex + delta).coerceIn(0, count - 1))
    }

    /** Draws [screenIndex] and repaints the dots. Out-of-range or unchanged input draws nothing. */
    fun show(screenIndex: Int) {
        val count = screenCount()
        if (screenIndex == activeScreenIndex || screenIndex !in 0 until count) return
        activeScreenIndex = screenIndex
        Timber.d("S2301: launcher active screen %d of %d", activeScreenIndex, count)
        onScreenChanged()
        renderIndicators()
    }

    /**
     * Re-reads the screen count: repaints the dots and, when the count shrank past the active screen,
     * falls back to the last one that still exists.
     */
    fun refresh() {
        val count = screenCount()
        if (activeScreenIndex >= count) {
            activeScreenIndex = (count - 1).coerceAtLeast(0)
        }
        renderIndicators()
    }

    private fun renderIndicators() {
        val count = screenCount()
        if (count <= SINGLE_SCREEN) {
            indicatorContainer.visibility = View.GONE
            return
        }
        val context = indicatorContainer.context
        indicatorContainer.visibility = View.VISIBLE
        indicatorContainer.removeAllViews()
        val density = context.resources.displayMetrics.density
        val dotSizePx = (PAGE_INDICATOR_DOT_SIZE_DP * density).toInt()
        val dotMarginPx = (PAGE_INDICATOR_DOT_MARGIN_DP * density).toInt()
        val activeColor = ContextCompat.getColor(context, R.color.teal_700)
        val inactiveColor = ContextCompat.getColor(context, R.color.m3_surface_variant)

        for (index in 0 until count) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).apply {
                    setMargins(dotMarginPx, 0, dotMarginPx, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (index == activeScreenIndex) activeColor else inactiveColor)
                }
                setOnClickListener { show(index) }
            }
            indicatorContainer.addView(dot)
        }
    }

    private companion object {
        const val SINGLE_SCREEN = 1
        const val FORWARD = 1
        const val BACKWARD = -1
        const val PAGE_INDICATOR_DOT_SIZE_DP = 8
        const val PAGE_INDICATOR_DOT_MARGIN_DP = 4
    }
}
