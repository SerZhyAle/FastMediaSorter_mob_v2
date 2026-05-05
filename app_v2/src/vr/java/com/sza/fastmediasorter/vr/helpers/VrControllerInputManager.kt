package com.sza.fastmediasorter.vr.helpers

import android.media.AudioManager
import android.os.Handler
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromXrInputEvent
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.vr.openxr.XrHand
import com.sza.fastmediasorter.vr.openxr.XrInputCallback
import com.sza.fastmediasorter.vr.openxr.XrInputEventType
import com.sza.fastmediasorter.vr.openxr.XrInputSource
import com.sza.fastmediasorter.vr.render.VrHudElementRegistry
import com.sza.fastmediasorter.vr.ui.VrHudHitTester
import com.sza.fastmediasorter.vr.ui.VrHudInputDispatcher
import com.sza.fastmediasorter.vr.ui.VrPanelHitZoneResolver
import com.sza.fastmediasorter.vr.ui.VrRayPanelHitTester
import timber.log.Timber

enum class VrCommandSource {
    CONTROLLER,
    HAND,
    KEYBOARD,
    MOUSE,
    UI,
}

/**
 * Central router that converts four parallel input surfaces into [PlaybackCommand]s:
 *
 *  - **Layer A** — OpenXR controllers (Touch / Touch Pro) via [XrInputCallback].
 *    C++ edge-detects on the xr-render-thread; events are dispatched to [mainHandler]
 *    before touching any UI / ViewModel state.
 *  - **Layer B** — Bluetooth keyboard via [onKeyEvent].
 *  - **Layer C** — Bluetooth mouse via [onMotionEvent] (+ clicks via [onKeyEvent]).
 *  - **Layer E** — OpenXR hand tracking (spec_vr-hand-tracking-tech). Shares the
 *    [XrInputCallback] channel but is distinguished by `source = XrInputSource.HAND`;
 *    pinches trigger UI clicks via [onPointerEvent], thumb microgestures map to
 *    seek / volume commands, and audio feedback replaces haptics.
 *
 * All business logic (pause, seek, file ops, zoom) is delegated to the enclosing
 * activity through [onCommand], [onVolumeStep] and [onZoomGripDelta]; this class
 * only maps events → commands with the rate limiting required for analog inputs.
 *
 * @param keyBindingManager resolver for XR trigger → [CommandId] lookups.
 * @param audioManager source for system UI click sounds emitted when hand-tracking
 *                     pinches fire — compensates for the lack of haptic feedback.
 *                     May be null on devices without an AudioManager (unusual).
 * @param onPointerEvent optional sink for pointer click transitions sourced from
 *                       hand tracking — forwarded to `VrHandRayManager` so the
 *                       current hover target receives synthetic MotionEvents.
 */
class VrControllerInputManager(
    private val mainHandler: Handler,
    private val keyBindingManager: KeyBindingManager,
    private val onCommand: (PlaybackCommand, VrCommandSource) -> Unit,
    private val onVolumeStep: (Int) -> Unit,
    private val onZoomGripDelta: (Float) -> Unit,
    private val audioManager: AudioManager? = null,
    private val onPointerEvent: ((hand: Int, down: Boolean) -> Unit)? = null,
) : XrInputCallback {

    private val commandDebouncer = VrCommandDebouncer()

    @Volatile
    private var lastVolumeEventMs = -1L

    @Volatile
    private var lastZoomStepMs = -1L

    // ═══════════════════════════════════════════════════════════════════════
    // Layer A — OpenXR controllers (called from xr-render-thread)
    // Layer E — OpenXR hand tracking (same channel, source = HAND)
    // ═══════════════════════════════════════════════════════════════════════

    override fun onInputEvent(type: Int, hand: Int, value: Float, source: Int) {
        // Hop to main thread before touching UI / ViewModel.
        mainHandler.post { dispatchXrEvent(type, hand, value, source) }
    }

    override fun onPointerMove(hand: Int, ndcX: Float, ndcY: Float) {
        // High-frequency per-frame stream — Kotlin side is expected to throttle before
        // forwarding to the view hierarchy. Forwarded through the main handler.
        val rayManager = pointerMoveSink ?: return
        mainHandler.post { rayManager(hand, ndcX, ndcY) }
    }

    override fun onControllerPointerMove(hand: Int, ndcX: Float, ndcY: Float) {
        // Raw render-thread call; VrControllerRayManager throttles internally.
        controllerPointerMoveSink?.invoke(hand, ndcX, ndcY)
        // Panel hit-test — runs only while the GL panel is visible.
        val ht = panelHitTester
        val rslv = panelHitResolver
        if (ht != null && rslv != null && panelVisibleProvider?.invoke() == true) {
            val hit = ht.computeHit(ndcX, ndcY)
            val zoneId = rslv.resolve(hit)
            val seekFrac = rslv.resolveSeekFraction(hit)
            panelHoverSink?.invoke(hand, zoneId, seekFrac)
        }
        // HUD hit-test (S0024 Phase 02) — gated by HUD layer visibility per §11 criterion 4.
        val hudTester = hudHitTester
        val sink = hudHoverSink
        if (hudTester != null && sink != null && hudVisibleProvider?.invoke() == true) {
            val registry = hudRegistryProvider?.invoke()
            if (registry != null) {
                val hit = hudTester.computeHit(ndcX, ndcY)
                val elementId = if (hit.isMiss) 0 else (registry.elementAt(hit.u, hit.v)?.id ?: 0)
                sink.invoke(hand, elementId)
            }
        }
    }

    override fun onControllerPanelHover(hand: Int, zoneId: Int, seekFraction: Float) {
        // Forwarding via panelHoverSink — no additional dispatch needed here.
    }

    /**
     * Optional high-frequency sink invoked from [onPointerMove]. Set by
     * `VrPlayerActivity` once `VrHandRayManager` is constructed so pointer coordinates
     * can reach the view hierarchy. Volatile so the render-thread observes
     * subscription changes without synchronisation.
     */
    @Volatile
    var pointerMoveSink: ((hand: Int, ndcX: Float, ndcY: Float) -> Unit)? = null

    /**
     * Optional high-frequency sink invoked from [onControllerPointerMove]. Set by
     * `VrPlayerActivity` once `VrControllerRayManager` is constructed.
     * Called on the xr-render-thread; VrControllerRayManager handles its own throttle.
     */
    @Volatile
    var controllerPointerMoveSink: ((hand: Int, ndcX: Float, ndcY: Float) -> Unit)? = null

    // ── Interactive panel hit-test (Phase 04) ────────────────────────────────

    @Volatile private var panelHitTester: VrRayPanelHitTester? = null
    @Volatile private var panelHitResolver: VrPanelHitZoneResolver? = null

    /** Set by `VrPlayerActivity` to determine whether the GL panel is currently shown. */
    @Volatile var panelVisibleProvider: (() -> Boolean)? = null

    /**
     * Called on the xr-render-thread with the resolved zone ID and optional seek fraction
     * whenever the panel is visible. [VrInteractivePanelDriver] posts internally to the main
     * looper, so this sink is safe to invoke from any thread.
     */
    @Volatile var panelHoverSink: ((hand: Int, zoneId: Int, seekFrac: Float) -> Unit)? = null

    /**
     * Called on the main thread when a trigger click arrives while the panel is visible.
     * The sink reads the last-hovered zone from its own state (set via [panelHoverSink]).
     */
    @Volatile var panelClickSink: (() -> Unit)? = null

    /** Attach hit-test infrastructure after the GL panel swapchain is ready. */
    fun attachHitTester(hitTester: VrRayPanelHitTester, resolver: VrPanelHitZoneResolver) {
        panelHitTester = hitTester
        panelHitResolver = resolver
    }

    // ── HUD ray-input (S0024 Phase 02) ───────────────────────────────────────

    @Volatile private var hudHitTester: VrHudHitTester? = null

    /** Source of the per-frame HUD element registry (`VrHudSceneDriver.registry`). */
    @Volatile var hudRegistryProvider: (() -> VrHudElementRegistry?)? = null

    /** Predicate that returns true when the HUD composition layer is currently submitted. */
    @Volatile var hudVisibleProvider: (() -> Boolean)? = null

    /**
     * Called on the xr-render-thread with the resolved HUD element id under the controller
     * aim-ray (or `0` for miss / no element). Pipeline-side stores the value for Phase 03
     * hover redraw consumption.
     */
    @Volatile var hudHoverSink: ((hand: Int, hudElementId: Int) -> Unit)? = null

    /** Attach the HUD hit-tester after the HUD swapchain is ready. */
    fun attachHudHitTester(tester: VrHudHitTester) {
        hudHitTester = tester
    }

    /**
     * Attach the dispatcher that routes controller-trigger and hand-pinch events to
     * registered HUD element callbacks (S0024 Phase 04). Must be set after the HUD
     * scene driver is constructed by [VrRenderPipelineManager].
     */
    @Volatile var hudInputDispatcher: VrHudInputDispatcher? = null

    /**
     * Provider for the latest HUD hover id (`VrHudSceneDriver.hoverState.current()`).
     * Used by the trigger handler to decide whether to consume the trigger as a HUD
     * click or fall through to the player command.
     */
    @Volatile var hudHoverIdProvider: (() -> Int)? = null

    private fun dispatchXrEvent(type: Int, hand: Int, value: Float, source: Int) {
        val commandSource = xrCommandSource(source)
        Timber.v("VrInput[xr]: type=%d hand=%d value=%.3f source=%d", type, hand, value, source)

        // Pointer-click events feed the hand-ray manager (UI layer), not the command bus.
        // They are not routed through the resolver — they have no PlaybackCommand equivalent.
        when (type) {
            XrInputEventType.POINTER_CLICK_DOWN -> { handlePointerClick(hand, down = true); return }
            XrInputEventType.POINTER_CLICK_UP   -> { handlePointerClick(hand, down = false); return }
        }

        // When the interactive panel is visible, the primary trigger (PAUSE_TOGGLE) acts as a
        // panel-zone click rather than toggling play/pause. The panelClickSink reads the last
        // hovered zone from VrPlayerActivity state and dispatches the zone command.
        if (type == XrInputEventType.PAUSE_TOGGLE && panelVisibleProvider?.invoke() == true) {
            panelClickSink?.invoke()
            return
        }

        // S0024 Phase 04: when the HUD has an interactive element under the controller aim,
        // the trigger fires a HUD click instead of toggling play/pause. C++ emits PAUSE_TOGGLE
        // as a single edge — model it as press-and-immediate-release on the same hover id.
        if (type == XrInputEventType.PAUSE_TOGGLE) {
            val hoverId = hudHoverIdProvider?.invoke() ?: 0
            val dispatcher = hudInputDispatcher
            if (hoverId != 0 && dispatcher != null) {
                dispatcher.onTriggerDown(VrHudInputDispatcher.Source.CONTROLLER_TRIGGER)
                dispatcher.onTriggerUp(VrHudInputDispatcher.Source.CONTROLLER_TRIGGER)
                return
            }
        }

        val trigger = InputTrigger.fromXrInputEvent(type)
        val commandId = keyBindingManager.resolve(trigger, InputSurface.VR) ?: run {
            Timber.w("VrInput[xr]: unresolved event type=%d", type)
            return
        }
        dispatchVrCommand(commandId, hand, value, commandSource)
    }

    private fun handlePointerClick(hand: Int, down: Boolean) {
        if (down) {
            // UI click sound compensates for the missing haptic cue. Using the system
            // click avoids shipping additional audio assets. DOUBLE_PINCH is emitted
            // by native and bypasses this method entirely.
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
        } else {
            // Pinch-complete SFX — distinguishes release from press (spec S0007 §3.5).
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN)
        }
        onPointerEvent?.invoke(hand, down)

        // S0024 Phase 04: single dispatcher per ADR-2 — panel and HUD do not collide because
        // each consults its own registry under its own hover-id state. HUD branch fires only
        // when the pinched hand currently hovers a registered HUD element.
        val dispatcher = hudInputDispatcher ?: return
        val hoverId = hudHoverIdProvider?.invoke() ?: 0
        if (down) {
            if (hoverId != 0) dispatcher.onTriggerDown(VrHudInputDispatcher.Source.HAND_PINCH)
        } else {
            if (dispatcher.hasLatch()) dispatcher.onTriggerUp(VrHudInputDispatcher.Source.HAND_PINCH)
        }
    }

    /**
     * Maps a resolved [CommandId] to the corresponding [PlaybackCommand] and dispatches it.
     * The [hand] and [value] parameters carry analog payload for commands that need it
     * (e.g. [CommandId.VR_ZOOM_GRIP] uses [value] as the grip delta).
     */
    private fun dispatchVrCommand(commandId: String, hand: Int, value: Float, source: VrCommandSource) {
        when (commandId) {
            CommandId.PAUSE_PLAY, CommandId.PLAY, CommandId.PAUSE ->
                dispatchCommand(PlaybackCommand.TogglePausePlay, source)
            CommandId.EXIT ->
                dispatchCommand(PlaybackCommand.Exit, source)
            CommandId.FILE_OPS ->
                dispatchCommand(PlaybackCommand.OpenFileOps, source)
            CommandId.TOGGLE_CONTROLS -> {
                // S0031 П1: trace the moment the input manager receives the open-controls command.
                Timber.i("VrControllerInputManager: TOGGLE_CONTROLS received source=%s — dispatching OpenControls", source)
                dispatchCommand(PlaybackCommand.OpenControls, source)
            }
            CommandId.SEEK_FORWARD_5S, CommandId.SEEK_FORWARD_30S, CommandId.SEEK_MACRO_FORWARD ->
                dispatchCommand(PlaybackCommand.SeekForward, source)
            CommandId.SEEK_BACKWARD_5S, CommandId.SEEK_BACKWARD_30S, CommandId.SEEK_MACRO_BACKWARD ->
                dispatchCommand(PlaybackCommand.SeekBackward, source)
            CommandId.SEEK_MICRO_FORWARD, CommandId.VR_SWIPE_RIGHT ->
                dispatchCommand(PlaybackCommand.SeekMicro(forward = true), source)
            CommandId.SEEK_MICRO_BACKWARD, CommandId.VR_SWIPE_LEFT ->
                dispatchCommand(PlaybackCommand.SeekMicro(forward = false), source)
            CommandId.NEXT_FILE ->
                dispatchCommand(PlaybackCommand.NextFile, source)
            CommandId.PREVIOUS_FILE ->
                dispatchCommand(PlaybackCommand.PreviousFile, source)
            CommandId.VOLUME_UP, CommandId.VR_SWIPE_UP ->
                rateLimitedVolume(+1, source)
            CommandId.VOLUME_DOWN, CommandId.VR_SWIPE_DOWN ->
                rateLimitedVolume(-1, source)
            CommandId.VR_RECENTER ->
                dispatchCommand(PlaybackCommand.Recenter, source)
            CommandId.VR_TOGGLE_IMMERSIVE ->
                dispatchCommand(PlaybackCommand.ToggleImmersiveMode, source)
            CommandId.VR_CHEATSHEET ->
                dispatchCommand(PlaybackCommand.ShowCheatsheet, source)
            CommandId.VR_ZOOM_GRIP ->
                onZoomGripDelta(value)
            CommandId.VR_ZOOM_START, CommandId.VR_ZOOM_END -> { /* no-op: grip state transitions */ }
            CommandId.ZOOM_RESET ->
                dispatchCommand(PlaybackCommand.ZoomReset, source)
            CommandId.VR_DOUBLE_PINCH ->
                dispatchCommand(PlaybackCommand.TogglePausePlay, source)
            else ->
                Timber.w("VrInput: unknown commandId=%s", commandId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer B — Bluetooth keyboard (main thread via dispatchKeyEvent)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Keyboard sources already flow through PlayerActivity.onKeyDown on ACTION_DOWN.
     * Only VR-exclusive shortcuts should bypass that path; shared media/navigation
     * keys must stay on the base handler to avoid duplicate playback commands.
     */
    fun shouldInterceptKeyboardShortcut(event: KeyEvent): Boolean {
        val isKeyboardSource = (event.source and InputDevice.SOURCE_KEYBOARD) != 0
        if (!isKeyboardSource) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_C -> !event.isCtrlPressed && !event.isAltPressed
            KeyEvent.KEYCODE_V -> !event.isCtrlPressed && !event.isAltPressed
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_NUMPAD_0,
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_ADD,
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> !event.isCtrlPressed && !event.isAltPressed
            else -> false
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false

        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        val alt = event.isAltPressed

        val code = event.keyCode

        // ── Gamepad / HID buttons that some BT devices route through KeyEvent ──
        // Primary/secondary/tertiary mouse clicks come via ACTION_BUTTON_PRESS in onMotionEvent.
        // KEYCODE_BUTTON_4/5 handle gamepad-style side buttons on some HID devices.
        when (code) {
            KeyEvent.KEYCODE_BUTTON_4 -> {
                dispatchCommand(PlaybackCommand.PreviousFile, VrCommandSource.KEYBOARD)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_5 -> {
                dispatchCommand(PlaybackCommand.NextFile, VrCommandSource.KEYBOARD)
                return true
            }
        }

        // ── Media keys (BT remotes) — take precedence over letter keys ──
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { dispatchCommand(PlaybackCommand.TogglePausePlay, VrCommandSource.KEYBOARD); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY       -> { dispatchCommand(PlaybackCommand.Play, VrCommandSource.KEYBOARD);            return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE      -> { dispatchCommand(PlaybackCommand.Pause, VrCommandSource.KEYBOARD);           return true }
            KeyEvent.KEYCODE_MEDIA_NEXT       -> { dispatchCommand(PlaybackCommand.NextFile, VrCommandSource.KEYBOARD);        return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS   -> { dispatchCommand(PlaybackCommand.PreviousFile, VrCommandSource.KEYBOARD);    return true }
            KeyEvent.KEYCODE_MEDIA_STOP       -> { dispatchCommand(PlaybackCommand.Pause, VrCommandSource.KEYBOARD);           return true }
            // KEYCODE_VOLUME_* intentionally NOT intercepted — Android system handles those.
        }

        // ── F-keys (Norton Commander layer) ──
        when (code) {
            KeyEvent.KEYCODE_F1 -> { dispatchCommand(PlaybackCommand.ShowCheatsheet, VrCommandSource.KEYBOARD); return true }
            KeyEvent.KEYCODE_F2 -> { dispatchCommand(PlaybackCommand.RenameFile, VrCommandSource.KEYBOARD);     return true }
            KeyEvent.KEYCODE_F3 -> { dispatchCommand(PlaybackCommand.OpenFileOps, VrCommandSource.KEYBOARD);    return true } // Info lives inside ops panel.
            KeyEvent.KEYCODE_F4 -> {
                if (alt) dispatchCommand(PlaybackCommand.Exit, VrCommandSource.KEYBOARD) else dispatchCommand(PlaybackCommand.OpenControls, VrCommandSource.KEYBOARD)
                return true
            }
            KeyEvent.KEYCODE_F5 -> { dispatchCommand(PlaybackCommand.CopyFile, VrCommandSource.KEYBOARD);       return true }
            KeyEvent.KEYCODE_F6 -> { dispatchCommand(PlaybackCommand.MoveFile, VrCommandSource.KEYBOARD);       return true }
            KeyEvent.KEYCODE_F7 -> { dispatchCommand(PlaybackCommand.OpenFileOps, VrCommandSource.KEYBOARD);    return true }
            KeyEvent.KEYCODE_F8 -> { dispatchCommand(PlaybackCommand.DeleteFile, VrCommandSource.KEYBOARD);     return true }
            KeyEvent.KEYCODE_F10 -> { dispatchCommand(PlaybackCommand.Exit, VrCommandSource.KEYBOARD);          return true }
        }

        // ── Navigation / seek / volume arrows ──
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when {
                    shift -> dispatchCommand(PlaybackCommand.SeekMicro(forward = false), VrCommandSource.KEYBOARD)
                    ctrl  -> dispatchCommand(PlaybackCommand.SeekMacro(forward = false), VrCommandSource.KEYBOARD)
                    else  -> dispatchCommand(PlaybackCommand.SeekBackward, VrCommandSource.KEYBOARD)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when {
                    shift -> dispatchCommand(PlaybackCommand.SeekMicro(forward = true), VrCommandSource.KEYBOARD)
                    ctrl  -> dispatchCommand(PlaybackCommand.SeekMacro(forward = true), VrCommandSource.KEYBOARD)
                    else  -> dispatchCommand(PlaybackCommand.SeekForward, VrCommandSource.KEYBOARD)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                rateLimitedVolume(+1, VrCommandSource.KEYBOARD); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                rateLimitedVolume(-1, VrCommandSource.KEYBOARD); return true
            }
        }

        // ── File navigation Page keys ──
        when (code) {
            KeyEvent.KEYCODE_PAGE_UP   -> { dispatchCommand(PlaybackCommand.PreviousFile, VrCommandSource.KEYBOARD); return true }
            KeyEvent.KEYCODE_PAGE_DOWN -> { dispatchCommand(PlaybackCommand.NextFile, VrCommandSource.KEYBOARD);     return true }
        }

        // ── Letter / symbol keys ──
        return when (code) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_K -> {
                dispatchCommand(PlaybackCommand.TogglePausePlay, VrCommandSource.KEYBOARD); true
            }
            KeyEvent.KEYCODE_J -> { dispatchCommand(PlaybackCommand.SeekBackward, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_L -> { dispatchCommand(PlaybackCommand.SeekForward, VrCommandSource.KEYBOARD);  true }
            KeyEvent.KEYCODE_M -> { dispatchCommand(PlaybackCommand.Mute, VrCommandSource.KEYBOARD);         true }
            KeyEvent.KEYCODE_N -> { dispatchCommand(PlaybackCommand.NextFile, VrCommandSource.KEYBOARD);     true }
            KeyEvent.KEYCODE_P -> { dispatchCommand(PlaybackCommand.PreviousFile, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_TAB -> { dispatchCommand(PlaybackCommand.OpenControls, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_C -> {
                when {
                    ctrl -> { dispatchCommand(PlaybackCommand.CopyFile, VrCommandSource.KEYBOARD); true }
                    else -> { dispatchCommand(PlaybackCommand.Recenter, VrCommandSource.KEYBOARD); true }
                }
            }
            KeyEvent.KEYCODE_X -> {
                when {
                    ctrl -> { dispatchCommand(PlaybackCommand.MoveFile, VrCommandSource.KEYBOARD); true }
                    else -> false
                }
            }
            KeyEvent.KEYCODE_R -> {
                when {
                    ctrl -> { dispatchCommand(PlaybackCommand.RenameFile, VrCommandSource.KEYBOARD); true }
                    else -> false
                }
            }
            KeyEvent.KEYCODE_I -> {
                when {
                    ctrl -> { dispatchCommand(PlaybackCommand.OpenFileOps, VrCommandSource.KEYBOARD); true }
                    else -> false
                }
            }
            KeyEvent.KEYCODE_Y -> {
                when {
                    ctrl -> { dispatchCommand(PlaybackCommand.OpenFileOps, VrCommandSource.KEYBOARD); true }
                    else -> false
                }
            }
            KeyEvent.KEYCODE_V -> { dispatchCommand(PlaybackCommand.ToggleImmersiveMode, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_ESCAPE -> { dispatchCommand(PlaybackCommand.Exit, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_DEL -> { dispatchCommand(PlaybackCommand.DeleteFile, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_ADD -> { rateLimitedZoom(increase = true, source = VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { rateLimitedZoom(increase = false, source = VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_NUMPAD_0 -> { dispatchCommand(PlaybackCommand.ZoomReset, VrCommandSource.KEYBOARD); true }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> { dispatchCommand(PlaybackCommand.OpenControls, VrCommandSource.KEYBOARD); true }
            else -> false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer C — Bluetooth mouse (main thread via onGenericMotionEvent)
    // ═══════════════════════════════════════════════════════════════════════

    fun onMotionEvent(event: MotionEvent): Boolean {
        val isMouse = (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        if (!isMouse) return false

        when (event.action) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY   -> { dispatchCommand(PlaybackCommand.TogglePausePlay, VrCommandSource.MOUSE); return true }
                    MotionEvent.BUTTON_SECONDARY -> { dispatchCommand(PlaybackCommand.OpenControls, VrCommandSource.MOUSE);    return true }
                    MotionEvent.BUTTON_TERTIARY  -> { dispatchCommand(PlaybackCommand.Recenter, VrCommandSource.MOUSE);        return true }
                    MotionEvent.BUTTON_BACK      -> { dispatchCommand(PlaybackCommand.PreviousFile, VrCommandSource.MOUSE);    return true }
                    MotionEvent.BUTTON_FORWARD   -> { dispatchCommand(PlaybackCommand.NextFile, VrCommandSource.MOUSE);        return true }
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (vScroll == 0f) return false
                val metaShift = (event.metaState and KeyEvent.META_SHIFT_ON) != 0
                if (metaShift) {
                    if (vScroll > 0f) dispatchCommand(PlaybackCommand.SeekForward, VrCommandSource.MOUSE)
                    else dispatchCommand(PlaybackCommand.SeekBackward, VrCommandSource.MOUSE)
                } else {
                    rateLimitedVolume(if (vScroll > 0f) +1 else -1, VrCommandSource.MOUSE)
                }
                return true
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                // Cursor movement — not mapped to a command; overlay layer handles cursor tracking.
                return false
            }
        }
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Rate-limiting helpers (analog inputs produce bursts)
    // ═══════════════════════════════════════════════════════════════════════

    private fun rateLimitedVolume(delta: Int, source: VrCommandSource) {
        val now = SystemClock.uptimeMillis()
        // Allow the very first analog step immediately; later events are throttled.
        if (lastVolumeEventMs >= 0L && now - lastVolumeEventMs < VOLUME_STEP_INTERVAL_MS) return
        lastVolumeEventMs = now
        onVolumeStep(delta)
        dispatchCommand(PlaybackCommand.VolumeStep(delta), source)
    }

    private fun rateLimitedZoom(increase: Boolean, source: VrCommandSource) {
        val now = SystemClock.uptimeMillis()
        if (lastZoomStepMs >= 0L && now - lastZoomStepMs < ZOOM_STEP_INTERVAL_MS) return
        lastZoomStepMs = now
        dispatchCommand(PlaybackCommand.ZoomStep(increase), source)
    }

    private fun dispatchCommand(command: PlaybackCommand, source: VrCommandSource) {
        if (!commandDebouncer.shouldDispatch(command, source)) {
            Timber.d("VrControllerInputManager: debounced %s", command)
            return
        }
        onCommand(command, source)
    }

    private fun xrCommandSource(source: Int): VrCommandSource = when (source) {
        XrInputSource.HAND -> VrCommandSource.HAND
        else -> VrCommandSource.CONTROLLER
    }

    companion object {
        /** Minimum interval between consecutive volume steps from analog sources. */
        const val VOLUME_STEP_INTERVAL_MS = 150L

        /** Minimum interval between discrete zoom steps to avoid key-repeat overshoot. */
        const val ZOOM_STEP_INTERVAL_MS = 120L

        @Suppress("unused")
        private const val HAND_LEFT = XrHand.LEFT

        @Suppress("unused")
        private const val HAND_RIGHT = XrHand.RIGHT

        @Suppress("unused")
        private const val SOURCE_CONTROLLER = XrInputSource.CONTROLLER

        @Suppress("unused")
        private const val SOURCE_HAND = XrInputSource.HAND
    }
}
