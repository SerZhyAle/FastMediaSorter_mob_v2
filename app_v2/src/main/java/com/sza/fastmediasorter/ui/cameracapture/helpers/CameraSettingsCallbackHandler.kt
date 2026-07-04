package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraMetadata
import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.ui.cameracapture.CameraSettingsDialogFragment

/**
 * S0844: implements [CameraSettingsDialogFragment.Callbacks] and owns showing the dialog on behalf
 * of [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity] - a standalone object is
 * substituted for the Activity at the dialog's `callbacks` field (strategic ADR-1), so these methods
 * no longer count against the Activity's detekt `TooManyFunctions`.
 */
class CameraSettingsCallbackHandler(
    private val sessionManager: CameraCaptureSessionManager,
    private val flowManager: CameraCaptureFlowManager,
    private val onGridToggled: () -> Unit,
) : CameraSettingsDialogFragment.Callbacks {

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.findFragmentByTag(CameraSettingsDialogFragment.TAG) != null) return
        CameraSettingsDialogFragment().apply {
            callbacks = this@CameraSettingsCallbackHandler
            capabilities = flowManager.currentCapabilities
            initialSettings = CameraSettingsDialogFragment.CameraSettingsState(
                selfTimerSeconds = flowManager.selfTimerSeconds,
                gridEnabled = flowManager.gridEnabled,
                aspectRatio = sessionManager.currentAspectRatio(),
                resolution = sessionManager.currentResolution(),
                exposureCompensationIndex = sessionManager.currentExposureCompensationIndex(),
                whiteBalanceMode = sessionManager.currentWhiteBalanceMode() ?: CameraMetadata.CONTROL_AWB_MODE_AUTO,
                manualSensorEnabled = sessionManager.currentManualIso() != null &&
                    sessionManager.currentManualShutterNs() != null,
                manualIso = sessionManager.currentManualIso(),
                manualShutterNs = sessionManager.currentManualShutterNs(),
                hdrEnabled = sessionManager.hdrEnabled,
            )
        }.show(fragmentManager, CameraSettingsDialogFragment.TAG)
    }

    override fun onCameraSettingsPreviewChanged(state: CameraSettingsDialogFragment.CameraSettingsState) {
        sessionManager.setExposureCompensation(state.exposureCompensationIndex)
        sessionManager.setWhiteBalance(state.whiteBalanceMode)
        if (state.manualSensorEnabled && state.manualIso != null && state.manualShutterNs != null) {
            sessionManager.setManualSensor(state.manualIso, state.manualShutterNs)
        } else {
            sessionManager.clearManualSensor()
        }
        sessionManager.applyHdr(state.hdrEnabled)
    }

    override fun onCameraSettingsApplied(state: CameraSettingsDialogFragment.CameraSettingsState) {
        onCameraSettingsPreviewChanged(state)
        flowManager.setSelfTimerSeconds(state.selfTimerSeconds)
        flowManager.setGridEnabled(state.gridEnabled)
        onGridToggled()
        sessionManager.setAspectRatioAndResolution(state.aspectRatio, state.resolution)
    }

    override fun onCameraSettingsCancelled(state: CameraSettingsDialogFragment.CameraSettingsState) {
        onCameraSettingsPreviewChanged(state)
    }
}
