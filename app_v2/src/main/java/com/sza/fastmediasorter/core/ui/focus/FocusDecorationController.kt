package com.sza.fastmediasorter.core.ui.focus

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * S0943: draws the D-pad/TV focus affordance the platform-standard way - by decorating the focused
 * view IN PLACE with an accent outline (set as the view's foreground), instead of a separate overlay
 * that computes the view's coordinates. Because the outline is drawn by the focused view in its own
 * bounds, it can never be offset from the view (the failure mode of the retired overlay frame).
 *
 * One controller per [Window]; kept Window-generic so dialog / bottom-sheet windows reuse it. The
 * outline is applied only when the window is NOT in touch mode: a highlight around the last-tapped
 * control under touch would be misleading, so it is reserved for hardware focus (D-pad / keyboard /
 * gamepad).
 */
class FocusDecorationController(private val window: Window) {

    // The view currently wearing our outline, and the foreground it had before we replaced it, so the
    // original (ripple, etc.) is restored verbatim when focus leaves.
    private var decoratedView: View? = null
    private var savedForeground: Drawable? = null

    private val globalFocusListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus -> onFocusChanged(newFocus) }

    private val touchModeListener =
        ViewTreeObserver.OnTouchModeChangeListener { isInTouchMode -> onTouchModeChanged(isInTouchMode) }

    fun attach() {
        val observer = window.decorView.viewTreeObserver
        observer.addOnGlobalFocusChangeListener(globalFocusListener)
        observer.addOnTouchModeChangeListener(touchModeListener)
        // Reflect whatever focus already exists at attach time (e.g. Activity restored in D-pad mode).
        onFocusChanged(window.decorView.findFocus())
    }

    fun detach() {
        val observer = window.decorView.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(globalFocusListener)
            observer.removeOnTouchModeChangeListener(touchModeListener)
        }
        clearDecoration()
    }

    private fun onFocusChanged(newFocus: View?) {
        clearDecoration()
        if (newFocus != null && shouldDecorate(newFocus)) {
            decorate(newFocus)
        }
    }

    private fun shouldDecorate(view: View): Boolean {
        if (!view.isFocusable || window.decorView.isInTouchMode) return false
        // Skip views that already carry their own foreground focus affordance (e.g. buttons with a
        // focus-stroke foreground): overwriting it would drop a purpose-built, higher-contrast ring.
        return view.foreground == null
    }

    private fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (isInTouchMode) {
            clearDecoration()
        } else {
            onFocusChanged(window.decorView.findFocus())
        }
    }

    private fun decorate(view: View) {
        val outline = ContextCompat.getDrawable(view.context, R.drawable.focus_decoration_outline)
            ?: return
        savedForeground = view.foreground
        view.foreground = outline
        decoratedView = view
    }

    private fun clearDecoration() {
        decoratedView?.let { it.foreground = savedForeground }
        decoratedView = null
        savedForeground = null
    }
}
