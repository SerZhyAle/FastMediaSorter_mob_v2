package com.sza.fastmediasorter.vr.ui

import android.os.Handler
import android.os.Looper
import com.sza.fastmediasorter.vr.render.VrHudElementRegistry
import com.sza.fastmediasorter.vr.render.VrHudHoverState
import timber.log.Timber

/**
 * Single sink for "trigger" events that target HUD interactive elements,
 * regardless of producer (controller trigger / hand-tracking pinch).
 *
 * Strategic ADR-2 (S0024 §9): the consumer never knows which input source fired.
 * The [Source] enum is **diagnostic only** — behaviour is identical.
 *
 * Trigger-button-mapping (S0024 strategic §6.3, Resolved 2026-04-28):
 *   - **Controller**: trigger button (analogous to mouse click). Wired in
 *     `VrControllerInputManager.dispatchXrEvent` ahead of the existing
 *     `PAUSE_TOGGLE → TogglePausePlay` resolution. The fall-through path is
 *     preserved for the case where no HUD element is hovered.
 *   - **Hand**: pinch click via `XrInputEventType.POINTER_CLICK_DOWN/UP`.
 *
 * **A/X/Y/B/menu** stay on player commands (no collision — confirmed by
 * walking `VrControllerInputManager.dispatchVrCommand` 2026-05-03).
 *
 * "Press → drift → release" rule (mirrors `VrControllerRayManager`): the hover
 * id is latched at [onTriggerDown]; if the latched id no longer matches the
 * current hover id at [onTriggerUp], the click is dropped. Prevents accidental
 * clicks on adjacent elements when the ray drifts mid-hold.
 */
class VrHudInputDispatcher(
    private val hoverState: VrHudHoverState,
    private val registryProvider: () -> VrHudElementRegistry?,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    /**
     * Optional accessibility cue played on a successful dispatch (S0024 strategic
     * §3.2 — hover highlight must not be the only feedback channel). Owner supplies
     * a system-sound lambda; default is no-op so unit tests stay silent.
     */
    private val onClickAudioCue: () -> Unit = {},
) {

    enum class Source { CONTROLLER_TRIGGER, HAND_PINCH }

    @Volatile private var latchedId: Int = 0

    /** Capture the current hover id at the moment the trigger goes down. */
    fun onTriggerDown(source: Source) {
        val current = hoverState.current()
        latchedId = current
        Timber.v("VrHudInputDispatcher: down source=%s hover=%d", source, current)
    }

    /**
     * Dispatch the latched callback when the release-time hover id still matches.
     * Always clears [latchedId] back to 0.
     */
    fun onTriggerUp(source: Source) {
        val latched = latchedId
        latchedId = 0
        if (latched == 0) return
        val current = hoverState.current()
        if (latched != current) {
            Timber.d("VrHudInputDispatcher: drop source=%s latched=%d current=%d (drift)", source, latched, current)
            return
        }
        val registry = registryProvider() ?: return
        val callback = registry.callbackOf(latched) ?: return
        Timber.d("VrHudInputDispatcher: click source=%s element=%d", source, latched)
        Timber.d("VR_AUDIT/2: HUD button click source=%s element=%d", source, latched)
        onClickAudioCue.invoke()
        mainHandler.post { callback.invoke() }
    }

    /** True while a trigger is currently latched on a non-zero hover id. */
    fun hasLatch(): Boolean = latchedId != 0
}
