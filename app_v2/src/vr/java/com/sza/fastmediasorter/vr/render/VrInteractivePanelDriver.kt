package com.sza.fastmediasorter.vr.render

import android.os.Handler
import android.os.Looper
import com.sza.fastmediasorter.vr.ui.VrHudSink
import timber.log.Timber

/**
 * State machine + GL submit loop for the interactive controls panel
 * (spec_vr-immersive-controls-panel Phase 03/05). Maintains a mutable [VrHudState],
 * re-renders via [VrInteractivePanelComposer] and uploads through [VrInteractivePanelRenderer]
 * on every relevant state change. Implements [VrHudSink] panel-feed methods so callers
 * can push live playback state without depending on the concrete driver type.
 *
 * Threading: all public methods are safe to call from any thread — mutations are
 * posted to the main looper before touching state.
 *
 * Auto-hide: every call to [show] or any [update*] method (except [updateProgress]) while
 * the panel is visible cancels and restarts a [AUTO_HIDE_DELAY_MS] countdown.
 */
class VrInteractivePanelDriver(
    private val renderer: VrInteractivePanelRenderer,
    private val composer: VrInteractivePanelComposer,
) : VrHudSink {

    private val handler = Handler(Looper.getMainLooper())
    private var state: VrHudState = VrHudState()
    private var redrawScheduled = false

    private val autoHideRunnable = Runnable { hide() }

    private val redrawRunnable = Runnable {
        redrawScheduled = false
        if (!state.panelVisible) return@Runnable
        renderer.submit { canvas -> composer.draw(state, canvas) }
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    fun show() {
        runOnMain {
            if (state.panelVisible) {
                scheduleAutoHide()
                return@runOnMain
            }
            Timber.d("VrInteractivePanelDriver: show")
            Timber.d("VR_AUDIT/2: panel SHOW request visible=true autoHideMs=%d", AUTO_HIDE_DELAY_MS)
            state = state.copy(panelVisible = true)
            renderer.setVisible(true)
            scheduleAutoHide()
            requestRedraw()
        }
    }

    fun hide() {
        runOnMain {
            if (!state.panelVisible) return@runOnMain
            Timber.d("VrInteractivePanelDriver: hide")
            Timber.d("VR_AUDIT/2: panel HIDE (auto-hide-timer or explicit) durationMs=%d", AUTO_HIDE_DELAY_MS)
            handler.removeCallbacks(autoHideRunnable)
            state = state.copy(panelVisible = false)
            renderer.setVisible(false)
        }
    }

    fun toggle() {
        runOnMain { if (state.panelVisible) hide() else show() }
    }

    fun isPanelVisible(): Boolean = state.panelVisible

    // ── State updates ─────────────────────────────────────────────────────────

    fun updateHoverZone(zoneId: Int) {
        runOnMain {
            if (state.hoveredZoneId == zoneId) return@runOnMain
            state = state.copy(hoveredZoneId = zoneId)
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateSeekDrag(fraction: Float) {
        runOnMain {
            state = state.copy(seekDragFraction = fraction)
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateVolume(percent: Int) {
        runOnMain {
            state = state.copy(volumePercent = percent.coerceIn(0, 100))
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateBrightness(percent: Int) {
        runOnMain {
            state = state.copy(brightnessPercent = percent.coerceIn(0, 100))
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateSpeed(speed: Float) {
        runOnMain {
            state = state.copy(playbackSpeed = speed)
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateTrackLabel(label: String) {
        runOnMain {
            state = state.copy(audioTrackLabel = label)
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    fun updateFormatLabel(label: String) {
        runOnMain {
            state = state.copy(stereoModeLabel = label)
            rescheduleIfVisible()
            requestRedraw()
        }
    }

    /** Does NOT reschedule auto-hide — progress updates are too frequent to reset the timer. */
    override fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long) {
        runOnMain {
            state = state.copy(positionMs = positionMs, bufferedMs = bufferedMs, totalMs = totalMs)
            requestRedraw()
        }
    }

    // ── VrHudSink panel feed (Phase 05) ────────────────────────────────────────

    override fun updatePanelVolume(percent: Int) = updateVolume(percent)
    override fun updatePanelBrightness(percent: Int) = updateBrightness(percent)
    override fun updatePanelSpeed(speed: Float) = updateSpeed(speed)
    override fun updatePanelTrackLabel(label: String) = updateTrackLabel(label)
    override fun updatePanelFormatLabel(label: String) = updateFormatLabel(label)
    override fun showPanel() = show()
    override fun hidePanel() = hide()
    override fun togglePanel() = toggle()

    // ── VrHudSink stubs (HUD-layer methods not applicable to the panel) ────────

    override fun showPauseIndicator(paused: Boolean) {}
    override fun showVolumeIndicator(percent: Int) {}
    override fun showSeekIndicator(deltaSeconds: Int, positionMs: Long, totalMs: Long) {}
    override fun showFileIndicator(name: String, index: Int, total: Int) {}
    override fun showZoomIndicator(factor: Float) {}
    override fun showRecenterFlash() {}
    override fun showBoundaryMessage(isFirst: Boolean) {}
    override fun showErrorMessage(message: String) {}
    override fun showImmersiveModeChanged(immersive: Boolean) {}
    override fun showActionBadge(badge: com.sza.fastmediasorter.vr.render.ActionBadge) {}
    override fun showRepeatMode(mode: com.sza.fastmediasorter.vr.render.RepeatMode?) {}
    override fun showBannerText(text: String?) {}
    override fun reportActivity() {}
    override fun showStereoModeLabel(label: String?) {}
    override fun hideAll() = hide()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun release() {
        handler.removeCallbacks(autoHideRunnable)
        handler.removeCallbacks(redrawRunnable)
        renderer.release()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun requestRedraw() {
        if (redrawScheduled) return
        redrawScheduled = true
        handler.post(redrawRunnable)
    }

    private fun rescheduleIfVisible() {
        if (state.panelVisible) scheduleAutoHide()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS = 10_000L
    }
}
