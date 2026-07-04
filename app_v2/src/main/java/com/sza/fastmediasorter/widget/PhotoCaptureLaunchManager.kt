package com.sza.fastmediasorter.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.HeadlessPhotoCapturer
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
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
 * S0790-S0794 - all business logic for the edge-gesture "take photo" family, kept out of the thin
 * [PhotoCaptureLaunchActivity] trampoline (Rule 3).
 *
 * Flow: resolve photo availability -> CAMERA permission -> [HeadlessPhotoCapturer] takes a single
 * photo with no visible camera UI -> on success either save to the public folder and toast (plain
 * take-photo, [autoAction] null), or route the captured photo into [PhotoVideoStandaloneActivity]
 * with the given auto-action (send-to / draw editor / OCR-translate).
 *
 * The capture is headless (no [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]): the
 * previous design opened that full screen in auto-capture mode and read the shot back via an activity
 * result, but the trampoline's `android:noHistory` teardown destroyed it while the camera was in the
 * foreground, so the result never came back and no photo was ever saved (S0790-S0794 device fail).
 *
 * @param autoAction one of [PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO] /
 *   [PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW] / [PhotoVideoStandaloneActivity.AUTO_ACTION_TRANSLATE],
 *   or null for a plain silent save.
 */
class PhotoCaptureLaunchManager(
    private val activity: Activity,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
    private val coroutineScope: CoroutineScope,
    private val autoAction: String?,
    private val capturer: HeadlessPhotoCapturer,
    private val locationProvider: CameraLocationProvider,
    private val requestPermission: () -> Unit,
    private val finish: () -> Unit,
) {

    private var pendingDir: File? = null
    private var pendingBaseName: String? = null

    // S0766: geotag is opt-in; resolved once from settings in start() and read on the capture path.
    private var geotagEnabled = false

    /** Entry point from the trampoline's onCreate. */
    fun start() {
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val photoAvailable = !settings.disableCameraCapture && mediaCapabilities.supportsImages
            geotagEnabled = settings.cameraGeotagEnabled
            withContext(Dispatchers.Main) {
                // S0766: warm the location source early (opt-in + permission held) so a cached fix is
                // ready by the shutter; a headless shot never blocks waiting for a fresh fix.
                if (geotagEnabled && hasLocationPermission()) locationProvider.start(activity)
                prepareAndLaunch(photoAvailable)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) performCapture() else toastAndFinish(R.string.camera_permission_required)
    }

    private fun prepareAndLaunch(photoAvailable: Boolean) {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) || !photoAvailable) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val dir = createScratchDir() ?: run {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        pendingDir = dir
        pendingBaseName = "CAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        if (hasCameraPermission()) performCapture() else requestPermission()
    }

    // S0790-S0794: headless single shot - no visible camera screen, no inter-activity result hop.
    private fun performCapture() {
        val dir = pendingDir
        val base = pendingBaseName
        if (dir == null || base == null) {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        val captured = File(dir, "$base.jpg")
        val location = if (geotagEnabled && hasLocationPermission()) {
            locationProvider.lastKnownLocation()
        } else {
            null
        }
        Timber.d("S0790: headless photo capture autoAction=%s geotag=%s", autoAction, location != null)
        capturer.capture(
            outputFile = captured,
            location = location,
            onSaved = { onCaptured(captured) },
            onError = { error ->
                Timber.e(error, "PhotoCaptureLaunchManager: headless capture failed")
                clearPending()
                toastAndFinish(R.string.camera_capture_error_save_generic)
            },
        )
    }

    private fun onCaptured(captured: File) {
        if (!captured.exists()) {
            clearPending()
            toastAndFinish(R.string.camera_capture_error_session_expired)
            return
        }
        if (autoAction == null) saveAndToast(captured) else routeToViewer(captured)
    }

    // S0790: plain take-photo - persist to the public Camera folder and toast, no viewer.
    private fun saveAndToast(captured: File) {
        coroutineScope.launch {
            val saveResult = saveCapturedMedia(captured, isVideo = false)
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

    // S0791/S0792/S0794: hand the captured photo to the standalone viewer with the requested auto-action
    // (send-to / draw editor / OCR-translate). The scratch file is kept (the viewer reads it) and cleaned
    // by the OS with the app-private Capture dir, so it is not deleted here.
    private fun routeToViewer(captured: File) {
        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", captured)
        } catch (t: Throwable) {
            Timber.e(t, "PhotoCaptureLaunchManager: FileProvider failed")
            toastAndFinish(R.string.camera_capture_error_save_generic)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_JPEG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClass(activity, PhotoVideoStandaloneActivity::class.java)
            putExtra(PhotoVideoStandaloneActivity.EXTRA_AUTO_ACTION, autoAction)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { Timber.w(it, "PhotoCaptureLaunchManager: failed to open viewer") }
        pendingDir = null
        pendingBaseName = null
        finish()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** S0766: true when fine OR coarse location is granted; geotag silently skips otherwise. */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createScratchDir(): File? = try {
        val base = activity.getExternalFilesDir(null) ?: activity.filesDir
        File(base, "Capture").takeIf { it.exists() || it.mkdirs() }
    } catch (e: Exception) {
        Timber.e(e, "PhotoCaptureLaunchManager: scratch dir creation failed")
        null
    }

    private fun clearPending() {
        val dir = pendingDir
        val base = pendingBaseName
        if (dir != null && base != null) File(dir, "$base.jpg").delete()
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

    private companion object {
        private const val MIME_JPEG = "image/jpeg"
    }
}
