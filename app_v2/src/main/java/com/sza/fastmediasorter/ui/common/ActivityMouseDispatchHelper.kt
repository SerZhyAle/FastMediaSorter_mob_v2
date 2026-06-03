package com.sza.fastmediasorter.ui.common

import android.view.MotionEvent
import android.view.View

/**
 * Activity-level bridge around [MouseEventHandler].
 *
 * Keeps the default mouse contract small and conservative:
 * - wheel is routed only when the Activity provides an explicit scroll target,
 *   or when the currently-focused view is itself scrollable;
 * - right-click / back / forward fall back to Activity hooks;
 * - screen-specific surfaces can override the hooks in BaseActivity descendants
 *   without re-implementing mouse parsing.
 */
class ActivityMouseDispatchHelper(
    private val rootViewProvider: () -> View?,
    private val focusedViewProvider: () -> View?,
    private val scrollTargetProvider: () -> View?,
    private val onContextClick: (View, Float, Float) -> Unit,
    private val onMiddleClick: (View) -> Unit,
    private val onNavigateBack: (View) -> Unit,
    private val onNavigateForward: (View) -> Unit,
) {

    private val mouseHandler = MouseEventHandler(
        // Activity-level helper owns only secondary buttons, wheel and hover. The primary (left)
        // click must pass through to the view under the cursor, so it is never consumed here.
        consumePrimaryClick = false,
        callbacks = object : MouseEventHandler.MouseEventCallbacks {
            override fun onRightClick(view: View, x: Float, y: Float) {
                resolveInteractionTarget()?.let { onContextClick(it, x, y) }
            }

            override fun onMiddleClick(view: View) {
                resolveInteractionTarget()?.let(onMiddleClick)
            }

            override fun onScrollWheel(
                view: View,
                deltaY: Float,
                deltaX: Float,
                withShift: Boolean,
                withCtrl: Boolean,
            ) {
                val scrollTarget = resolveScrollTarget() ?: return
                val scrollFactor = scrollTarget.context.resources.displayMetrics.density * 64f
                val dx = (-deltaX * scrollFactor).toInt()
                val dy = (-deltaY * scrollFactor).toInt()
                scrollTarget.scrollBy(dx, dy)
            }

            override fun onNavigateBack(view: View) {
                resolveInteractionTarget()?.let(onNavigateBack)
            }

            override fun onNavigateForward(view: View) {
                resolveInteractionTarget()?.let(onNavigateForward)
            }
        }
    )

    fun handleTouchEvent(event: MotionEvent?): Boolean {
        val root = rootViewProvider() ?: return false
        val inputEvent = event ?: return false
        return mouseHandler.handleMotionEvent(root, inputEvent)
    }

    fun handleGenericMotionEvent(event: MotionEvent?): Boolean {
        val root = rootViewProvider() ?: return false
        val inputEvent = event ?: return false
        return mouseHandler.handleGenericMotionEvent(root, inputEvent)
    }

    private fun resolveInteractionTarget(): View? = focusedViewProvider() ?: rootViewProvider()

    private fun resolveScrollTarget(): View? {
        val explicitTarget = scrollTargetProvider()
        if (explicitTarget != null && isScrollable(explicitTarget)) return explicitTarget

        val focused = focusedViewProvider()
        if (focused != null && isScrollable(focused)) return focused

        // No explicit target and the focused view itself does not scroll: walk up the focused
        // view's ancestor chain to the nearest scrollable container (ScrollView / NestedScrollView /
        // RecyclerView). Without this, a mouse-only user on a long form cannot wheel-scroll to a
        // field or the Save button when focus sits on a non-scrollable child. S0289.
        return focused?.let { findScrollableAncestor(it) }
    }

    private fun findScrollableAncestor(start: View): View? {
        var parent = start.parent
        while (parent is View) {
            if (isScrollable(parent)) return parent
            parent = parent.parent
        }
        return null
    }

    private fun isScrollable(view: View): Boolean {
        return view.canScrollVertically(-1) || view.canScrollVertically(1) ||
            view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)
    }
}