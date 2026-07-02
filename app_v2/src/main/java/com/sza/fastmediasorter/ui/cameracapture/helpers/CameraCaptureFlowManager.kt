package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /**
     * S0566: true for the general entry / widget. The host stays open after each capture and saves
     * every file itself; false keeps the legacy one-shot-then-finish contract for fixed callers.
     */
    val multiCapture: Boolean = CameraCaptureContract.readMultiCapture(intent)

    /** S0790: fire the shutter automatically once the preview is ready, then finish (edge-gesture photo). */
    val autoCapture: Boolean = CameraCaptureContract.readAutoCapture(intent)

    /** S0566: live zoom ratio, kept in sync with preset taps, pinch and double-tap so the UI can reflect it. */
    var liveZoomRatio: Float = CameraRuntimeCapabilities.DEFAULT_ZOOM
        private set

    /** S0753: slider mirror of [liveZoomRatio] in 0..1 linear space; the two are kept in lockstep. */
    var liveLinearZoom: Float = 0f
        private set

    /** S0753: night-mode UI state, reconciled against the session after each rebind. */
    var nightModeEnabled: Boolean = false
        private set

    /** S0753: macro UI state, reconciled against the session after each rebind. */
    var macroEnabled: Boolean = false
        private set

    /** S0754: UI-only self-timer delay, applied by the host before shutter/record start. */
    var selfTimerSeconds: Int = 0
        private set

    /** S0754: UI-only preview grid toggle; the host shows/hides the overlay. */
    var gridEnabled: Boolean = false
        private set

    /** S0566: monotonically increasing per-capture suffix so a multi-capture session never overwrites a file. */
    private var captureSequence = 0

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

    /**
     * S0566: target file for the next capture. In a multi-capture session each shot needs a unique
     * scratch file (the host owns the dir), so a timestamp + sequence suffix is appended; single-shot
     * callers keep [currentOutputFile] verbatim so their fixed result path is unchanged.
     */
    fun nextOutputFile(): File? {
        val dir = outputDir
        if (!multiCapture || dir == null) return currentOutputFile()
        captureSequence++
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "CAP_${stamp}_$captureSequence${extensionFor(mode)}")
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
        // A rebind resets the camera control to its default zoom, so mirror that here.
        liveZoomRatio = capabilities.currentZoomRatio.coerceIn(
            capabilities.minZoomRatio,
            capabilities.maxZoomRatio,
        )
        liveLinearZoom = capabilities.currentLinearZoom
        // S0753: a rebind for a night toggle keeps the icon on; a lens without NIGHT clears it.
        nightModeEnabled = session.nightMode && capabilities.supportsNightMode
        macroEnabled = session.macroEnabled && capabilities.supportsMacro
        host.renderCapabilities(capabilities)
    }

    /** Toggles the torch when the active lens has a flash unit; returns the resulting state. */
    fun onFlashToggle(): Boolean {
        if (!currentCapabilities.hasFlashUnit) return false
        flashEnabled = !flashEnabled
        session.setTorchEnabled(flashEnabled)
        return flashEnabled
    }

    /** S0753: toggles the NIGHT extension when the active lens supports it; returns the resulting state. */
    fun onNightModeToggle(): Boolean {
        if (!currentCapabilities.supportsNightMode) return false
        nightModeEnabled = !nightModeEnabled
        session.applyNightMode(nightModeEnabled)
        return nightModeEnabled
    }

    /** S0753: toggles macro (close focus) when the active lens supports it; returns the resulting state. */
    fun onMacroToggle(): Boolean {
        if (!currentCapabilities.supportsMacro) return false
        macroEnabled = !macroEnabled
        session.applyMacro(macroEnabled)
        return macroEnabled
    }

    /** Flips to the next lens; capabilities (and control visibility) refresh via the bind callback. */
    fun onLensSwitch() {
        if (currentCapabilities.canSwitchLens) session.switchCamera()
    }

    fun onZoomRatioSelected(ratio: Float) {
        if (!currentCapabilities.supportsZoom) return
        // S0753: clamp to the digital-extended max so presets/pinch can reach 10/20/30 via soft zoom.
        val clamped = ratio.coerceIn(
            currentCapabilities.minZoomRatio,
            currentCapabilities.maxDisplayZoomRatio,
        )
        liveZoomRatio = clamped
        session.setZoomRatio(clamped)
        // Mirror the resulting linear position so a preset tap repositions the slider.
        liveLinearZoom = session.currentLinearZoom()
    }

    /**
     * S0753: slider-driven zoom in perceptually-linear (0..1) space. Sets linear zoom, then reads the
     * resulting ratio back so the preset highlight ([liveZoomRatio]) stays the single source of truth.
     */
    fun onLinearZoomSelected(linear: Float) {
        if (!currentCapabilities.supportsZoom) return
        val clampedLinear = linear.coerceIn(0f, 1f)
        session.setLinearZoom(clampedLinear)
        liveLinearZoom = clampedLinear
        liveZoomRatio = session.currentZoomRatio().coerceIn(
            currentCapabilities.minZoomRatio,
            currentCapabilities.maxZoomRatio,
        )
    }

    /** S0566: continuous pinch zoom - scales the live ratio by the detector factor, clamped to the lens range. */
    fun onPinchZoom(scaleFactor: Float) {
        if (!currentCapabilities.supportsZoom) return
        onZoomRatioSelected(liveZoomRatio * scaleFactor)
    }

    /**
     * S0566: double-tap zoom toggle. Jumps to the lens maximum (approximated by the largest zoom
     * preset, else [CameraRuntimeCapabilities.maxZoomRatio]) when near 1x, and back to 1x otherwise.
     * CameraX exposes no optical/digital boundary, so the largest preset is the closest stand-in.
     */
    fun onDoubleTapZoom() {
        if (!currentCapabilities.supportsZoom) return
        val caps = currentCapabilities
        val baseline = ZOOM_BASELINE.coerceIn(caps.minZoomRatio, caps.maxZoomRatio)
        val zoomedIn = caps.zoomPresets.lastOrNull()?.takeIf { it > baseline } ?: caps.maxZoomRatio
        val target = if (liveZoomRatio > baseline + ZOOM_TOGGLE_EPSILON) baseline else zoomedIn
        onZoomRatioSelected(target)
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

    fun setSelfTimerSeconds(seconds: Int) {
        selfTimerSeconds = seconds.coerceAtLeast(0)
    }

    fun setGridEnabled(enabled: Boolean) {
        gridEnabled = enabled
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

    private companion object {
        const val ZOOM_BASELINE = 1f
        const val ZOOM_TOGGLE_EPSILON = 0.1f
    }
}
