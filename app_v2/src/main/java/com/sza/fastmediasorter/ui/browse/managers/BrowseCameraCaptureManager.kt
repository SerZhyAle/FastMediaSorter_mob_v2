package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
            handleResult(result)
        }

    // endregion

    // region Public API

    fun launch(resource: MediaResource) {
        pendingResource = resource
        val captureVideo = resource.supportedMediaTypes.let { types ->
            !resource.allFiles &&
                types.none { it == MediaType.IMAGE || it == MediaType.GIF } &&
                types.any { it == MediaType.VIDEO }
        }
        val ext = if (captureVideo) ".mp4" else ".jpg"
        val action = if (captureVideo) MediaStore.ACTION_VIDEO_CAPTURE
                     else MediaStore.ACTION_IMAGE_CAPTURE
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTemp(timestamp, ext) ?: run {
            Toast.makeText(activity, R.string.camera_capture_error_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        pendingTempFile = tempFile
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", tempFile)
        launcher.launch(Intent(action).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) })
    }

    // endregion

    // region Result handling

    private fun handleResult(result: ActivityResult) {
        val tempFile = pendingTempFile ?: return
        val resource = pendingResource ?: return
        if (result.resultCode != Activity.RESULT_OK) {
            tempFile.delete()
            pendingTempFile = null
            return
        }
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
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
        val success = try {
            if (isVirtualCameraTarget) saveToDcim(tempFile, name)
            else when (resource.type) {
                ResourceType.LOCAL -> saveLocal(tempFile, name, path)
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP,
                ResourceType.CLOUD -> onUploadFile(tempFile, name, resource)
            }
        } catch (e: Exception) {
            Timber.e(e, "BrowseCameraCaptureManager: save failed")
            false
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
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
