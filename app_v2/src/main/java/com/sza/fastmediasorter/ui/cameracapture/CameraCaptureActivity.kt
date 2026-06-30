package com.sza.fastmediasorter.ui.cameracapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.hardware.camera2.CameraMetadata
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureFlowManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOrientationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraRecordingTimer
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * Thin host for the unified in-app camera. View binding, launcher registration and event delegation
 * only - capture decisions live in [CameraCaptureFlowManager], camera I/O in
 * [CameraCaptureSessionManager], gesture math in [CameraCaptureGestureManager] (CLAUDE.md Rule 3).
 *
 * S0566 re-aligns the UI to a One UI camera (text mode tabs, always-visible zoom pills, a dynamic
 * shutter, viewfinder gestures, a recording timer + pause/resume) and adds a stay-open multi-capture
 * mode for the general "Camera" entry: when [CameraCaptureFlowManager.multiCapture] is set the host
 * saves each capture itself and stays open, refreshing the gallery thumbnail; fixed callers keep the
 * one-shot-then-finish contract.
 */
@AndroidEntryPoint
class CameraCaptureActivity : BaseActivity<ActivityCameraCaptureBinding>(),
    CameraCaptureFlowManager.Host,
    CameraCaptureGestureManager.Callbacks,
    CameraSettingsDialogFragment.Callbacks,
    SelfManagedScreenOrientation {

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

        private const val TAB_SELECTED_ALPHA = 1f
        private const val TAB_UNSELECTED_ALPHA = 0.5f
        private const val ZOOM_PILL_MATCH_EPSILON = 0.15f

        // S0753: lens-multiplier thresholds for naming the active lens.
        private const val ULTRA_WIDE_MAX_MULTIPLIER = 0.8f
        private const val TELE_MIN_MULTIPLIER = 1.5f

        // S0753: compact, see-through zoom preset pills.
        private const val CHIP_SIDE_PADDING_DP = 10f
        private const val CHIP_VERT_PADDING_DP = 4f
        private const val CHIP_SPACING_DP = 6f
        private const val CHIP_TEXT_SP = 11f
    }

    // S0566: host-side persistence for the stay-open multi-capture session. Reuses the shared
    // use-case (S0568) so the main entry, the widget and this host save captures identically.
    @Inject
    lateinit var saveCapturedMedia: SaveCapturedMediaUseCase

    // S0766: read the opt-in geotag flag; the host owns the location source so every photo path
    // (including widget launches that route through this activity) inherits geotagging.
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var resourceRepository: ResourceRepository

    @Inject
    lateinit var sendToMenuManager: SendToMenuManager

    private val locationProvider = CameraLocationProvider()
    private var geotagEnabled = false

    private lateinit var sessionManager: CameraCaptureSessionManager
    private lateinit var flowManager: CameraCaptureFlowManager
    private lateinit var gestureManager: CameraCaptureGestureManager
    private lateinit var recordingTimer: CameraRecordingTimer
    private lateinit var orientationManager: CameraOrientationManager

    private var captureInFlight = false
    private var recordingPaused = false
    private var recordingFile: File? = null
    private var lastSavedPath: String? = null
    private var lastSavedMediaType: MediaType? = null
    private var currentOverlayRotation = 0f
    private var countdownJob: Job? = null

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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        sessionManager = CameraCaptureSessionManager(this)
        flowManager = CameraCaptureFlowManager(intent, this, sessionManager)
        recordingTimer = CameraRecordingTimer { formatted -> binding.txtRecordingTimer.text = formatted }
        orientationManager = CameraOrientationManager(
            context = this,
            onIconRotationChanged = ::applyOverlayRotation,
            onTargetRotationChanged = { sessionManager.setTargetRotation(it) },
        )
        binding.cameraTopBar.applySystemBarInsetPadding(applyBottom = false)
        binding.cameraActionBar.applySystemBarInsetPadding(applyTop = false)

        if (!flowManager.resolveOutput()) return

        binding.btnCloseCamera.setOnClickListener { flowManager.onClose() }
        binding.btnCameraSettings.setOnClickListener { showCameraSettingsDialog() }
        binding.btnCameraSendTo.setOnClickListener { openSendToMenu() }
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
        setupModeSelector()
        refreshSaveDestinationLabel()
        updateSendToVisibility()
        renderGridOverlay()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showError(R.string.camera_capture_error_no_camera_app)
            finishCancelled()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        flowManager.ensurePermissionAndBind(hasPermission)

        // S0766: warm the location source as early as possible (only when opted in + permission held),
        // so a fix is ready by the first shutter; consent is taken in settings, never re-prompted here.
        lifecycleScope.launch {
            geotagEnabled = settingsRepository.getSettings().first().cameraGeotagEnabled
            if (geotagEnabled && hasLocationPermission()) locationProvider.start(this@CameraCaptureActivity)
        }
    }

    /** S0766: true when fine OR coarse location is granted; geotag silently skips otherwise. */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun observeData() = Unit

    override fun getInitialFocusView() = binding.btnCapturePhoto

    // S0801: orientationManager is a lateinit built in setupViews(), which BaseActivity defers to
    // binding.root.post {} for a fast first frame - so raw onResume() can fire before it exists. Use
    // the onResumeWithViews() hook, which is guaranteed to run only after setupViews() completes. The
    // OCR capture entry resumes the host inside that window; the previous raw onResume() override
    // crashed on the uninitialised manager.
    override fun onResumeWithViews() {
        Timber.d("S0801: camera onResumeWithViews after deferred setup; orientationManager ready")
        orientationManager.enable()
    }

    override fun onPause() {
        // S0801: an early pause (before the deferred setupViews() ran) leaves orientationManager
        // uninitialised; its symmetric enable() never fired either, so the disable is a safe skip.
        if (::orientationManager.isInitialized) orientationManager.disable()
        cancelCountdown()
        super.onPause()
    }

    override fun onDestroy() {
        cancelCountdown()
        // S0801: same deferred-setup race - recordingTimer may not exist yet on an early destroy, so
        // the shutdown path must not require a completed start.
        if (::recordingTimer.isInitialized) recordingTimer.stop()
        // S0766: symmetric with the warm-up start() in setupViews; releases the location listener.
        locationProvider.stop()
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
        binding.btnCameraSettings.visibility = View.VISIBLE
        binding.btnCameraLensSwitch.visibility =
            if (capabilities.canSwitchLens) View.VISIBLE else View.GONE
        // S0753: name the active lens next to the switch so the user knows why flash hides on ultra-wide.
        binding.cameraLensLabel.visibility =
            if (capabilities.canSwitchLens) View.VISIBLE else View.GONE
        binding.cameraLensLabel.text = lensLabel(capabilities)
        // S0753: night mode is photo-only and device-gated; hidden where the lens has no NIGHT extension.
        binding.btnCameraNight.visibility =
            if (capabilities.supportsNightMode && !flowManager.isVideoMode) View.VISIBLE else View.GONE
        binding.btnCameraNight.setIconResource(
            if (flowManager.nightModeEnabled) R.drawable.ic_camera_night_on else R.drawable.ic_camera_night_off,
        )
        binding.btnCameraMacro.visibility =
            if (capabilities.supportsMacro && !flowManager.isVideoMode) View.VISIBLE else View.GONE
        binding.btnCameraMacro.setIconResource(
            if (flowManager.macroEnabled) R.drawable.ic_camera_macro_on else R.drawable.ic_camera_macro_off,
        )

        // S0566/ADR-5: zoom presets are always visible (no "More" toggle) whenever the lens can zoom.
        if (capabilities.supportsZoom) {
            binding.cameraZoomPresetGroup.visibility = View.VISIBLE
            configureZoomControls(capabilities)
            binding.cameraZoomSlider.visibility = View.VISIBLE
            binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)
            binding.cameraZoomValue.visibility = View.VISIBLE
        } else {
            binding.cameraZoomPresetGroup.visibility = View.GONE
            binding.cameraZoomPresetGroup.removeAllViews()
            binding.cameraZoomSlider.visibility = View.GONE
            binding.cameraZoomValue.visibility = View.GONE
        }
        applyOverlayRotation(currentOverlayRotation, animate = false)
    }

    // endregion

    // region CameraCaptureGestureManager.Callbacks

    override fun onTapToFocus(x: Float, y: Float) {
        if (flowManager.onTapToFocus(x, y)) binding.focusRingOverlay.showAt(x, y)
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
        flowManager.onLensSwitch()
    }

    override fun onSwipeModeSwitch(toNext: Boolean) {
        if (!flowManager.allowModeSwitch || sessionManager.isRecording()) return
        selectMode(if (flowManager.isVideoMode) CameraCaptureMode.PHOTO else CameraCaptureMode.VIDEO)
    }

    // endregion

    private fun setupCameraControls() {
        binding.btnCameraFlash.setOnClickListener {
            val enabled = flowManager.onFlashToggle()
            binding.btnCameraFlash.setIconResource(
                if (enabled) R.drawable.ic_camera_flash_on else R.drawable.ic_camera_flash_off,
            )
        }
        binding.btnCameraNight.setOnClickListener {
            val on = flowManager.onNightModeToggle()
            binding.btnCameraNight.setIconResource(
                if (on) R.drawable.ic_camera_night_on else R.drawable.ic_camera_night_off,
            )
        }
        binding.btnCameraMacro.setOnClickListener {
            val on = flowManager.onMacroToggle()
            binding.btnCameraMacro.setIconResource(
                if (on) R.drawable.ic_camera_macro_on else R.drawable.ic_camera_macro_off,
            )
        }
        binding.btnCameraLensSwitch.setOnClickListener { flowManager.onLensSwitch() }
        binding.btnCameraPauseResume.setOnClickListener { onPauseResumeClicked() }
        binding.btnGalleryThumbnail.setOnClickListener { openLastCapture() }
        binding.toggleCameraMicrophone.setOnClickListener {
            updateMicrophoneIcon(flowManager.onMicrophoneToggle())
        }
        binding.cameraZoomSlider.addOnChangeListener { _, value, fromUser ->
            // fromUser guard stops the programmatic value sync below from looping back here.
            if (fromUser) {
                flowManager.onLinearZoomSelected(value)
                syncZoomSelection()
            }
        }
        gestureManager = CameraCaptureGestureManager(binding.previewViewCamera, this)
        gestureManager.attach()
    }

    /**
     * Reflects the active capture mode in the controls. Idempotent and direction-agnostic so it can
     * run both at startup and on every in-screen mode switch (S0563): video shows the microphone
     * toggle, photo hides it. Session mutation (videoMode / rebind) is owned by the caller, not here.
     */
    private fun applyCaptureModeUi() {
        if (flowManager.isVideoMode) {
            binding.toggleCameraMicrophone.visibility = View.VISIBLE
            updateMicrophoneIcon(flowManager.microphoneEnabled)
            // S0753: NIGHT and macro are photo-only, so the toggles never show in video mode.
            binding.btnCameraNight.visibility = View.GONE
            binding.btnCameraMacro.visibility = View.GONE
        } else {
            binding.toggleCameraMicrophone.visibility = View.GONE
        }
        refreshSaveDestinationLabel()
        applyShutterAppearance(recording = false)
    }

    /**
     * S0566: One UI text mode selector (PHOTO / VIDEO), shown only for the switchable "Camera" entry.
     * A tap flips the flow-manager mode, rebinds the session with the matching CameraX use-case set,
     * and refreshes the controls; a horizontal swipe does the same via [onSwipeModeSwitch].
     */
    private fun setupModeSelector() {
        if (!flowManager.allowModeSwitch) {
            binding.cameraModeSelector.visibility = View.GONE
            return
        }
        binding.cameraModeSelector.visibility = View.VISIBLE
        binding.tabModePhoto.setOnClickListener { selectMode(CameraCaptureMode.PHOTO) }
        binding.tabModeVideo.setOnClickListener { selectMode(CameraCaptureMode.VIDEO) }
        renderModeTabs()
    }

    private fun selectMode(target: CameraCaptureMode) {
        if (sessionManager.isRecording()) return
        if (flowManager.switchMode(target)) {
            sessionManager.applyMode(videoMode = target == CameraCaptureMode.VIDEO)
            applyCaptureModeUi()
            renderModeTabs()
            refreshSaveDestinationLabel()
        }
    }

    private fun renderModeTabs() {
        val videoActive = flowManager.isVideoMode
        styleTab(binding.tabModePhoto, selected = !videoActive)
        styleTab(binding.tabModeVideo, selected = videoActive)
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.alpha = if (selected) TAB_SELECTED_ALPHA else TAB_UNSELECTED_ALPHA
        tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun onShutterClicked() {
        if (sessionManager.isRecording()) {
            toggleRecording()
            return
        }
        if (captureInFlight || countdownJob != null) return
        val selfTimerSeconds = flowManager.selfTimerSeconds
        if (selfTimerSeconds > 0) {
            startSelfTimer(selfTimerSeconds) { triggerCapture() }
        } else {
            triggerCapture()
        }
    }

    private fun triggerCapture() {
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
        val file = flowManager.nextOutputFile() ?: return
        recordingFile = file
        updateShutterRecordingState(recording = true)
        sessionManager.startRecording(file, withAudio) { hasError ->
            // Finalize callback can land after the user already left the screen.
            if (isFinishing || isDestroyed) return@startRecording
            updateShutterRecordingState(recording = false)
            if (flowManager.multiCapture) {
                if (hasError) {
                    showError(R.string.camera_capture_error_save_generic)
                } else {
                    recordingFile?.let { persistMultiCapture(it, isVideo = true) }
                }
                recordingFile = null
            } else {
                flowManager.onRecordingFinalized(hasError)
            }
        }
    }

    private fun onPauseResumeClicked() {
        if (!sessionManager.isRecording()) return
        recordingPaused = !recordingPaused
        if (recordingPaused) {
            sessionManager.pauseRecording()
            recordingTimer.pause()
        } else {
            sessionManager.resumeRecording()
            recordingTimer.resume()
        }
        updatePauseResumeIcon()
    }

    private fun updatePauseResumeIcon() {
        binding.btnCameraPauseResume.setIconResource(
            if (recordingPaused) R.drawable.ic_play else R.drawable.ic_pause,
        )
        val label = if (recordingPaused) R.string.camera_control_resume else R.string.camera_control_pause
        binding.btnCameraPauseResume.contentDescription = getString(label)
        binding.btnCameraPauseResume.tooltipText = getString(label)
    }

    /**
     * Swaps the shutter foreground per mode and recording state (S0566/ADR-? dynamic shutter): photo
     * shows a white ring, video-idle a red dot, video-recording a red square. The button stays a white
     * disc so only the inner glyph and its tint change.
     */
    private fun applyShutterAppearance(recording: Boolean) {
        val iconRes: Int
        val tintRes: Int
        val labelRes: Int
        when {
            !flowManager.isVideoMode -> {
                iconRes = R.drawable.ic_shutter_photo
                tintRes = R.color.camera_capture_shutter_icon
                labelRes = R.string.cmd_camera_capture
            }
            recording -> {
                iconRes = R.drawable.ic_shutter_video_recording
                tintRes = R.color.recording_active_tint
                labelRes = R.string.camera_capture_record_stop
            }
            else -> {
                iconRes = R.drawable.ic_shutter_video_idle
                tintRes = R.color.recording_active_tint
                labelRes = R.string.camera_capture_record_start
            }
        }
        binding.btnCapturePhoto.setIconResource(iconRes)
        binding.btnCapturePhoto.iconTint = ContextCompat.getColorStateList(this, tintRes)
        binding.btnCapturePhoto.contentDescription = getString(labelRes)
        binding.btnCapturePhoto.tooltipText = getString(labelRes)
    }

    private fun updateShutterRecordingState(recording: Boolean) {
        applyShutterAppearance(recording)
        // Mode rebuilds the camera pipeline, so block switching while a recording is in flight.
        binding.tabModePhoto.isEnabled = !recording
        binding.tabModeVideo.isEnabled = !recording
        binding.btnCameraPauseResume.visibility = if (recording) View.VISIBLE else View.GONE
        if (recording) {
            recordingPaused = false
            updatePauseResumeIcon()
            // The thumbnail and recording controls share the start slot; hide it while recording.
            binding.btnGalleryThumbnail.visibility = View.GONE
            recordingTimer.start()
            binding.cameraRecordingTimer.visibility = View.VISIBLE
        } else {
            recordingTimer.stop()
            binding.cameraRecordingTimer.visibility = View.GONE
            if (flowManager.multiCapture && lastSavedPath != null) {
                binding.btnGalleryThumbnail.visibility = View.VISIBLE
            }
        }
        updateSendToVisibility()
    }

    private fun updateMicrophoneIcon(enabled: Boolean) {
        binding.toggleCameraMicrophone.setIconResource(
            if (enabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
        )
    }

    private fun configureZoomControls(capabilities: CameraRuntimeCapabilities) {
        val group = binding.cameraZoomPresetGroup
        group.removeAllViews()
        val density = resources.displayMetrics.density
        val padH = (density * CHIP_SIDE_PADDING_DP).toInt()
        val padV = (density * CHIP_VERT_PADDING_DP).toInt()
        val spacing = (density * CHIP_SPACING_DP).toInt()
        capabilities.zoomPresets.forEach { preset ->
            // Custom see-through pill (the Material chip kept drawing an opaque light surface). The label
            // is the equivalent zoom (native ratio x lens multiplier), so an ultra-wide reads 0.5x.
            val pill = OutlinedTextView(this).apply {
                text = formatZoomLabel(preset * capabilities.zoomMultiplier)
                tag = preset
                contentDescription = getString(R.string.camera_control_zoom)
                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SP)
                setPadding(padH, padV, padH, padV)
                background = ContextCompat.getDrawable(context, R.drawable.bg_camera_zoom_chip)
                setOnClickListener {
                    flowManager.onZoomRatioSelected(preset)
                    syncZoomSelection()
                }
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = spacing }
            group.addView(pill, lp)
        }
        syncZoomSelection()
    }

    /** Highlights the preset pill nearest the live zoom ratio; clears all when pinched between steps. */
    private fun syncZoomSelection() {
        val group = binding.cameraZoomPresetGroup
        val live = flowManager.liveZoomRatio
        var best: View? = null
        var bestDelta = Float.MAX_VALUE
        group.children.forEach { view ->
            view.isSelected = false
            val preset = view.tag as? Float ?: return@forEach
            val delta = abs(preset - live)
            if (delta < bestDelta) {
                bestDelta = delta
                best = view
            }
        }
        if (bestDelta < ZOOM_PILL_MATCH_EPSILON) best?.isSelected = true
        // Mirror the live linear position onto the slider (fromUser=false, so the listener no-ops).
        binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)
        // S0753: live equivalent-zoom readout next to the slider.
        binding.cameraZoomValue.text =
            formatZoomRatio(flowManager.liveZoomRatio * flowManager.currentCapabilities.zoomMultiplier)
    }

    private fun formatZoomRatio(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}×" else String.format(Locale.US, "%.1f×", ratio)

    /** Zoom label without the "x" suffix so the preset pills stay narrow (S0753), e.g. "1", "0.5". */
    private fun formatZoomLabel(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}" else String.format(Locale.US, "%.1f", ratio)

    /** Active-lens name next to the switch button (Ultra-wide / Wide / Tele / Front), by lens multiplier. */
    private fun lensLabel(capabilities: CameraRuntimeCapabilities): String = when {
        capabilities.isFront -> getString(R.string.camera_lens_front)
        capabilities.zoomMultiplier < ULTRA_WIDE_MAX_MULTIPLIER -> getString(R.string.camera_lens_ultrawide)
        capabilities.zoomMultiplier > TELE_MIN_MULTIPLIER -> getString(R.string.camera_lens_tele)
        else -> getString(R.string.camera_lens_wide)
    }

    private fun capturePhoto() {
        val file = flowManager.nextOutputFile() ?: return
        if (captureInFlight) return
        captureInFlight = true
        binding.btnCapturePhoto.isEnabled = false

        // S0766: stamp the freshest warmed fix only when opted in + permission held; null = no GPS.
        val location = if (geotagEnabled && hasLocationPermission()) {
            locationProvider.lastKnownLocation()
        } else {
            null
        }
        Timber.d("S0766: geotag enabled=%s perm=%s loc=%s", geotagEnabled, hasLocationPermission(), location != null)

        sessionManager.capture(
            previewView = binding.previewViewCamera,
            outputFile = file,
            location = location,
            onSaved = {
                // CameraX delivers this asynchronously; bail out if the user already left the screen.
                if (isFinishing || isDestroyed) return@capture
                if (flowManager.multiCapture) {
                    // Stay-open session: save this shot, refresh the thumbnail, keep the camera live.
                    captureInFlight = false
                    binding.btnCapturePhoto.isEnabled = true
                    persistMultiCapture(file, isVideo = false)
                } else {
                    flowManager.onCaptureSucceeded()
                }
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

    /**
     * S0566/ADR-2: persist one capture of a stay-open session to its public folder (photo ->
     * DCIM/Camera, video -> Movies) through the shared use-case, then surface the result as the
     * gallery thumbnail. The saver deletes the scratch file; the camera is never finished here.
     */
    private fun persistMultiCapture(file: File, isVideo: Boolean) {
        val name = file.name
        lifecycleScope.launch {
            val result = saveCapturedMedia(file, isVideo)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is SaveResult.Success -> {
                    lastSavedPath = result.savedPath
                    lastSavedMediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
                    showGalleryThumbnail(result.savedPath)
                    updateSendToVisibility()
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        getString(R.string.camera_capture_saved, name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    // The saver only deletes the scratch file on success, so drop the failed shot's
                    // scratch copy here to avoid leaving CAP_<stamp>_<seq> orphans in the session dir.
                    file.delete()
                    showError(R.string.camera_capture_error_save_generic)
                }
            }
        }
    }

    private fun showGalleryThumbnail(path: String) {
        binding.btnGalleryThumbnail.visibility = View.VISIBLE
        Glide.with(this).load(File(path)).centerCrop().into(binding.btnGalleryThumbnail)
    }

    private fun updateSendToVisibility() {
        binding.btnCameraSendTo.visibility = if (!sessionManager.isRecording() && lastSavedPath != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun startSelfTimer(seconds: Int, onFinish: () -> Unit) {
        cancelCountdown()
        binding.btnCapturePhoto.isEnabled = false
        countdownJob = lifecycleScope.launch {
            binding.cameraCountdownOverlay.visibility = View.VISIBLE
            for (remaining in seconds downTo 1) {
                binding.cameraCountdownOverlay.text = remaining.toString()
                delay(1_000)
            }
            binding.cameraCountdownOverlay.visibility = View.GONE
            countdownJob = null
            if (!isFinishing && !isDestroyed) onFinish()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        binding.cameraCountdownOverlay.visibility = View.GONE
        binding.btnCapturePhoto.isEnabled = !captureInFlight
    }

    private fun applyOverlayRotation(degrees: Float, animate: Boolean = true) {
        currentOverlayRotation = degrees
        orientationAwareViews().forEach { view ->
            if (animate) {
                view.animate()
                    .rotation(degrees)
                    .setDuration(180L)
                    .start()
            } else {
                view.animate().cancel()
                view.rotation = degrees
            }
        }
    }

    private fun orientationAwareViews(): List<View> = listOf(
        binding.btnCloseCamera,
        binding.cameraSaveDestination,
        binding.btnCameraSettings,
        binding.toggleCameraMicrophone,
        binding.btnCameraFlash,
        binding.btnCameraNight,
        binding.btnCameraMacro,
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
    )

    private fun showCameraSettingsDialog() {
        if (supportFragmentManager.findFragmentByTag(CameraSettingsDialogFragment.TAG) != null) return
        CameraSettingsDialogFragment().apply {
            callbacks = this@CameraCaptureActivity
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
        }.show(supportFragmentManager, CameraSettingsDialogFragment.TAG)
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
        Timber.d("S0754: applying camera settings dialog state")
        onCameraSettingsPreviewChanged(state)
        flowManager.setSelfTimerSeconds(state.selfTimerSeconds)
        flowManager.setGridEnabled(state.gridEnabled)
        renderGridOverlay()
        sessionManager.setAspectRatioAndResolution(state.aspectRatio, state.resolution)
    }

    override fun onCameraSettingsCancelled(state: CameraSettingsDialogFragment.CameraSettingsState) {
        onCameraSettingsPreviewChanged(state)
    }

    private fun renderGridOverlay() {
        binding.cameraGridOverlay.visibility = if (flowManager.gridEnabled) View.VISIBLE else View.GONE
    }

    private fun openSendToMenu() {
        val path = lastSavedPath ?: return
        lifecycleScope.launch {
            val file = File(path)
            if (!file.exists()) return@launch
            val uri = runCatching {
                FileProvider.getUriForFile(this@CameraCaptureActivity, "$packageName.fileprovider", file)
            }.getOrNull() ?: return@launch
            val settings = settingsRepository.getSettings().first()
            val mediaType = lastSavedMediaType ?: inferMediaType(file)
            val content = ShareableContent(
                uris = listOf(uri),
                mime = ShareableContent.mimeForMediaType(file.name, mediaType),
                mediaType = mediaType,
                displayName = file.name,
            )
            sendToMenuManager.show(this@CameraCaptureActivity, content, settings)
        }
    }

    private fun inferMediaType(file: File): MediaType {
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            "mp4", "mkv", "webm" -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    }

    private fun refreshSaveDestinationLabel() {
        lifecycleScope.launch {
            val destinationName = resolveSaveDestinationName()
            if (destinationName.isNullOrBlank()) {
                binding.cameraSaveDestination.visibility = View.GONE
            } else {
                binding.cameraSaveDestination.text = destinationName
                binding.cameraSaveDestination.visibility = View.VISIBLE
                applyOverlayRotation(currentOverlayRotation, animate = false)
            }
        }
    }

    private suspend fun resolveSaveDestinationName(): String? {
        val fixedOutputParent = flowManager.currentOutputFile()?.parentFile?.name
        if (!flowManager.multiCapture) return fixedOutputParent
        val settings = settingsRepository.getSettings().first()
        val configuredId = if (flowManager.isVideoMode) {
            settings.videoRecordingDestinationResourceId
        } else {
            settings.cameraPhotosDestinationResourceId
        }
        val configuredName = configuredId?.toLongOrNull()?.let { id ->
            withContext(Dispatchers.IO) { resourceRepository.getResourceById(id)?.name }
        }
        if (!configuredName.isNullOrBlank()) return configuredName
        return if (flowManager.isVideoMode) {
            CaptureDestinationPolicy.resolveVideoDestination(null).name
        } else {
            CaptureDestinationPolicy.resolveCameraDestination(null).name
        }
    }

    /**
     * S0566/§6.7: opens the most recent capture in the in-app player. Routes through
     * [StandalonePlayerDispatcherActivity] (resolves the media family from the URI and forwards to the
     * matching standalone host) instead of an implicit ACTION_VIEW the OS would hand to an external
     * gallery, keeping the user inside the app. A missing/unviewable file is logged, not fatal.
     */
    private fun openLastCapture() {
        val path = lastSavedPath ?: return
        val file = File(path)
        if (!file.exists()) return
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull() ?: return
        val intent = Intent(this, StandalonePlayerDispatcherActivity::class.java)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }
            .onFailure { Timber.w(it, "CameraCaptureActivity: failed to open last capture in player") }
    }
}
