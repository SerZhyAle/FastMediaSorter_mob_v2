package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.io.File

/**
 * Owns the host-level decisions for [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]
 * so the Activity stays a thin view + launcher shell (CLAUDE.md Rule 3).
 *
 * Responsibilities: output-file resolution, initial mode selection, permission outcome handling,
 * result packing, and close/error decisions. The Activity wires UI/camera side effects through
 * [Host]; this manager never touches views or CameraX directly.
 */
class CameraCaptureFlowManager(
    private val intent: Intent,
    private val host: Host,
    private val session: CameraCaptureSessionManager,
) {

    /** Side-effect surface implemented by the Activity. */
    interface Host {
        fun showError(messageRes: Int)
        fun finishCancelled()
        fun finishWithResult(result: Intent)
        fun requestCameraPermission()
        fun bindCamera()

        /** Render the controls allowed by the active lens (later phases hide unsupported ones). */
        fun renderCapabilities(capabilities: CameraRuntimeCapabilities)
    }

    /** Single-sourced capabilities of the active lens; the view layer only reads this. */
    var currentCapabilities: CameraRuntimeCapabilities = CameraRuntimeCapabilities.NONE
        private set

    /** Torch/flash state; reset on every lens switch because rebinding drops the torch. */
    var flashEnabled: Boolean = false
        private set

    /**
     * Capture mode. Fixed by the launching entry point for legacy callers (S0545 §3.4); mutable only
     * for the switchable "Camera" entry (S0563), changed via [switchMode].
     */
    var mode: CameraCaptureMode = CameraCaptureContract.readMode(intent)
        private set

    /** True only for the "Camera" entry (S0563): in-screen photo/video switching is allowed. */
    val allowModeSwitch: Boolean = CameraCaptureContract.readAllowModeSwitch(intent)

    /** Scratch dir + extension-less base name when the host owns the output file (switchable mode). */
    private val outputDir: String? = CameraCaptureContract.readOutputDir(intent)
    private val outputBaseName: String? = CameraCaptureContract.readOutputBaseName(intent)

    /** Initial microphone state for video mode; ignored in photo mode. */
    val microphoneDefault: Boolean = CameraCaptureContract.readMicrophoneDefault(intent)

    /** True when the active mode records video; tracks [mode] so it follows an in-screen switch. */
    val isVideoMode: Boolean get() = mode == CameraCaptureMode.VIDEO

    /** Live microphone-enabled state for video mode; user-toggled, starts at [microphoneDefault]. */
    var microphoneEnabled: Boolean = microphoneDefault
        private set

    /** Output target for the active [mode]; recomputed on access so it follows an in-screen switch. */
    val outputFile: File? get() = currentOutputFile()

    /** Validates the output target upfront. Returns false (after signalling the host) when it cannot. */
    fun resolveOutput(): Boolean {
        val dir = outputDir
        if (dir != null && outputBaseName != null) {
            // Switchable mode: the host owns the file, so only the scratch dir must be writable here.
            val ready = runCatching { File(dir).apply { mkdirs() }.isDirectory }.getOrDefault(false)
            if (!ready) {
                host.showError(R.string.camera_capture_error_save_generic)
                host.finishCancelled()
                return false
            }
            return true
        }
        if (resolveLegacyOutputFile() == null) {
            host.showError(R.string.camera_capture_error_save_generic)
            host.finishCancelled()
            return false
        }
        return true
    }

    /**
     * Switches the active capture mode for the "Camera" entry (S0563). No-op (returns false) for
     * fixed-mode callers or when the target equals the current mode; the Activity rebinds the session
     * only when this returns true.
     */
    fun switchMode(target: CameraCaptureMode): Boolean {
        if (!allowModeSwitch || target == mode) return false
        mode = target
        return true
    }

    /** Output file for the active mode: host-owned dir/basename (extension per mode) or legacy path. */
    fun currentOutputFile(): File? {
        val dir = outputDir
        val base = outputBaseName
        if (dir != null && base != null) {
            return File(dir, base + extensionFor(mode))
        }
        return resolveLegacyOutputFile()
    }

    private fun extensionFor(activeMode: CameraCaptureMode): String =
        if (activeMode == CameraCaptureMode.VIDEO) ".mp4" else ".jpg"

    /** Camera permission gate: bind immediately when granted, otherwise ask the host to request it. */
    fun ensurePermissionAndBind(hasCameraPermission: Boolean) {
        if (hasCameraPermission) host.bindCamera() else host.requestCameraPermission()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) {
            host.bindCamera()
        } else {
            host.showError(R.string.camera_permission_required)
            host.finishCancelled()
        }
    }

    /** Receives a fresh capability snapshot after each bind / lens switch and re-renders controls. */
    fun onCapabilitiesChanged(capabilities: CameraRuntimeCapabilities) {
        currentCapabilities = capabilities
        flashEnabled = false
        host.renderCapabilities(capabilities)
    }

    /** Toggles the torch when the active lens has a flash unit; returns the resulting state. */
    fun onFlashToggle(): Boolean {
        if (!currentCapabilities.hasFlashUnit) return false
        flashEnabled = !flashEnabled
        session.setTorchEnabled(flashEnabled)
        return flashEnabled
    }

    /** Flips to the next lens; capabilities (and control visibility) refresh via the bind callback. */
    fun onLensSwitch() {
        if (currentCapabilities.canSwitchLens) session.switchCamera()
    }

    fun onZoomRatioSelected(ratio: Float) {
        if (currentCapabilities.supportsZoom) session.setZoomRatio(ratio)
    }

    /** Runs tap-to-focus when supported; returns whether the focus ring should be shown. */
    fun onTapToFocus(x: Float, y: Float): Boolean {
        if (!currentCapabilities.supportsTapToFocus) return false
        session.startFocusAndMetering(x, y)
        return true
    }

    /** Toggles the microphone for the next/active recording; returns the resulting state. */
    fun onMicrophoneToggle(): Boolean {
        microphoneEnabled = !microphoneEnabled
        return microphoneEnabled
    }

    /** Completes the video flow: success packs the result, error surfaces a message and waits. */
    fun onRecordingFinalized(hasError: Boolean) {
        if (hasError) {
            host.showError(R.string.camera_capture_error_save_generic)
        } else {
            host.finishWithResult(
                CameraCaptureContract.packResult(mode, microphoneEnabled, currentOutputFile()?.absolutePath),
            )
        }
    }

    fun onClose() = host.finishCancelled()

    /** Packs the activity result with the actually-captured media kind and file path for the caller. */
    fun onCaptureSucceeded() =
        host.finishWithResult(
            CameraCaptureContract.packResult(mode, outputPath = currentOutputFile()?.absolutePath),
        )

    private fun resolveLegacyOutputFile(): File? {
        CameraCaptureContract.readOutputPath(intent)?.let { return File(it) }
        val outputPath = CameraCaptureContract.readOutputUri(intent)?.path ?: return null
        return outputPath.takeIf { it.isNotBlank() }?.let(::File)
    }
}
