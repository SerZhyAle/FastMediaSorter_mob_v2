package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowseCameraCaptureManager(
    private val activity: FragmentActivity,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onFileSaved: (fileName: String) -> Unit,
    private val onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean
) {

    // region Fields & launcher

    private var pendingTempFile: File? = null
    private var pendingResource: MediaResource? = null

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
                Timber.e(t, "S0022-CAM: handleResult threw — captured to prevent crash")
                showSnackbar(R.string.camera_capture_error_save_generic)
            }
        }

    // endregion

    // region Public API

    fun launch(resource: MediaResource) {
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
        val captureVideo = resource.supportedMediaTypes.let { types ->
            !resource.allFiles &&
                types.none { it == MediaType.IMAGE || it == MediaType.GIF } &&
                types.any { it == MediaType.VIDEO }
        }
        val ext = if (captureVideo) ".mp4" else ".jpg"
        val action = if (captureVideo) MediaStore.ACTION_VIDEO_CAPTURE
                     else MediaStore.ACTION_IMAGE_CAPTURE
        Timber.i(
            "S0022-CAM: launch resolved captureVideo=%b ext=%s action=%s supportedMediaTypes=%s",
            captureVideo,
            ext,
            action,
            resource.supportedMediaTypes.joinToString(),
        )

        // Check for a camera handler BEFORE creating the temp file so that no disk artifact is
        // left behind on devices without a camera app (e.g. Quest 3 / HorizonOS).
        val probeIntent = Intent(action)
        val handlers = activity.packageManager.queryIntentActivities(probeIntent, 0)
        Timber.i(
            "S0022-CAM: launch packageManager.queryIntentActivities action=%s handlers=%d list=%s",
            action,
            handlers.size,
            handlers.joinToString { "${it.activityInfo?.packageName}/${it.activityInfo?.name}" },
        )
        if (handlers.isEmpty()) {
            Timber.w("S0022-CAM: launch ABORT — no Activity handles %s on this device", action)
            showSnackbar(R.string.camera_capture_error_no_camera_app)
            pendingResource = null
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp, ext) ?: run {
            Timber.w("S0022-CAM: launch ABORT — createTemp returned null")
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

        val intent = Intent(action).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
        try {
            Timber.i("S0022-CAM: launch dispatching launcher.launch(intent) action=%s", action)
            launcher.launch(intent)
            Timber.i("S0022-CAM: launch dispatched launcher.launch(intent) — awaiting result")
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
     * Persist pending capture context before the host Activity goes to background.
     * Called from BrowseActivity.onSaveInstanceState so the context survives process death.
     */
    fun saveState(outState: Bundle) {
        pendingTempFile?.absolutePath?.let { outState.putString(KEY_TEMP_FILE, it) }
        pendingResource?.id?.let { outState.putLong(KEY_RESOURCE_ID, it) }
        Timber.d("S0022-CAM: saveState tempFile=%s resourceId=%s", pendingTempFile?.absolutePath, pendingResource?.id)
    }

    /**
     * Restore pending capture context after process death.
     * [getResourceById] looks up the resource from the ViewModel/repository by its persisted id.
     */
    fun restoreState(savedState: Bundle, getResourceById: (Long) -> MediaResource?) {
        val path = savedState.getString(KEY_TEMP_FILE) ?: return
        val file = File(path)
        if (!file.exists()) {
            // Temp file was cleaned up by the OS — inform user and bail.
            Timber.w("S0022-CAM: restoreState tempFile missing after process death path=%s", path)
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val resourceId = savedState.getLong(KEY_RESOURCE_ID, -1L)
        val resource = if (resourceId != -1L) getResourceById(resourceId) else null
        if (resource == null) {
            Timber.w("S0022-CAM: restoreState resource not found id=%d — aborting, deleting tempFile", resourceId)
            file.delete()
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        pendingTempFile = file
        pendingResource = resource
        Timber.i("S0022-CAM: restoreState restored tempFile=%s resource=%s", path, resource.name)
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
            // Process death between launch and result — context is gone.
            Timber.w("S0022-CAM: handleResult ABORT — pendingTempFile is null (process death?)")
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        val resource = pendingResource ?: run {
            Timber.w("S0022-CAM: handleResult ABORT — pendingResource is null (process death?)")
            tempFile.delete()
            pendingTempFile = null
            showSnackbar(R.string.camera_capture_error_session_expired)
            return
        }
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.i(
                "S0022-CAM: handleResult NON-OK resultCode=%d — deleting tempFile=%s and returning quietly",
                result.resultCode,
                tempFile.absolutePath,
            )
            tempFile.delete()
            pendingTempFile = null
            return
        }
        Timber.i("S0022-CAM: handleResult OK — proceeding to save flow tempFile=%s size=%d", tempFile.absolutePath, tempFile.length())
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
            Timber.i("S0022-CAM: handleResult settings.skipCameraFilenameDialog=%b defaultName=%s", settings.skipCameraFilenameDialog, defaultName)
            if (settings.skipCameraFilenameDialog) {
                save(tempFile, defaultName, resource)
            } else {
                withContext(Dispatchers.Main) { showNameDialog(tempFile, defaultName, resource) }
            }
        }
    }

    private fun showNameDialog(tempFile: File, defaultName: String, resource: MediaResource) {
        val input = EditText(activity).apply { setText(defaultName); selectAll() }
        AlertDialog.Builder(activity)
            .setTitle(R.string.camera_capture_filename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { defaultName }
                coroutineScope.launch { save(tempFile, withExt(name, tempFile.extension), resource) }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete() }
            .setOnCancelListener { tempFile.delete() }
            .show()
    }

    // endregion

    // region Save routing

    private suspend fun save(tempFile: File, name: String, resource: MediaResource) {
        val path = resource.path
        val isVirtualCameraTarget = VirtualPathUtils.isVirtualPath(path) ||
            path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
            path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
            path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
        Timber.i(
            "S0022-CAM: save ENTRY tempFile=%s name=%s resource={type=%s, path=%s} isVirtualCameraTarget=%b",
            tempFile.absolutePath,
            name,
            resource.type,
            path,
            isVirtualCameraTarget,
        )
        val success = try {
            if (isVirtualCameraTarget) saveToDcim(tempFile, name)
            else when (resource.type) {
                ResourceType.LOCAL -> saveLocal(tempFile, name, path)
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP,
                ResourceType.CLOUD -> onUploadFile(tempFile, name, resource)
            }
        } catch (e: IOException) {
            Timber.e(e, "S0022-CAM: save IO error resource.type=%s path=%s", resource.type, path)
            false
        } catch (e: Exception) {
            Timber.e(e, "S0022-CAM: save FAILED resource.type=%s path=%s", resource.type, path)
            false
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
        Timber.i("S0022-CAM: save EXIT success=%b name=%s", success, name)
        withContext(Dispatchers.Main) {
            if (success) {
                showSnackbar(activity.getString(R.string.camera_capture_saved, name))
                onFileSaved(name)
            } else {
                showSnackbar(R.string.camera_capture_error_save_generic)
            }
        }
    }

    private suspend fun saveToDcim(tempFile: File, name: String): Boolean =
        withContext(Dispatchers.IO) {
            val cameraDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"
            ).also { it.mkdirs() }
            tempFile.copyTo(File(cameraDir, name), overwrite = true)
            @Suppress("DEPRECATION")
            activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(cameraDir, name))))
            true
        }

    private suspend fun saveLocal(tempFile: File, name: String, rootPath: String): Boolean =
        withContext(Dispatchers.IO) {
            tempFile.copyTo(File(rootPath, name), overwrite = true)
            true
        }

    // endregion

    // region Helpers

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

        /**
         * Returns true if the system has at least one Activity that can handle the capture intent
         * for [resource]. Should be called before showing the camera capture command in a menu so
         * that the command is invisible on devices without a camera app (e.g. Quest 3 / HorizonOS).
         *
         * Logs a warning when no handlers are found — callers rely on this side-effect for tracing.
         */
        fun hasCameraHandler(context: Context, resource: MediaResource): Boolean {
            val captureVideo = resource.supportedMediaTypes.let { types ->
                !resource.allFiles &&
                    types.none { it == MediaType.IMAGE || it == MediaType.GIF } &&
                    types.any { it == MediaType.VIDEO }
            }
            val action = if (captureVideo) MediaStore.ACTION_VIDEO_CAPTURE
                         else MediaStore.ACTION_IMAGE_CAPTURE
            val handlers = context.packageManager.queryIntentActivities(Intent(action), 0)
            if (handlers.isEmpty()) {
                // Separate marker so visibility decisions are traceable independently of launch().
                Timber.w("CameraCapture: no handlers, command hidden action=%s", action)
            }
            return handlers.isNotEmpty()
        }
    }
}
