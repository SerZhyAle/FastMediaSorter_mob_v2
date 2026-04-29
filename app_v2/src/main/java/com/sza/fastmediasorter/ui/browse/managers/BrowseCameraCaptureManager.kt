package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
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
                Toast.makeText(
                    activity,
                    "Camera capture handler failed: ${t.javaClass.simpleName}: ${t.message}",
                    Toast.LENGTH_LONG,
                ).show()
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
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp, ext) ?: run {
            Timber.w("S0022-CAM: launch ABORT — createTemp returned null")
            Toast.makeText(activity, R.string.camera_capture_error_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        Timber.i("S0022-CAM: launch tempFile created path=%s exists=%b", tempFile.absolutePath, tempFile.exists())
        pendingTempFile = tempFile
        val uri = try {
            FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", tempFile)
        } catch (t: Throwable) {
            Timber.e(t, "S0022-CAM: FileProvider.getUriForFile FAILED authority=%s.fileprovider", activity.packageName)
            Toast.makeText(
                activity,
                "FileProvider misconfigured: ${t.javaClass.simpleName}: ${t.message}",
                Toast.LENGTH_LONG,
            ).show()
            tempFile.delete()
            pendingTempFile = null
            return
        }
        Timber.i("S0022-CAM: launch FileProvider uri=%s", uri)

        val intent = Intent(action).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
        // S0022 diagnostic: enumerate intent handlers BEFORE dispatch — Quest 3 has no built-in
        // camera activity, so a missing handler is the most likely root cause of the user-reported
        // "error dialog without a process crash" symptom.
        val handlers = activity.packageManager.queryIntentActivities(intent, 0)
        Timber.i(
            "S0022-CAM: launch packageManager.queryIntentActivities action=%s handlers=%d list=%s",
            action,
            handlers.size,
            handlers.joinToString { "${it.activityInfo?.packageName}/${it.activityInfo?.name}" },
        )
        if (handlers.isEmpty()) {
            Timber.w("S0022-CAM: launch ABORT — no Activity handles %s on this device", action)
            Toast.makeText(
                activity,
                activity.getString(R.string.camera_capture_error_save_generic) +
                    " (no camera app for $action)",
                Toast.LENGTH_LONG,
            ).show()
            tempFile.delete()
            pendingTempFile = null
            return
        }

        try {
            Timber.i("S0022-CAM: launch dispatching launcher.launch(intent) action=%s", action)
            launcher.launch(intent)
            Timber.i("S0022-CAM: launch dispatched launcher.launch(intent) — awaiting result")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "S0022-CAM: launcher.launch threw ActivityNotFoundException action=%s", action)
            Toast.makeText(
                activity,
                "Camera unavailable: ${e.message}",
                Toast.LENGTH_LONG,
            ).show()
            tempFile.delete()
            pendingTempFile = null
        } catch (t: Throwable) {
            Timber.e(t, "S0022-CAM: launcher.launch threw %s action=%s", t.javaClass.simpleName, action)
            Toast.makeText(
                activity,
                "Camera dispatch failed: ${t.javaClass.simpleName}: ${t.message}",
                Toast.LENGTH_LONG,
            ).show()
            tempFile.delete()
            pendingTempFile = null
        }
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
            Timber.w("S0022-CAM: handleResult ABORT — pendingTempFile is null (process death between launch and result?)")
            return
        }
        val resource = pendingResource ?: run {
            Timber.w("S0022-CAM: handleResult ABORT — pendingResource is null (process death between launch and result?)")
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
        } catch (e: Exception) {
            Timber.e(e, "S0022-CAM: save FAILED resource.type=%s path=%s", resource.type, path)
            false
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
        Timber.i("S0022-CAM: save EXIT success=%b name=%s", success, name)
        withContext(Dispatchers.Main) {
            val msg = if (success) activity.getString(R.string.camera_capture_saved, name)
                      else activity.getString(R.string.camera_capture_error_save_generic)
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            if (success) onFileSaved(name)
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
}
