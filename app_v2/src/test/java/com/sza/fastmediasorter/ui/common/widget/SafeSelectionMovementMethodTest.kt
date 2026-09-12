package com.sza.fastmediasorter.ui.common.widget

import android.app.Activity
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.method.MetaKeyKeyListener
import android.text.method.TextKeyListener
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2572: a tap whose ACTION_DOWN never reached the movement method must not kill the process.
 *
 * The state under test is "the buffer is selecting, but no LAST_TAP_DOWN span was recorded".
 * `TextView.onTouchEvent` hands the event to its `Editor` before the `mLayout != null` gate that
 * gates the movement method, so on the device the clear-text icon produces exactly that split: the
 * ACTION_DOWN lands in the Editor while the field is between layouts, the movement method never sees
 * it, and the ACTION_UP arrives with the span missing. The setup below reproduces the split with
 * public API by delivering the ACTION_DOWN before the buffer enters selecting state.
 *
 * The first case is a negative control on purpose - it asserts the stock method still throws on this
 * exact state, so a platform change that stops reproducing the defect fails loudly instead of leaving
 * the second case passing while observing nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeSelectionMovementMethodTest {

    @Test
    fun `stock movement method still throws when the tap-down span is missing`() {
        val field = fieldWithUnrecordedTapDown()

        assertThrows(IndexOutOfBoundsException::class.java) {
            ArrowKeyMovementMethod.getInstance().onTouchEvent(field, field.editable(), tap(MotionEvent.ACTION_UP))
        }
    }

    @Test
    fun `guarded movement method leaves a valid selection instead of throwing`() {
        val field = fieldWithUnrecordedTapDown()
        val buffer = field.editable()

        SafeSelectionMovementMethod.INSTANCE.onTouchEvent(field, buffer, tap(MotionEvent.ACTION_UP))

        val start = Selection.getSelectionStart(buffer)
        val end = Selection.getSelectionEnd(buffer)
        assertTrue("selection start $start out of bounds", start in 0..buffer.length)
        assertTrue("selection end $end out of bounds", end in 0..buffer.length)
    }

    @Test
    fun `the shared input field carries the guard`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val themed = ContextThemeWrapper(activity, MATERIAL_THEME)

        val field = SafeSelectionTextInputEditText(themed)

        assertTrue(
            "movement method is ${field.movementMethod}",
            field.movementMethod is SafeSelectionMovementMethod,
        )
    }

    /**
     * Every precondition of the crashing branch is established and then asserted rather than assumed.
     * The caret matters as much as focus: `Selection.setSelection` writes no span when asked for the
     * selection already in place, and on a field that never held one that is the very `-1, -1` under
     * test. The ACTION_DOWN is delivered while the buffer is still not selecting, which is what leaves
     * LAST_TAP_DOWN unrecorded while clearing the Editor's touch-focus flag - the same asymmetry the
     * missing layout produces on the device.
     */
    private fun fieldWithUnrecordedTapDown(): EditText {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val field = EditText(activity)
        activity.setContentView(field, ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT))
        field.setText("")
        field.requestFocus()
        field.measure(
            View.MeasureSpec.makeMeasureSpec(FIELD_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FIELD_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        field.layout(0, 0, FIELD_WIDTH, FIELD_HEIGHT)
        Selection.setSelection(field.editable(), 0)

        field.onTouchEvent(tap(MotionEvent.ACTION_DOWN))

        TextKeyListener.getInstance().onKeyDown(
            field,
            field.text,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT),
        )

        assertTrue("field must be focused for the movement method to run", field.isFocused)
        assertFalse("touch-focus select would return before the crashing branch", field.didTouchFocusSelect())
        assertEquals(
            "buffer must be in selecting state for the crashing branch to run",
            1,
            MetaKeyKeyListener.getMetaState(field.editable(), KeyEvent.META_SHIFT_ON),
        )
        return field
    }

    private fun EditText.editable(): Spannable = text as Spannable

    private fun tap(action: Int): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, TAP_X, TAP_Y, 0)
    }

    private companion object {
        const val FIELD_WIDTH = 500
        const val FIELD_HEIGHT = 100
        const val TAP_X = 10f
        const val TAP_Y = 10f
        val MATERIAL_THEME = com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    }
}
