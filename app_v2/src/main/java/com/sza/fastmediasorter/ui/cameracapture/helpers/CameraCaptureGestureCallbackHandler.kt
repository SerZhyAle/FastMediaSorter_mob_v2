package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.ui.cameracapture.FocusRingOverlayView
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode

/**
 * S0844: implements [CameraCaptureGestureManager.Callbacks] on behalf of
 * [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity] - a standalone object is
 * substituted for the Activity at the gesture manager's construction site (strategic ADR-1), so
 * these 5 callback methods no longer count against the Activity's detekt `TooManyFunctions`.
 */
class CameraCaptureGestureCallbackHandler(
    private val flowManager: CameraCaptureFlowManager,
    private val sessionManager: CameraCaptureSessionManager,
    private val focusRingOverlay: FocusRingOverlayView,
    private val zoomControlsManager: CameraZoomControlsManager,
    private val selectMode: (CameraCaptureMode) -> Unit,
    private val requestLensSwitch: () -> Unit,
) : CameraCaptureGestureManager.Callbacks {

    override fun onTapToFocus(x: Float, y: Float, visualX: Float, visualY: Float) {
        if (flowManager.onTapToFocus(x, y)) focusRingOverlay.showAt(visualX, visualY)
    }

    override fun onDoubleTapZoom() {
        flowManager.onDoubleTapZoom()
        syncZoomSelection()
    }

    override fun onPinchZoom(scaleFactor: Float) {
        flowManager.onPinchZoom(scaleFactor)
        syncZoomSelection()
    }

    override fun onSwipeLensSwitch() {
        // S1987: through the same gate as the button, so a swipe made during a rebind is refused and
        // shown as busy rather than queued behind it.
        requestLensSwitch()
    }

    override fun onSwipeModeSwitch(toNext: Boolean) {
        if (!flowManager.allowModeSwitch || sessionManager.isRecording()) return
        selectMode(if (flowManager.isVideoMode) CameraCaptureMode.PHOTO else CameraCaptureMode.VIDEO)
    }

    private fun syncZoomSelection() = zoomControlsManager.syncSelection(
        flowManager.liveZoomRatio,
        flowManager.liveLinearZoom,
        flowManager.currentCapabilities.zoomMultiplier,
    )
}
