package com.sza.fastmediasorter.ui.common

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.ui.common.input.InputAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseEventHandlerTest {

    private val view = mockk<View>(relaxed = true)

    @Test
    fun `right click forwards coordinates and context-menu action`() {
        val callbacks = RecordingCallbacks()
        val handler = MouseEventHandler(callbacks)

        val event = mouseEvent(
            action = MotionEvent.ACTION_DOWN,
            buttonState = MotionEvent.BUTTON_SECONDARY,
            x = 12.5f,
            y = 48.25f,
        )

        assertTrue(handler.handleMotionEvent(view, event))
        assertEquals(12.5f, callbacks.rightClickX, 0.001f)
        assertEquals(48.25f, callbacks.rightClickY, 0.001f)
        assertEquals(InputAction.ShowContextMenuAt(12.5f, 48.25f), callbacks.lastAction)
    }

    @Test
    fun `middle click toggles favourite`() {
        val callbacks = RecordingCallbacks()
        val handler = MouseEventHandler(callbacks)

        val event = mouseEvent(
            action = MotionEvent.ACTION_DOWN,
            buttonState = MotionEvent.BUTTON_TERTIARY,
        )

        assertTrue(handler.handleMotionEvent(view, event))
        assertEquals(1, callbacks.middleClickCount)
        assertEquals(InputAction.ToggleFavourite, callbacks.lastAction)
    }

    @Test
    fun `wheel action preserves shift and ctrl modifiers`() {
        val callbacks = RecordingCallbacks()
        val handler = MouseEventHandler(callbacks)
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON

        val event = mouseEvent(
            action = MotionEvent.ACTION_SCROLL,
            metaState = meta,
            verticalScroll = 3.5f,
            horizontalScroll = -1.25f,
        )

        assertTrue(handler.handleGenericMotionEvent(view, event))
        assertNotNull(callbacks.lastWheelAction)
        assertEquals(3.5f, callbacks.lastWheelAction?.deltaY ?: 0f, 0.001f)
        assertEquals(-1.25f, callbacks.lastWheelAction?.deltaX ?: 0f, 0.001f)
        assertTrue(callbacks.lastWheelAction?.withShift == true)
        assertTrue(callbacks.lastWheelAction?.withCtrl == true)
    }

    private fun mouseEvent(
        action: Int,
        buttonState: Int = 0,
        x: Float = 0f,
        y: Float = 0f,
        metaState: Int = 0,
        verticalScroll: Float = 0f,
        horizontalScroll: Float = 0f,
    ): MotionEvent {
        val event = mockk<MotionEvent>()
        every { event.actionMasked } returns action
        every { event.buttonState } returns buttonState
        every { event.source } returns InputDevice.SOURCE_MOUSE
        every { event.x } returns x
        every { event.y } returns y
        every { event.metaState } returns metaState
        every { event.getAxisValue(MotionEvent.AXIS_VSCROLL) } returns verticalScroll
        every { event.getAxisValue(MotionEvent.AXIS_HSCROLL) } returns horizontalScroll
        return event
    }

    private class RecordingCallbacks : MouseEventHandler.MouseEventCallbacks {
        var rightClickX = 0f
        var rightClickY = 0f
        var middleClickCount = 0
        var lastAction: InputAction? = null
        var lastWheelAction: InputAction.ScrollWheel? = null

        override fun onRightClick(view: View, x: Float, y: Float) {
            rightClickX = x
            rightClickY = y
        }

        override fun onMiddleClick(view: View) {
            middleClickCount += 1
        }

        override fun onInputAction(view: View, action: InputAction): Boolean {
            lastAction = action
            if (action is InputAction.ScrollWheel) {
                lastWheelAction = action
            }
            return true
        }
    }
}