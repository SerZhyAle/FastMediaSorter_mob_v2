package com.sza.fastmediasorter.core.ui

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.app.AlertDialog
import timber.log.Timber

/**
 * Posts an [AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED] event on the first
 * interactive element of a dialog [DEFAULT_DELAY_MS] after `show()`, so TalkBack
 * lands on a meaningful control rather than on the dialog window root.
 *
 * Why an event instead of `requestFocus()` - TalkBack focus is governed by the
 * accessibility tree, not the standard view focus system; `View.requestFocus()`
 * does NOT move the TalkBack cursor. The only reliable way to move TalkBack focus
 * programmatically is to dispatch a focused-event from the target view.
 *
 * The 100 ms delay covers the dialog window-attach lifecycle - calling
 * `sendAccessibilityEvent` immediately after `show()` is racy because the
 * dialog's decor view is not yet bound to the AccessibilityManager.
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
     *  4. Custom content view - first child with `isImportantForAccessibility=true`
     *     and `isFocusable=true`. Walks only one nesting level (cheap; deep walk
     *     belongs in a custom-view helper, out of scope here).
     *
     * If no candidate is found, the call is a no-op and TalkBack continues with
     * its default behaviour (read the dialog title, then announce items).
     */
    fun applyInitialFocus(
        dialog: Dialog,
        postDelayMs: Long = DEFAULT_DELAY_MS,
    ) {
        mainHandler.postDelayed({
            val target = pickTarget(dialog) ?: run {
                Timber.d("DialogAccessibilityHelper: no a11y target on ${dialog::class.simpleName}")
                return@postDelayed
            }
            target.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }, postDelayMs)
    }

    private fun pickTarget(dialog: Dialog): View? {
        if (dialog is AlertDialog) {
            listOf(
                AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEUTRAL,
                AlertDialog.BUTTON_NEGATIVE,
            ).forEach { which ->
                val btn = runCatching { dialog.getButton(which) }.getOrNull()
                if (btn != null && btn.visibility == View.VISIBLE) return btn
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
