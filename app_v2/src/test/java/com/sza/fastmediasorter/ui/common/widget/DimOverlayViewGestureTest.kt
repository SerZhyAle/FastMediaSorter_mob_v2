package com.sza.fastmediasorter.ui.common.widget

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DimOverlayViewGestureTest {

    private fun newView(): DimOverlayView =
        DimOverlayView(RuntimeEnvironment.getApplication())

    @Test
    fun `single tap starts ripple animation and does not trigger exit`() {
        var exitCalled = false
        val view = newView().apply {
            onExit = { exitCalled = true }
        }

        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 100f, 200f, 0)
        val handled = view.gestureListener.onSingleTapConfirmed(event)

        assertTrue(handled)
        assertFalse(exitCalled)
        assertTrue(view.isAnimating)
        assertEquals(0f, view.animationProgress, 0.001f)
        event.recycle()
    }

    @Test
    fun `double tap triggers exit`() {
        var exitCalled = false
        val view = newView().apply {
            onExit = { exitCalled = true }
        }

        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val handled = view.gestureListener.onDoubleTap(event)

        assertTrue(handled)
        assertTrue(exitCalled)
        event.recycle()
    }

    @Test
    fun `long press triggers exit`() {
        var exitCalled = false
        val view = newView().apply {
            onExit = { exitCalled = true }
        }

        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        view.gestureListener.onLongPress(event)

        assertTrue(exitCalled)
        event.recycle()
    }

    @Test
    fun `back key triggers exit`() {
        var exitCalled = false
        val view = newView().apply {
            onExit = { exitCalled = true }
        }

        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        val backHandled = view.onKeyDown(KeyEvent.KEYCODE_BACK, keyEvent)
        assertTrue(backHandled)
        assertTrue(exitCalled)
    }

    @Test
    fun `escape key triggers exit`() {
        var exitCalled = false
        val view = newView().apply {
            onExit = { exitCalled = true }
        }

        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)
        val escapeHandled = view.onKeyDown(KeyEvent.KEYCODE_ESCAPE, keyEvent)
        assertTrue(escapeHandled)
        assertTrue(exitCalled)
    }
}
