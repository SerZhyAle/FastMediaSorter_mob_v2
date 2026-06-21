package com.sza.fastmediasorter.ui.cameracapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureFlowManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureGestureManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraRecordingTimer
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    CameraCaptureGestureManager.Callbacks {

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
    }

    // S0566: host-side persistence for the stay-open multi-capture session. Reuses the shared
    // use-case (S0568) so the main entry, the widget and this host save captures identically.
    @Inject
    lateinit var saveCapturedMedia: SaveCapturedMediaUseCase

    private lateinit var sessionManager: CameraCaptureSessionManager
    private lateinit var flowManager: CameraCaptureFlowManager
    private lateinit var gestureManager: CameraCaptureGestureManager
    private lateinit var recordingTimer: CameraRecordingTimer

    private var captureInFlight = false
    private var recordingPaused = false
    private var recordingFile: File? = null
    private var lastSavedPath: String? = null

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
        recordingTimer = CameraRecordingTimer { formatted -> binding.txtRecordingTimer.text = formatted }
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
        setupModeSelector()

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
        recordingTimer.stop()
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

        // S0566/ADR-5: zoom presets are always visible (no "More" toggle) whenever the lens can zoom.
        if (capabilities.supportsZoom) {
            binding.cameraZoomPresetGroup.visibility = View.VISIBLE
            configureZoomControls(capabilities)
        } else {
            binding.cameraZoomPresetGroup.visibility = View.GONE
            binding.cameraZoomPresetGroup.removeAllViews()
        }
    }

    // endregion

    // region CameraCaptureGestureManager.Callbacks

    override fun onTapToFocus(x: Float, y: Float) {
        if (flowManager.onTapToFocus(x, y)) binding.focusRingOverlay.showAt(x, y)
    }

    override fun onDoubleTapZoom() {
        Timber.d("S0566: double-tap zoom toggle gesture")
        flowManager.onDoubleTapZoom()
        syncZoomSelection()
    }

    override fun onPinchZoom(scaleFactor: Float) {
        Timber.d("S0566: pinch zoom gesture factor=%s", scaleFactor)
        flowManager.onPinchZoom(scaleFactor)
        syncZoomSelection()
    }

    override fun onSwipeLensSwitch() {
        Timber.d("S0566: swipe lens-switch gesture")
        flowManager.onLensSwitch()
    }

    override fun onSwipeModeSwitch(toNext: Boolean) {
        Timber.d("S0566: swipe mode-switch gesture toNext=%s", toNext)
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
        binding.btnCameraLensSwitch.setOnClickListener { flowManager.onLensSwitch() }
        binding.btnCameraPauseResume.setOnClickListener { onPauseResumeClicked() }
        binding.btnGalleryThumbnail.setOnClickListener { openLastCapture() }
        binding.toggleCameraMicrophone.setOnClickListener {
            updateMicrophoneIcon(flowManager.onMicrophoneToggle())
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
        } else {
            binding.toggleCameraMicrophone.visibility = View.GONE
        }
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
        Timber.d("S0545: video recording start withAudio=$withAudio")
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
        Timber.d("S0566: recording pause/resume toggle, paused=%s", !recordingPaused)
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
    }

    private fun updateMicrophoneIcon(enabled: Boolean) {
        binding.toggleCameraMicrophone.setIconResource(
            if (enabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
        )
    }

    private fun configureZoomControls(capabilities: CameraRuntimeCapabilities) {
        val group = binding.cameraZoomPresetGroup
        group.removeAllViews()
        capabilities.zoomPresets.forEach { preset ->
            val chip = Chip(this).apply {
                text = formatZoomRatio(preset)
                isCheckable = true
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.camera_control_zoom)
                tag = preset
                setOnClickListener {
                    flowManager.onZoomRatioSelected(preset)
                    syncZoomSelection()
                }
            }
            group.addView(chip)
        }
        syncZoomSelection()
    }

    /** Highlights the preset pill nearest the live zoom ratio; clears all when pinched between steps. */
    private fun syncZoomSelection() {
        val group = binding.cameraZoomPresetGroup
        val live = flowManager.liveZoomRatio
        var best: Chip? = null
        var bestDelta = Float.MAX_VALUE
        group.children.forEach { view ->
            val chip = view as? Chip ?: return@forEach
            chip.isChecked = false
            val preset = chip.tag as? Float ?: return@forEach
            val delta = abs(preset - live)
            if (delta < bestDelta) {
                bestDelta = delta
                best = chip
            }
        }
        if (bestDelta < ZOOM_PILL_MATCH_EPSILON) best?.isChecked = true
    }

    private fun formatZoomRatio(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}×" else String.format(Locale.US, "%.1f×", ratio)

    private fun capturePhoto() {
        val file = flowManager.nextOutputFile() ?: return
        if (captureInFlight) return
        captureInFlight = true
        binding.btnCapturePhoto.isEnabled = false

        sessionManager.capture(
            previewView = binding.previewViewCamera,
            outputFile = file,
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
        Timber.d("S0566: multi-capture persist (stay-open), isVideo=%s", isVideo)
        val name = file.name
        lifecycleScope.launch {
            val result = saveCapturedMedia(file, isVideo)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is SaveResult.Success -> {
                    lastSavedPath = result.savedPath
                    showGalleryThumbnail(result.savedPath)
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

    /**
     * S0566/§6.7: opens the most recent capture in the in-app player. Routes through
     * [StandalonePlayerDispatcherActivity] (resolves the media family from the URI and forwards to the
     * matching standalone host) instead of an implicit ACTION_VIEW the OS would hand to an external
     * gallery, keeping the user inside the app. A missing/unviewable file is logged, not fatal.
     */
    private fun openLastCapture() {
        Timber.d("S0566: open last capture thumbnail in in-app player")
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
