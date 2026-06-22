package com.sza.fastmediasorter.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
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
 * mode when both modes are available (the user picks photo/video in-screen), otherwise fixed in the
 * single available mode (degenerate gating reused from S0563) -> persist the captured file to the
 * device public folders by its actual media kind via the shared [SaveCapturedMediaUseCase].
 *
 * Mirrors [com.sza.fastmediasorter.ui.main.helpers.MainCameraCaptureManager] (the overflow "Camera"
 * entry) for launch + save, and [CameraQuickCaptureLaunchManager] for the trampoline glue (toast,
 * scratch file, permission). It never binds to a sorting resource, so the save use case routes to the
 * public folders only.
 *
 * @param launchCapture invoked with the prepared capture intent; the trampoline owns the
 *   `ActivityResultLauncher` and routes the result back via [onCaptureResult].
 */
class CameraLaunchWidgetManager(
    private val activity: Activity,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
    private val coroutineScope: CoroutineScope,
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
        Timber.d("S0568: camera launch widget tapped - resolving photo/video availability")
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

    /** Result from [CameraCaptureActivity]: RESULT_OK means the host wrote the captured file. */
    fun onCaptureResult(resultCode: Int, data: Intent?) {
        val dir = pendingDir
        val base = pendingBaseName
        if (resultCode != Activity.RESULT_OK || dir == null || base == null) {
            clearPending()
            finish()
            return
        }
        val mediaKind = CameraCaptureContract.readResultMediaKind(data)
        val isVideo = mediaKind == CameraCaptureMode.VIDEO
        val captured = CameraCaptureContract.readResultOutputPath(data)?.let { File(it) }
            ?: File(dir, base + if (isVideo) ".mp4" else ".jpg")
        if (!captured.exists()) {
            clearPending()
            toastAndFinish(R.string.camera_capture_error_session_expired)
            return
        }
        coroutineScope.launch {
            val saveResult = saveCapturedMedia(captured, isVideo)
            pendingDir = null
            pendingBaseName = null
            withContext(Dispatchers.Main) {
                when (saveResult) {
                    is SaveResult.Success ->
                        toast(activity.getString(R.string.camera_capture_saved, captured.name))
                    SaveResult.Failure.Io -> toast(activity.getString(R.string.camera_capture_error_io))
                    SaveResult.Failure.Generic ->
                        toast(activity.getString(R.string.camera_capture_error_save_generic))
                }
                finish()
            }
        }
    }

    private fun prepareAndLaunch(photoAvailable: Boolean, videoAvailable: Boolean) {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val videoOk = videoAvailable && BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)
        if (!photoAvailable && !videoOk) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val dir = createScratchDir() ?: run {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        pendingDir = dir
        pendingBaseName = "CAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // S0563 degenerate gating: offer the in-screen PHOTO|VIDEO switch only when both are available;
        // otherwise open the host fixed in the single available mode.
        initialMode = if (photoAvailable) CameraCaptureMode.PHOTO else CameraCaptureMode.VIDEO
        allowSwitch = photoAvailable && videoOk
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
        finish()
    }
}
