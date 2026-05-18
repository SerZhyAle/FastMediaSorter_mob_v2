package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Handles folder picker requests and results for file copy/move operations.
 * Owns [pendingFolderPickerOp] state and bridges between [BrowseFileOperationsManager]
 * callbacks and the Activity's OpenDocumentTree launcher.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseFolderPickerHandler(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val fileOperationsManager: BrowseFileOperationsManager,
    private val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    private val onLaunchPicker: (Uri?) -> Unit
) {
    data class PendingFolderPickerOp(
        val operationType: FileOperationType,
        val sourceFiles: List<File>,
        val sourceCredentialsId: String?,
        val overwriteFiles: Boolean,
        val resource: MediaResource,
        val dirItems: List<MediaFile> = emptyList()
    )

    var pendingFolderPickerOp: PendingFolderPickerOp? = null

    /**
     * Called from [BrowseFileOperationsManager.FileOperationCallbacks.onFolderPickerRequested].
     * Stores the pending op and launches the OS folder picker.
     */
    fun requestFolderPick(
        operationType: FileOperationType,
        sourceFiles: List<File>,
        sourceCredentialsId: String?,
        resourceType: ResourceType,
        resource: MediaResource,
        dirItems: List<MediaFile>
    ) {
        coroutineScope.launch {
            val settings = try {
                settingsRepository.getSettings().first()
            } catch (e: Exception) {
                com.sza.fastmediasorter.domain.model.AppSettings()
            }
            val overwrite = if (operationType == FileOperationType.COPY)
                settings.overwriteOnCopy else settings.overwriteOnMove

            pendingFolderPickerOp = PendingFolderPickerOp(
                operationType = operationType,
                sourceFiles = sourceFiles,
                sourceCredentialsId = sourceCredentialsId,
                overwriteFiles = overwrite,
                resource = resource,
                dirItems = dirItems
            )

            val initialUri = settings.lastSelectedLocalFolder?.let {
                try { Uri.parse(it) } catch (e: Exception) { null }
            }

            if (resourceType != ResourceType.LOCAL) {
                Toast.makeText(activity, activity.getString(R.string.select_folder_network_hint), Toast.LENGTH_SHORT).show()
            }

            try {
                onLaunchPicker(initialUri)
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch folder picker")
                Toast.makeText(activity, activity.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                pendingFolderPickerOp = null
            }
        }
    }

    /**
     * Called from the Activity's OpenDocumentTree ActivityResultCallback with the selected URI.
     */
    fun onFolderPicked(uri: Uri?) {
        if (uri == null) {
            Timber.d("folderPickerLauncher: user cancelled folder picker")
            return
        }

        Timber.i("folderPickerLauncher: selected uri=$uri")
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Timber.w(e, "takePersistableUriPermission failed (non-fatal)")
        }

        val path = com.sza.fastmediasorter.core.util.UriPathResolver.getPath(activity, uri) ?: uri.path
        if (path == null) {
            Timber.w("folderPickerLauncher: could not resolve path from uri=$uri")
            Toast.makeText(activity, activity.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
            return
        }

        val destDir = java.io.File(path)
        if (!destDir.exists() || !destDir.canWrite()) {
            Timber.w("folderPickerLauncher: path=$path is not writable")
            Toast.makeText(activity, activity.getString(R.string.error_folder_not_writable), Toast.LENGTH_LONG).show()
            pendingFolderPickerOp = null
            return
        }

        val op = pendingFolderPickerOp ?: return
        pendingFolderPickerOp = null

        // Persist last selected folder URI
        coroutineScope.launch {
            try {
                val current = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(current.copy(lastSelectedLocalFolder = uri.toString()))
            } catch (e: Exception) {
                Timber.w(e, "Failed to save last local folder")
            }
        }

        fileOperationsManager.executeOperationToPath(
            operationType = op.operationType,
            sourceFiles = op.sourceFiles,
            destinationPath = path,
            sourceCredentialsId = op.sourceCredentialsId,
            overwriteFiles = op.overwriteFiles
        )

        if (op.dirItems.isNotEmpty()) {
            coroutineScope.launch {
                var dirSucceeded = 0
                var dirFailed = 0
                for (dir in op.dirItems) {
                    val result = when (op.operationType) {
                        FileOperationType.COPY -> unifiedFileOperationHandler.executeCopyDirectory(dir.path, path)
                        FileOperationType.MOVE -> unifiedFileOperationHandler.executeMoveDirectory(dir.path, path)
                        else -> Result.failure(IllegalArgumentException("Unsupported dir op: ${op.operationType}"))
                    }
                    result.onSuccess { dirSucceeded++ }
                        .onFailure { e ->
                            Timber.e(e, "folderPickerLauncher: dir op failed for ${dir.path}")
                            dirFailed++
                        }
                }
                if (dirFailed > 0) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.error_some_operations_failed, dirFailed, dirSucceeded + dirFailed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
