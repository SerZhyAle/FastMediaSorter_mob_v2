package com.sza.fastmediasorter.ui.common.widget

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Handle resolution and D-pad contract of the consolidated crop frame, on the default `bands` preset:
 * the camera-OCR and player crop surfaces share this model, so a regression here reaches both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CropFrameViewHandleTest {

    private val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density

    private fun laidOutView(): CropFrameView {
        val view = CropFrameView(RuntimeEnvironment.getApplication())
        val spec = View.MeasureSpec.makeMeasureSpec(dp(VIEW_SIDE_DP).toInt(), View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun down(view: CropFrameView, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)
        val handled = view.onTouchEvent(event)
        event.recycle()
        return handled
    }

    private fun moveTo(view: CropFrameView, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    private fun key(view: CropFrameView, keyCode: Int): Boolean =
        view.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

    @Test
    fun `corner grab resizes only that corner`() {
        val view = laidOutView()

        assertTrue(down(view, dp(INSET_DP), dp(INSET_DP)))
        moveTo(view, dp(60f), dp(60f))

        val rect = view.getCropRectNormalized()
        assertEquals(0.15f, rect.left, TOLERANCE)
        assertEquals(0.15f, rect.top, TOLERANCE)
        assertEquals(0.97f, rect.right, TOLERANCE)
        assertTrue(view.isFrameTouched())
    }

    @Test
    fun `grab inside moves the whole frame`() {
        val view = laidOutView()
        val before = view.getCropRectNormalized()

        assertTrue(down(view, dp(200f), dp(200f)))
        moveTo(view, dp(220f), dp(200f))

        // The default frame sits 12dp from each side, so a 20dp push travels only until it is flush.
        val after = view.getCropRectNormalized()
        assertEquals(before.width(), after.width(), TOLERANCE)
        assertEquals(0.06f, after.left, TOLERANCE)
        assertEquals(1f, after.right, TOLERANCE)
    }

    @Test
    fun `grab outside the frame is not handled`() {
        val view = laidOutView()
        // Shrink the frame first, so there is room for a point that no grab band covers.
        assertTrue(down(view, dp(VIEW_SIDE_DP - INSET_DP), dp(VIEW_SIDE_DP - INSET_DP)))
        moveTo(view, dp(200f), dp(200f))

        assertFalse(down(view, dp(350f), dp(350f)))
    }

    @Test
    fun `edge pushed to the view border still resolves`() {
        val view = laidOutView()
        assertTrue(down(view, dp(INSET_DP), dp(200f)))
        moveTo(view, -dp(50f), dp(200f))
        assertEquals(0f, view.getCropRectNormalized().left, TOLERANCE)

        // S1602: the grab band of an edge at the border keeps half of itself on-screen.
        assertTrue(down(view, 0f, dp(200f)))
        moveTo(view, dp(30f), dp(200f))

        assertEquals(0.075f, view.getCropRectNormalized().left, TOLERANCE)
    }

    @Test
    fun `dpad moves the frame and resizes it after the center toggle`() {
        val view = laidOutView()

        assertTrue(key(view, KeyEvent.KEYCODE_DPAD_RIGHT))
        val moved = view.getCropRectNormalized()
        assertEquals(0.06f, moved.left, TOLERANCE)
        assertEquals(1f, moved.right, TOLERANCE)

        assertTrue(key(view, KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(key(view, KeyEvent.KEYCODE_DPAD_LEFT))

        val resized = view.getCropRectNormalized()
        assertEquals(0.06f, resized.left, TOLERANCE)
        assertEquals(0.96f, resized.right, TOLERANCE)
    }

    private companion object {
        const val VIEW_SIDE_DP = 400f
        const val INSET_DP = 12f
        const val TOLERANCE = 0.01f
    }
}
