package com.sza.fastmediasorter.core.ui

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Moves the TalkBack cursor onto the first interactive element of a dialog
 * [DEFAULT_DELAY_MS] after `show()`, so TalkBack lands on a meaningful control
 * rather than on the dialog window root.
 *
 * Why `ACTION_ACCESSIBILITY_FOCUS` - TalkBack focus is governed by the accessibility
 * tree, not the view focus system, so `View.requestFocus()` does not move it. Sending
 * a `TYPE_VIEW_ACCESSIBILITY_FOCUSED` event does not move it either: the event only
 * reports a focus change, and `ViewRootImpl` adopts it as the accessibility-focus host
 * only for views with an `AccessibilityNodeProvider`. Performing the action is the call
 * that actually grants accessibility focus.
 *
 * The 100 ms delay covers the dialog window-attach lifecycle - acting immediately
 * after `show()` is racy because the dialog's decor view is not yet bound to the
 * AccessibilityManager. A dialog dismissed inside that window is skipped.
 *
 * Source: §6.5 of `PLAN/S0230_tv-keyboard-navigation-coverage.md` (best-practice
 * research 2026-05-17), Material Components issue #1400.
 */
object DialogAccessibilityHelper {

    /** Default focus-post delay. Matches Microsoft Mobile Engineering accessibility-guide recipe. */
    const val DEFAULT_DELAY_MS = 100L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Schedule TalkBack initial focus on [dialog]'s most-meaningful button.
     *
     * Search order:
     *  1. Positive button (`BUTTON_POSITIVE`).
     *  2. Neutral button (`BUTTON_NEUTRAL`).
     *  3. Negative button (`BUTTON_NEGATIVE`).
     *  4. Custom content view - the first view, depth-first over the whole decor
     *     tree, with `isImportantForAccessibility=true` and `isFocusable=true`.
     *
     * If no candidate is found, the call is a no-op and TalkBack continues with
     * its default behaviour (read the dialog title, then announce items).
     */
    fun applyInitialFocus(
        dialog: Dialog,
        postDelayMs: Long = DEFAULT_DELAY_MS,
    ) {
        mainHandler.postDelayed({
            if (!dialog.isShowing) return@postDelayed
            val target = pickTarget(dialog) ?: run {
                Timber.d("DialogAccessibilityHelper: no a11y target on ${dialog::class.simpleName}")
                return@postDelayed
            }
            target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        }, postDelayMs)
    }

    private fun pickTarget(dialog: Dialog): View? {
        when (dialog) {
            is androidx.appcompat.app.AlertDialog -> {
                listOf(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE,
                    androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL,
                    androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE,
                ).forEach { which ->
                    val btn = runCatching { dialog.getButton(which) }.getOrNull()
                    if (btn != null && btn.visibility == View.VISIBLE) return btn
                }
            }
            is android.app.AlertDialog -> {
                listOf(
                    android.app.AlertDialog.BUTTON_POSITIVE,
                    android.app.AlertDialog.BUTTON_NEUTRAL,
                    android.app.AlertDialog.BUTTON_NEGATIVE,
                ).forEach { which ->
                    val btn = runCatching { dialog.getButton(which) }.getOrNull()
                    if (btn != null && btn.visibility == View.VISIBLE) return btn
                }
            }
        }
        // Custom-content fallback - walk the decor view tree for the first focusable leaf.
        val root = dialog.window?.decorView ?: return null
        return firstFocusableLeaf(root)
    }

    private fun firstFocusableLeaf(view: View): View? {
        if (view.isImportantForAccessibility && view.isFocusable && view.visibility == View.VISIBLE) {
            return view
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val candidate = firstFocusableLeaf(view.getChildAt(i)) ?: continue
                return candidate
            }
        }
        return null
    }
}
