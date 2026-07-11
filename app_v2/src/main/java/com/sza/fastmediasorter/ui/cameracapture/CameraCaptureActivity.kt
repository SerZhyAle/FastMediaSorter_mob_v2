package com.sza.fastmediasorter.ui.cameracapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureFlowManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureCallbackHandler
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureResultManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSaveDestinationLabelManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOrientationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOverlayRotationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraSettingsCallbackHandler
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraZoomControlsManager
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.CameraScenario
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

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
class CameraCaptureActivity :
    BaseActivity<ActivityCameraCaptureBinding>(),
    CameraCaptureFlowManager.Host,
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

        // S0812: carry the calling scenario so the host can show a context label.
        fun createIntent(
            context: Context,
            outputUri: Uri,
            outputPath: String,
            mode: CameraCaptureMode,
            scenario: CameraScenario,
        ): Intent =
            CameraCaptureContract.createIntent(context, outputUri, outputPath, mode, scenario = scenario)

        private const val TAB_SELECTED_ALPHA = 1f
        private const val TAB_UNSELECTED_ALPHA = 0.5f

        private const val COUNTDOWN_TICK_INTERVAL_MS = 1_000L
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
    private lateinit var recordingTimer: RecordingElapsedTimer
    private lateinit var orientationManager: CameraOrientationManager
    private lateinit var rotationManager: CameraOverlayRotationManager
    private lateinit var zoomControlsManager: CameraZoomControlsManager
    private lateinit var resultManager: CameraCaptureResultManager
    private lateinit var saveDestinationLabelManager: CameraCaptureSaveDestinationLabelManager
    private lateinit var gestureCallbackHandler: CameraCaptureGestureCallbackHandler
    private lateinit var settingsCallbackHandler: CameraSettingsCallbackHandler

    private var captureInFlight = false
    private var autoCaptureFired = false
    private var recordingPaused = false
    private var recordingFile: File? = null
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
        recordingTimer = RecordingElapsedTimer { formatted -> binding.txtRecordingTimer.text = formatted }
        initializeHelperManagers()
        binding.cameraTopBar.applySystemBarInsetPadding(applyBottom = false)
        binding.cameraActionBar.applySystemBarInsetPadding(applyTop = false)

        if (!flowManager.resolveOutput()) return

        binding.btnCloseCamera.setOnClickListener { flowManager.onClose() }
        binding.btnCameraSettings.setOnClickListener { settingsCallbackHandler.show(supportFragmentManager) }
        binding.btnCameraSendTo.setOnClickListener { resultManager.openSendToMenu() }
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
        saveDestinationLabelManager.refresh()
        saveDestinationLabelManager.renderScenario()
        resultManager.updateSendToVisibility()
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

    /** S0844: builds every UI-only helper role the Activity delegates to (CLAUDE.md Rule 3). */
    private fun initializeHelperManagers() {
        rotationManager = CameraOverlayRotationManager(binding)
        orientationManager = CameraOrientationManager(
            context = this,
            onIconRotationChanged = { rotationManager.apply(it) },
            onTargetRotationChanged = { sessionManager.setTargetRotation(it) },
        )
        zoomControlsManager = CameraZoomControlsManager(
            context = this,
            presetGroup = binding.cameraZoomPresetGroup,
            zoomSlider = binding.cameraZoomSlider,
            zoomValue = binding.cameraZoomValue,
            lensLabel = binding.cameraLensLabel,
            onPresetSelected = { preset ->
                flowManager.onZoomRatioSelected(preset)
                syncZoomSelection()
            },
        )
        resultManager = CameraCaptureResultManager(
            activity = this,
            lifecycleScope = lifecycleScope,
            sessionManager = sessionManager,
            settingsRepository = settingsRepository,
            saveCapturedMedia = saveCapturedMedia,
            sendToMenuManager = sendToMenuManager,
            galleryThumbnail = binding.btnGalleryThumbnail,
            sendToButton = binding.btnCameraSendTo,
            onError = ::showError,
        )
        saveDestinationLabelManager = CameraCaptureSaveDestinationLabelManager(
            intent = intent,
            flowManager = flowManager,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            rotationManager = rotationManager,
            destinationLabel = binding.cameraSaveDestination,
            scenarioLabel = binding.cameraScenarioLabel,
            lifecycleScope = lifecycleScope,
        )
        settingsCallbackHandler = CameraSettingsCallbackHandler(
            sessionManager = sessionManager,
            flowManager = flowManager,
            onGridToggled = ::renderGridOverlay,
            rotationBucket = orientationManager.rotationBucket,
        )
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
            onReady = {
                binding.btnCapturePhoto.isEnabled = true
                maybeAutoCapture()
            },
            onError = {
                showError(R.string.camera_capture_error_no_camera_app)
                finishCancelled()
            },
        )
    }

    // S0790: auto-capture opens this screen and fires once the preview is ready - PHOTO takes a shot and
    // finishes; S0926: VIDEO auto-starts recording ("start video recording" gesture), user stops it.
    private fun maybeAutoCapture() {
        if (!flowManager.autoCapture || autoCaptureFired) return
        autoCaptureFired = true
        triggerCapture()
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
        zoomControlsManager.renderLensLabel(capabilities)
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
            zoomControlsManager.configure(capabilities, flowManager.liveZoomRatio, flowManager.liveLinearZoom)
            binding.cameraZoomSlider.visibility = View.VISIBLE
            binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)
            binding.cameraZoomValue.visibility = View.VISIBLE
        } else {
            binding.cameraZoomPresetGroup.visibility = View.GONE
            binding.cameraZoomPresetGroup.removeAllViews()
            binding.cameraZoomSlider.visibility = View.GONE
            binding.cameraZoomValue.visibility = View.GONE
        }
        rotationManager.reapply()
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
        binding.btnGalleryThumbnail.setOnClickListener { resultManager.openLastCapture() }
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
        gestureCallbackHandler = CameraCaptureGestureCallbackHandler(
            flowManager = flowManager,
            sessionManager = sessionManager,
            focusRingOverlay = binding.focusRingOverlay,
            zoomControlsManager = zoomControlsManager,
            selectMode = ::selectMode,
        )
        gestureManager = CameraCaptureGestureManager(binding.previewViewCamera, gestureCallbackHandler)
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
        saveDestinationLabelManager.refresh()
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
            saveDestinationLabelManager.refresh()
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
                    recordingFile?.let { resultManager.persistMultiCapture(it, isVideo = true) }
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
            if (flowManager.multiCapture && resultManager.lastSavedPath != null) {
                binding.btnGalleryThumbnail.visibility = View.VISIBLE
            }
        }
        resultManager.updateSendToVisibility()
    }

    private fun updateMicrophoneIcon(enabled: Boolean) {
        binding.toggleCameraMicrophone.setIconResource(
            if (enabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
        )
    }

    /** Re-syncs zoom pill/slider/readout to the flow manager's live values (S0844). */
    private fun syncZoomSelection() = zoomControlsManager.syncSelection(
        flowManager.liveZoomRatio,
        flowManager.liveLinearZoom,
        flowManager.currentCapabilities.zoomMultiplier,
    )

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
                    resultManager.persistMultiCapture(file, isVideo = false)
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

    private fun startSelfTimer(seconds: Int, onFinish: () -> Unit) {
        cancelCountdown()
        binding.btnCapturePhoto.isEnabled = false
        countdownJob = lifecycleScope.launch {
            binding.cameraCountdownOverlay.visibility = View.VISIBLE
            for (remaining in seconds downTo 1) {
                binding.cameraCountdownOverlay.text = remaining.toString()
                delay(COUNTDOWN_TICK_INTERVAL_MS)
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

    private fun renderGridOverlay() {
        binding.cameraGridOverlay.visibility = if (flowManager.gridEnabled) View.VISIBLE else View.GONE
    }
}
