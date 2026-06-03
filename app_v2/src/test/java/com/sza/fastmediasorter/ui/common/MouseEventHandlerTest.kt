package com.sza.fastmediasorter.ui.common

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.ui.common.input.InputAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            action = MotionEvent.ACTION_BUTTON_PRESS,
            actionButton = MotionEvent.BUTTON_SECONDARY,
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
            action = MotionEvent.ACTION_BUTTON_PRESS,
            actionButton = MotionEvent.BUTTON_TERTIARY,
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

    // --- S0333 regression guards: the activity-level mouse bridge must never swallow the primary
    // (left) click, or dialogs / dropdowns / buttons stop responding to the mouse. ---

    @Test
    fun `primary click is not consumed when consumePrimaryClick is false`() {
        val handler = MouseEventHandler(RecordingCallbacks(), consumePrimaryClick = false)

        val up = mouseEvent(action = MotionEvent.ACTION_UP, actionButton = 0, buttonState = 0)

        // false => the event keeps propagating to the view under the cursor for normal click handling.
        assertFalse(handler.handleMotionEvent(view, up))
    }

    @Test
    fun `primary click is consumed when consumePrimaryClick is true`() {
        val callbacks = RecordingCallbacks()
        val handler = MouseEventHandler(callbacks, consumePrimaryClick = true)

        val up = mouseEvent(action = MotionEvent.ACTION_UP, actionButton = 0, buttonState = 0)

        assertTrue(handler.handleMotionEvent(view, up))
        assertEquals(1, callbacks.singleClickCount)
    }

    @Test
    fun `finger tap is never treated as a mouse event`() {
        val handler = MouseEventHandler(RecordingCallbacks(), consumePrimaryClick = false)

        // Touchscreen-injected events can carry SOURCE_MOUSE / buttonState; the tool type is the only
        // reliable discriminator (S0289). A finger tap must not enter the mouse pipeline.
        val up = mouseEvent(
            action = MotionEvent.ACTION_UP,
            tool = MotionEvent.TOOL_TYPE_FINGER,
            buttonState = MotionEvent.BUTTON_PRIMARY,
        )

        assertFalse(handler.handleMotionEvent(view, up))
    }

    private fun mouseEvent(
        action: Int,
        actionButton: Int = 0,
        buttonState: Int = 0,
        tool: Int = MotionEvent.TOOL_TYPE_MOUSE,
        x: Float = 0f,
        y: Float = 0f,
        metaState: Int = 0,
        verticalScroll: Float = 0f,
        horizontalScroll: Float = 0f,
    ): MotionEvent {
        val event = mockk<MotionEvent>()
        every { event.actionMasked } returns action
        every { event.actionButton } returns actionButton
        every { event.buttonState } returns buttonState
        every { event.getToolType(0) } returns tool
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
        var singleClickCount = 0
        var lastAction: InputAction? = null
        var lastWheelAction: InputAction.ScrollWheel? = null

        override fun onSingleClick(view: View) {
            singleClickCount += 1
        }

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
