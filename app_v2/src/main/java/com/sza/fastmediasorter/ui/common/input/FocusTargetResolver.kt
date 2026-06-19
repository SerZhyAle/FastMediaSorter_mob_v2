package com.sza.fastmediasorter.ui.common.input

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Guards against the "D-pad / keyboard focus parks on a scrollable container" bug class
 * (S0504, generalising the S0289 WelcomeActivity fix).
 *
 * When initial focus is requested on a non-touch input device, a screen may hand back a paged or
 * scrollable container (`ViewPager2`, `ScrollView` / `NestedScrollView`, or a fragment-host
 * `ViewGroup`). The framework then parks focus on that container (or its internal `RecyclerView`),
 * directional handling finds no real target, and the in-screen controls become unreachable while
 * the first directional key is wasted. This resolver redirects such a target to the first real
 * focusable control inside it.
 *
 * Intentionally left alone: a `RecyclerView` that already delegates focus to its bound items
 * (`descendantFocusability == FOCUS_AFTER_DESCENDANTS` with at least one child) - it legitimately
 * owns focus so list scrolling and item traversal keep working.
 */
object FocusTargetResolver {

    /**
     * @return the first real focusable leaf inside [view] if [view] is a container that would
     * otherwise trap focus, [view] itself if it is already a valid target, or null if [view] is null.
     */
    fun resolveToLeafFocusable(view: View?): View? {
        if (view == null) return null
        // A leaf control (Button, ImageButton, TextView, etc.) is already a valid focus target.
        if (view !is ViewGroup) return view
        // A list that delegates focus to its bound items should keep focus so scrolling still works.
        if (view is RecyclerView &&
            view.descendantFocusability == ViewGroup.FOCUS_AFTER_DESCENDANTS &&
            view.childCount > 0
        ) {
            return view
        }

        val candidates = ArrayList<View>()
        // FOCUSABLES_ALL (not the touch-mode-filtered default): this guard only runs for non-touch
        // input (TV / D-pad / keyboard), where buttons are focusable even though they are not
        // focusable-in-touch-mode. The default 2-arg overload would drop them while in touch mode.
        view.addFocusables(candidates, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        val focusable = candidates.filter {
            it !== view && it.isFocusable && it.visibility == View.VISIBLE
        }

        // Prefer a real interactive control: not another scroll container (which would re-trap focus)
        // and not an EditText (which would pop the soft keyboard the moment the screen opens).
        return focusable.firstOrNull { !isScrollContainer(it) && it !is EditText }
            ?: focusable.firstOrNull { !isScrollContainer(it) }
            ?: focusable.firstOrNull()
            ?: view
    }

    /**
     * @return true when a directional key landing on [view] would be wasted because focus is parked
     * on a trapping container (or nowhere). Mirrors [resolveToLeafFocusable]'s redirect decision so
     * the dispatch-time guard and the initial-focus redirect never diverge: null focus traps, and a
     * container traps only when the resolver would redirect off it to a real leaf. A leaf control or a
     * focus-delegating RecyclerView (returned as-is by [resolveToLeafFocusable]) is not a trap.
     */
    fun isFocusTrapContainer(view: View?): Boolean {
        if (view == null) return true
        val leaf = resolveToLeafFocusable(view)
        return leaf != null && leaf !== view
    }

    private fun isScrollContainer(view: View): Boolean =
        view is RecyclerView ||
            view is ViewPager2 ||
            view is ScrollView ||
            view is NestedScrollView
}
