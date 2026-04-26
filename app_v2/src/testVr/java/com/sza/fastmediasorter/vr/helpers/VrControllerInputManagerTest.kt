package com.sza.fastmediasorter.vr.helpers

import android.os.Handler
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
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
    private lateinit var mockKeyBindingManager: KeyBindingManager

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
        mockKeyBindingManager = mockk<KeyBindingManager>()
        // Catch-all: unrecognised trigger → null (silently dropped).
        every { mockKeyBindingManager.resolve(any(), any()) } returns null
        // Seed default VR bindings matching default_bindings.json.
        fun vrStub(type: Int, commandId: String) {
            every {
                mockKeyBindingManager.resolve(InputTrigger.VrEvent(type), InputSurface.VR)
            } returns commandId
        }
        vrStub(XrInputEventType.PAUSE_TOGGLE,     CommandId.PAUSE_PLAY)
        vrStub(XrInputEventType.EXIT,             CommandId.EXIT)
        vrStub(XrInputEventType.FILE_OPS,         CommandId.FILE_OPS)
        vrStub(XrInputEventType.MENU,             CommandId.TOGGLE_CONTROLS)
        vrStub(XrInputEventType.SEEK_FORWARD,     CommandId.SEEK_FORWARD_5S)
        vrStub(XrInputEventType.SEEK_BACKWARD,    CommandId.SEEK_BACKWARD_5S)
        vrStub(XrInputEventType.FILE_NEXT,        CommandId.NEXT_FILE)
        vrStub(XrInputEventType.FILE_PREV,        CommandId.PREVIOUS_FILE)
        vrStub(XrInputEventType.VOLUME_UP,        CommandId.VOLUME_UP)
        vrStub(XrInputEventType.VOLUME_DOWN,      CommandId.VOLUME_DOWN)
        vrStub(XrInputEventType.RECENTER,         CommandId.VR_RECENTER)
        vrStub(XrInputEventType.TOGGLE_IMMERSIVE, CommandId.VR_TOGGLE_IMMERSIVE)
        vrStub(XrInputEventType.CHEATSHEET,       CommandId.VR_CHEATSHEET)
        vrStub(XrInputEventType.ZOOM_START,       CommandId.VR_ZOOM_START)
        vrStub(XrInputEventType.ZOOM_DELTA,       CommandId.VR_ZOOM_GRIP)
        vrStub(XrInputEventType.ZOOM_END,         CommandId.VR_ZOOM_END)
        vrStub(XrInputEventType.ZOOM_RESET,       CommandId.ZOOM_RESET)
        vrStub(XrInputEventType.SWIPE_LEFT,       CommandId.VR_SWIPE_LEFT)
        vrStub(XrInputEventType.SWIPE_RIGHT,      CommandId.VR_SWIPE_RIGHT)
        vrStub(XrInputEventType.SWIPE_UP,         CommandId.VR_SWIPE_UP)
        vrStub(XrInputEventType.SWIPE_DOWN,       CommandId.VR_SWIPE_DOWN)
        vrStub(XrInputEventType.DOUBLE_PINCH,     CommandId.VR_DOUBLE_PINCH)

        manager = VrControllerInputManager(
            mainHandler = handler,
            keyBindingManager = mockKeyBindingManager,
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

    // ── New Phase 05 tests ───────────────────────────────────────────────────

    /** Override binding: PAUSE_TOGGLE remapped to EXIT fires Exit, not TogglePausePlay. */
    @Test
    fun xrOverrideBinding_pauseToggleRemappedToExit_firesExit() {
        // Override the default stub: PAUSE_TOGGLE now resolves to EXIT.
        every {
            mockKeyBindingManager.resolve(InputTrigger.VrEvent(XrInputEventType.PAUSE_TOGGLE), InputSurface.VR)
        } returns CommandId.EXIT

        manager.onInputEvent(XrInputEventType.PAUSE_TOGGLE, 1, 0f, 0)

        assertEquals(listOf(PlaybackCommand.Exit), commands)
        assertTrue("TogglePausePlay must NOT fire", commands.none { it == PlaybackCommand.TogglePausePlay })
    }

    /** Volume rate-limiter: two VOLUME_UP events in rapid succession only emit once. */
    @Test
    fun xrVolumeRateLimit_rapidVolumeUpFiresOnlyOnce() {
        // lastVolumeEventMs starts at -1 so first event passes; second within VOLUME_STEP_INTERVAL_MS is throttled.
        manager.onInputEvent(XrInputEventType.VOLUME_UP, 0, 0f, 0)
        manager.onInputEvent(XrInputEventType.VOLUME_UP, 0, 0f, 0) // rapid — same SystemClock.uptimeMillis() in JVM

        assertEquals("Only one volume step should fire", 1, volumeSteps.size)
        assertEquals(1, volumeSteps[0])
    }

    /** Unknown XrInputEventType code is silently dropped — no crash, no dispatch. */
    @Test
    fun xrUnknownEventType_isSilentlyDropped() {
        val unknownType = 9999 // guaranteed not in XrInputEventType
        // Resolver returns null for the unknown trigger (catch-all stub in setUp).

        manager.onInputEvent(unknownType, 0, 0f, 0)

        assertTrue("No command should be dispatched for unknown event type", commands.isEmpty())
        assertTrue("No volume step should fire for unknown event type", volumeSteps.isEmpty())
    }
}
