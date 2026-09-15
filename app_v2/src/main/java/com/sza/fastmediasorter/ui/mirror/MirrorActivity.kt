package com.sza.fastmediasorter.ui.mirror

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityMirrorBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraOrientationManager
import com.sza.fastmediasorter.ui.mirror.helpers.MirrorCaptureManager
import com.sza.fastmediasorter.ui.mirror.helpers.MirrorZoomManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Host of the mirror sub-program (strategic S1924): the front camera shown full screen inside a bright
 * field that lights the face.
 *
 * Only the window's own brightness is raised, never the device setting - ADR-2, inherited verbatim from
 * the front flashlight (S1796): a program that raised the system brightness would leave the device on
 * maximum after it closed. The camera itself comes from the capture screen's session manager rather
 * than a second stack of its own (ADR-1), so a session leak or a zoom divergence has one place to fix.
 */
@AndroidEntryPoint
class MirrorActivity : BaseActivity<ActivityMirrorBinding>() {

    @Inject
    lateinit var captureManager: MirrorCaptureManager

    private val viewModel: MirrorViewModel by viewModels()

    private val sessionManager = CameraCaptureSessionManager(this)

    private lateinit var zoomManager: MirrorZoomManager

    /**
     * Feeds CameraX the device's rotation bucket. Without it `targetRotation` keeps its ROTATION_0 seed
     * and a photo or video taken with the phone on its side is written to disk rotated - the preview
     * looks right and the saved file does not. The capture screen wires the same manager for the same
     * reason; the mirror needs it more, because it is not orientation-locked.
     *
     * The icon callback is a deliberate no-op: this screen rotates with the device, so its controls are
     * already upright, unlike the portrait-locked host the manager was written for.
     */
    private val orientationManager by lazy {
        CameraOrientationManager(
            context = this,
            onIconRotationChanged = {},
            onTargetRotationChanged = { sessionManager.setTargetRotation(it) },
        )
    }

    private var cameraBound = false

    /**
     * Set when the screen pauses while a recording is running. `stopRecording` only asks the recorder to
     * stop and finalize arrives asynchronously, so unbinding straight away would tear the `VideoCapture`
     * out from under it and finalize the take as an error - a lost video and a "save failed" toast for
     * nothing more than leaving the screen. The unbind waits for the finalize callback instead.
     */
    private var unbindAfterRecording = false

    /**
     * Set from the shutter press until the capture is saved or failed. The capture screen has carried
     * the same guard since it was written; the mirror was built without one, and a second press while
     * `takePicture` is still in flight fails inside CameraX and reports an error over a shot that
     * actually succeeded. The shutter is the biggest control on this screen and a double press is the
     * natural way to use it, so the guard is the difference between "works" and "shows an error".
     */
    private var captureInFlight = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bindFrontLens()
            } else {
                // A dead black preview would leave the user guessing; say why and leave.
                Toast.makeText(this, R.string.mirror_no_front_camera, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    /**
     * Asked at the first recording, never at screen entry (strategic 3.2). A refusal records silently
     * instead of refusing to record - a button that does nothing is worse than one that does less.
     */
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            startRecording(withAudio = granted)
        }

    override fun getViewBinding(): ActivityMirrorBinding =
        ActivityMirrorBinding.inflate(layoutInflater)

    /**
     * A mirror may not go dark while somebody is looking into it. The flag lives on this window, so
     * [BaseActivity] drops it with the window and the hold cannot outlive the program.
     */
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = true

    override fun setupViews() {
        applyControlInsets()
        zoomManager = MirrorZoomManager(
            container = binding.mirrorCornerBottomStart,
            sessionManager = sessionManager,
            onRatioPicked = { viewModel.setZoomRatio(it) },
        )
        zoomManager.attach()
        binding.btnMirrorClose.setOnClickListener { finish() }
        binding.btnMirrorBacklight.setOnClickListener {
            viewModel.setBacklightOn(!viewModel.backlightOn.value)
        }
        binding.btnMirrorFlip.setOnClickListener {
            viewModel.setHorizontallyFlipped(!viewModel.horizontallyFlipped.value)
        }
        binding.btnMirrorPhoto.setOnClickListener { capturePhoto() }
        binding.btnMirrorVideo.setOnClickListener { toggleRecording() }
        ensurePermissionAndBind()
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.backlightOn) { on ->
            binding.mirrorGlowField.isVisible = on
            applyControlTint(on)
            applyBrightness(on)
        }
        collectOnLifecycle(viewModel.horizontallyFlipped) { flipped ->
            // CameraX already mirrors a front preview, so the remembered "mirror-like" state is the
            // untransformed one and the toggle un-mirrors it. Written to the host, never to the
            // PreviewView, whose scaleX belongs to the session manager's soft zoom.
            binding.mirrorPreviewFlipHost.scaleX = if (flipped) NORMAL_SCALE else FLIP_SCALE
        }
        collectOnLifecycle(viewModel.zoomRatio) {
            zoomManager.showActive(it)
        }
    }

    /**
     * Rule 18: the camera goes the moment the screen stops being looked at, and the raised brightness
     * with it - ADR-2's reason covers the exit path too. [onResume] rebinds, so leaving and returning
     * lands on the saved zoom rather than on whatever the session last held.
     */
    override fun onPause() {
        orientationManager.disable()
        if (sessionManager.isRecording()) {
            // The recorder keeps the session until finalize lands; the callback unbinds for us.
            unbindAfterRecording = true
            stopRecording()
        } else {
            sessionManager.unbind()
            cameraBound = false
        }
        applyBrightness(on = false)
        super.onPause()
    }

    /**
     * Not `onResume`: [BaseActivity] defers `setupViews` to the first frame, so a plain resume can fire
     * before [zoomManager] exists - and the rebind below reaches it through the bind callback.
     */
    override fun onResumeWithViews() {
        super.onResumeWithViews()
        orientationManager.enable()
        applyBrightness(viewModel.backlightOn.value)
        ensurePermissionAndBind()
    }

    private fun ensurePermissionAndBind() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            bindFrontLens()
        } else {
            // Strategic 3.2: the right is asked on first entry into the mirror, never at app start.
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * The session always opens on the main back lens, so the front one is selected right after the
     * bind reports ready. `restoreSaved = false` keeps the capture screen's per-lens memory out of the
     * mirror - the two screens remember different things about the same lens.
     */
    private fun bindFrontLens() {
        if (cameraBound) return
        cameraBound = true
        sessionManager.bind(
            previewView = binding.mirrorPreview,
            onReady = {
                sessionManager.switchToFacing(CameraSelector.LENS_FACING_FRONT, restoreSaved = false)
                sessionManager.setZoomRatio(viewModel.zoomRatio.value)
                zoomManager.showActive(viewModel.zoomRatio.value)
            },
            onError = { error ->
                Timber.e(error, "MirrorActivity: front lens bind failed")
                Toast.makeText(this, R.string.mirror_no_front_camera, Toast.LENGTH_LONG).show()
                finish()
            },
        )
    }

    /**
     * The flip is how the user sees themselves, not how the photograph is stored, so nothing about it
     * reaches the file - the session captures the frame the lens produced.
     */
    private fun capturePhoto() {
        if (captureInFlight) return
        captureInFlight = true
        binding.btnMirrorPhoto.isEnabled = false
        lifecycleScope.launch {
            val tempFile = captureManager.newPhotoFile() ?: run {
                releaseShutter()
                showCaptureFailed(R.string.mirror_capture_error_file)
                return@launch
            }
            sessionManager.capture(
                previewView = binding.mirrorPreview,
                outputFile = tempFile,
                onSaved = { lifecycleScope.launch { persistPhoto(tempFile) } },
                onError = { error ->
                    Timber.e(error, "MirrorActivity: photo capture failed")
                    if (isFinishing || isDestroyed) return@capture
                    releaseShutter()
                    showCaptureFailed(R.string.mirror_capture_error_camera)
                },
            )
        }
    }

    private suspend fun persistPhoto(tempFile: File) {
        // CameraX delivers the save callback asynchronously, so the screen may already be gone -
        // touching the binding after onDestroy released it crashes, the way the capture screen records.
        if (isFinishing || isDestroyed) return
        val saved = captureManager.savePhoto(tempFile)
        releaseShutter()
        if (saved) {
            Toast.makeText(this, R.string.camera_capture_saved, Toast.LENGTH_SHORT).show()
        } else {
            showCaptureFailed(R.string.mirror_capture_error_save)
        }
    }

    /** Re-arms the shutter after a capture ended, whichever way it ended. */
    private fun releaseShutter() {
        captureInFlight = false
        if (!isFinishing && !isDestroyed) binding.btnMirrorPhoto.isEnabled = true
    }

    private fun toggleRecording() {
        if (sessionManager.isRecording()) {
            stopRecording()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording(withAudio = true)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording(withAudio: Boolean) {
        lifecycleScope.launch {
            val tempFile = captureManager.newVideoFile() ?: run {
                showCaptureFailed(R.string.mirror_capture_error_file)
                return@launch
            }
            // The session binds an image pipeline by default and only carries a Recorder in video mode,
            // so without this the first recording would find no videoCapture and finalize as an error.
            sessionManager.applyMode(videoMode = true)
            restoreZoom()
            renderRecordingState(recording = true)
            sessionManager.startRecording(tempFile, withAudio) { failed ->
                renderRecordingState(recording = false)
                // Back to the photo pipeline so the next shutter press has an ImageCapture to use.
                sessionManager.applyMode(videoMode = false)
                restoreZoom()
                // Finalize has landed by now, so the session may be torn down: a pause that arrived
                // mid-recording deferred its unbind to here rather than cutting the recorder off.
                if (unbindAfterRecording) {
                    unbindAfterRecording = false
                    sessionManager.unbind()
                    cameraBound = false
                }
                lifecycleScope.launch { finishRecording(tempFile, failed) }
            }
        }
    }

    private fun stopRecording() {
        sessionManager.stopRecording()
        renderRecordingState(recording = false)
    }

    private suspend fun finishRecording(tempFile: File, failed: Boolean) {
        if (isFinishing || isDestroyed) return
        when {
            failed -> showCaptureFailed(R.string.mirror_capture_error_camera)
            !captureManager.saveVideo(tempFile) -> showCaptureFailed(R.string.mirror_capture_error_save)
            else -> Toast.makeText(this, R.string.camera_capture_saved, Toast.LENGTH_SHORT).show()
        }
    }

    /** The button carries the state, so a running recording is visible without a second indicator. */
    private fun renderRecordingState(recording: Boolean) {
        val icon = if (recording) R.drawable.ic_shutter_video_recording else R.drawable.ic_shutter_video_idle
        binding.btnMirrorVideo.setImageResource(icon)
        binding.btnMirrorVideo.isSelected = recording
        // The icon alone leaves a screen reader saying "record video" while it is already recording.
        binding.btnMirrorVideo.contentDescription = getString(
            if (recording) R.string.camera_capture_record_stop else R.string.mirror_video_button,
        )
    }

    /**
     * Names the branch that failed rather than reporting one word for four different causes. The
     * owner's 2026-09-06 device run reported "the photo button errors" and left nothing to act on,
     * because a file that could not be allocated, a camera that refused the shot and a folder that
     * refused the write all produced the same sentence.
     */
    private fun showCaptureFailed(@StringRes messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun applyControlInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mirrorRoot) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            // Padding goes on the corner containers, not on the root: the glow field has to keep
            // covering the cutout and the bar areas, which is where most of its light comes from.
            // Relative, not physical: the containers are constrained to Start/End, so in RTL the
            // "start" corner sits on the right of the screen and must take the right-hand inset.
            val rtl = binding.mirrorRoot.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val startInset = if (rtl) bars.right else bars.left
            val endInset = if (rtl) bars.left else bars.right
            binding.mirrorCornerTopStart.updatePaddingRelative(top = bars.top, start = startInset)
            binding.mirrorCornerTopEnd.updatePaddingRelative(top = bars.top, end = endInset)
            binding.mirrorCornerBottomStart.updatePaddingRelative(bottom = bars.bottom, start = startInset)
            binding.mirrorCornerBottomEnd.updatePaddingRelative(bottom = bars.bottom, end = endInset)
            insets
        }
        // BaseActivity defers setupViews to binding.root.post, which is after the window's first insets
        // dispatch - a listener registered here would otherwise never fire, and the owner's 2026-09-06
        // run found the zoom row sitting under the navigation bar for exactly that reason (Rule 17).
        val root = binding.mirrorRoot
        ViewCompat.getRootWindowInsets(root)
            ?.let { ViewCompat.dispatchApplyWindowInsets(root, it) }
            ?: ViewCompat.requestApplyInsets(root)
    }

    /**
     * The icon controls sit on an opaque dark circle (S2847), so their tint is fixed white:
     * white on dark reads on both the white glow field and the black root, and the orange ring
     * carries the contrast the circle itself does not.
     *
     * The zoom row is deliberately out of this: it carries an opaque body of its own, so it reads on
     * either state of the field without being repainted.
     */
    private fun applyControlTint(@Suppress("UNUSED_PARAMETER") backlightOn: Boolean) {
        val tint = ContextCompat.getColor(this, R.color.white)
        listOf(
            binding.btnMirrorClose,
            binding.btnMirrorBacklight,
            binding.btnMirrorFlip,
            binding.btnMirrorPhoto,
            binding.btnMirrorVideo,
        ).forEach { it.imageTintList = ColorStateList.valueOf(tint) }
    }

    /**
     * Re-applies the chosen preset after a mode switch. `applyMode` rebinds the session, and a rebind
     * resets the digital zoom while restoring only the optical ratio - and a front lens usually reports
     * an optical maximum of 1.0, so every preset above x1 is entirely digital. Without this the x3 the
     * mirror opens at would silently drop to x1 on the first tap of the record button.
     */
    private fun restoreZoom() {
        sessionManager.setZoomRatio(viewModel.zoomRatio.value)
    }

    private fun applyBrightness(on: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) MAX_BRIGHTNESS else BRIGHTNESS_OVERRIDE_NONE
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, MirrorActivity::class.java)

        private const val MAX_BRIGHTNESS = 1.0f

        // Hands the window back to whatever the device brightness says, rather than to a value of ours.
        private const val BRIGHTNESS_OVERRIDE_NONE = -1.0f
        private const val FLIP_SCALE = -1.0f
        private const val NORMAL_SCALE = 1.0f
    }
}
