package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding

/**
 * S0844: applies the device-orientation icon rotation to the fixed set of overlay controls on
 * [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]. Angle computation stays in
 * [CameraOrientationManager]; this class only owns the last-applied angle and the view fan-out
 * (extracted from the Activity to shed its detekt `LargeClass`/`TooManyFunctions` findings).
 */
class CameraOverlayRotationManager(binding: ActivityCameraCaptureBinding) {

    // Built once here (not per-call) since the view set never changes across the host's lifetime.
    private val views = listOf(
        binding.btnCloseCamera,
        binding.cameraSaveDestination,
        binding.btnCameraSettings,
        binding.toggleCameraMicrophone,
        binding.btnCameraFlash,
        binding.btnCameraProfile,
        binding.cameraRecordingTimer,
        binding.cameraZoomValue,
        binding.tabModePhoto,
        binding.tabModeVideo,
        binding.btnGalleryThumbnail,
        binding.btnCameraPauseResume,
        binding.btnCameraSendTo,
        binding.btnCapturePhoto,
        binding.btnCameraLensSwitch,
        binding.cameraLensLabel,
        binding.cameraScenarioLabel,
    )

    private var currentDegrees: Float = 0f

    /** Rotates every tracked view to [degrees], animated unless [animate] is false. */
    fun apply(degrees: Float, animate: Boolean = true) {
        currentDegrees = degrees
        views.forEach { view ->
            if (animate && AnimationPolicy.isAnimationAllowed) {
                view.animate()
                    .rotation(degrees)
                    .setDuration(OVERLAY_ROTATION_ANIMATION_MS)
                    .start()
            } else {
                view.animate().cancel()
                view.rotation = degrees
            }
        }
    }

    /** Re-draws at the last-applied angle - used after a label's visibility/text just changed. */
    fun reapply(animate: Boolean = false) = apply(currentDegrees, animate)

    private companion object {
        const val OVERLAY_ROTATION_ANIMATION_MS = 180L
    }
}
