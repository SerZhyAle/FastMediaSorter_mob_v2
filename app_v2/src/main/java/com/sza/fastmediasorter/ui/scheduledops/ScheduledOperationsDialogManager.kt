package com.sza.fastmediasorter.ui.scheduledops

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ScheduledOperationsEntryPoint
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hosts the create/edit [ScheduledOperationDialog] for the program screen: loads the destination
 * snapshot through the DI seam, augments hidden FK resources (S1009) and receives SAF folder picks
 * routed through the activity's launcher. A manager rather than activity code so the domain-layer
 * dependency stays off the host (CLAUDE.md Rule 3).
 */
class ScheduledOperationsDialogManager(
    private val mediaCapabilities: MediaCapabilities,
    private val scheduledViewModel: ScheduledOperationsViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
) {

    private var currentDialog: ScheduledOperationDialog? = null
    private var pendingPickSide: SchedOpPickSide? = null

    /**
     * S1009: build + show the dialog. Resolves any hidden FK (an ad-hoc local folder, filtered out
     * of the visible lists) so an edited operation still displays and preserves it.
     */
    fun openScheduledOperationDialog(
        context: Context,
        scope: CoroutineScope,
        resources: List<MediaResource>,
        existing: ScheduledOperation?,
        prefilledSourceId: Long?,
    ) {
        scope.launch {
            val destinationsUseCase = EntryPointAccessors
                .fromApplication(context.applicationContext, ScheduledOperationsEntryPoint::class.java)
                .getDestinationsUseCase()
            val destinations = augmentWithHiddenFk(
                destinationsUseCase().first(),
                existing?.targetResourceId
            )
            val augmentedResources = augmentWithHiddenFk(resources, existing?.sourceResourceId)
            val dialog = ScheduledOperationDialog(
                context = context,
                resources = augmentedResources,
                destinations = destinations,
                existing = existing,
                prefilledSourceId = prefilledSourceId,
                mediaCapabilities = mediaCapabilities,
                onPickLocalFolder = { side ->
                    pendingPickSide = side
                    folderPickerLauncher.launch(null)
                },
                onSave = { draft -> scheduledViewModel.saveOperation(draft) },
            )
            currentDialog = dialog
            // Drop the reference on dismiss so a dismissed dialog (and its Activity context) is not retained.
            dialog.setOnDismissListener { currentDialog = null }
            dialog.show()
        }
    }

    /**
     * S1009: receive a picked folder from the SAF launcher - persist the tree permission, check
     * writability (a non-writable receiver is rejected), stage it into the shown dialog.
     */
    fun onFolderPicked(context: Context, scope: CoroutineScope, uri: Uri?) {
        if (uri == null) return
        val side = pendingPickSide ?: return
        pendingPickSide = null
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Could not persist folder permission for %s", uri)
        }
        scope.launch {
            val path = uri.toString()
            val writable = scheduledViewModel.isFolderWritable(path)
            if (side == SchedOpPickSide.TARGET && !writable) {
                Toast.makeText(context, R.string.error_folder_not_writable, Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment
                ?: path
            currentDialog?.onLocalFolderPicked(side, path, name, readOnly = !writable)
        }
    }

    private suspend fun augmentWithHiddenFk(
        visible: List<MediaResource>,
        fkId: Long?,
    ): List<MediaResource> {
        val missingId = fkId?.takeUnless { id -> visible.any { it.id == id } }
            ?: return visible
        return listOfNotNull(scheduledViewModel.resolveResourceById(missingId)) + visible
    }
}
