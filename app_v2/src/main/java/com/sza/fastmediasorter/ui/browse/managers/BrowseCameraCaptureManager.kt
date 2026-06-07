package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.CameraCaptureTarget
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
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

class BrowseCameraCaptureManager(
    private val activity: FragmentActivity,
    private val settingsRepository: SettingsRepository,
    // S0367/S0375: resolves the user-configured capture destination resource ids (String in
    // settings) to MediaResource instances for the shared browse capture path.
    private val resourceRepository: ResourceRepository,
    private val coroutineScope: CoroutineScope,
    private val cameraCaptureSaver: CameraCaptureSaver,
    private val onFileSaved: (fileName: String) -> Unit,
    private val onCapturedForEditing: (path: String, resourceId: Long) -> Unit,
    // S0371: video capture completion. Distinct from [onFileSaved] so the host can honor the
    // open-in-player setting (never the drawing editor) after a recording is saved.
    private val onVideoCaptured: (fileName: String) -> Unit,
    private val onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean
) {

    // region Fields & launcher

    private var pendingTempFile: File? = null
    private var pendingResource: MediaResource? = null
    // S0371: true when the in-flight capture is a video recording (explicit launchVideo, or the
    // media-type auto-decision in launch() for a video-only resource). Drives the save outcome:
    // video -> host video callback (optional open-in-player); photo -> existing onFileSaved/editor.
    private var pendingIsVideo: Boolean = false

    val launcher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.i(
                "S0022-CAM: launcher callback fired resultCode=%d data=%s extras=%s",
                result.resultCode,
                result.data?.dataString,
                result.data?.extras?.keySet()?.joinToString(),
            )
            try {
                handleResult(result)
            } catch (t: Throwable) {
                Timber.e(t, "S0022-CAM: handleResult threw - captured to prevent crash")
                showSnackbar(R.string.camera_capture_error_save_generic)
            }
        }

    // endregion

    // region Public API

    fun launch(resource: MediaResource) {
        Timber.d("S0359: in-app camera capture to resource")
        Timber.i(
            "S0022-CAM: launch ENTRY resource={id=%d, name=%s, type=%s, path=%s, allFiles=%b} device={mfr=%s, model=%s, sdk=%d}",
            resource.id,
            resource.name,
            resource.type,
            resource.path,
            resource.allFiles,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.SDK_INT,
        )
        pendingResource = resource
        // S0371 follow-up: this is the photo command only - video recording has its own command
        // (launchVideo). The photo command is gated to image-capable resources, so capture is always
        // in-app CameraX photo and never auto-routes to a system video recording.
        pendingIsVideo = false
        val ext = ".jpg"
        val action = "in-app-photo"
        Timber.i(
            "S0022-CAM: launch resolved action=%s ext=%s supportedMediaTypes=%s",
            action,
            ext,
            resource.supportedMediaTypes.joinToString(),
        )

        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Timber.w("S0022-CAM: launch ABORT - no camera hardware available for in-app photo capture")
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            pendingResource = null
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp, ext) ?: run {
            Timber.w("S0022-CAM: launch ABORT - createTemp returned null")
            showSnackbar(R.string.camera_capture_error_temp_file)
            pendingResource = null
            return
        }
        Timber.i("S0022-CAM: launch tempFile created path=%s exists=%b", tempFile.absolutePath, tempFile.exists())
        pendingTempFile = tempFile

        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", tempFile)
        } catch (e: SecurityException) {
            Timber.e(e, "S0022-CAM: FileProvider.getUriForFile denied authority=%s.fileprovider", activity.packageName)
            showSnackbar(R.string.camera_capture_error_permission_denied)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            return
        } catch (t: Throwable) {
            Timber.e(t, "S0022-CAM: FileProvider.getUriForFile FAILED authority=%s.fileprovider", activity.packageName)
            showSnackbar(R.string.camera_capture_error_save_generic)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            return
        }
        Timber.i("S0022-CAM: launch FileProvider uri=%s", uri)

        // In-app photo capture removes the OEM confirmation step before returning to Browse.
        val intent = CameraCaptureActivity.createIntent(activity, uri, tempFile.absolutePath)
        try {
            Timber.i("S0022-CAM: launch dispatching launcher.launch(intent) action=%s", action)
            launcher.launch(intent)
            Timber.i("S0022-CAM: launch dispatched launcher.launch(intent) - awaiting result")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "S0022-CAM: launcher.launch threw ActivityNotFoundException action=%s", action)
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
        } catch (e: SecurityException) {
            Timber.e(e, "S0022-CAM: launcher.launch threw SecurityException action=%s", action)
            showSnackbar(R.string.camera_capture_error_permission_denied)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
        } catch (t: Throwable) {
            Timber.e(t, "S0022-CAM: launcher.launch threw %s action=%s", t.javaClass.simpleName, action)
            showSnackbar(R.string.camera_capture_error_save_generic)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
        }
    }

    /**
     * S0371: explicit "record video into this resource" entry point, independent of the resource's
     * media-type auto-decision. Always takes the system [MediaStore.ACTION_VIDEO_CAPTURE] path with a
     * `.mp4` temp in DIRECTORY_MOVIES and saves through the shared [CameraCaptureSaver]. The capture
     * outcome is routed to the video contract (no editor handoff; optional open-in-player).
     */
    fun launchVideo(resource: MediaResource) {
        Timber.i(
            "VideoCapture: launchVideo ENTRY resource={id=%d, name=%s, type=%s, path=%s} device={mfr=%s, model=%s, sdk=%d}",
            resource.id,
            resource.name,
            resource.type,
            resource.path,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.SDK_INT,
        )
        pendingResource = resource
        pendingIsVideo = true

        val handlers = activity.packageManager.queryIntentActivities(Intent(MediaStore.ACTION_VIDEO_CAPTURE), 0)
        if (handlers.isEmpty()) {
            Timber.w("VideoCapture: launchVideo ABORT - no Activity handles %s on this device", MediaStore.ACTION_VIDEO_CAPTURE)
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            pendingResource = null
            pendingIsVideo = false
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp, ".mp4") ?: run {
            Timber.w("VideoCapture: launchVideo ABORT - createTemp returned null")
            showSnackbar(R.string.camera_capture_error_temp_file)
            pendingResource = null
            pendingIsVideo = false
            return
        }
        pendingTempFile = tempFile

        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", tempFile)
        } catch (t: Throwable) {
            Timber.e(t, "VideoCapture: FileProvider.getUriForFile FAILED authority=%s.fileprovider", activity.packageName)
            showSnackbar(R.string.camera_capture_error_save_generic)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            pendingIsVideo = false
            return
        }

        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
        try {
            launcher.launch(intent)
            Timber.i("VideoCapture: launchVideo dispatched launcher.launch(intent) - awaiting result")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "VideoCapture: launcher.launch threw ActivityNotFoundException")
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            pendingIsVideo = false
        } catch (e: SecurityException) {
            Timber.e(e, "VideoCapture: launcher.launch threw SecurityException")
            showSnackbar(R.string.camera_capture_error_permission_denied)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            pendingIsVideo = false
        } catch (t: Throwable) {
            Timber.e(t, "VideoCapture: launcher.launch threw %s", t.javaClass.simpleName)
            showSnackbar(R.string.camera_capture_error_save_generic)
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            pendingIsVideo = false
        }
    }

    /**
     * Persist pending capture context before the host Activity goes to background.
     * Called from BrowseActivity.onSaveInstanceState so the context survives process death.
     */
    fun saveState(outState: Bundle) {
        pendingTempFile?.absolutePath?.let { outState.putString(KEY_TEMP_FILE, it) }
        pendingResource?.id?.let { outState.putLong(KEY_RESOURCE_ID, it) }
        // S0371: persist the capture mode so the save outcome stays video/photo-correct after a kill.
        outState.putBoolean(KEY_IS_VIDEO, pendingIsVideo)
        Timber.d("S0022-CAM: saveState tempFile=%s resourceId=%s isVideo=%b", pendingTempFile?.absolutePath, pendingResource?.id, pendingIsVideo)
    }

    /**
     * Restore pending capture context after process death.
     * [getResourceById] looks up the resource from the ViewModel/repository by its persisted id.
     */
    fun restoreState(savedState: Bundle, getResourceById: (Long) -> MediaResource?) {
        val path = savedState.getString(KEY_TEMP_FILE) ?: return
        val file = File(path)
        if (!file.exists()) {
            // Temp file was cleaned up by the OS - inform user and bail.
            Timber.w("S0022-CAM: restoreState tempFile missing after process death path=%s", path)
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val resourceId = savedState.getLong(KEY_RESOURCE_ID, -1L)
        val resource = if (resourceId != -1L) getResourceById(resourceId) else null
        if (resource == null) {
            Timber.w("S0022-CAM: restoreState resource not found id=%d - aborting, deleting tempFile", resourceId)
            file.delete()
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        pendingTempFile = file
        pendingResource = resource
        pendingIsVideo = savedState.getBoolean(KEY_IS_VIDEO, false)
        Timber.i("S0022-CAM: restoreState restored tempFile=%s resource=%s isVideo=%b", path, resource.name, pendingIsVideo)
    }

    // endregion

    // region Result handling

    private fun handleResult(result: ActivityResult) {
        Timber.i(
            "S0022-CAM: handleResult ENTRY resultCode=%d (RESULT_OK=%d, RESULT_CANCELED=%d) pendingTempFile=%s pendingResource=%s",
            result.resultCode,
            Activity.RESULT_OK,
            Activity.RESULT_CANCELED,
            pendingTempFile?.absolutePath,
            pendingResource?.name,
        )
        val tempFile = pendingTempFile ?: run {
            // Process death between launch and result - context is gone.
            Timber.w("S0022-CAM: handleResult ABORT - pendingTempFile is null (process death?)")
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val resource = pendingResource ?: run {
            Timber.w("S0022-CAM: handleResult ABORT - pendingResource is null (process death?)")
            tempFile.delete()
            pendingTempFile = null
            pendingIsVideo = false
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        // S0371: snapshot the capture mode now; the rename dialog is async and pendingIsVideo may be
        // overwritten by a subsequent capture before the user commits the name.
        val isVideo = pendingIsVideo
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.i(
                "S0022-CAM: handleResult NON-OK resultCode=%d - deleting tempFile=%s and returning quietly",
                result.resultCode,
                tempFile.absolutePath,
            )
            tempFile.delete()
            pendingTempFile = null
            pendingIsVideo = false
            return
        }
        Timber.i("S0022-CAM: handleResult OK - proceeding to save flow tempFile=%s size=%d isVideo=%b", tempFile.absolutePath, tempFile.length(), isVideo)
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
            // Video recordings never open the drawing editor (cameraCaptureOpenForEditing is photo-only).
            val openForEditing = !isVideo && settings.cameraCaptureOpenForEditing
            Timber.i("S0022-CAM: handleResult settings.skipCameraFilenameDialog=%b defaultName=%s", settings.skipCameraFilenameDialog, defaultName)
            if (settings.skipCameraFilenameDialog) {
                save(tempFile, defaultName, resource, openForEditing, isVideo)
            } else {
                withContext(Dispatchers.Main) {
                    showNameDialog(tempFile, defaultName, resource, openForEditing, isVideo)
                }
            }
        }
    }

    private fun showNameDialog(
        tempFile: File,
        defaultName: String,
        resource: MediaResource,
        openForEditing: Boolean,
        isVideo: Boolean,
    ) {
        val input = EditText(activity).apply { setText(defaultName); selectAll() }
        AlertDialog.Builder(activity)
            .setTitle(R.string.camera_capture_filename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { defaultName }
                coroutineScope.launch {
                    save(tempFile, withExt(name, tempFile.extension), resource, openForEditing, isVideo)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete() }
            .setOnCancelListener { tempFile.delete() }
            .show()
    }

    // endregion

    // region Save routing

    /**
     * Delegate the target-routing save to the shared [CameraCaptureSaver] (S0369). The saver owns
     * local/DCIM writes and deletes the temp file; this method maps the result back to the existing
     * snackbar + open-for-editing UI so Browse behaviour is unchanged. The [onUploadFile] callback
     * (network/cloud copy strategies) is bridged through the saver's [upload] hook.
     *
     * S0371: when [isVideo] is true the success path routes to [onVideoCaptured] (reload + optional
     * open-in-player, decided by the host) and never to the drawing editor.
     */
    private suspend fun save(
        tempFile: File,
        name: String,
        resource: MediaResource,
        openForEditing: Boolean,
        isVideo: Boolean,
    ) {
        // S0367: a real browsed folder always saves into itself (unchanged). Only when the capture
        // would otherwise land in DCIM/Camera - i.e. the browsed resource is a virtual/camera target
        // and is not a usable on-device folder - is the configured camera-photos destination honoured.
        val target = if (isVideo) {
            resolveVideoSaveTarget(resource)
        } else {
            resolveCameraSaveTarget(resource)
        }
        val editorResourceId = target.id
        val result = cameraCaptureSaver.save(tempFile, name, target) { temp, fileName, uploadTarget ->
            onUploadFile(temp, fileName, uploadTarget.toMediaResource())
        }
        pendingTempFile = null
        pendingIsVideo = false
        withContext(Dispatchers.Main) {
            when (result) {
                is SaveResult.Success -> {
                    showSnackbar(activity.getString(R.string.camera_capture_saved, name))
                    when {
                        isVideo -> onVideoCaptured(name)
                        openForEditing ->
                            // Reuse the existing drawing editor instead of introducing a parallel edit flow.
                            // S0367: hand the editor the resource the file actually landed in.
                            onCapturedForEditing(result.savedPath, editorResourceId)
                        else -> onFileSaved(name)
                    }
                }
                SaveResult.Failure.Io -> showSnackbar(R.string.camera_capture_error_io)
                SaveResult.Failure.Generic -> showSnackbar(R.string.camera_capture_error_save_generic)
            }
        }
    }

    // endregion

    // region Helpers

    /**
     * S0367: pick the resource the capture should be saved into.
     *
     * Non-breaking override semantics:
     * - A real, writable, on-device browsed folder (`CaptureDestinationPolicy.isUsableTarget` true)
     *   always saves into itself - the configured destination never redirects a normal folder capture.
     * - Only when the browsed resource is a virtual/camera-folder target (the path that
     *   [CameraCaptureSaver] would route to DCIM/Camera) is the configured camera-photos destination
     *   consulted: a non-blank id that resolves to a usable target replaces [browsedResource];
     *   an unset/stale/invalid selection degrades to [browsedResource] (-> DCIM/Camera), unchanged.
     */
    private suspend fun resolveCameraSaveTarget(browsedResource: MediaResource): CameraCaptureTarget.Resource {
        if (CaptureDestinationPolicy.isUsableTarget(browsedResource)) return browsedResource.toCaptureTarget()
        val configuredId = settingsRepository.getSettings().first()
            .cameraPhotosDestinationResourceId
            ?.toLongOrNull()
            ?: return browsedResource.toCaptureTarget()
        val configured = resourceRepository.getResourceById(configuredId)
        return if (CaptureDestinationPolicy.isUsableTarget(configured)) {
            Timber.i(
                "CameraCapture: redirecting virtual/camera capture to configured destination id=%d name=%s",
                configured!!.id,
                configured.name,
            )
            configured.toCaptureTarget()
        } else {
            browsedResource.toCaptureTarget()
        }
    }

    /**
     * S0375: pick the target the video recording should be saved into.
     *
     * Non-breaking override semantics:
     * - A real, writable browsed folder stays primary - the recording still saves directly there.
     * - Otherwise a configured `videoRecordingDestinationResourceId` that resolves to a usable
     *   target wins.
     * - If neither target is usable, the save degrades to the public Movies folder.
     */
    private suspend fun resolveVideoSaveTarget(browsedResource: MediaResource): CameraCaptureTarget.Resource {
        if (CaptureDestinationPolicy.isUsableTarget(browsedResource)) return browsedResource.toCaptureTarget()
        val configuredId = settingsRepository.getSettings().first()
            .videoRecordingDestinationResourceId
            ?.toLongOrNull()
        if (configuredId != null) {
            val configured = resourceRepository.getResourceById(configuredId)
            if (CaptureDestinationPolicy.isUsableTarget(configured)) {
                Timber.i(
                    "VideoCapture: redirecting recording to configured destination id=%d name=%s",
                    configured!!.id,
                    configured.name,
                )
                return configured.toCaptureTarget()
            }
        }
        val moviesDir = CaptureDestinationPolicy.resolveVideoDestination(null).also { it.mkdirs() }
        return CameraCaptureTarget.Resource(
            id = -1L,
            name = Environment.DIRECTORY_MOVIES,
            path = moviesDir.absolutePath,
            type = ResourceType.LOCAL,
        )
    }

    private fun MediaResource.toCaptureTarget(): CameraCaptureTarget.Resource = CameraCaptureTarget.Resource(
        id = id,
        name = name,
        path = path,
        type = type,
    )

    private fun CameraCaptureTarget.Resource.toMediaResource(): MediaResource = MediaResource(
        id = id,
        name = name,
        path = path,
        type = type,
    )

    /** Show a Snackbar anchored to the Activity's decor root. Must be called on Main thread. */
    private fun showSnackbar(msgRes: Int) {
        Snackbar.make(activity.window.decorView.rootView, msgRes, Snackbar.LENGTH_LONG).show()
    }

    /** Show a Snackbar with a pre-formatted string. Must be called on Main thread. */
    private fun showSnackbar(message: String) {
        Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun createTemp(timestamp: String, ext: String): File? = try {
        val dir = activity.getExternalFilesDir(
            if (ext == ".mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        ) ?: activity.filesDir
        File(dir, "CAP_$timestamp$ext").also { it.createNewFile() }
    } catch (e: Exception) {
        Timber.e(e, "BrowseCameraCaptureManager: createTemp failed")
        null
    }

    private fun withExt(name: String, ext: String): String {
        val dotExt = if (ext.startsWith(".")) ext else ".$ext"
        return if (name.endsWith(dotExt, ignoreCase = true)) name else "$name$dotExt"
    }

    // endregion

    companion object {
        private const val KEY_TEMP_FILE = "cam_pending_temp_file"
        private const val KEY_RESOURCE_ID = "cam_pending_resource_id"
        // S0371: persisted capture-mode flag so the save outcome survives process death.
        private const val KEY_IS_VIDEO = "cam_pending_is_video"

        /**
         * Returns true if the device has camera hardware able to back the in-app photo capture.
         * Should be called before showing the camera (photo) command in a menu so that the command
         * is invisible on devices without a camera.
         *
         * Logs a warning when no camera is present - callers rely on this side-effect for tracing.
         */
        fun hasCameraHandler(context: Context): Boolean {
            // S0371 follow-up: the camera command is in-app photo only (video has its own command),
            // so availability is purely a camera-hardware question.
            val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            if (!hasCamera) {
                Timber.w("CameraCapture: no camera hardware, command hidden for in-app photo capture")
            }
            return hasCamera
        }

        /**
         * S0371: true if at least one Activity handles [MediaStore.ACTION_VIDEO_CAPTURE]. Independent
         * of the resource's media-type auto-decision so the explicit record-video command can be
         * shown for mixed resources too (where [hasCameraHandler] would check camera hardware for the
         * photo path instead). Call before showing the video-capture command in a menu.
         */
        fun hasVideoCaptureHandler(context: Context): Boolean {
            val handlers = context.packageManager.queryIntentActivities(Intent(MediaStore.ACTION_VIDEO_CAPTURE), 0)
            if (handlers.isEmpty()) {
                Timber.w("CameraCapture: no handlers, video-capture command hidden action=%s", MediaStore.ACTION_VIDEO_CAPTURE)
            }
            return handlers.isNotEmpty()
        }
    }
}
