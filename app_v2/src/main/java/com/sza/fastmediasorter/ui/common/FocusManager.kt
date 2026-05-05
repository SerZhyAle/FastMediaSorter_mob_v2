package com.sza.fastmediasorter.ui.common

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.FocusRingHelper
import com.sza.fastmediasorter.ui.common.input.InputAction
import timber.log.Timber

/**
 * Shared focus primitive for keyboard-driven list / grid surfaces.
 *
 * Responsibilities:
 *  - arrow / page / home-end navigation over a RecyclerView
 *  - deterministic initial focus
 *  - visible focus state via [FocusRingHelper]
 *  - selection-range extension hooks for `Shift+Up/Down`
 *
 * Arrow navigation is grid-aware (uses [GridLayoutManager.spanCount]).
 * Callers translate [android.view.KeyEvent] -> [InputAction] via the
 * shared parser and call [applyAction], or hand raw key codes to
 * [handleArrowKey] for the legacy path.
 */
class FocusManager(
    private val recyclerView: RecyclerView,
    private val callbacks: FocusCallbacks,
) {

    interface FocusCallbacks {
        /** Focus moved to [position]. */
        fun onItemFocused(position: Int)

        /** User opened item at [position] (Enter / Space / double-click). */
        fun onItemSelected(position: Int)

        /** Total item count in the adapter. */
        fun getItemCount(): Int

        /**
         * Optional: selection range extended from [anchor] to [position].
         * Default no-op; surfaces that support range selection override.
         */
        fun onRangeExtended(anchor: Int, position: Int) {}
    }

    companion object {
        private const val TAG = "FocusManager"
        private const val PAGE_SIZE = 10
        private const val DPAD_ACCEL_REPEAT_THRESHOLD = 6
    }

    private var currentFocusPosition = -1
    private var rangeAnchorPosition = -1
    private var focusHighlightEnabled = true

    /**
     * Legacy key-code entry point. Prefer [applyAction] for new callers.
     */
    fun handleArrowKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val action = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP ->
                if (event.repeatCount > DPAD_ACCEL_REPEAT_THRESHOLD) InputAction.PageJump(-1)
                else InputAction.MoveFocus(FocusDirection.UP)
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (event.repeatCount > DPAD_ACCEL_REPEAT_THRESHOLD) InputAction.PageJump(+1)
                else InputAction.MoveFocus(FocusDirection.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> InputAction.MoveFocus(FocusDirection.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> InputAction.MoveFocus(FocusDirection.RIGHT)
            KeyEvent.KEYCODE_MOVE_HOME -> InputAction.MoveFocus(FocusDirection.FIRST)
            KeyEvent.KEYCODE_MOVE_END -> InputAction.MoveFocus(FocusDirection.LAST)
            KeyEvent.KEYCODE_PAGE_UP -> InputAction.PageJump(-1)
            KeyEvent.KEYCODE_PAGE_DOWN -> InputAction.PageJump(+1)
            else -> null
        } ?: return false
        return applyAction(action)
    }

    /**
     * Apply a semantic navigation action. Returns true if the action
     * affected focus state.
     */
    fun applyAction(action: InputAction): Boolean {
        val itemCount = callbacks.getItemCount()
        if (itemCount == 0) {
            Timber.v("%s: no items", TAG)
            return false
        }

        if (currentFocusPosition == -1) {
            moveFocus(0)
            if (action !is InputAction.MoveFocus && action !is InputAction.PageJump) return true
        }

        val layoutManager = recyclerView.layoutManager ?: return false
        val spanCount = getSpanCount(layoutManager)
        val isGrid = spanCount > 1
        val cur = currentFocusPosition

        return when (action) {
            is InputAction.MoveFocus -> {
                val target = when (action.direction) {
                    FocusDirection.UP -> if (isGrid) cur - spanCount else cur - 1
                    FocusDirection.DOWN -> if (isGrid) cur + spanCount else cur + 1
                    FocusDirection.LEFT -> if (isGrid && cur % spanCount != 0) cur - 1 else cur
                    FocusDirection.RIGHT -> if (isGrid && (cur + 1) % spanCount != 0) cur + 1 else cur
                    FocusDirection.FIRST,
                    FocusDirection.PREVIOUS -> if (action.direction == FocusDirection.FIRST) 0 else cur - 1
                    FocusDirection.LAST,
                    FocusDirection.NEXT -> if (action.direction == FocusDirection.LAST) itemCount - 1 else cur + 1
                }.coerceIn(0, itemCount - 1)
                if (target == cur) return false
                moveFocus(target)
                true
            }
            is InputAction.PageJump -> {
                val jump = if (isGrid) spanCount * 3 else PAGE_SIZE
                val target = (cur + action.deltaPages * jump).coerceIn(0, itemCount - 1)
                moveFocus(target)
                true
            }
            InputAction.RangeExtendUp -> {
                val target = (cur - 1).coerceAtLeast(0)
                extendRange(target)
                true
            }
            InputAction.RangeExtendDown -> {
                val target = (cur + 1).coerceAtMost(itemCount - 1)
                extendRange(target)
                true
            }
            InputAction.OpenCurrent -> selectCurrentItem()
            else -> false
        }
    }

    private fun getSpanCount(lm: RecyclerView.LayoutManager): Int = when (lm) {
        is GridLayoutManager -> lm.spanCount
        is LinearLayoutManager -> 1
        else -> 1
    }

    private fun moveFocus(newPosition: Int) {
        if (newPosition == currentFocusPosition || newPosition < 0) return
        val itemCount = callbacks.getItemCount()
        if (newPosition >= itemCount) return

        val old = currentFocusPosition
        currentFocusPosition = newPosition
        rangeAnchorPosition = newPosition
        recyclerView.smoothScrollToPosition(newPosition)
        callbacks.onItemFocused(newPosition)
        recyclerView.post {
            if (old >= 0) {
                recyclerView.findViewHolderForAdapterPosition(old)?.itemView?.let { view ->
                    FocusRingHelper.setFocused(view, focusHighlightEnabled && false)
                }
            }
            recyclerView.findViewHolderForAdapterPosition(newPosition)?.itemView?.let { view ->
                FocusRingHelper.setFocused(view, focusHighlightEnabled)
                view.requestFocus()
            }
        }
    }

    private fun extendRange(target: Int) {
        val anchor = if (rangeAnchorPosition == -1) currentFocusPosition else rangeAnchorPosition
        if (anchor < 0) {
            moveFocus(target)
            return
        }
        currentFocusPosition = target
        rangeAnchorPosition = anchor
        recyclerView.smoothScrollToPosition(target)
        callbacks.onItemFocused(target)
        callbacks.onRangeExtended(anchor, target)
        recyclerView.post {
            recyclerView.findViewHolderForAdapterPosition(target)?.itemView?.let { view ->
                FocusRingHelper.setFocused(view, focusHighlightEnabled)
                view.requestFocus()
            }
        }
    }

    /** Activate current item (Enter / Space / click). */
    fun selectCurrentItem(): Boolean {
        if (currentFocusPosition in 0 until callbacks.getItemCount()) {
            callbacks.onItemSelected(currentFocusPosition)
            return true
        }
        return false
    }

    fun getCurrentPosition(): Int = currentFocusPosition

    /** Programmatic focus move. */
    fun setPosition(position: Int) {
        val itemCount = callbacks.getItemCount()
        if (position in 0 until itemCount) moveFocus(position)
    }

    /** Reset focus state and visual highlight. */
    fun reset() {
        if (currentFocusPosition >= 0) {
            recyclerView.findViewHolderForAdapterPosition(currentFocusPosition)?.itemView?.let { view ->
                FocusRingHelper.setFocused(view, false)
            }
        }
        currentFocusPosition = -1
        rangeAnchorPosition = -1
    }

    fun setFocusHighlightEnabled(enabled: Boolean) {
        focusHighlightEnabled = enabled
    }

    fun hasFocus(): Boolean = currentFocusPosition >= 0

    /** Focus first item if nothing is focused. */
    fun ensureFocus() {
        if (currentFocusPosition == -1 && callbacks.getItemCount() > 0) moveFocus(0)
    }
}
