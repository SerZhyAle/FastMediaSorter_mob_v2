package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Routing of the INPUT-PARITY 0.4 VIEWER-KEYBOARD commands (S4363). */
class PlayerKeyboardHandlerViewerKeysTest {

    private val callback = mockk<PlayerKeyboardHandler.PlayerKeyboardCallback>(relaxed = true)
    private val handler = PlayerKeyboardHandler(callback, mockk<KeyBindingManager>(relaxed = true))

    private fun on(type: MediaType) {
        every { callback.getCurrentMediaType() } returns type
    }

    @Test
    fun `file collection commands reach their distinct surfaces`() {
        every { callback.onShowFileGrid() } returns true
        every { callback.onShowFileList() } returns true
        every { callback.onShowFileNumber() } returns true
        assertTrue(handler.handleCommand(CommandId.FILE_GRID))
        assertTrue(handler.handleCommand(CommandId.FILE_LIST))
        assertTrue(handler.handleCommand(CommandId.FILE_NUMBER))
        verify(exactly = 1) { callback.onShowFileGrid() }
        verify(exactly = 1) { callback.onShowFileList() }
        verify(exactly = 1) { callback.onShowFileNumber() }
    }

    @Test
    fun `modal ownership blocks direct commands including destructive actions`() {
        every { callback.hasModalInput() } returns true
        assertFalse(handler.handleCommand(CommandId.DELETE))
        assertFalse(handler.handleCommand(CommandId.NEXT_FILE))
        assertFalse(handler.handleCommand(CommandId.FILE_NUMBER))
        verify(exactly = 0) { callback.onDeleteFile() }
        verify(exactly = 0) { callback.onNextFile() }
        verify(exactly = 0) { callback.onShowFileNumber() }
    }

    @Test
    fun `arrows seek ten seconds on a video`() {
        on(MediaType.VIDEO)
        assertTrue(handler.handleCommand(CommandId.NEXT_OR_SEEK_FORWARD))
        assertTrue(handler.handleCommand(CommandId.PREVIOUS_OR_SEEK_BACKWARD))
        verify { callback.onSeekForward(10) }
        verify { callback.onSeekBackward(10) }
        verify(exactly = 0) { callback.onNextFile() }
    }

    @Test
    fun `arrows change file on a still image and page a PDF`() {
        on(MediaType.IMAGE)
        assertTrue(handler.handleCommand(CommandId.NEXT_OR_SEEK_FORWARD))
        verify { callback.onNextFile() }
        on(MediaType.PDF)
        assertTrue(handler.handleCommand(CommandId.PREVIOUS_OR_SEEK_BACKWARD))
        verify { callback.onPdfPreviousPage() }
    }

    @Test
    fun `shift arrows seek three seconds and the legacy ids keep their steps`() {
        on(MediaType.VIDEO)
        handler.handleCommand(CommandId.SEEK_FORWARD_3S)
        handler.handleCommand(CommandId.SEEK_BACKWARD_30S)
        handler.handleCommand(CommandId.SEEK_FORWARD_5S)
        verify { callback.onSeekForward(3) }
        verify { callback.onSeekBackward(60) }
        verify { callback.onSeekForward(10) }
    }

    @Test
    fun `file steps skip documents`() {
        every { callback.onStepFiles(any()) } returns true
        on(MediaType.IMAGE)
        assertTrue(handler.handleCommand(CommandId.STEP_FORWARD_TEN))
        assertTrue(handler.handleCommand(CommandId.STEP_BACK_HUNDRED))
        verify { callback.onStepFiles(10) }
        verify { callback.onStepFiles(-100) }
        on(MediaType.TEXT)
        assertFalse(handler.handleCommand(CommandId.STEP_FORWARD_TEN))
    }

    @Test
    fun `home and end reach the first and last file on media`() {
        every { callback.onEdgeFile(any()) } returns true
        on(MediaType.VIDEO)
        assertTrue(handler.handleCommand(CommandId.JUMP_END))
        verify { callback.onEdgeFile(last = true) }
        on(MediaType.PDF)
        handler.handleCommand(CommandId.JUMP_START)
        verify { callback.onPdfHome() }
    }

    @Test
    fun `speed and audio track only on playable kinds`() {
        every { callback.onChangeSpeed(any()) } returns true
        every { callback.onCycleAudioTrack() } returns true
        on(MediaType.IMAGE)
        assertFalse(handler.handleCommand(CommandId.SPEED_UP))
        assertFalse(handler.handleCommand(CommandId.CYCLE_TRACK))
        on(MediaType.VIDEO)
        assertTrue(handler.handleCommand(CommandId.SPEED_DOWN))
        assertTrue(handler.handleCommand(CommandId.CYCLE_TRACK))
        verify { callback.onChangeSpeed(faster = false) }
    }

    @Test
    fun `rotate random and slideshow route to their callbacks`() {
        on(MediaType.IMAGE)
        handler.handleCommand(CommandId.ROTATE)
        handler.handleCommand(CommandId.ROTATE_COUNTER)
        handler.handleCommand(CommandId.RANDOM_FILE)
        handler.handleCommand(CommandId.SLIDESHOW_RANDOM)
        verify { callback.onRotateContent(clockwise = true) }
        verify { callback.onRotateContent(clockwise = false) }
        verify { callback.onRandomFile() }
        verify { callback.onStartSlideshow(random = true) }
    }
}
