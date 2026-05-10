package com.sza.fastmediasorter.vr.render

import android.os.Handler
import android.os.Looper
import com.sza.fastmediasorter.vr.ui.VrHudSink
import timber.log.Timber

/**
 * GL backend of [VrHudSink]. Maintains a mutable [VrHudState], renders it via
 * [VrHudSceneComposer] into a Bitmap, and uploads through [VrHudRenderer].
 *
 * Threading: every public method posts to the main looper before mutating state,
 * so callers from background threads (e.g. ExoPlayer position polling) are safe.
 *
 * Visibility: the underlying composition layer is shown only when at least one
 * slot's `*UntilMs` is in the future or the progress bar has live data. When all
 * slots time out the layer is hidden — zero overdraw.
 */
class VrHudSceneDriver(
    private val renderer: VrHudRenderer,
    private val composer: VrHudSceneComposer,
    /**
     * Hover state owned by the driver — written by the input manager via
     * [VrHudHoverState.setCurrent], consulted by [redrawRunnable] each tick to
     * decide whether to repaint. S0024 Phase 03.
     */
    val hoverState: VrHudHoverState = VrHudHoverState(),
) : VrHudSink {

    /**
     * Read-only access to the per-frame interactive-element registry maintained by
     * [composer]. S0024 Phase 02 Step 02.2 — surfaces the registry without leaking
     * the composer through additional public types.
     */
    val registry: VrHudElementRegistry get() = composer.registry

    /**
     * True while the HUD composition layer is currently submitted to the compositor
     * (mirrors the most recent `renderer.setVisible(..)` call). S0024 strategic §11
     * criterion 4 — ray-input math is gated by this so off-HUD frames are free.
     */
    val isLayerVisible: Boolean get() = lastVisibleSubmitted

    private val handler = Handler(Looper.getMainLooper())
    private var state: VrHudState = VrHudState.OFF
    private var redrawScheduled = false
    private var lastVisibleSubmitted = false
    // WHY: tracks the current pause/play state so reportActivity() can restore
    // isPaused into the state when waking the HUD for a 15 s window.
    private var persistedIsPaused: Boolean? = null
    // Deadline of the most-recently opened 15 s activity window; used to decide
    // whether a pending delayed clear should be skipped (a newer window is open).
    private var lastActivityUntilMs = 0L

    private val redrawRunnable = Runnable {
        redrawScheduled = false
        val now = System.currentTimeMillis()
        val active = anySlotActive(state, now)
        if (active != lastVisibleSubmitted) {
            Timber.d("VR_AUDIT/3: HUD submit transition active=%b reason=auto-redraw", active)
            renderer.setVisible(active, reason = "auto-redraw")
            lastVisibleSubmitted = active
        }
        if (active) {
            // WHY: pass the latest hover id to the composer and mark it consumed in
            // the same tick. The hover paint is drawn last so id-changes are reflected
            // exactly once per redraw without an extra round-trip to the GL thread.
            val hoverId = hoverState.current()
            renderer.submit { canvas -> composer.draw(state, canvas, hoverId) }
            hoverState.markConsumed()
        }
    }

    override fun setVisible(visible: Boolean, reason: String) {
        // S0031 П1: log HUD visibility changes so we can confirm the swapchain received the request.
        Timber.i("VrHudSceneDriver: setVisible visible=%b reason=%s", visible, reason)
        handler.post {
            renderer.setVisible(visible, reason)
            lastVisibleSubmitted = visible
            if (visible) {
                requestRedraw()
            }
        }
    }

    private fun requestRedraw() {
        if (redrawScheduled) return
        redrawScheduled = true
        handler.post(redrawRunnable)
    }

    /**
     * Notify the driver that the hover id may have changed. If it actually did
     * (per [hoverState.hasPendingChange]) and the HUD is currently visible, schedule
     * a redraw — otherwise do nothing (idle frames stay free per strategic §11 #4).
     * Safe to call from any thread.
     */
    fun onHoverIdChanged() {
        if (!hoverState.hasPendingChange()) return
        if (!lastVisibleSubmitted) return
        handler.post { requestRedraw() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    override fun showPauseIndicator(paused: Boolean) {
        Timber.d("VR_AUDIT/3: indicator=pause visible=true paused=%b durationMs=%d", paused, DUR_PAUSE_MS)
        runOnMain {
            persistedIsPaused = paused  // track for reportActivity()
            val until = System.currentTimeMillis() + DUR_PAUSE_MS
            state = state.copy(isPaused = paused, visibleUntilMs = maxOf(state.visibleUntilMs, until))
            requestRedraw()
            // WHY: isPaused alone keeps the HUD visible when true (user expects to see controls
            // while paused). We schedule a clear only when no 15 s activity window is open —
            // if one is open, reportActivity's own handler will clear isPaused at the 15 s mark.
            handler.postDelayed({
                if (System.currentTimeMillis() >= until && lastActivityUntilMs < until) {
                    state = state.copy(isPaused = null)
                    requestRedraw()
                }
            }, DUR_PAUSE_MS)
        }
    }

    override fun showVolumeIndicator(percent: Int) {
        Timber.d("VR_AUDIT/3: indicator=volume visible=true percent=%d durationMs=%d", percent, DUR_VOLUME_MS)
        runOnMain {
            val clamped = percent.coerceIn(0, 100)
            val until = System.currentTimeMillis() + DUR_VOLUME_MS
            state = state.copy(volumePercent = clamped, visibleUntilMs = maxOf(state.visibleUntilMs, until))
            requestRedraw()
            handler.postDelayed({
                if (state.volumePercent != null && System.currentTimeMillis() >= until) {
                    state = state.copy(volumePercent = null)
                    requestRedraw()
                }
            }, DUR_VOLUME_MS)
        }
    }

    override fun showSeekIndicator(deltaSeconds: Int, positionMs: Long, totalMs: Long) {
        Timber.d("VR_AUDIT/3: indicator=seek visible=true deltaSec=%d posMs=%d totalMs=%d durationMs=%d", deltaSeconds, positionMs, totalMs, DUR_SEEK_MS)
        runOnMain {
            val until = System.currentTimeMillis() + DUR_SEEK_MS
            state = state.copy(
                seekDeltaSec = deltaSeconds,
                positionMs = positionMs,
                totalMs = totalMs,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({
                if (state.seekDeltaSec != null && System.currentTimeMillis() >= until) {
                    state = state.copy(seekDeltaSec = null)
                    requestRedraw()
                }
            }, DUR_SEEK_MS)
        }
    }

    override fun showFileIndicator(name: String, index: Int, total: Int) {
        Timber.d("VR_AUDIT/3: indicator=filename visible=true name=%s idx=%d/%d durationMs=%d", name, index, total, DUR_FILE_MS)
        runOnMain {
            val until = System.currentTimeMillis() + DUR_FILE_MS
            state = state.copy(
                fileLabel = name, fileIndex = index, fileTotal = total,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({
                if (System.currentTimeMillis() >= until) {
                    state = state.copy(fileLabel = null, fileIndex = null, fileTotal = null)
                    requestRedraw()
                }
            }, DUR_FILE_MS)
        }
    }

    override fun showZoomIndicator(factor: Float) {
        Timber.d("VR_AUDIT/3: indicator=zoom visible=true factor=%.2f durationMs=%d", factor, DUR_ZOOM_MS)
        runOnMain {
            val until = System.currentTimeMillis() + DUR_ZOOM_MS
            state = state.copy(zoomFactor = factor, visibleUntilMs = maxOf(state.visibleUntilMs, until))
            requestRedraw()
            handler.postDelayed({
                if (System.currentTimeMillis() >= until) {
                    state = state.copy(zoomFactor = null)
                    requestRedraw()
                }
            }, DUR_ZOOM_MS)
        }
    }

    override fun showRecenterFlash() {
        Timber.d("VR_AUDIT/3: indicator=recenter visible=true durationMs=%d", DUR_RECENTER_MS)
        runOnMain {
            val until = System.currentTimeMillis() + DUR_RECENTER_MS
            state = state.copy(
                recenterFlashUntilMs = until,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({ requestRedraw() }, DUR_RECENTER_MS)
        }
    }

    override fun showBoundaryMessage(isFirst: Boolean) {
        showBannerText(if (isFirst) "First file" else "Last file")
    }

    override fun showErrorMessage(message: String) {
        showBannerText(message)
    }

    override fun showImmersiveModeChanged(immersive: Boolean) {
        runOnMain {
            val until = System.currentTimeMillis() + DUR_IMMERSIVE_MS
            state = state.copy(
                immersiveBadgeUntilMs = until,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({ requestRedraw() }, DUR_IMMERSIVE_MS)
        }
    }

    override fun showActionBadge(badge: ActionBadge) {
        runOnMain {
            val until = System.currentTimeMillis() + DUR_ACTION_MS
            state = state.copy(
                actionBadge = badge,
                actionBadgeUntilMs = until,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({
                if (System.currentTimeMillis() >= until) {
                    state = state.copy(actionBadge = null, actionBadgeUntilMs = 0L)
                    requestRedraw()
                }
            }, DUR_ACTION_MS)
        }
    }

    override fun showRepeatMode(mode: RepeatMode?) {
        Timber.d("VR_AUDIT/3: indicator=repeat visible=%b mode=%s", mode != null, mode)
        runOnMain {
            state = state.copy(repeatMode = mode)
            // Repeat mode is "permanent" — refresh visibility but no auto-hide.
            if (mode != null) {
                state = state.copy(visibleUntilMs = maxOf(state.visibleUntilMs, System.currentTimeMillis() + DUR_REPEAT_FLASH_MS))
                handler.postDelayed({ requestRedraw() }, DUR_REPEAT_FLASH_MS)
            }
            requestRedraw()
        }
    }

    override fun showBannerText(text: String?) {
        runOnMain {
            if (text == null) {
                state = state.copy(bannerText = null, bannerUntilMs = 0L)
                requestRedraw()
            } else {
                val until = System.currentTimeMillis() + DUR_BANNER_MS
                state = state.copy(
                    bannerText = text,
                    bannerUntilMs = until,
                    visibleUntilMs = maxOf(state.visibleUntilMs, until),
                )
                requestRedraw()
                handler.postDelayed({
                    if (System.currentTimeMillis() >= until) {
                        state = state.copy(bannerText = null, bannerUntilMs = 0L)
                        requestRedraw()
                    }
                }, DUR_BANNER_MS)
            }
        }
    }

    override fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long) {
        runOnMain {
            // WHY: visibility is now driven exclusively by user activity (reportActivity → 15 s
            // window) and specific events (pause, seek, volume, etc.). The progress ticker must
            // NOT auto-extend visibilityWindow — doing so kept the HUD permanently visible and
            // prevented the 15 s auto-hide from ever firing. The bar values are updated here
            // so they are ready to display while the HUD is already shown for another reason.
            state = state.copy(
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                totalMs = totalMs,
            )
            requestRedraw()
        }
    }

    override fun updateFps(fps: Int) {
        runOnMain {
            state = state.copy(fps = fps)
            requestRedraw()
        }
    }

    override fun clearFps() {
        runOnMain {
            state = state.copy(fps = null)
            requestRedraw()
        }
    }

    override fun reportActivity() {
        runOnMain {
            val until = System.currentTimeMillis() + IDLE_HIDE_DELAY_MS
            lastActivityUntilMs = until
            state = state.copy(
                // Restore current pause/play state so the icon is visible during the window.
                isPaused = persistedIsPaused,
                visibleUntilMs = maxOf(state.visibleUntilMs, until),
            )
            requestRedraw()
            handler.postDelayed({
                // Only clear if no newer window was opened after this one.
                if (lastActivityUntilMs <= until) {
                    state = state.copy(isPaused = null)
                    requestRedraw()
                }
            }, IDLE_HIDE_DELAY_MS)
        }
    }

    override fun showStereoModeLabel(label: String?) {
        Timber.d("VR_AUDIT/3: indicator=mode visible=%b label=%s", label != null, label)
        runOnMain {
            state = state.copy(stereoModeLabel = label)
            requestRedraw()
        }
    }

    override fun hideAll() {
        runOnMain {
            persistedIsPaused = null
            lastActivityUntilMs = 0L
            state = VrHudState.OFF
            requestRedraw()
        }
    }

    private fun anySlotActive(s: VrHudState, now: Long): Boolean {
        // WHY: visibility is driven by visibleUntilMs (reportActivity + timed events) and by
        // isPaused == true. fps does NOT extend HUD visibility on its own — otherwise an
        // always-on FPS readout would block the 15 s idle auto-hide forever.
        if (s.isPaused == true) return true
        if (s.volumePercent != null) return true
        if (s.zoomFactor != null) return true
        if (s.fileLabel != null) return true
        if (s.seekDeltaSec != null) return true
        if (s.actionBadge != null && s.actionBadgeUntilMs > now) return true
        if (s.recenterFlashUntilMs > now) return true
        if (s.immersiveBadgeUntilMs > now) return true
        if (s.bannerText != null && s.bannerUntilMs > now) return true
        if (s.repeatMode != null && s.visibleUntilMs > now) return true
        return s.visibleUntilMs > now
    }

    companion object {
        private const val DUR_PAUSE_MS = 800L
        private const val DUR_VOLUME_MS = 1500L
        private const val DUR_SEEK_MS = 1500L
        private const val DUR_FILE_MS = 2000L
        private const val DUR_ZOOM_MS = 1200L
        private const val DUR_RECENTER_MS = 400L
        private const val DUR_IMMERSIVE_MS = 1200L
        private const val DUR_ACTION_MS = 350L
        private const val DUR_REPEAT_FLASH_MS = 1500L
        private const val DUR_BANNER_MS = 3000L

        // After 15 s of no controller/joystick input the HUD auto-hides.
        const val IDLE_HIDE_DELAY_MS = 15_000L
    }
}
