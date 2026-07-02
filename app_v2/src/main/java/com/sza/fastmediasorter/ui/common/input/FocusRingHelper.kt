package com.sza.fastmediasorter.ui.common.input

import android.view.View

/**
 * Reflects keyboard/D-pad focus as the view's activated state.
 *
 * S0819: the visible focus ring is now drawn app-wide by the window-level `FocusFrameOverlay`, so this
 * helper no longer paints its own foreground ring - that would stack a second ring under the overlay.
 * It keeps only the `isActivated` toggle that selection-state selectors still key off, so callers
 * (e.g. [com.sza.fastmediasorter.ui.common.FocusManager]) need no change.
 */
object FocusRingHelper {

    /** Reflect [focused] as the view's activated state; the app-wide overlay draws the ring itself. */
    fun setFocused(view: View, focused: Boolean) {
        view.isActivated = focused
    }

    /**
     * Convenience: drive [setFocused] from a focus listener for views that do not flow through
     * [com.sza.fastmediasorter.ui.common.FocusManager].
     */
    fun attach(view: View) {
        val existing = view.onFocusChangeListener
        view.setOnFocusChangeListener { v, hasFocus ->
            setFocused(v, hasFocus)
            existing?.onFocusChange(v, hasFocus)
        }
    }
}
