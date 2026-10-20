package com.sza.fastmediasorter.ui.player.helpers

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MEDIA-VIEWER conformance cases (MV-C) for the clauses the player keyboard handler closes:
 * destination slots (MV-4.1, MV-4.2) and zoom keys plus Ctrl+wheel (MV-3.1, MV-3.7). Every case
 * pairs a positive with a negative; same-folder refusal and locale collation carry their own unit
 * tests (SameFolderTransferPolicyTest, LocaleNameCollatorTest).
 */
class PlayerKeyboardHandlerMediaViewerTest {

    private val callback = mockk<PlayerKeyboardHandler.PlayerKeyboardCallback>(relaxed = true)
    private val handler = PlayerKeyboardHandler(callback, mockk<KeyBindingManager>(relaxed = true))

    private fun on(type: MediaType) {
        every { callback.getCurrentMediaType() } returns type
    }

    private fun scrollEvent(withCtrl: Boolean): MotionEvent = mockk {
        every { actionMasked } returns MotionEvent.ACTION_SCROLL
        every { metaState } returns if (withCtrl) KeyEvent.META_CTRL_ON else 0
        every { getAxisValue(MotionEvent.AXIS_VSCROLL) } returns 1f
        every { getAxisValue(MotionEvent.AXIS_HSCROLL) } returns 0f
        every { x } returns 10f
        every { y } returns 20f
        every { getToolType(any()) } returns MotionEvent.TOOL_TYPE_MOUSE
    }

    @Test
    fun `op slot 3 moves and never copies`() {
        on(MediaType.IMAGE)
        every { callback.canMoveCurrent() } returns true
        every { callback.onMoveToSlot(any()) } returns true
        assertTrue(handler.handleCommand(CommandId.OPERATION_SLOT_3))
        verify { callback.onMoveToSlot(3) }
        verify(exactly = 0) { callback.onCopyToSlot(any()) }
    }

    @Test
    fun `copy slot 3 copies`() {
        on(MediaType.IMAGE)
        every { callback.canCopyCurrent() } returns true
        every { callback.onCopyToSlot(any()) } returns true
        assertTrue(handler.handleCommand(CommandId.COPY_SLOT_3))
        verify { callback.onCopyToSlot(3) }
        verify(exactly = 0) { callback.onMoveToSlot(any()) }
    }

    @Test
    fun `a slot key is a no-op when the operation is unavailable`() {
        on(MediaType.IMAGE)
        every { callback.canMoveCurrent() } returns false
        assertFalse(handler.handleCommand(CommandId.OPERATION_SLOT_3))
        verify(exactly = 0) { callback.onMoveToSlot(any()) }
    }

    @Test
    fun `zoom_in zooms a still and a video but not a document`() {
        every { callback.onZoom(any()) } returns true
        on(MediaType.IMAGE)
        assertTrue(handler.handleCommand(CommandId.ZOOM_IN))
        on(MediaType.VIDEO)
        assertTrue(handler.handleCommand(CommandId.ZOOM_IN))
        on(MediaType.PDF)
        assertFalse(handler.handleCommand(CommandId.ZOOM_IN))
        verify(exactly = 2) { callback.onZoom(ZoomAction.IN) }
    }

    @Test
    fun `fit and actual size route as zoom actions`() {
        every { callback.onZoom(any()) } returns true
        on(MediaType.GIF)
        assertTrue(handler.handleCommand(CommandId.FIT_SCREEN))
        assertTrue(handler.handleCommand(CommandId.ZOOM_RESET))
        assertTrue(handler.handleCommand(CommandId.ZOOM_ACTUAL_SIZE))
        verify { callback.onZoom(ZoomAction.FIT) }
        verify { callback.onZoom(ZoomAction.ACTUAL_SIZE) }
        on(MediaType.TEXT)
        assertFalse(handler.handleCommand(CommandId.ZOOM_ACTUAL_SIZE))
    }

    @Test
    fun `ctrl wheel zooms an image at the pointer and never pages`() {
        on(MediaType.IMAGE)
        every { callback.onZoomAt(any(), any(), any()) } returns true
        assertTrue(handler.handlePointerEvent(mockk<View>(), scrollEvent(withCtrl = true)))
        verify { callback.onZoomAt(1.25f, 10f, 20f) }
        verify(exactly = 0) { callback.onNavigationScroll(any()) }
    }

    @Test
    fun `a plain wheel still pages an image`() {
        on(MediaType.IMAGE)
        assertTrue(handler.handlePointerEvent(mockk<View>(), scrollEvent(withCtrl = false)))
        verify { callback.onNavigationScroll(1f) }
        verify(exactly = 0) { callback.onZoomAt(any(), any(), any()) }
    }
}
