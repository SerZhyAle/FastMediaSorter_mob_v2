package com.sza.fastmediasorter.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S0568 - all business logic for the home-screen camera launch widget tap, kept out of the thin
 * [CameraLaunchActivity] trampoline (Rule 3).
 *
 * Flow: resolve photo/video availability (settings + injected media capabilities + camera hardware)
 * -> CAMERA permission -> open the unified in-app camera host ([CameraCaptureActivity]) in switchable
 * multi-capture mode when both modes are available (the user picks photo/video in-screen), otherwise
 * fixed in the single available mode (degenerate gating reused from S0563).
 *
 * S1182: the host is always opened in multi-capture mode, where it saves every capture to its public
 * folder itself (S0566/ADR-2) and returns RESULT_CANCELED on close. So this widget never saves and holds
 * no [com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase] - [onCaptureResult] only drops the
 * scratch bookkeeping, mirroring the `multiCapture` branch of
 * [com.sza.fastmediasorter.ui.main.helpers.MainCameraCaptureManager]. Shares
 * [CameraQuickCaptureLaunchManager]'s trampoline glue (toast, scratch dir, permission).
 *
 * @param launchCapture invoked with the prepared capture intent; the trampoline owns the
 *   `ActivityResultLauncher` and routes the result back via [onCaptureResult].
 */
class CameraLaunchWidgetManager(
    private val activity: Activity,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val coroutineScope: CoroutineScope,
    // S0795: force the host into video mode (edge-gesture "start video recording"); default keeps the
    // widget's photo-preferred switchable behaviour.
    private val forceVideo: Boolean = false,
    private val requestPermission: () -> Unit,
    private val launchCapture: (Intent) -> Unit,
    private val finish: () -> Unit,
) {

    // Scratch dir + extension-less base name handed to the host; the captured file is dir/base.<ext>.
    private var pendingDir: File? = null
    private var pendingBaseName: String? = null
    private var allowSwitch: Boolean = false
    private var initialMode: CameraCaptureMode = CameraCaptureMode.PHOTO

    /** Entry point from the trampoline's onCreate. */
    fun start() {
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val photoAvailable = !settings.disableCameraCapture && mediaCapabilities.supportsImages
            val videoAvailable = !settings.disableVideoCapture && mediaCapabilities.supportsVideo
            withContext(Dispatchers.Main) { prepareAndLaunch(photoAvailable, videoAvailable) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) launchCaptureIntent() else toastAndFinish(R.string.camera_permission_required)
    }

    /**
     * Result from [CameraCaptureActivity]. The host runs in multi-capture mode and has already saved
     * every capture to its public folder (S0566/ADR-2), returning RESULT_CANCELED on close, so the
     * widget saves nothing - it only drops the app-private scratch bookkeeping and finishes.
     */
    fun onCaptureResult() {
        clearPending()
        finish()
    }

    private fun prepareAndLaunch(photoAvailable: Boolean, videoAvailable: Boolean) {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val videoOk = videoAvailable && BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)
        // S0795: the video-recording gesture needs a usable video path; a plain launch needs either mode.
        if (forceVideo && !videoOk) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        if (!forceVideo && !photoAvailable && !videoOk) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val dir = createScratchDir() ?: run {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        pendingDir = dir
        pendingBaseName = "CAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // S0795 forces video with no switch; otherwise S0563 degenerate gating: offer the in-screen
        // PHOTO|VIDEO switch only when both are available, else open fixed in the single available mode.
        initialMode = if (forceVideo || !photoAvailable) CameraCaptureMode.VIDEO else CameraCaptureMode.PHOTO
        allowSwitch = !forceVideo && photoAvailable && videoOk
        ensurePermissionAndCapture()
    }

    private fun ensurePermissionAndCapture() {
        if (hasCameraPermission()) launchCaptureIntent() else requestPermission()
    }

    private fun launchCaptureIntent() {
        val dir = pendingDir
        val base = pendingBaseName
        if (dir == null || base == null) {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        val intent = CameraCaptureContract.createSwitchableIntent(
            activity,
            dir.absolutePath,
            base,
            initialMode,
            allowModeSwitch = allowSwitch,
            // S0926: the "start video recording" gesture auto-starts recording once the preview is ready;
            // a plain widget launch keeps the manual shutter.
            autoCapture = forceVideo,
        )
        launchCapture(intent)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** App-private scratch dir; the host writes here and the saver moves the result to a public folder. */
    private fun createScratchDir(): File? = try {
        val base = activity.getExternalFilesDir(null) ?: activity.filesDir
        File(base, "Capture").takeIf { it.exists() || it.mkdirs() }
    } catch (e: Exception) {
        Timber.e(e, "camera launch widget: scratch dir creation failed")
        null
    }

    private fun clearPending() {
        val dir = pendingDir
        val base = pendingBaseName
        if (dir != null && base != null) {
            File(dir, "$base.jpg").delete()
            File(dir, "$base.mp4").delete()
        }
        pendingDir = null
        pendingBaseName = null
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun toastAndFinish(msgRes: Int) {
        toast(activity.getString(msgRes))
        clearPending()
        finish()
    }
}
