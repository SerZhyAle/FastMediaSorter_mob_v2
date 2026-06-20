package com.sza.fastmediasorter.ui.cameracapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureFlowManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale

/**
 * Thin host for the unified in-app camera. View binding, launcher registration and event
 * delegation only - capture decisions live in [CameraCaptureFlowManager], camera I/O in
 * [CameraCaptureSessionManager] (CLAUDE.md Rule 3). The capture mode is fixed by the caller via
 * [CameraCaptureContract], except for the "Camera" entry (S0563) which passes
 * [CameraCaptureContract.EXTRA_ALLOW_MODE_SWITCH] to expose the in-screen PHOTO|VIDEO switch.
 */
@AndroidEntryPoint
class CameraCaptureActivity : BaseActivity<ActivityCameraCaptureBinding>(),
    CameraCaptureFlowManager.Host {

    companion object {
        // Backward-compatible entry points; extras are owned by CameraCaptureContract.
        fun createIntent(context: Context, outputUri: Uri): Intent =
            CameraCaptureContract.createIntent(context, outputUri)

        fun createIntent(context: Context, outputUri: Uri, outputPath: String): Intent =
            CameraCaptureContract.createIntent(context, outputUri, outputPath)

        fun createIntent(
            context: Context,
            outputUri: Uri,
            outputPath: String,
            mode: CameraCaptureMode,
        ): Intent =
            CameraCaptureContract.createIntent(context, outputUri, outputPath, mode)
    }

    private lateinit var sessionManager: CameraCaptureSessionManager
    private lateinit var flowManager: CameraCaptureFlowManager
    private var captureInFlight = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> flowManager.onCameraPermissionResult(granted) }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // ADR-5: never record audio silently. On denial fall back to a muted recording and say so.
        if (!granted) showError(R.string.camera_capture_microphone_muted)
        startRecording(withAudio = granted && flowManager.microphoneEnabled)
    }

    override fun getViewBinding(): ActivityCameraCaptureBinding =
        ActivityCameraCaptureBinding.inflate(layoutInflater)

    override fun setupViews() {
        sessionManager = CameraCaptureSessionManager(this)
        flowManager = CameraCaptureFlowManager(intent, this, sessionManager)
        Timber.d("S0545: capture host entry mode=${flowManager.mode}")
        binding.cameraTopBar.applySystemBarInsetPadding(applyBottom = false)
        binding.cameraActionBar.applySystemBarInsetPadding(applyTop = false)

        if (!flowManager.resolveOutput()) return

        binding.btnCloseCamera.setOnClickListener { flowManager.onClose() }
        binding.btnCapturePhoto.isEnabled = false
        binding.btnCapturePhoto.setOnClickListener { onShutterClicked() }
        binding.btnCapturePhoto.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                binding.btnCapturePhoto.performClick()
                return@setOnKeyListener true
            }
            false
        }
        setupCameraControls()
        sessionManager.videoMode = flowManager.isVideoMode
        applyCaptureModeUi()
        setupModeSwitch()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showError(R.string.camera_capture_error_no_camera_app)
            finishCancelled()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        flowManager.ensurePermissionAndBind(hasPermission)
    }

    override fun observeData() = Unit

    override fun getInitialFocusView() = binding.btnCapturePhoto

    override fun onDestroy() {
        if (::sessionManager.isInitialized) {
            sessionManager.unbind()
        }
        super.onDestroy()
    }

    // region CameraCaptureFlowManager.Host

    override fun showError(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    override fun finishCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun finishWithResult(result: Intent) {
        setResult(RESULT_OK, result)
        finish()
    }

    override fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun bindCamera() {
        sessionManager.onCapabilitiesChanged = { flowManager.onCapabilitiesChanged(it) }
        sessionManager.bind(
            previewView = binding.previewViewCamera,
            onReady = { binding.btnCapturePhoto.isEnabled = true },
            onError = {
                showError(R.string.camera_capture_error_no_camera_app)
                finishCancelled()
            },
        )
    }

    override fun renderCapabilities(capabilities: CameraRuntimeCapabilities) {
        binding.btnCameraFlash.visibility = if (capabilities.hasFlashUnit) View.VISIBLE else View.GONE
        binding.btnCameraFlash.setIconResource(R.drawable.ic_camera_flash_off)
        binding.btnCameraLensSwitch.visibility =
            if (capabilities.canSwitchLens) View.VISIBLE else View.GONE

        if (!capabilities.supportsZoom) {
            binding.btnCameraMore.visibility = View.GONE
            binding.cameraZoomBar.visibility = View.GONE
            binding.cameraZoomPresetGroup.removeAllViews()
            return
        }
        // Zoom is a secondary control: kept behind the "more" toggle so the primary row stays compact
        // (strategic §3.4 overflow policy).
        binding.btnCameraMore.visibility = View.VISIBLE
        configureZoomControls(capabilities)
    }

    // endregion

    private fun setupCameraControls() {
        binding.btnCameraFlash.setOnClickListener {
            val enabled = flowManager.onFlashToggle()
            binding.btnCameraFlash.setIconResource(
                if (enabled) R.drawable.ic_camera_flash_on else R.drawable.ic_camera_flash_off,
            )
        }
        binding.btnCameraLensSwitch.setOnClickListener { flowManager.onLensSwitch() }
        binding.btnCameraMore.setOnClickListener {
            binding.cameraZoomBar.visibility =
                if (binding.cameraZoomBar.isVisible) View.GONE else View.VISIBLE
        }
        binding.cameraZoomSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) flowManager.onZoomRatioSelected(value)
        }
        binding.previewViewCamera.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
                if (flowManager.onTapToFocus(event.x, event.y)) {
                    binding.focusRingOverlay.showAt(event.x, event.y)
                }
            }
            true
        }
        binding.toggleCameraMicrophone.setOnClickListener {
            updateMicrophoneIcon(flowManager.onMicrophoneToggle())
        }
    }

    /**
     * Reflects the active capture mode in the controls. Idempotent and direction-agnostic so it can
     * run both at startup and on every in-screen mode switch (S0563): video shows the microphone
     * toggle and the record shutter, photo restores the still-capture shutter. Session mutation
     * (videoMode / rebind) is owned by the caller, not this method.
     */
    private fun applyCaptureModeUi() {
        if (flowManager.isVideoMode) {
            binding.toggleCameraMicrophone.visibility = View.VISIBLE
            updateMicrophoneIcon(flowManager.microphoneEnabled)
            updateShutterRecordingState(recording = false)
        } else {
            binding.toggleCameraMicrophone.visibility = View.GONE
            binding.btnCapturePhoto.contentDescription = getString(R.string.cmd_camera_capture)
            binding.btnCapturePhoto.tooltipText = getString(R.string.cmd_camera_capture)
            binding.btnCapturePhoto.backgroundTintList =
                ContextCompat.getColorStateList(this, android.R.color.white)
        }
    }

    /**
     * Wires the in-screen PHOTO|VIDEO switch (S0563), shown only for the "Camera" entry. A new
     * selection flips the flow-manager mode, rebinds the session with the matching CameraX use-case
     * set, and refreshes the controls.
     */
    private fun setupModeSwitch() {
        if (!flowManager.allowModeSwitch) {
            binding.cameraModeSwitchGroup.visibility = View.GONE
            return
        }
        binding.cameraModeSwitchGroup.visibility = View.VISIBLE
        binding.cameraModeSwitchGroup.check(
            if (flowManager.isVideoMode) binding.btnModeVideo.id else binding.btnModePhoto.id,
        )
        binding.cameraModeSwitchGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = if (checkedId == binding.btnModeVideo.id) {
                CameraCaptureMode.VIDEO
            } else {
                CameraCaptureMode.PHOTO
            }
            if (flowManager.switchMode(target)) {
                sessionManager.applyMode(videoMode = target == CameraCaptureMode.VIDEO)
                applyCaptureModeUi()
            }
        }
    }

    private fun onShutterClicked() {
        if (flowManager.isVideoMode) toggleRecording() else capturePhoto()
    }

    private fun toggleRecording() {
        if (sessionManager.isRecording()) {
            sessionManager.stopRecording()
            return
        }
        val needsAudioPermission = flowManager.microphoneEnabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startRecording(withAudio = flowManager.microphoneEnabled)
    }

    private fun startRecording(withAudio: Boolean) {
        val file = flowManager.outputFile ?: return
        Timber.d("S0545: video recording start withAudio=$withAudio")
        updateShutterRecordingState(recording = true)
        sessionManager.startRecording(file, withAudio) { hasError ->
            // Finalize callback can land after the user already left the screen.
            if (isFinishing || isDestroyed) return@startRecording
            updateShutterRecordingState(recording = false)
            flowManager.onRecordingFinalized(hasError)
        }
    }

    private fun updateShutterRecordingState(recording: Boolean) {
        val labelRes = if (recording) {
            R.string.camera_capture_record_stop
        } else {
            R.string.camera_capture_record_start
        }
        binding.btnCapturePhoto.contentDescription = getString(labelRes)
        binding.btnCapturePhoto.tooltipText = getString(labelRes)
        binding.btnCapturePhoto.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (recording) R.color.recording_active_tint else android.R.color.white,
        )
        // Mode rebuilds the camera pipeline, so block switching while a recording is in flight.
        binding.btnModePhoto.isEnabled = !recording
        binding.btnModeVideo.isEnabled = !recording
    }

    private fun updateMicrophoneIcon(enabled: Boolean) {
        binding.toggleCameraMicrophone.setIconResource(
            if (enabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
        )
    }

    private fun configureZoomControls(capabilities: CameraRuntimeCapabilities) {
        binding.cameraZoomSlider.apply {
            valueFrom = capabilities.minZoomRatio
            valueTo = capabilities.maxZoomRatio
            value = capabilities.currentZoomRatio.coerceIn(
                capabilities.minZoomRatio,
                capabilities.maxZoomRatio,
            )
        }
        val group = binding.cameraZoomPresetGroup
        group.removeAllViews()
        capabilities.zoomPresets.forEach { preset ->
            val chip = Chip(this).apply {
                text = formatZoomRatio(preset)
                isCheckable = true
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.camera_control_zoom)
                setOnClickListener {
                    flowManager.onZoomRatioSelected(preset)
                    binding.cameraZoomSlider.value = preset.coerceIn(
                        capabilities.minZoomRatio,
                        capabilities.maxZoomRatio,
                    )
                }
            }
            group.addView(chip)
        }
    }

    private fun formatZoomRatio(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}×" else String.format(Locale.US, "%.1f×", ratio)

    private fun capturePhoto() {
        val file = flowManager.outputFile ?: return
        if (captureInFlight) return
        captureInFlight = true
        binding.btnCapturePhoto.isEnabled = false

        sessionManager.capture(
            previewView = binding.previewViewCamera,
            outputFile = file,
            onSaved = {
                // CameraX delivers this asynchronously; bail out if the user already left the screen.
                if (isFinishing || isDestroyed) return@capture
                flowManager.onCaptureSucceeded()
            },
            onError = { error ->
                // Async CameraX callback can arrive after onDestroy released the binding (user closed
                // the camera while takePicture was still in flight) - touching binding then crashes.
                if (isFinishing || isDestroyed) return@capture
                captureInFlight = false
                binding.btnCapturePhoto.isEnabled = true
                Timber.e(error, "CameraCaptureActivity: capture failed")
                showError(R.string.camera_capture_error_save_generic)
            },
        )
    }
}
