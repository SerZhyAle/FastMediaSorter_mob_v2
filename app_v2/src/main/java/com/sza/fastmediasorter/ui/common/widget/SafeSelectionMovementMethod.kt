package com.sza.fastmediasorter.ui.common.widget

import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import timber.log.Timber

/**
 * Keeps a touch on a programmatically cleared input field from crashing the process.
 *
 * S2572: both selecting branches of `ArrowKeyMovementMethod.onTouchEvent` read
 * `buffer.getSpanStart(LAST_TAP_DOWN)` and hand `Math.min(startOffset, offset)` straight to
 * [Selection.setSelection], with no check for the `-1` that `getSpanStart` returns for an absent span.
 * That span is written only on `ACTION_DOWN`, and `TextView.onTouchEvent` forwards to the movement
 * method only while `mLayout != null` - so the frame in which a `TextInputLayout` clear-text icon
 * empties the field and drops its layout swallows the `ACTION_DOWN`, and the matching `ACTION_UP`
 * arrives here with no span to read. `Selection.setSelection` then runs
 * `setSpan(SELECTION_START, -1, -1)` and throws on the main thread.
 *
 * `LAST_TAP_DOWN` is private to the framework class, so the app cannot repair the precondition. The
 * caret goes to the end of the text instead, which for the field that triggers this - just emptied -
 * is position 0, exactly where the tap was asking to put it.
 */
class SafeSelectionMovementMethod : ArrowKeyMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean =
        try {
            super.onTouchEvent(widget, buffer, event)
        } catch (e: IndexOutOfBoundsException) {
            Timber.d("S2572: guard caught the framework caret crash on a cleared field")
            Timber.w(e, "Caret restore rejected on a cleared field; collapsing selection to text end")
            Selection.setSelection(buffer, buffer.length)
            true
        }

    companion object {
        /** Stateless, like every framework movement method, so one instance serves every field. */
        val INSTANCE = SafeSelectionMovementMethod()
    }
}
