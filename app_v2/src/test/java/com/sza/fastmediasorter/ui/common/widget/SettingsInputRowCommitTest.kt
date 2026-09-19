package com.sza.fastmediasorter.ui.common.widget

import android.app.Activity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3234: the row committed only on focus loss, so tapping a screen action while the field stayed
 * focused ran that action on the previous value, and ENTER fell through to the window and closed the
 * hosting Activity instead of committing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsInputRowCommitTest {

    private val committed = mutableListOf<String>()

    private fun newRow(): SettingsInputRow {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
        val row = SettingsInputRow(context)
        row.setOnCommitListener { value -> committed += value.toString() }
        return row
    }

    private fun hostedRow(): SettingsInputRow {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
        val host = FrameLayout(context)
        val row = newRow()
        host.addView(row)
        return row
    }

    private fun editorOf(row: SettingsInputRow): TextInputEditText = row.findViewById(R.id.sir_input)

    @Test
    fun `commitPending reports the typed value without a focus change`() {
        val row = newRow()
        editorOf(row).setText("8917")

        row.commitPending()

        assertEquals(listOf("8917"), committed)
    }

    @Test
    fun `commitPending stays silent when the value has not changed since the last commit`() {
        val row = newRow()
        editorOf(row).setText("8917")

        row.commitPending()
        row.commitPending()

        assertEquals(listOf("8917"), committed)
    }

    @Test
    fun `focus loss still commits`() {
        val row = hostedRow()
        val editor = editorOf(row)
        editor.isFocusableInTouchMode = true
        editor.requestFocus()
        editor.setText("9001")

        editor.clearFocus()

        assertEquals(listOf("9001"), committed)
    }

    @Test
    fun `IME done commits`() {
        val row = newRow()
        val editor = editorOf(row)
        editor.setText("8080")

        editor.onEditorAction(EditorInfo.IME_ACTION_DONE)

        assertEquals(listOf("8080"), committed)
    }

    @Test
    fun `hardware enter commits and is consumed instead of reaching the window`() {
        val row = hostedRow()
        val editor = editorOf(row)
        editor.isFocusableInTouchMode = true
        editor.requestFocus()
        editor.setText("8081")

        editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        val consumed = editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))

        assertTrue("ENTER must be consumed by the row, not passed to the hosting window", consumed)
        assertEquals(listOf("8081"), committed)
    }

    @Test
    fun `detaching the row commits the pending edit`() {
        // Attached to a real window on purpose: onDetachedFromWindow never runs on a view tree that
        // was only ever built in memory.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter))
        activity.setContentView(host)
        val row = newRow()
        host.addView(row)
        editorOf(row).setText("8082")

        host.removeView(row)

        assertEquals(listOf("8082"), committed)
    }
}
