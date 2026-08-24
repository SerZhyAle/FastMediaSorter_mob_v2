package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import com.sza.fastmediasorter.util.CaptureFileNamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * S0566: true for the general entry / widget. The host stays open after each capture and saves
     * every file itself; false keeps the legacy one-shot-then-finish contract for fixed callers.
     */
    val multiCapture: Boolean = CameraCaptureContract.readMultiCapture(intent)

    /**
     * S0790: fire the shutter automatically once the preview is ready. PHOTO mode takes one shot then
     * finishes; S0926: VIDEO mode auto-starts recording (edge-gesture "start video recording").
     */
    val autoCapture: Boolean = CameraCaptureContract.readAutoCapture(intent)

    /** S0566: live zoom ratio, kept in sync with preset taps, pinch and double-tap so the UI can reflect it. */
    var liveZoomRatio: Float = CameraRuntimeCapabilities.DEFAULT_ZOOM
        private set

    /** S0753: slider mirror of [liveZoomRatio] in 0..1 linear space; the two are kept in lockstep. */
    var liveLinearZoom: Float = 0f
        private set

    /** S1262: single owner of the active photo profile; the capture screen renders its menu from it. */
    private val profiles = CameraProfileApplyManager(SessionProfileActions())

    /** S1262: the profile the capture screen shows as active. NORMAL on every entry into photo mode. */
    val activeProfile: PhotoProfile get() = profiles.activeProfile

    /**
     * S1658: what each lens remembers. Seeded from settings by the host and handed back to it after
     * every change, which is what makes the sets survive a restart.
     */
    private var lensMemory = CameraLensSettingsMemory()

    /** S1658: the set the entered lens asked for, applied to the profile once the rebind reports back. */
    private var pendingRestoredProfile: PhotoProfile? = null

    /** S1658: invoked with the encoded memory whenever a lens's set changes, so the host can persist it. */
    var onLensMemoryChanged: ((String) -> Unit)? = null

    /**
     * S1658: until the host has read the stored sets back, nothing may be saved or pruned - a write
     * from a switch made before that read would persist an empty memory over the real one.
     */
    private var lensMemorySeeded = false

    private var lensMemoryPruned = false

    /** S1658: replaces the in-memory sets with the ones the host read back from settings. */
    fun seedLensMemory(encoded: String) {
        lensMemory = CameraLensSettingsMemory.decode(encoded)
        lensMemorySeeded = true
    }

    /** S1658: forgets every set saved for a lens the current enumeration no longer offers. */
    fun retainLensMemory(lensIds: Set<String>) {
        lensMemory.retainOnly(lensIds)
        onLensMemoryChanged?.invoke(lensMemory.encode())
    }

    /**
     * S1658: true while a profile is moving the lens itself (SELFIE and its undo). Such a move is the
     * profile's own business at both ends: the lens being left must not be recorded as carrying the
     * profile that caused the move, and the lens being entered must not have its own set restored
     * over that profile.
     */
    private var profileDrivenSwitch = false

    /** S1658: saves the set the lens being left was carrying. */
    fun onLensLeaving(lensId: String) {
        if (profileDrivenSwitch) return
        rememberFor(lensId)
    }

    /**
     * S1658: writes the entered lens's remembered set onto the session before its bind, and parks the
     * profile so [onCapabilitiesChanged] can mark it active once that bind has reported.
     */
    fun onLensEntering(lensId: String) {
        val saved = lensMemory.recall(lensId) ?: return
        session.restorePerLensState(saved)
        pendingRestoredProfile = saved.profile
    }

    private fun rememberFor(lensId: String) {
        if (!lensMemorySeeded) return
        lensMemory.remember(lensId, currentLensSettings())
        onLensMemoryChanged?.invoke(lensMemory.encode())
    }

    private fun currentLensSettings(): CameraLensSettingsMemory.LensSettings =
        CameraLensSettingsMemory.LensSettings(
            profile = profiles.activeProfile,
            whiteBalanceMode = session.currentWhiteBalanceMode,
            manualIso = session.currentManualIso,
            manualShutterNs = session.currentManualShutterNs,
            exposureCompensationIndex = session.currentExposureCompensationIndex,
        )

    /** S0754: UI-only self-timer delay, applied by the host before shutter/record start. */
    var selfTimerSeconds: Int = 0
        private set

    /** S0754: UI-only preview grid toggle; the host shows/hides the overlay. */
    var gridEnabled: Boolean = false
        private set

    /** S0566: monotonically increasing per-capture suffix so a multi-capture session never overwrites a file. */
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

    /**
     * Validates the output target upfront. Returns false (after signalling the host) when it cannot.
     *
     * S1579: suspend because the scratch-dir probe below is filesystem work that used to run on the
     * main thread while the capture screen was still being assembled. The host resumes on its own
     * dispatcher, so the failure path still signals the UI from the main thread.
     */
    suspend fun resolveOutput(): Boolean {
        val dir = outputDir
        val ready = if (dir != null && outputBaseName != null) {
            // Switchable mode: the host owns the file, so only the scratch dir must be writable here.
            withContext(Dispatchers.IO) {
                runCatching { File(dir).apply { mkdirs() }.isDirectory }.getOrDefault(false)
            }
        } else {
            // The legacy target comes straight out of the intent - no filesystem access to move off.
            resolveLegacyOutputFile() != null
        }
        if (!ready) {
            host.showError(R.string.camera_capture_error_save_generic)
            host.finishCancelled()
        }
        return ready
    }

    /**
     * Switches the active capture mode for the "Camera" entry (S0563). No-op (returns false) for
     * fixed-mode callers or when the target equals the current mode; the Activity rebinds the session
     * only when this returns true.
     */
    fun switchMode(target: CameraCaptureMode): Boolean {
        if (!allowModeSwitch || target == mode) return false
        mode = target
        // S1262: profiles are photo-only. The session drops their intents itself when it takes the
        // video pipeline, so the state is released rather than un-applied (ADR-2).
        if (target == CameraCaptureMode.VIDEO) profiles.releaseWithoutClearing("video mode")
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
        val kind = if (mode == CameraCaptureMode.VIDEO) {
            CaptureFileNamer.CaptureKind.VIDEO
        } else {
            CaptureFileNamer.CaptureKind.PHOTO
        }
        val fileName = CaptureFileNamer.shared.allocate(kind, extensionFor(mode))
        return File(dir, fileName)
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
        // S1658: once the enumeration is known, forget the sets of lenses this device no longer
        // offers. Once per session: the offered set only changes when a lens refuses to bind, and
        // that lens is dropped from the memory by the next save anyway.
        if (lensMemorySeeded && !lensMemoryPruned) {
            lensMemoryPruned = true
            retainLensMemory(session.offeredLensIds)
        }
        // S1658: the entered lens's remembered profile becomes active now that its bind has reported.
        // The session wrote the matching intents before that bind, so this only syncs the menu.
        pendingRestoredProfile?.let { profiles.restore(it) }
        pendingRestoredProfile = null
        // S1262: the new lens may not honour the active profile; drop it before the UI renders the
        // menu, so a ticked entry never survives onto optics that cannot deliver it - which also
        // covers a restored profile the entered optics turn out not to support.
        profiles.reconcile(capabilities)
        host.renderCapabilities(capabilities)
    }

    /** Toggles the torch when the active lens has a flash unit; returns the resulting state. */
    fun onFlashToggle(): Boolean {
        if (!currentCapabilities.hasFlashUnit) return false
        flashEnabled = !flashEnabled
        session.setTorchEnabled(flashEnabled)
        return flashEnabled
    }

    /** S1262: menu contents for the bound lens - the UI renders from this instead of re-deriving it. */
    fun availableProfiles(): List<PhotoProfile> = profiles.availableProfiles(currentCapabilities)

    /** S1262: applies a menu choice; re-picking the active entry returns the camera to NORMAL. */
    fun onProfileSelected(profile: PhotoProfile) {
        profiles.apply(profile)
        // S1658: an explicit choice outranks whatever the lens remembered and becomes its new stored
        // value. Read after apply, because a profile that moves the lens (SELFIE) stores against the
        // lens it landed on.
        session.boundLensId?.let(::rememberFor)
    }

    /** Flips to the next lens; capabilities (and control visibility) refresh via the bind callback. */
    fun onLensSwitch() {
        if (!currentCapabilities.canSwitchLens) return
        session.switchCamera()
    }

    /**
     * S1261: cross-lens floor pill tap - switch to the lens that reaches the device-wide floor and
     * land the zoom on it in one action, like the system camera's wide button. Live values are
     * re-read after the switch because the rebind reset them before the zoom landed.
     */
    fun onCrossLensFloorSelected(equivalent: Float) {
        // Two callers own a pill that lands here: the cross-lens floor pill on a lens that shows one
        // (S1261), and the rear-lens pills S1675 puts on a lens with no range of its own - where
        // showsCrossLensFloor is false by its own definition, so guarding on it alone would make every
        // tap from that row a no-op. The front camera owns neither pill.
        val fromLensPillRow = !currentCapabilities.supportsZoom
        if (currentCapabilities.isFront) return
        if (!currentCapabilities.showsCrossLensFloor && !fromLensPillRow) return
        session.switchCamera(targetEquivalentFloor = equivalent)
        liveZoomRatio = session.currentZoomRatio()
        liveLinearZoom = session.currentLinearZoom()
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

    /**
     * Runs tap-to-focus when supported; returns whether the focus ring should be shown.
     *
     * S1419: the answer now comes from the session rather than being a constant. The capability check
     * below is one of several reasons a focus request never leaves - the session knows the rest, and
     * a ring drawn for a request nobody made is what made this look like a broken autofocus.
     */
    fun onTapToFocus(x: Float, y: Float): Boolean {
        if (!currentCapabilities.supportsTapToFocus) return false
        return session.startFocusAndMetering(x, y)
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

    /**
     * S1262: the session primitives a profile recipe is built from.
     *
     * Every adapter is idempotent against the session: a clear sweep for an intent the session never
     * held must stay a no-op, or leaving a profile would rebind the camera for nothing and, in the
     * macro case, drag the lens back to wherever the macro primitive last remembered.
     */
    private inner class SessionProfileActions : CameraProfileApplyManager.Actions {

        override fun setNightMode(enabled: Boolean) {
            if (session.nightMode == enabled) return
            session.applyNightMode(enabled)
        }

        override fun setBokeh(enabled: Boolean) {
            session.applyProfileIntents(bokeh = enabled, sport = session.sportEnabled)
        }

        override fun setSport(enabled: Boolean) {
            session.applyProfileIntents(bokeh = session.bokehEnabled, sport = enabled)
        }

        override fun setMacro(enabled: Boolean) {
            if (session.macroEnabled == enabled) return
            session.applyMacro(enabled)
        }

        override fun switchToFrontLens() {
            // S1658: profile-driven, so neither end of the move touches the memory - the front lens's
            // own set would otherwise overwrite the SELFIE the user just picked.
            profileDrivenSwitch = true
            session.switchToFacing(CameraSelector.LENS_FACING_FRONT, restoreSaved = false)
            profileDrivenSwitch = false
        }

        override fun switchToMainBackLens() {
            // S1658: the profile is undoing its own lens move, so the same rule applies.
            profileDrivenSwitch = true
            session.switchToFacing(CameraSelector.LENS_FACING_BACK, restoreSaved = false)
            profileDrivenSwitch = false
        }
    }

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
