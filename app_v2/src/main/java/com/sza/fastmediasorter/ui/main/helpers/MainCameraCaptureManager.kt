package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
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
 * S0523 / S0563: the main-screen overflow "Camera" entry. It launches the in-app CameraX host
 * ([CameraCaptureActivity], which owns the CAMERA and RECORD_AUDIO permissions) in switchable mode
 * when both photo and video are available, so the user picks the mode in-screen; the host owns the
 * output file and returns the actually-captured media kind and path. The result persists through the
 * shared [CameraCaptureSaver] to the phone's public folders (photo -> DCIM/Camera, video -> Movies) -
 * never into a sorting resource, so the saver's network/upload hook is unused.
 */
class MainCameraCaptureManager(
    private val activity: FragmentActivity,
    private val coroutineScope: CoroutineScope,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
    // Owned by the host Activity (registered before STARTED); the host's result callback delegates here.
    private val launcher: ActivityResultLauncher<Intent>,
) {

    // Scratch dir + extension-less base name handed to the host; the captured file is dir/base.<ext>.
    private var pendingDir: File? = null
    private var pendingBaseName: String? = null

    // S0566/ADR-2: the general entry launches a stay-open multi-capture session, so the host saves each
    // capture itself. This flag makes "do not move on result" explicit instead of inferring it from the
    // host's RESULT_CANCELED close; persisted across process death so a restored session stays authoritative.
    private var multiCapture = false

    /**
     * Launches the unified camera. [photoAvailable] / [videoAvailable] reflect the user settings AND
     * media capability already resolved by the caller. The in-screen PHOTO|VIDEO switch is offered only
     * when both are available; otherwise the host opens fixed in the single available mode.
     */
    fun captureCamera(photoAvailable: Boolean, videoAvailable: Boolean) {
        Timber.d("S0523: quick capture from main menu")
        Timber.d("S0563: unified camera entry photo=$photoAvailable video=$videoAvailable")
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            return
        }
        val videoOk = videoAvailable && BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)
        if (!photoAvailable && !videoOk) {
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            return
        }
        val dir = createScratchDir() ?: run {
            showSnackbar(R.string.camera_capture_error_temp_file)
            return
        }
        val baseName = "CAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val initialMode = if (photoAvailable) CameraCaptureMode.PHOTO else CameraCaptureMode.VIDEO
        val allowSwitch = photoAvailable && videoOk
        pendingDir = dir
        pendingBaseName = baseName
        multiCapture = true
        dispatch(
            CameraCaptureContract.createSwitchableIntent(
                activity,
                dir.absolutePath,
                baseName,
                initialMode,
                allowSwitch,
                multiCapture = true,
            ),
        )
    }

    private fun dispatch(intent: Intent) {
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "quick camera: no handler for capture intent")
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            clearPending()
        } catch (e: SecurityException) {
            Timber.e(e, "quick camera: launch denied")
            showSnackbar(R.string.camera_capture_error_permission_denied)
            clearPending()
        } catch (t: Throwable) {
            Timber.e(t, "quick camera: launch failed")
            showSnackbar(R.string.camera_capture_error_save_generic)
            clearPending()
        }
    }

    fun handleResult(result: ActivityResult) {
        if (multiCapture) {
            // S0566/ADR-2: the host already saved every in-session capture to its public folder; the
            // manager must not move anything. Just drop the scratch bookkeeping for this session.
            clearPending()
            return
        }
        val dir = pendingDir
        val base = pendingBaseName
        if (dir == null || base == null) {
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        if (result.resultCode != Activity.RESULT_OK) {
            clearPending()
            return
        }
        val mediaKind = CameraCaptureContract.readResultMediaKind(result.data)
        val isVideo = mediaKind == CameraCaptureMode.VIDEO
        val captured = CameraCaptureContract.readResultOutputPath(result.data)?.let { File(it) }
            ?: File(dir, base + if (isVideo) ".mp4" else ".jpg")
        if (!captured.exists()) {
            clearPending()
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val name = captured.name
        coroutineScope.launch {
            val saveResult = saveCapturedMedia(captured, isVideo)
            pendingDir = null
            pendingBaseName = null
            withContext(Dispatchers.Main) {
                when (saveResult) {
                    is SaveResult.Success -> showSnackbar(activity.getString(R.string.camera_capture_saved, name))
                    SaveResult.Failure.Io -> showSnackbar(R.string.camera_capture_error_io)
                    SaveResult.Failure.Generic -> showSnackbar(R.string.camera_capture_error_save_generic)
                }
            }
        }
    }

    /** App-private scratch dir; the host writes here and the saver moves the result to a public folder. */
    private fun createScratchDir(): File? = try {
        val base = activity.getExternalFilesDir(null) ?: activity.filesDir
        File(base, "Capture").takeIf { it.exists() || it.mkdirs() }
    } catch (e: Exception) {
        Timber.e(e, "quick camera: scratch dir creation failed")
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
        multiCapture = false
    }

    /**
     * S0564: persist the pending quick-capture target before the host Activity may be killed (process
     * death while the in-app camera host is foreground). Mirrors [BrowseCameraCaptureManager.saveState]
     * but simpler - the target is a fixed public folder, so no resource id is persisted. Without this
     * the in-memory fields are lost and [handleResult] abandons the captured file with session_expired.
     */
    fun saveState(outState: Bundle) {
        pendingDir?.absolutePath?.let { outState.putString(KEY_PENDING_DIR, it) }
        pendingBaseName?.let { outState.putString(KEY_PENDING_BASE, it) }
        outState.putBoolean(KEY_PENDING_MULTI, multiCapture)
    }

    /**
     * S0564: restore the pending quick-capture target after process death so [handleResult] can
     * complete the save instead of returning session_expired. The scratch dir is app-private
     * ([createScratchDir]) and normally survives the kill; if the OS reclaimed it, bail and let the
     * existing session_expired path handle the missing file.
     */
    fun restoreState(savedState: Bundle) {
        val dirPath = savedState.getString(KEY_PENDING_DIR) ?: return
        val base = savedState.getString(KEY_PENDING_BASE) ?: return
        val dir = File(dirPath)
        if (!dir.exists()) {
            Timber.w("quick camera: scratch dir gone after process death path=%s", dirPath)
            return
        }
        pendingDir = dir
        pendingBaseName = base
        multiCapture = savedState.getBoolean(KEY_PENDING_MULTI, false)
        Timber.d("S0564: restored pending quick-capture target dir=$dirPath base=$base")
    }

    private fun showSnackbar(msgRes: Int) {
        Snackbar.make(activity.window.decorView.rootView, msgRes, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        const val KEY_PENDING_DIR = "main_cam_pending_dir"
        const val KEY_PENDING_BASE = "main_cam_pending_base"
        const val KEY_PENDING_MULTI = "main_cam_pending_multi"
    }
}
