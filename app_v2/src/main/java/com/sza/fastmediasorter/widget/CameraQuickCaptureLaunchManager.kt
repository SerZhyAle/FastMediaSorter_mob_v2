package com.sza.fastmediasorter.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.CameraCaptureTarget
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity
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
 * S0369 - all business logic for the quick camera-capture widget tap, kept out of the thin
 * [CameraQuickCaptureActivity] trampoline (Rule 3).
 *
 * Flow: load the per-widget target -> defensive availability gate (global camera disable, missing
 * or invalid target) -> CAMERA permission -> capture via [CameraCaptureActivity] -> save into the
 * bound target through the shared [CameraCaptureSaver]. Network/cloud targets reuse the app-wide
 * [UnifiedFileOperationHandler] copy backend (no parallel save subsystem). Post-capture naming
 * follows the existing app-level camera settings (`skipCameraFilenameDialog`).
 *
 * @param launchCapture invoked with the prepared capture intent; the trampoline owns the
 *   `ActivityResultLauncher` and routes the result back via [onCaptureResult].
 */
class CameraQuickCaptureLaunchManager(
    private val activity: Activity,
    private val appWidgetId: Int,
    private val settingsRepository: SettingsRepository,
    private val cameraCaptureSaver: CameraCaptureSaver,
    private val fileOperationHandler: UnifiedFileOperationHandler,
    private val coroutineScope: CoroutineScope,
    private val requestPermission: () -> Unit,
    private val launchCapture: (Intent) -> Unit,
    private val finish: () -> Unit,
) {

    private var pendingTempFile: File? = null
    private var target: CameraCaptureTarget? = null
    // S0371: per-widget capture mode resolved in start(); drives the launch intent + temp extension.
    private var isVideoMode: Boolean = false

    /** Entry point from the trampoline's onCreate. */
    fun start() {
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            isVideoMode = CameraQuickCaptureWidgetProvider.captureMode(activity, appWidgetId) ==
                CameraQuickCaptureWidgetProvider.CAPTURE_MODE_VIDEO
            // Defensive runtime gate: an instance pinned before capture was disabled survives. The
            // video master toggle is independent of the photo one, so honor whichever mode is bound.
            val disabled = if (isVideoMode) settings.disableVideoCapture else settings.disableCameraCapture
            if (disabled) {
                Timber.i("CameraQuickCapture: capture globally disabled (video=%b) - aborting widget tap", isVideoMode)
                toastAndFinish(R.string.widget_camera_quick_capture_target_missing)
                return@launch
            }
            val loaded = loadTarget()
            if (loaded == null) {
                Timber.w("CameraQuickCapture: no/invalid target for appWidgetId=%d", appWidgetId)
                toastAndFinish(R.string.widget_camera_quick_capture_target_missing)
                return@launch
            }
            target = loaded
            withContext(Dispatchers.Main) { ensurePermissionAndCapture() }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            launchCaptureIntent()
        } else {
            toastAndFinish(R.string.camera_permission_required)
        }
    }

    /** Result from [CameraCaptureActivity]: RESULT_OK means the temp file holds the new photo. */
    fun onCaptureResult(resultCode: Int) {
        val tempFile = pendingTempFile
        val boundTarget = target
        if (resultCode != Activity.RESULT_OK || tempFile == null || boundTarget == null) {
            tempFile?.delete()
            pendingTempFile = null
            finish()
            return
        }
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
            if (settings.skipCameraFilenameDialog) {
                save(tempFile, defaultName, boundTarget)
            } else {
                withContext(Dispatchers.Main) {
                    showNameDialog(tempFile, defaultName, boundTarget)
                }
            }
        }
    }

    private fun ensurePermissionAndCapture() {
        if (hasCameraPermission()) {
            launchCaptureIntent()
        } else {
            requestPermission()
        }
    }

    private fun launchCaptureIntent() {
        // S0371: video uses the system ACTION_VIDEO_CAPTURE (handler-probed); photo keeps the in-app
        // CameraCaptureActivity path gated on camera hardware.
        if (isVideoMode) {
            val handlers = activity.packageManager.queryIntentActivities(Intent(MediaStore.ACTION_VIDEO_CAPTURE), 0)
            if (handlers.isEmpty()) {
                toastAndFinish(R.string.camera_capture_error_no_camera_app)
                return
            }
        } else if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toastAndFinish(R.string.camera_capture_error_no_camera_app)
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp) ?: run {
            toastAndFinish(R.string.camera_capture_error_temp_file)
            return
        }
        pendingTempFile = tempFile
        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", tempFile)
        } catch (t: Throwable) {
            Timber.e(t, "CameraQuickCapture: FileProvider.getUriForFile failed")
            tempFile.delete()
            pendingTempFile = null
            toastAndFinish(R.string.camera_capture_error_save_generic)
            return
        }
        val intent = if (isVideoMode) {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
        } else {
            CameraCaptureActivity.createIntent(activity, uri, tempFile.absolutePath)
        }
        launchCapture(intent)
    }

    private fun showNameDialog(tempFile: File, defaultName: String, boundTarget: CameraCaptureTarget) {
        val input = EditText(activity).apply { setText(defaultName); selectAll() }
        AlertDialog.Builder(activity)
            .setTitle(R.string.camera_capture_filename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { defaultName }
                coroutineScope.launch { save(tempFile, withCapturedExt(name, tempFile), boundTarget) }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete(); finish() }
            .setOnCancelListener { tempFile.delete(); finish() }
            .show()
    }

    private suspend fun save(tempFile: File, name: String, boundTarget: CameraCaptureTarget) {
        val result = cameraCaptureSaver.save(tempFile, name, boundTarget) { temp, fileName, resource ->
            uploadToNetworkTarget(temp, fileName, resource)
        }
        pendingTempFile = null
        withContext(Dispatchers.Main) {
            when (result) {
                is SaveResult.Success ->
                    toast(activity.getString(R.string.camera_capture_saved, name))
                SaveResult.Failure.Io -> toast(activity.getString(R.string.camera_capture_error_io))
                SaveResult.Failure.Generic ->
                    toast(activity.getString(R.string.camera_capture_error_save_generic))
            }
            finish()
        }
    }

    /** Reuse the app-wide copy backend so network/cloud saves match every other transfer path. */
    private suspend fun uploadToNetworkTarget(
        tempFile: File,
        name: String,
        resource: CameraCaptureTarget.Resource,
    ): Boolean {
        val destResource = MediaResource(
            id = resource.id,
            name = resource.name,
            path = resource.path,
            type = resource.type,
        )
        val sourceResource = MediaResource(
            id = -1L,
            name = tempFile.parentFile?.name ?: "temp",
            path = tempFile.parent ?: tempFile.absolutePath,
            type = ResourceType.LOCAL,
        )
        val sourceFile = MediaFile(
            name = name,
            path = tempFile.absolutePath,
            type = if (isVideoMode) MediaType.VIDEO else MediaType.IMAGE,
            size = tempFile.length(),
            createdDate = System.currentTimeMillis(),
        )
        return fileOperationHandler.executeCopy(sourceFile, sourceResource, destResource).isSuccess
    }

    private fun loadTarget(): CameraCaptureTarget? {
        val prefs = activity.getSharedPreferences(
            CameraQuickCaptureWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE,
        )
        if (prefs.getBoolean(CameraQuickCaptureWidgetProvider.keyTargetIsCameraFolder(appWidgetId), false)) {
            return CameraCaptureTarget.CameraFolder
        }
        val id = prefs.getLong(CameraQuickCaptureWidgetProvider.keyTargetId(appWidgetId), -1L)
        val path = prefs.getString(CameraQuickCaptureWidgetProvider.keyTargetPath(appWidgetId), null)
        val typeName = prefs.getString(CameraQuickCaptureWidgetProvider.keyTargetType(appWidgetId), null)
        val name = prefs.getString(CameraQuickCaptureWidgetProvider.keyTargetName(appWidgetId), null)
        if (id == -1L || path.isNullOrBlank() || typeName == null) return null
        val type = runCatching { ResourceType.valueOf(typeName) }.getOrNull() ?: return null
        return CameraCaptureTarget.Resource(id = id, name = name ?: path, path = path, type = type)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun createTemp(timestamp: String): File? = try {
        // S0371: video temps go to DIRECTORY_MOVIES as .mp4; photos stay .jpg in DIRECTORY_PICTURES.
        val dirType = if (isVideoMode) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val ext = if (isVideoMode) ".mp4" else ".jpg"
        val dir = activity.getExternalFilesDir(dirType) ?: activity.filesDir
        File(dir, "CAP_$timestamp$ext").also { it.createNewFile() }
    } catch (e: Exception) {
        Timber.e(e, "CameraQuickCapture: createTemp failed")
        null
    }

    /** Ensure [name] carries the extension of the recorded temp file (.mp4 for video, .jpg for photo). */
    private fun withCapturedExt(name: String, tempFile: File): String {
        val dotExt = ".${tempFile.extension}"
        return if (name.endsWith(dotExt, ignoreCase = true)) name else "$name$dotExt"
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun toastAndFinish(msgRes: Int) {
        toast(activity.getString(msgRes))
        finish()
    }
}
