package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.CameraCaptureTarget
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S0523: host-neutral quick photo/video capture launched from the main-screen overflow menu. Photo
 * uses in-app CameraX ([CameraCaptureActivity], which owns the CAMERA permission); video uses the
 * system [MediaStore.ACTION_VIDEO_CAPTURE]. Both persist through the shared [CameraCaptureSaver] to
 * the phone's public folders (photo -> DCIM/Camera, video -> Movies) - never into a sorting resource,
 * so the saver's network/upload hook is unused.
 */
class MainCameraCaptureManager(
    private val activity: FragmentActivity,
    private val coroutineScope: CoroutineScope,
    private val cameraCaptureSaver: CameraCaptureSaver,
    // Owned by the host Activity (registered before STARTED), shared for photo and video; the host's
    // result callback delegates to handleResult.
    private val launcher: ActivityResultLauncher<Intent>,
) {

    private var pendingTempFile: File? = null
    private var pendingIsVideo: Boolean = false

    fun capturePhoto() {
        Timber.d("S0523: quick photo capture from main menu")
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            return
        }
        val tempFile = createTemp(".jpg") ?: run {
            showSnackbar(R.string.camera_capture_error_temp_file)
            return
        }
        val uri = fileProviderUri(tempFile) ?: return
        pendingTempFile = tempFile
        pendingIsVideo = false
        dispatch(CameraCaptureActivity.createIntent(activity, uri, tempFile.absolutePath), tempFile)
    }

    fun captureVideo() {
        Timber.d("S0523: quick video capture from main menu")
        if (!BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)) {
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            return
        }
        val tempFile = createTemp(".mp4") ?: run {
            showSnackbar(R.string.camera_capture_error_temp_file)
            return
        }
        val uri = fileProviderUri(tempFile) ?: return
        pendingTempFile = tempFile
        pendingIsVideo = true
        dispatch(Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri), tempFile)
    }

    private fun dispatch(intent: Intent, tempFile: File) {
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "quick camera: no handler for capture intent")
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            clearPending(tempFile)
        } catch (e: SecurityException) {
            Timber.e(e, "quick camera: launch denied")
            showSnackbar(R.string.camera_capture_error_permission_denied)
            clearPending(tempFile)
        } catch (t: Throwable) {
            Timber.e(t, "quick camera: launch failed")
            showSnackbar(R.string.camera_capture_error_save_generic)
            clearPending(tempFile)
        }
    }

    fun handleResult(result: ActivityResult) {
        val tempFile = pendingTempFile ?: run {
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val isVideo = pendingIsVideo
        if (result.resultCode != Activity.RESULT_OK) {
            clearPending(tempFile)
            return
        }
        val name = tempFile.name
        coroutineScope.launch {
            val target: CameraCaptureTarget = if (isVideo) moviesTarget() else CameraCaptureTarget.CameraFolder
            val saveResult = cameraCaptureSaver.save(tempFile, name, target) { _, _, _ -> false }
            pendingTempFile = null
            pendingIsVideo = false
            withContext(Dispatchers.Main) {
                when (saveResult) {
                    is SaveResult.Success -> showSnackbar(activity.getString(R.string.camera_capture_saved, name))
                    SaveResult.Failure.Io -> showSnackbar(R.string.camera_capture_error_io)
                    SaveResult.Failure.Generic -> showSnackbar(R.string.camera_capture_error_save_generic)
                }
            }
        }
    }

    /** Public Movies folder target (mirrors BrowseCameraCaptureManager.localVideoFallbackTarget). */
    private fun moviesTarget(): CameraCaptureTarget.Resource {
        val moviesDir = CaptureDestinationPolicy.resolveVideoDestination(null).also { it.mkdirs() }
        return CameraCaptureTarget.Resource(
            id = -1L,
            name = Environment.DIRECTORY_MOVIES,
            path = moviesDir.absolutePath,
            type = ResourceType.LOCAL,
        )
    }

    private fun fileProviderUri(tempFile: File): android.net.Uri? = try {
        FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", tempFile)
    } catch (t: Throwable) {
        Timber.e(t, "quick camera: FileProvider.getUriForFile failed")
        showSnackbar(R.string.camera_capture_error_permission_denied)
        clearPending(tempFile)
        null
    }

    private fun createTemp(ext: String): File? = try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = activity.getExternalFilesDir(
            if (ext == ".mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        ) ?: activity.filesDir
        File(dir, "CAP_$timestamp$ext").also { it.createNewFile() }
    } catch (e: Exception) {
        Timber.e(e, "quick camera: createTemp failed")
        null
    }

    private fun clearPending(tempFile: File) {
        tempFile.delete()
        pendingTempFile = null
        pendingIsVideo = false
    }

    private fun showSnackbar(msgRes: Int) {
        Snackbar.make(activity.window.decorView.rootView, msgRes, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG).show()
    }
}
