package com.sza.fastmediasorter.core.ui.focus

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import timber.log.Timber

/**
 * S0819: drives one [FocusFrameOverlay] for a single [Window], showing a travelling frame around the
 * currently D-pad/TV-focused view and hiding it in touch mode.
 *
 * Kept [Window]-generic (no Activity cast) so Phase 03 can reuse it for dialog / bottom-sheet windows.
 *
 * The overlay is attached to `decorView.overlay`, so all bounds must be expressed in decorView-local
 * coordinates - hence [offsetDescendantRectToMyCoords][ViewGroup.offsetDescendantRectToMyCoords]
 * rather than screen/window coordinates.
 *
 * The frame is only ever shown when the window is NOT in touch mode: on a touchscreen a highlight
 * around the last-tapped control would be misleading, so it is reserved for hardware focus (D-pad /
 * gamepad / keyboard).
 */
class FocusFrameController(private val window: Window) {

    private var overlay: FocusFrameOverlay? = null

    // True while the frame is drawn - lets the pre-draw pass early-return without touching the tree.
    private var showing = false

    // Reused across every bounds computation so per-frame re-sync stays allocation-free.
    private val boundsScratch = Rect()

    private val globalFocusListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus -> onFocusChanged(newFocus) }

    private val touchModeListener =
        ViewTreeObserver.OnTouchModeChangeListener { isInTouchMode -> onTouchModeChanged(isInTouchMode) }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (showing) {
            // Keep the frame glued to its view while the layout shifts under it (scroll, resize, ..).
            val focused = window.decorView.findFocus()
            if (focused != null) {
                moveToView(focused)
            } else {
                hide()
            }
        }
        true
    }

    fun attach() {
        if (overlay != null) return
        val created = FocusFrameOverlay(window.context)
        overlay = created
        window.decorView.overlay.add(created)

        val observer = window.decorView.viewTreeObserver
        observer.addOnGlobalFocusChangeListener(globalFocusListener)
        observer.addOnTouchModeChangeListener(touchModeListener)
        observer.addOnPreDrawListener(preDrawListener)

        // Reflect whatever focus already exists at attach time (e.g. Activity restored in D-pad mode).
        onFocusChanged(window.decorView.findFocus())
    }

    fun detach() {
        val observer = window.decorView.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(globalFocusListener)
            observer.removeOnTouchModeChangeListener(touchModeListener)
            observer.removeOnPreDrawListener(preDrawListener)
        }
        overlay?.let { window.decorView.overlay.remove(it) }
        overlay = null
        showing = false
    }

    private fun onFocusChanged(newFocus: View?) {
        if (newFocus != null && newFocus.isFocusable && !window.decorView.isInTouchMode) {
            Timber.d("S0819: frame moved to focused view %s", newFocus.javaClass.simpleName)
            moveToView(newFocus)
        } else {
            hide()
        }
    }

    private fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (isInTouchMode) {
            hide()
        } else {
            onFocusChanged(window.decorView.findFocus())
        }
    }

    private fun moveToView(view: View) {
        boundsScratch.set(0, 0, view.width, view.height)
        (window.decorView as ViewGroup).offsetDescendantRectToMyCoords(view, boundsScratch)
        overlay?.moveTo(boundsScratch)
        showing = true
    }

    private fun hide() {
        if (!showing) return
        overlay?.clear()
        showing = false
    }
}
