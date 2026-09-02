package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.util.UriPathResolver
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.FileOperationsHandler
import com.sza.fastmediasorter.utils.SafHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Handles folder-picker requests from player Copy/Move panels.
 * Stores the pending operation, launches OpenDocumentTree, resolves the returned SAF URI
 * to a real File path via UriPathResolver, and delegates to FileOperationsHandler.
 *
 * Modelled after BrowseFolderPickerHandler - same URI resolution and persistence flow.
 */
class PlayerFolderPickerHandler(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val fileOperationsHandler: FileOperationsHandler,
    private val restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy,
    private val onLaunchPicker: (Uri?) -> Unit
) {
    data class PendingOp(
        val operationType: FileOperationType,
        val sourceCredentialsId: String?
    )

    var pendingOp: PendingOp? = null

    fun requestFolderPick(operationType: FileOperationType, sourceCredentialsId: String?) {
        coroutineScope.launch {
            val settings = try {
                settingsRepository.getSettings().first()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                com.sza.fastmediasorter.domain.model.AppSettings()
            }
            pendingOp = PendingOp(operationType, sourceCredentialsId)
            val initialUri = settings.lastSelectedLocalFolder?.let {
                try { Uri.parse(it) } catch (e: Exception) { null }
            }
            try {
                onLaunchPicker(initialUri)
            } catch (e: Exception) {
                e.errorUnlessCancellation("PlayerFolderPickerHandler: failed to launch folder picker")
                Toast.makeText(activity, activity.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                pendingOp = null
            }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) {
            Timber.d("PlayerFolderPickerHandler: user cancelled folder picker")
            return
        }
        Timber.i("PlayerFolderPickerHandler: selected uri=$uri")
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Timber.w(e, "PlayerFolderPickerHandler: takePersistableUriPermission failed (non-fatal)")
        }

        val normalizedUri = SafHelper.normalizeContentUri(uri.toString())
        val resolvedPath = UriPathResolver.getPath(activity, uri)
        val writableResolvedPath = resolvedPath?.takeIf { path ->
            File(path).let { it.exists() && it.canWrite() }
        }
        val treeAllowedByPolicy = !SafHelper.isRestrictedTreeUri(normalizedUri) ||
            restrictedTreeTargetPolicy.allowsRestrictedTreeTargets()
        val writableSafTree = treeAllowedByPolicy && SafHelper.getTreeRoot(activity, normalizedUri) != null
        val destinationPath = writableResolvedPath ?: normalizedUri.takeIf { writableSafTree }

        if (destinationPath == null) {
            Timber.w("PlayerFolderPickerHandler: uri=$uri is not writable as path or SAF tree")
            Toast.makeText(activity, activity.getString(R.string.error_folder_not_writable), Toast.LENGTH_LONG).show()
            return
        }

        val op = pendingOp ?: return
        pendingOp = null

        coroutineScope.launch {
            try {
                val current = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(current.copy(lastSelectedLocalFolder = uri.toString()))
            } catch (e: Exception) {
                e.warnUnlessCancellation("PlayerFolderPickerHandler: failed to save last local folder")
            }
        }

        when (op.operationType) {
            FileOperationType.COPY -> fileOperationsHandler.performCopyToPath(destinationPath)
            FileOperationType.MOVE -> fileOperationsHandler.performMoveToPath(destinationPath)
            else -> Timber.w("PlayerFolderPickerHandler: unsupported operation type ${op.operationType}")
        }
    }
}
