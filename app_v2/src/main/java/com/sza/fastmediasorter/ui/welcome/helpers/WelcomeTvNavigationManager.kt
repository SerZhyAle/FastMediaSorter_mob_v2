package com.sza.fastmediasorter.ui.welcome.helpers

import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding

/**
 * Owns D-pad / keyboard focus movement across the onboarding slider (S0230, S0289): focus search
 * inside the current page or the bottom bar, pulling focus off the ViewPager2 container onto a real
 * control, TAB cycling, and page flipping at a scope's horizontal edge.
 *
 * The Activity keeps only the key-dispatch entry point and routes each key here (S2312). It holds no
 * state of its own - page index, page count and the focused view are read through the suppliers
 * below - so a recreation of the host needs no teardown.
 */
class WelcomeTvNavigationManager(
    private val binding: ActivityWelcomeBinding,
    private val currentPage: () -> Int,
    private val pageCount: () -> Int,
    private val focusedView: () -> View?,
    private val activateFocused: () -> Boolean,
) {

    /** ENTER / DPAD_CENTER: activate the focused clickable control, else the visible primary CTA. */
    fun handleSelect(): Boolean {
        if (activateFocused()) return true
        val cta = binding.btnFinish.takeIf { it.isVisible }
            ?: binding.btnNext.takeIf { it.isVisible }
        cta?.performClick()
        return cta != null
    }

    /**
     * LEFT / RIGHT: move focus to a neighbour inside the current scope; flip the page only at the
     * scope's horizontal edge. Always consumes so ViewPager2 never performs its own page scroll.
     */
    fun handleHorizontal(direction: Int, forward: Boolean): Boolean {
        val focused = focusedView()
        val scope = horizontalScope(focused)
        // Focus on the pager container (typical after the very first D-pad key): pull it into the page
        // instead of flipping, so the in-page pickers become reachable without a TAB key.
        if (scope == null && enterPageFromContainer()) return true
        val neighbour = if (focused != null && scope != null) {
            FocusFinder.getInstance().findNextFocus(scope, focused, direction)
        } else {
            null
        }
        return if (neighbour != null && neighbour !== focused) {
            neighbour.requestFocus()
            true
        } else {
            flipPage(forward)
        }
    }

    /**
     * UP / DOWN: move focus between the current page content and the bottom bar without letting the
     * ViewPager2 RecyclerView perform its focus-escape page scroll. Always consumes.
     */
    fun handleVertical(direction: Int): Boolean {
        val focused = focusedView()
        val bar = binding.layoutBottomNav
        val page = currentPageGroup()
        val inBar = focused != null && isDescendantOf(focused, bar)
        val inPage = focused != null && page != null && isDescendantOf(focused, page)

        when {
            inPage -> {
                val next = FocusFinder.getInstance().findNextFocus(page, focused, direction)
                if (next != null && next !== focused) {
                    next.requestFocus()
                } else if (direction == View.FOCUS_DOWN) {
                    focusBar()
                }
            }
            inBar -> if (direction == View.FOCUS_UP) {
                // From the bar, UP re-enters the page on a real control (last focusable for a natural
                // "come back to where you were near the bottom" feel), else stays put.
                val target = currentPageGroup()?.let { p ->
                    val list = ArrayList<View>()
                    p.addFocusables(list, View.FOCUS_FORWARD)
                    list.lastOrNull { it.isShown && it.isFocusable }
                }
                target?.requestFocus()
            }
            // Focus on the pager container: pull it onto a real control instead of leaving it stranded.
            else -> enterPageFromContainer()
        }
        return true
    }

    /** TAB / SHIFT+TAB: cycle focus within the slider (page + bar) with wraparound, no pager scroll. */
    fun handleSequential(forward: Boolean): Boolean {
        val all = sliderFocusables()
        if (all.isEmpty()) return false
        val idx = all.indexOf(focusedView())
        val nextIdx = when {
            idx < 0 -> 0
            forward -> (idx + 1) % all.size
            else -> (idx - 1 + all.size) % all.size
        }
        all[nextIdx].requestFocus()
        return true
    }

    /**
     * Advance or rewind the pager by one page, returning false at either end. Public because the
     * device-profile page confirms a tile by asking for the same step Next would take (S1383), so
     * page flipping is not exclusively a key-driven action.
     */
    fun flipPage(forward: Boolean): Boolean {
        val page = currentPage()
        return if (forward) {
            if (page < pageCount() - 1) {
                binding.viewPager.currentItem = page + 1
                true
            } else {
                false
            }
        } else {
            if (page > 0) {
                binding.viewPager.currentItem = page - 1
                true
            } else {
                false
            }
        }
    }

    /**
     * The itemView of the currently-visible ViewPager2 page, or null if the pager is not laid out
     * yet. Every caller needs it as a focus container, so a leaf itemView reads as absent.
     */
    private fun currentPageGroup(): ViewGroup? {
        val recycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        return recycler.findViewHolderForAdapterPosition(currentPage())?.itemView as? ViewGroup
    }

    private fun isDescendantOf(view: View, parent: View): Boolean {
        var p = view.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
    }

    /** Horizontal focus scope for [view]: the bottom bar if focus is there, else the current page. */
    private fun horizontalScope(view: View?): ViewGroup? {
        if (view == null) return null
        return if (isDescendantOf(view, binding.layoutBottomNav)) {
            binding.layoutBottomNav
        } else {
            currentPageGroup()?.takeIf { isDescendantOf(view, it) }
        }
    }

    /**
     * The first focusable control inside the current page, or null if the page has none.
     * ViewPager2's RecyclerView descends to the page when asked for FOCUS_DOWN, so this returns the
     * top-most actionable control (e.g. the language picker on the first page).
     */
    private fun firstPageFocusable(): View? {
        val page = currentPageGroup() ?: return null
        val candidates = ArrayList<View>()
        page.addFocusables(candidates, View.FOCUS_FORWARD)
        return candidates.firstOrNull { it.isShown && it.isFocusable }
    }

    /**
     * Move focus from the pager container onto a real control: the first focusable in the current
     * page, falling back to the bottom bar. Returns true if focus moved off the container.
     *
     * Why: the first D-pad press on a freshly-opened page leaves focus on the ViewPager2 RecyclerView
     * (the page's parent, not a descendant), so it belongs to neither the page nor the bar scope.
     * Without this, LEFT/RIGHT would resolve a null scope and fall straight through to flipPage,
     * making the in-page pickers unreachable on a remote that has no TAB key. S0289.
     */
    private fun enterPageFromContainer(): Boolean {
        val first = firstPageFocusable()
        if (first != null) {
            first.requestFocus()
            return true
        }
        val barReachable = binding.btnNext.isVisible || binding.btnFinish.isVisible
        if (barReachable) focusBar()
        return barReachable
    }

    /** Focus the visible primary button in the bottom bar. */
    private fun focusBar() {
        val target = binding.btnFinish.takeIf { it.isVisible }
            ?: binding.btnNext
        target.requestFocus()
    }

    /** Visible, focusable controls of the slider in TAB order: current page content, then bottom bar. */
    private fun sliderFocusables(): List<View> {
        val list = ArrayList<View>()
        currentPageGroup()?.addFocusables(list, View.FOCUS_FORWARD)
        binding.layoutBottomNav.addFocusables(list, View.FOCUS_FORWARD)
        return list.filter { it.isShown && it.isFocusable }
    }
}
