package com.sza.fastmediasorter.vr.helpers

import android.os.Handler
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.vr.openxr.XrInputEventType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * VR-only unit coverage must live in `testVr` so the standard flavor does not
 * try to compile sources that only exist under `src/vr/java`.
 *
 * This class intentionally stays a plain JVM test instead of Robolectric: the
 * router itself is pure mapping logic, and avoiding Robolectric removes a flaky
 * Windows temp-db cleanup failure during VR suite shutdown.
 */
class VrControllerInputManagerTest {

    private lateinit var commands: MutableList<PlaybackCommand>
    private lateinit var commandSources: MutableList<VrCommandSource>
    private lateinit var volumeSteps: MutableList<Int>
    private lateinit var zoomDeltas: MutableList<Float>
    private lateinit var manager: VrControllerInputManager

    @Before
    fun setUp() {
        commands = mutableListOf()
        commandSources = mutableListOf()
        volumeSteps = mutableListOf()
        zoomDeltas = mutableListOf()
        val handler = mockk<Handler>()
        every { handler.post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        manager = VrControllerInputManager(
            mainHandler = handler,
            onCommand = { command, source ->
                commands.add(command)
                commandSources.add(source)
            },
            onVolumeStep = { volumeSteps.add(it) },
            onZoomGripDelta = { zoomDeltas.add(it) },
        )
    }

    private fun keyUp(code: Int, meta: Int = 0): KeyEvent {
        val ev = mockk<KeyEvent>(relaxed = true)
        every { ev.action } returns KeyEvent.ACTION_UP
        every { ev.keyCode } returns code
        every { ev.metaState } returns meta
        every { ev.isCtrlPressed } returns ((meta and KeyEvent.META_CTRL_ON) != 0)
        every { ev.isShiftPressed } returns ((meta and KeyEvent.META_SHIFT_ON) != 0)
        every { ev.isAltPressed } returns ((meta and KeyEvent.META_ALT_ON) != 0)
        return ev
    }

    private fun mouseScroll(vScroll: Float, shift: Boolean = false): MotionEvent {
        val ev = mockk<MotionEvent>(relaxed = true)
        every { ev.source } returns InputDevice.SOURCE_MOUSE
        every { ev.action } returns MotionEvent.ACTION_SCROLL
        every { ev.getAxisValue(MotionEvent.AXIS_VSCROLL) } returns vScroll
        every { ev.metaState } returns if (shift) KeyEvent.META_SHIFT_ON else 0
        return ev
    }

    @Test
    fun xrPauseToggle_dispatchesTogglePausePlay() {
        manager.onInputEvent(XrInputEventType.PAUSE_TOGGLE, 1, 0f, 0)
        assertEquals(listOf(PlaybackCommand.TogglePausePlay), commands)
        assertEquals(listOf(VrCommandSource.CONTROLLER), commandSources)
    }

    @Test
    fun xrExit_dispatchesExit() {
        manager.onInputEvent(XrInputEventType.EXIT, 1, 0f, 0)
        assertEquals(listOf(PlaybackCommand.Exit), commands)
    }

    @Test
    fun xrFileOps_dispatchesOpenFileOps() {
        manager.onInputEvent(XrInputEventType.FILE_OPS, 0, 0f, 0)
        assertEquals(listOf(PlaybackCommand.OpenFileOps), commands)
    }

    @Test
    fun xrStickSeekForward_dispatchesSeekForward() {
        manager.onInputEvent(XrInputEventType.SEEK_FORWARD, 0, 0.9f, 0)
        assertEquals(listOf(PlaybackCommand.SeekForward), commands)
    }

    @Test
    fun xrFileNext_dispatchesNextFile() {
        manager.onInputEvent(XrInputEventType.FILE_NEXT, 1, 0.9f, 0)
        assertEquals(listOf(PlaybackCommand.NextFile), commands)
    }

    @Test
    fun xrVolumeUp_callsVolumeStepPlusOne() {
        manager.onInputEvent(XrInputEventType.VOLUME_UP, 0, 0.9f, 0)
        assertEquals(listOf(1), volumeSteps)
        assertTrue(commands.any { it is PlaybackCommand.VolumeStep && it.delta == 1 })
    }

    @Test
    fun xrZoomDelta_callsOnZoomGripDelta() {
        manager.onInputEvent(XrInputEventType.ZOOM_DELTA, 1, 0.05f, 0)
        assertEquals(listOf(0.05f), zoomDeltas)
    }

    @Test
    fun xrZoomReset_dispatchesZoomReset() {
        manager.onInputEvent(XrInputEventType.ZOOM_RESET, -1, 0f, 0)
        assertEquals(listOf(PlaybackCommand.ZoomReset), commands)
    }

    @Test
    fun xrRecenter_dispatchesRecenter() {
        manager.onInputEvent(XrInputEventType.RECENTER, 1, 0f, 0)
        assertEquals(listOf(PlaybackCommand.Recenter), commands)
    }

    @Test
    fun xrCheatsheet_dispatchesShowCheatsheet() {
        manager.onInputEvent(XrInputEventType.CHEATSHEET, 0, 0f, 0)
        assertEquals(listOf(PlaybackCommand.ShowCheatsheet), commands)
    }

    @Test
    fun xrDoublePinch_dispatchesTogglePausePlay_fromHandSource() {
        manager.onInputEvent(XrInputEventType.DOUBLE_PINCH, 0, 1f, 1)

        assertEquals(listOf(PlaybackCommand.TogglePausePlay), commands)
        assertEquals(listOf(VrCommandSource.HAND), commandSources)
    }

    @Test
    fun handSwipeRight_propagatesHandSource() {
        manager.onInputEvent(XrInputEventType.SWIPE_RIGHT, 1, 0f, 1)

        assertEquals(listOf(PlaybackCommand.SeekMicro(forward = true)), commands)
        assertEquals(listOf(VrCommandSource.HAND), commandSources)
    }

    @Test
    fun keySpace_dispatchesTogglePausePlay() {
        val handled = manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_SPACE))
        assertTrue(handled)
        assertEquals(listOf(PlaybackCommand.TogglePausePlay), commands)
        assertEquals(listOf(VrCommandSource.KEYBOARD), commandSources)
    }

    @Test
    fun keyF5_dispatchesCopyFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_F5))
        assertEquals(listOf(PlaybackCommand.CopyFile), commands)
    }

    @Test
    fun keyF8_dispatchesDeleteFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_F8))
        assertEquals(listOf(PlaybackCommand.DeleteFile), commands)
    }

    @Test
    fun keyF2_dispatchesRenameFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_F2))
        assertEquals(listOf(PlaybackCommand.RenameFile), commands)
    }

    @Test
    fun keyCtrlR_dispatchesRenameFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON))
        assertEquals(listOf(PlaybackCommand.RenameFile), commands)
    }

    @Test
    fun keyCtrlC_dispatchesCopyFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON))
        assertEquals(listOf(PlaybackCommand.CopyFile), commands)
    }

    @Test
    fun bareKeyboardV_isMarkedAsVrExclusiveShortcut() {
        val event = keyUp(KeyEvent.KEYCODE_V)
        every { event.source } returns InputDevice.SOURCE_KEYBOARD

        assertTrue(manager.shouldInterceptKeyboardShortcut(event))
    }

    @Test
    fun ctrlKeyboardV_isNotMarkedAsVrExclusiveShortcut() {
        val event = keyUp(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
        every { event.source } returns InputDevice.SOURCE_KEYBOARD

        assertFalse(manager.shouldInterceptKeyboardShortcut(event))
    }

    @Test
    fun bareKeyboardC_isMarkedAsVrExclusiveShortcut() {
        val event = keyUp(KeyEvent.KEYCODE_C)
        every { event.source } returns InputDevice.SOURCE_KEYBOARD

        assertTrue(manager.shouldInterceptKeyboardShortcut(event))
    }

    @Test
    fun ctrlKeyboardC_isNotMarkedAsVrExclusiveShortcut() {
        val event = keyUp(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)
        every { event.source } returns InputDevice.SOURCE_KEYBOARD

        assertFalse(manager.shouldInterceptKeyboardShortcut(event))
    }

    @Test
    fun keyboardEquals_isMarkedAsVrExclusiveShortcut() {
        val event = keyUp(KeyEvent.KEYCODE_EQUALS)
        every { event.source } returns InputDevice.SOURCE_KEYBOARD

        assertTrue(manager.shouldInterceptKeyboardShortcut(event))
    }

    @Test
    fun keyCtrlX_dispatchesMoveFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON))
        assertEquals(listOf(PlaybackCommand.MoveFile), commands)
    }

    @Test
    fun keyDelete_dispatchesDeleteFile() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(listOf(PlaybackCommand.DeleteFile), commands)
    }

    @Test
    fun keyEsc_dispatchesExit() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(listOf(PlaybackCommand.Exit), commands)
    }

    @Test
    fun keyF1_dispatchesShowCheatsheet() {
        manager.onKeyEvent(keyUp(KeyEvent.KEYCODE_F1))
        assertEquals(listOf(PlaybackCommand.ShowCheatsheet), commands)
    }

    @Test
    fun keyActionDown_isNotHandled() {
        val ev = mockk<KeyEvent>(relaxed = true)
        every { ev.action } returns KeyEvent.ACTION_DOWN
        every { ev.keyCode } returns KeyEvent.KEYCODE_SPACE
        assertFalse(manager.onKeyEvent(ev))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun mouseWheelUp_dispatchesVolumeUp() {
        val handled = manager.onMotionEvent(mouseScroll(vScroll = +1f))
        assertTrue(handled)
        assertEquals(listOf(1), volumeSteps)
    }

    @Test
    fun shiftMouseWheelUp_dispatchesSeekForward() {
        manager.onMotionEvent(mouseScroll(vScroll = +1f, shift = true))
        assertEquals(listOf(PlaybackCommand.SeekForward), commands)
    }

    @Test
    fun mouseRightClick_dispatchesOpenControls() {
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_MOUSE
        every { event.action } returns MotionEvent.ACTION_BUTTON_PRESS
        every { event.actionButton } returns MotionEvent.BUTTON_SECONDARY
        manager.onMotionEvent(event)
        assertEquals(listOf(PlaybackCommand.OpenControls), commands)
        assertEquals(listOf(VrCommandSource.MOUSE), commandSources)
    }

    @Test
    fun mouseLeftClick_dispatchesTogglePausePlay() {
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_MOUSE
        every { event.action } returns MotionEvent.ACTION_BUTTON_PRESS
        every { event.actionButton } returns MotionEvent.BUTTON_PRIMARY
        manager.onMotionEvent(event)
        assertEquals(listOf(PlaybackCommand.TogglePausePlay), commands)
    }
}