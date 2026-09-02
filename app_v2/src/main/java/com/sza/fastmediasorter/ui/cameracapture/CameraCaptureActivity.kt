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
import androidx.appcompat.widget.PopupMenu
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.CameraTestHooksBridge
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureFlowManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureCallbackHandler
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureHelperFactory
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureResultManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSaveDestinationLabelManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensSwitchManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOrientationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOverlayRotationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraProfilePresentation
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraSettingsCallbackHandler
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraZoomControlsManager
import com.sza.fastmediasorter.ui.cameracapture.model.CameraAspectSelection
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.CameraScenario
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    CameraSettingsDialogFragment.Host,
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

        private const val COUNTDOWN_TICK_INTERVAL_MS = 1_000L

        /** S1262: one checkable group, so exactly one profile row can carry the tick. */
        private const val PROFILE_MENU_GROUP = 1
    }

    // S0566/S0766/S1195: the host's domain access - capture persistence (S0568, shared with the main
    // entry and the widget), the opt-in geotag flag and the destination lookup - lives behind this
    // factory so the Activity itself holds no repository or use case (Rule 3).
    @Inject
    lateinit var helperFactory: CameraCaptureHelperFactory

    @Inject
    lateinit var sendToMenuManager: SendToMenuManager

    private val locationProvider = CameraLocationProvider()
    private var geotagEnabled = false

    private lateinit var sessionManager: CameraCaptureSessionManager
    private lateinit var flowManager: CameraCaptureFlowManager
    private lateinit var gestureManager: CameraCaptureGestureManager
    private lateinit var recordingTimer: RecordingElapsedTimer
    private lateinit var orientationManager: CameraOrientationManager

    /** S1986: token of the debug-only rotation-override receiver; always null in a release build. */
    private var rotationOverrideToken: Any? = null

    /** S1988: token of the debug-only lens-pinning receiver; always null in a release build. */
    private var pinningOverrideToken: Any? = null
    private lateinit var rotationManager: CameraOverlayRotationManager
    private lateinit var zoomControlsManager: CameraZoomControlsManager
    private lateinit var lensSwitchManager: CameraLensSwitchManager
    private lateinit var resultManager: CameraCaptureResultManager
    private lateinit var saveDestinationLabelManager: CameraCaptureSaveDestinationLabelManager
    private lateinit var gestureCallbackHandler: CameraCaptureGestureCallbackHandler
    private lateinit var settingsCallbackHandler: CameraSettingsCallbackHandler

    private var captureInFlight = false
    private var autoCaptureFired = false

    // S1579: the two halves of the shutter-arming condition. The output answer now arrives off the
    // main thread, so it can land before or after the bind - whichever is last arms the button.
    private var previewReady = false
    private var outputReady = false

    /** S1262: the sport trade-off notice is shown once per screen session, not per re-pick. */
    private var sportNoticeShown = false
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

        // S1579: the output target is confirmed off the main thread, so the screen is assembled
        // without waiting for the answer. A negative answer still ends in the same error + close;
        // the shutter, disabled above, is armed only once this and the bind have both answered.
        lifecycleScope.launch {
            if (!flowManager.resolveOutput()) return@launch
            outputReady = true
            if (previewReady) {
                binding.btnCapturePhoto.isEnabled = true
                maybeAutoCapture()
            }
        }

        // S0766: warm the location source as early as possible (only when opted in + permission held),
        // so a fix is ready by the first shutter; consent is taken in settings, never re-prompted here.
        // S1066: restore the remembered aspect ratio in the same read - photo keeps the full-frame
        // ViewPort so this only toggles the result frame + crop; video rebinds to the ratio.
        lifecycleScope.launch {
            val settings = helperFactory.currentSettings()
            geotagEnabled = settings.cameraGeotagEnabled
            if (geotagEnabled && PermissionHelper.hasLocationPermission(this@CameraCaptureActivity)) {
                locationProvider.start(this@CameraCaptureActivity)
            }
            // S1658: seeded before anything can save, so a switch never persists an empty memory
            // over the stored one - the flow manager refuses to write until this has run.
            flowManager.seedLensMemory(settings.cameraLensSettings)
            flowManager.setGridEnabled(settings.cameraGridEnabled)
            renderGridOverlay()
            sessionManager.setAspectRatioAndResolution(
                CameraAspectSelection.fromStored(settings.cameraAspectRatio),
                sessionManager.currentResolution,
            )
            applyPreviewScaleType()
        }
    }

    // S1336: a regular field (not lateinit) so it exists before onCreate ever runs - lets
    // CameraSettingsDialogFragment.onAttach read this safely even when it fires from a
    // framework-driven recreation, before initializeHelperManagers() (called from setupViews(),
    // which BaseActivity defers via post {}) has built settingsCallbackHandler. A property, not a
    // function - this class already sits at the detekt TooManyFunctions ceiling (CLAUDE.md Rule 19).
    private val cameraSettingsCallbacksState = MutableStateFlow<CameraSettingsDialogFragment.Callbacks?>(null)

    override val cameraSettingsCallbacksFlow: StateFlow<CameraSettingsDialogFragment.Callbacks?>
        get() = cameraSettingsCallbacksState

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
            onCrossLensFloorSelected = { equivalent ->
                // S1261: lens switch + zoom in one tap; the rebind redraws the row, then the sync
                // highlights the landed value like any other pill.
                flowManager.onCrossLensFloorSelected(equivalent)
                syncZoomSelection()
            },
            switchButton = binding.btnCameraLensSwitch,
        )
        lensSwitchManager = CameraLensSwitchManager(
            switchButton = binding.btnCameraLensSwitch,
            lensLabel = binding.cameraLensLabel,
            onSwitch = { flowManager.onLensSwitch() },
            onRestoreLabel = { zoomControlsManager.renderLensLabel(flowManager.currentCapabilities) },
        )
        resultManager = helperFactory.createResultManager(
            activity = this,
            lifecycleScope = lifecycleScope,
            sessionManager = sessionManager,
            sendToMenuManager = sendToMenuManager,
            galleryThumbnail = binding.btnGalleryThumbnail,
            sendToButton = binding.btnCameraSendTo,
            onError = ::showError,
        )
        saveDestinationLabelManager = helperFactory.createSaveDestinationLabelManager(
            intent = intent,
            flowManager = flowManager,
            rotationManager = rotationManager,
            destinationLabel = binding.cameraSaveDestination,
            scenarioLabel = binding.cameraScenarioLabel,
            lifecycleScope = lifecycleScope,
        )
        settingsCallbackHandler = CameraSettingsCallbackHandler(
            sessionManager = sessionManager,
            flowManager = flowManager,
            onGridToggled = ::renderGridOverlay,
            onAspectRatioApplied = ::handleAspectRatioApplied,
            rotationBucket = orientationManager.rotationBucket,
            onManualStateChanged = ::renderProfileButton,
        )
    }

    override fun observeData() = Unit

    override fun getInitialFocusView() = binding.btnCapturePhoto

    // S0801: orientationManager is a lateinit built in setupViews(), which BaseActivity defers to
    // binding.root.post {} for a fast first frame - so raw onResume() can fire before it exists. Use
    // the onResumeWithViews() hook, which is guaranteed to run only after setupViews() completes. The
    // OCR capture entry resumes the host inside that window; the previous raw onResume() override
    // crashed on the uninitialised manager.
    override fun onResumeWithViews() {
        orientationManager.enable()
        // S1986: debug builds only - the class behind this bridge lives in src/debug, so a release
        // build finds nothing and the call is a no-op. It lets a host-side sweep pin the rotation
        // bucket, which no adb command can do on a retail phone.
        rotationOverrideToken = CameraTestHooksBridge.installRotationOverride(this) { rotation ->
            orientationManager.forceRotation(rotation)
        }
        // S1988: same debug-only mechanism, for the sub-lens pin. The rebind is what makes the switch
        // observable at all - the pin is read while the use cases are built, so a flag flipped after
        // the bind would leave the sweep photographing the session it meant to change.
        pinningOverrideToken = CameraTestHooksBridge.installLensPinningOverride(this) {
            if (::sessionManager.isInitialized) sessionManager.rebindForDiagnostics()
        }
    }

    override fun onPause() {
        // S0801: an early pause (before the deferred setupViews() ran) leaves orientationManager
        // uninitialised; its symmetric enable() never fired either, so the disable is a safe skip.
        if (::orientationManager.isInitialized) orientationManager.disable()
        CameraTestHooksBridge.remove(this, rotationOverrideToken)
        rotationOverrideToken = null
        CameraTestHooksBridge.remove(this, pinningOverrideToken)
        pinningOverrideToken = null
        cancelCountdown()
        // S1181: the camera is bound to this activity's lifecycle, so CameraX unbinds VideoCapture on
        // ON_STOP and the recording finalizes with NO_VALID_DATA - the footage is lost. Stop it here,
        // while the source is still live, so the file finalizes exactly as a shutter-tap stop would.
        // onPause, not onStop: the order between our onStop and LifecycleCameraRepository's observer is
        // not guaranteed, and onPause is reliably earlier than any ON_STOP dispatch.
        if (::sessionManager.isInitialized && sessionManager.isRecording()) {
            sessionManager.stopRecording()
        }
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
        // S1658: the session reports both ends of a lens switch; the flow manager owns what each lens
        // remembers, and the encoded result goes straight back to settings so it outlives the screen.
        sessionManager.onLensLeaving = { flowManager.onLensLeaving(it) }
        sessionManager.onLensEntering = { flowManager.onLensEntering(it) }
        flowManager.onLensMemoryChanged = { encoded ->
            lifecycleScope.launch { helperFactory.rememberLensSettings(encoded) }
        }
        sessionManager.bind(
            previewView = binding.previewViewCamera,
            onReady = {
                previewReady = true
                // S1579: warm the extension-availability map for the lens/mode pairs this bind did
                // not touch, so a mode or lens switch no longer reads the vendor config from disk.
                lifecycleScope.launch(Dispatchers.IO) { sessionManager.warmOfferedExtensions() }
                if (outputReady) {
                    binding.btnCapturePhoto.isEnabled = true
                    maybeAutoCapture()
                }
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
        // S1336: the settings dialog reads flowManager.currentCapabilities (just updated above this
        // callback, per CameraCaptureFlowManager), not the NONE default a bare handler-exists signal
        // would have caught mid-recreation - waiting for the first real render also matches the
        // button's own visibility below, so the dialog can never open before capabilities are known.
        cameraSettingsCallbacksState.value = settingsCallbackHandler
        binding.btnCameraFlash.visibility = if (capabilities.hasFlashUnit) View.VISIBLE else View.GONE
        binding.btnCameraFlash.setIconResource(R.drawable.ic_camera_flash_off)
        binding.btnCameraSettings.visibility = View.VISIBLE
        binding.btnCameraLensSwitch.visibility =
            if (capabilities.canSwitchLens) View.VISIBLE else View.GONE
        // S0753: name the active lens next to the switch so the user knows why flash hides on ultra-wide.
        binding.cameraLensLabel.visibility =
            if (capabilities.canSwitchLens) View.VISIBLE else View.GONE
        zoomControlsManager.renderLensLabel(capabilities)
        renderProfileButton()

        // S0566/ADR-5: zoom presets are always visible (no "More" toggle) whenever the lens can zoom.
        if (capabilities.supportsZoom) {
            binding.cameraZoomPresetGroup.visibility = View.VISIBLE
            zoomControlsManager.configure(capabilities, flowManager.liveZoomRatio, flowManager.liveLinearZoom)
            binding.cameraZoomSlider.visibility = View.VISIBLE
            binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)
            binding.cameraZoomValue.visibility = View.VISIBLE
        } else if (!capabilities.isFront && capabilities.rearLensEquivalentFloors.size > 1) {
            // S1675: this lens has no range of its own, so the preset row would be empty and used to
            // disappear with it - leaving the wide lens without a marked way back. It carries one pill
            // per rear lens instead. Slider and readout stay hidden: min == max, nothing to drag.
            binding.cameraZoomPresetGroup.visibility = View.VISIBLE
            zoomControlsManager.configureLensPills(capabilities)
            binding.cameraZoomSlider.visibility = View.GONE
            binding.cameraZoomValue.visibility = View.GONE
        } else {
            binding.cameraZoomPresetGroup.visibility = View.GONE
            binding.cameraZoomPresetGroup.removeAllViews()
            binding.cameraZoomSlider.visibility = View.GONE
            binding.cameraZoomValue.visibility = View.GONE
        }
        rotationManager.reapply()
        applyPreviewScaleType()
    }

    // endregion

    /**
     * S1262: the profile button carries the active profile - icon for a glance, description and
     * tooltip in words, so the state is not colour-only. Hidden in video mode, and on a device whose
     * only offer is NORMAL: a menu with one neutral row would be a dead button (ADR-3).
     */
    private fun renderProfileButton() {
        val offered = flowManager.availableProfiles().size > 1 && !flowManager.isVideoMode
        binding.btnCameraProfile.visibility = if (offered) View.VISIBLE else View.GONE
        val profile = flowManager.activeProfile
        val manual = CameraProfilePresentation.isManual(
            profile,
            sessionManager.currentExposureCompensationIndex,
            sessionManager.currentWhiteBalanceMode,
        )
        if (offered) {
            val description = getString(
                R.string.camera_profile_button,
                getString(CameraProfilePresentation.labelRes(profile, manual)),
            )
            binding.btnCameraProfile.setIconResource(CameraProfilePresentation.iconRes(profile))
            binding.btnCameraProfile.contentDescription = description
            binding.btnCameraProfile.tooltipText = description
        }
        // S1418: when the device offers only NORMAL the profile button is hidden (ADR-3), so the
        // settings button - always visible, and the very place exposure and white balance are edited -
        // becomes the only carrier the manual state has.
        val settingsDescription = getString(
            if (manual && !offered) R.string.camera_settings_button_manual else R.string.camera_settings_title,
        )
        binding.btnCameraSettings.contentDescription = settingsDescription
        binding.btnCameraSettings.tooltipText = settingsDescription
    }

    /** S1262: the anchored profile menu - one checkable row per profile the bound lens can honour. */
    private fun showProfileMenu() {
        val profiles = flowManager.availableProfiles()
        val popup = PopupMenu(this, binding.btnCameraProfile)
        popup.setForceShowIcon(true)
        profiles.forEachIndexed { index, profile ->
            val item = popup.menu.add(PROFILE_MENU_GROUP, index, index, CameraProfilePresentation.labelRes(profile))
            item.setIcon(CameraProfilePresentation.iconRes(profile))
        }
        popup.menu.setGroupCheckable(PROFILE_MENU_GROUP, true, true)
        profiles.indexOf(flowManager.activeProfile)
            .takeIf { it >= 0 }
            ?.let { popup.menu.findItem(it)?.isChecked = true }
        popup.setOnMenuItemClickListener { item ->
            val picked = profiles.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            applyProfile(picked)
            true
        }
        popup.show()
    }

    private fun applyProfile(profile: PhotoProfile) {
        flowManager.onProfileSelected(profile)
        // The sport recipe trades light for a frozen frame; say so once, not on every re-pick.
        if (flowManager.activeProfile == PhotoProfile.SPORT && !sportNoticeShown) {
            sportNoticeShown = true
            Toast.makeText(this, R.string.camera_profile_sport_notice, Toast.LENGTH_LONG).show()
        }
        // A recipe that rebinds re-renders through renderCapabilities; NORMAL and SPORT do not.
        renderProfileButton()
        rotationManager.reapply()
    }

    private fun setupCameraControls() {
        binding.btnCameraFlash.setOnClickListener {
            val enabled = flowManager.onFlashToggle()
            binding.btnCameraFlash.setIconResource(
                if (enabled) R.drawable.ic_camera_flash_on else R.drawable.ic_camera_flash_off,
            )
        }
        binding.btnCameraProfile.setOnClickListener { showProfileMenu() }
        binding.btnCameraLensSwitch.setOnClickListener { lensSwitchManager.onRequested() }
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
            requestLensSwitch = lensSwitchManager::onRequested,
        )
        gestureManager = CameraCaptureGestureManager(
            touchSurface = binding.root,
            previewView = binding.previewViewCamera,
            callbacks = gestureCallbackHandler,
        )
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
            // S1262: photo profiles are photo-only, so their menu never shows in video mode.
            binding.btnCameraProfile.visibility = View.GONE
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
            applyPreviewScaleType()
        }
    }

    private fun renderModeTabs() {
        val videoActive = flowManager.isVideoMode
        binding.tabModePhoto.styleModeTab(selected = !videoActive)
        binding.tabModeVideo.styleModeTab(selected = videoActive)
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
        val location = if (geotagEnabled && PermissionHelper.hasLocationPermission(this)) {
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
        lifecycleScope.launch { helperFactory.rememberGridEnabled(flowManager.gridEnabled) }
    }

    /**
     * S1658: the full-screen selection fills the screen and is saved cropped to it; the other two show
     * the whole requested frame. Re-run on bind, mode switch and format apply, so switching the option
     * re-shapes the preview without leaving the screen.
     */
    private fun applyPreviewScaleType() {
        // S1920: the selection as the CURRENT MODE resolves it. Video has no post-encode crop, so
        // full screen degrades to the 16:9 stream it is built from (CameraAspectSelection.forMode) -
        // and the capture path already reads it that way. Reading the raw selection here cropped the
        // video viewfinder to the screen while the recorder kept the whole 16:9 frame.
        val selection = sessionManager.currentAspect?.forMode(flowManager.isVideoMode)
        val fullScreen = selection?.cropsToScreen == true
        binding.previewViewCamera.scaleType =
            if (fullScreen) {
                PreviewView.ScaleType.FILL_CENTER
            } else {
                PreviewView.ScaleType.FIT_CENTER
            }
        // S1920 (ADR-1): give the clip box the stream's own shape, so the soft-zoom scale is clipped
        // by the same rectangle CapturedPhotoAspectCropper.cropCenter writes to the file - 1/factor on
        // both axes. Left unshaped, the box is the screen and the viewfinder keeps more scene
        // vertically than the file does, which is the reported mismatch.
        val ratio = if (fullScreen) null else portraitClipRatio(selection)
        val params = binding.previewClipBox.layoutParams as ConstraintLayout.LayoutParams
        if (params.dimensionRatio != ratio) {
            params.dimensionRatio = ratio
            binding.previewClipBox.layoutParams = params
        }
    }

    /** S1658: after the settings dialog applies a shape, re-scale the preview and remember the choice. */
    private fun handleAspectRatioApplied() {
        applyPreviewScaleType()
        val value = (sessionManager.currentAspect ?: CameraAspectSelection.DEFAULT).storedValue
        lifecycleScope.launch { helperFactory.rememberAspectRatio(value) }
    }
}

private const val TAB_SELECTED_ALPHA = 1f
private const val TAB_UNSELECTED_ALPHA = 0.5f

/** S1920: the 4:3 stream as a portrait width-to-height, for the clip box that must match it. */
private const val CLIP_RATIO_4_3 = "3:4"

/** S1920: the 16:9 stream as a portrait width-to-height - every selection but 4:3 is built on it. */
private const val CLIP_RATIO_16_9 = "9:16"

/**
 * S1920: the stream [selection] asks CameraX for, expressed as the clip box's width-to-height.
 *
 * Portrait, because the manifest locks this activity to it (S0754) - a branch on the configuration
 * would be a branch that can never take its other side. Top-level for the reason [styleModeTab] is:
 * the class sits on the detekt `TooManyFunctions` ceiling and this reads no Activity state.
 */
private fun portraitClipRatio(selection: CameraAspectSelection?): String =
    if (selection == CameraAspectSelection.RATIO_4_3) CLIP_RATIO_4_3 else CLIP_RATIO_16_9

// S1336: top-level, not a class member - a pure TextView styling helper needs no Activity state, and
// CameraCaptureActivity already sits at the detekt TooManyFunctions ceiling (CLAUDE.md Rule 19).
private fun TextView.styleModeTab(selected: Boolean) {
    alpha = if (selected) TAB_SELECTED_ALPHA else TAB_UNSELECTED_ALPHA
    setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
}
