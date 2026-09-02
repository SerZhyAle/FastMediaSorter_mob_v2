package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.AnimationPolicy
import java.util.Locale

/**
 * S2323: how a launcher desktop screen change is shown - a directional slide, or the screen's number.
 *
 * A screen is not a view here: it is a filter over one hand-written desktop container that is rebound in
 * place (ADR-9), so there is no second screen to animate against. The slide therefore moves the single
 * container out towards the edge the user swiped, rebinds it behind the motion, and brings it back from
 * the opposite edge - which reads as a page change without a second desktop, a second bind pass or an
 * adapter.
 *
 * The animations-off branch lives here rather than at the call site so the desktop's five other render
 * triggers - cells changing, edit mode, backdrop alpha, section collapse, rotation - cannot acquire an
 * animation by calling the render path directly, and so none of them has to know the flag exists.
 *
 * The badge itself never animates. It exists only when the user turned animations off, and fading it in
 * would hand back the motion that setting removed (strategic ADR-3).
 */
class LauncherScreenTransitionManager(
    private val lifecycleOwner: LifecycleOwner,
    private val content: View,
    private val badge: TextView,
    private val screenIndex: () -> Int,
) : DefaultLifecycleObserver {

    private val hideBadge = Runnable { badge.visibility = View.GONE }

    /** Paints the badge's halo once and keeps the manager alive until the host is destroyed. */
    fun attach() {
        val radius = badge.resources.getDimension(R.dimen.launcher_screen_badge_shadow_radius)
        badge.setShadowLayer(
            radius,
            SHADOW_OFFSET_PX,
            SHADOW_OFFSET_PX,
            ContextCompat.getColor(badge.context, R.color.launcher_screen_badge_shadow),
        )
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * Shows the screen change [render] produces, moving towards [direction] - `+1` forward, `-1` back.
     *
     * [render] runs exactly once either way: mid-slide when animating, immediately when not.
     */
    fun transition(direction: Int, render: () -> Unit) {
        val distance = content.width.toFloat()
        // Before the first layout pass the container has no width, so there is no distance to travel and
        // the slide would be a flicker at zero offset. Rebinding alone is the honest answer there.
        if (AnimationPolicy.isAnimationAllowed && distance > 0f) {
            slide(direction, distance, render)
        } else {
            render()
            if (!AnimationPolicy.isAnimationAllowed) {
                showBadge()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        content.animate().cancel()
        badge.removeCallbacks(hideBadge)
        owner.lifecycle.removeObserver(this)
    }

    private fun slide(direction: Int, distance: Float, render: () -> Unit) {
        // A swipe arriving mid-slide cancels the running one and restores the container first: cancel()
        // skips the pending end action, so without this reset the desktop would stay parked off-centre
        // and half-transparent. The dropped render is not lost - the one below reads the newest index.
        content.animate().cancel()
        content.translationX = 0f
        content.alpha = 1f
        val offset = distance * SLIDE_FRACTION
        content.animate()
            .translationX(-direction * offset)
            .alpha(FADED_ALPHA)
            .setDuration(HALF_DURATION_MS)
            .withEndAction {
                render()
                content.translationX = direction * offset
                content.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(HALF_DURATION_MS)
                    .start()
            }
            .start()
    }

    private fun showBadge() {
        badge.removeCallbacks(hideBadge)
        val index = screenIndex()
        val colorRes = BADGE_COLORS[index % BADGE_COLORS.size]
        badge.setTextColor(ContextCompat.getColor(badge.context, colorRes))
        // Formatted rather than concatenated so a locale with its own digits gets them.
        badge.text = String.format(Locale.getDefault(), "%d", index + FIRST_SCREEN_NUMBER)
        badge.visibility = View.VISIBLE
        badge.postDelayed(hideBadge, BADGE_VISIBLE_MS)
    }

    private companion object {
        /** One hue per desktop screen, sized to MAX_LAUNCHER_SCREEN_COUNT. */
        val BADGE_COLORS = intArrayOf(
            R.color.launcher_screen_badge_1,
            R.color.launcher_screen_badge_2,
            R.color.launcher_screen_badge_3,
            R.color.launcher_screen_badge_4,
            R.color.launcher_screen_badge_5,
        )

        /** Half a screen width each way: a full width leaves the viewport blank at the turn. */
        const val SLIDE_FRACTION = 0.5f
        const val FADED_ALPHA = 0.3f
        const val HALF_DURATION_MS = 130L

        /** Half a second - the more precise of the two figures the request gave for the same badge. */
        const val BADGE_VISIBLE_MS = 500L

        const val SHADOW_OFFSET_PX = 0f
        const val FIRST_SCREEN_NUMBER = 1
    }
}
