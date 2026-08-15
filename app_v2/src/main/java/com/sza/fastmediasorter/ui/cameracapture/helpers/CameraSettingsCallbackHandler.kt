package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraMetadata
import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.ui.cameracapture.CameraSettingsDialogFragment
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import kotlinx.coroutines.flow.StateFlow

/**
 * S0844: implements [CameraSettingsDialogFragment.Callbacks] and owns showing the dialog on behalf
 * of [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity] - a standalone object is
 * substituted for the Activity at the dialog's `callbacks` field (strategic ADR-1), so these methods
 * no longer count against the Activity's detekt `TooManyFunctions`.
 *
 * S0924: forwards the Activity's single [CameraOrientationManager] rotation bucket to the dialog so it
 * can rotate with the device without a second `OrientationEventListener`.
 */
class CameraSettingsCallbackHandler(
    private val sessionManager: CameraCaptureSessionManager,
    private val flowManager: CameraCaptureFlowManager,
    private val onGridToggled: () -> Unit,
    private val onAspectRatioApplied: () -> Unit,
    private val rotationBucket: StateFlow<Int>,
    // S1418: exposure and white balance are what turn NORMAL into the manual mode, and they are
    // written here on every preview tick as well as on apply and cancel, so the host is told from
    // the one method all three paths funnel through.
    private val onManualStateChanged: () -> Unit = {},
) : CameraSettingsDialogFragment.Callbacks {

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.findFragmentByTag(CameraSettingsDialogFragment.TAG) != null) return
        // S1336: no field injection here - the fragment pulls its inputs from this handler via
        // Callbacks/Host once attached, which is what lets a framework-restored instance (theme,
        // language, "don't keep activities", process death) reconstruct itself without a crash.
        CameraSettingsDialogFragment().show(fragmentManager, CameraSettingsDialogFragment.TAG)
    }

    override fun currentCameraCapabilities(): CameraRuntimeCapabilities = flowManager.currentCapabilities

    override fun currentCameraSettingsState(): CameraSettingsDialogFragment.CameraSettingsState =
        CameraSettingsDialogFragment.CameraSettingsState(
            selfTimerSeconds = flowManager.selfTimerSeconds,
            gridEnabled = flowManager.gridEnabled,
            aspect = sessionManager.currentAspect,
            videoMode = sessionManager.videoMode,
            resolution = sessionManager.currentResolution,
            exposureCompensationIndex = sessionManager.currentExposureCompensationIndex,
            whiteBalanceMode = sessionManager.currentWhiteBalanceMode ?: CameraMetadata.CONTROL_AWB_MODE_AUTO,
            manualSensorEnabled = sessionManager.currentManualIso != null &&
                sessionManager.currentManualShutterNs != null,
            manualIso = sessionManager.currentManualIso,
            manualShutterNs = sessionManager.currentManualShutterNs,
            hdrEnabled = sessionManager.hdrEnabled,
        )

    override fun cameraRotationBucket(): StateFlow<Int> = rotationBucket

    override fun onCameraSettingsPreviewChanged(state: CameraSettingsDialogFragment.CameraSettingsState) {
        sessionManager.setExposureCompensation(state.exposureCompensationIndex)
        sessionManager.setWhiteBalance(state.whiteBalanceMode)
        if (state.manualSensorEnabled && state.manualIso != null && state.manualShutterNs != null) {
            sessionManager.setManualSensor(state.manualIso, state.manualShutterNs)
        } else {
            sessionManager.clearManualSensor()
        }
        sessionManager.applyHdr(state.hdrEnabled)
        onManualStateChanged()
    }

    override fun onCameraSettingsApplied(state: CameraSettingsDialogFragment.CameraSettingsState) {
        onCameraSettingsPreviewChanged(state)
        flowManager.setSelfTimerSeconds(state.selfTimerSeconds)
        flowManager.setGridEnabled(state.gridEnabled)
        onGridToggled()
        sessionManager.setAspectRatioAndResolution(state.aspect, state.resolution)
        // S1066: the selected ratio drives the result frame (photo) - rebuild it after an apply.
        onAspectRatioApplied()
    }

    override fun onCameraSettingsCancelled(state: CameraSettingsDialogFragment.CameraSettingsState) {
        onCameraSettingsPreviewChanged(state)
    }
}
