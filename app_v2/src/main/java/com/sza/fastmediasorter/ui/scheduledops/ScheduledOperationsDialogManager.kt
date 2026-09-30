package com.sza.fastmediasorter.ui.scheduledops

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ScheduledOperationsEntryPoint
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.utils.queryTreeDisplayName
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * One open create/edit dialog: what it was opened for, plus what it held when its host was torn down
 * for a configuration change. Owned by [ScheduledOperationsViewModel] so it outlives the activity.
 */
class ScheduledOpDialogSession(
    val existing: ScheduledOperation?,
    val prefilledSourceId: Long?,
) {
    var savedState: Bundle? = null
    var pendingPickSide: SchedOpPickSide? = null
    var pendingPickedUri: Uri? = null
}

/**
 * Hosts the create/edit [ScheduledOperationDialog] for the program screen: loads the destination
 * snapshot through the DI seam, augments hidden FK resources (S1009) and receives SAF folder picks
 * routed through the activity's launcher. A manager rather than activity code so the domain-layer
 * dependency stays off the host (CLAUDE.md Rule 3).
 *
 * The dialog is a plain [android.app.Dialog], so it cannot survive its host by itself: on a
 * configuration change its state is saved into the ViewModel's [ScheduledOpDialogSession] and the
 * window is closed, and the recreated screen reopens it through [restoreDialog].
 */
class ScheduledOperationsDialogManager(
    private val host: ComponentActivity,
    private val mediaCapabilities: MediaCapabilities,
    private val scheduledViewModel: ScheduledOperationsViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
) {

    private var currentDialog: ScheduledOperationDialog? = null

    init {
        host.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = detachDialog()
            }
        )
    }

    fun openScheduledOperationDialog(
        resources: List<MediaResource>,
        existing: ScheduledOperation?,
        prefilledSourceId: Long?,
    ) {
        // A session still loading its destinations owns the screen; a second tap must not stack a dialog.
        if (scheduledViewModel.dialogSession != null) return
        val session = ScheduledOpDialogSession(existing, prefilledSourceId)
        scheduledViewModel.dialogSession = session
        showSession(resources, session)
    }

    /** Reopens the dialog the previous activity instance had open; a no-op when none was. */
    fun restoreDialog(resources: List<MediaResource>) {
        val session = scheduledViewModel.dialogSession ?: return
        if (currentDialog == null) showSession(resources, session)
    }

    /**
     * S1009: build + show the dialog. Resolves any hidden FK (an ad-hoc local folder, filtered out
     * of the visible lists) so an edited operation still displays and preserves it.
     */
    private fun showSession(resources: List<MediaResource>, session: ScheduledOpDialogSession) {
        host.lifecycleScope.launch {
            val destinationsUseCase = EntryPointAccessors
                .fromApplication(host.applicationContext, ScheduledOperationsEntryPoint::class.java)
                .getDestinationsUseCase()
            val destinations = augmentWithHiddenFk(
                destinationsUseCase().first(),
                session.existing?.targetResourceId
            )
            val augmentedResources = augmentWithHiddenFk(resources, session.existing?.sourceResourceId)
            val dialog = ScheduledOperationDialog(
                context = host,
                resources = augmentedResources,
                destinations = destinations,
                existing = session.existing,
                prefilledSourceId = session.prefilledSourceId,
                mediaCapabilities = mediaCapabilities,
                onPickLocalFolder = { side ->
                    session.pendingPickSide = side
                    folderPickerLauncher.launch(null)
                },
                onSave = { draft -> scheduledViewModel.saveOperation(draft) },
            )
            dialog.setOnDismissListener {
                currentDialog = null
                // Closed by the host's own teardown: the recreated screen reopens this session.
                if (!host.isChangingConfigurations) scheduledViewModel.dialogSession = null
            }
            dialog.show()
            currentDialog = dialog
            session.savedState?.let(dialog::onRestoreInstanceState)
            session.savedState = null
            session.pendingPickedUri?.let { uri ->
                session.pendingPickedUri = null
                onFolderPicked(uri)
            }
        }
    }

    private fun detachDialog() {
        val dialog = currentDialog ?: return
        if (host.isChangingConfigurations) {
            scheduledViewModel.dialogSession?.savedState = dialog.onSaveInstanceState()
        }
        dialog.dismiss()
    }

    /**
     * S1009: receive a picked folder from the SAF launcher. A result that lands while the dialog is
     * still being rebuilt waits in the session and is applied once the dialog is shown.
     */
    fun onFolderPicked(uri: Uri?) {
        val session = scheduledViewModel.dialogSession
        if (uri == null || session == null) return
        val side = session.pendingPickSide
        when {
            currentDialog == null -> session.pendingPickedUri = uri
            side != null -> {
                session.pendingPickSide = null
                applyFolderPick(side, uri)
            }
        }
    }

    /** Persist the tree permission, check writability (a non-writable receiver is rejected), stage it. */
    private fun applyFolderPick(side: SchedOpPickSide, uri: Uri) {
        try {
            host.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Could not persist folder permission for %s", uri)
        }
        host.lifecycleScope.launch {
            val path = uri.toString()
            val writable = scheduledViewModel.isFolderWritable(path)
            if (side == SchedOpPickSide.TARGET && !writable) {
                Toast.makeText(host, R.string.error_folder_not_writable, Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = host.queryTreeDisplayName(uri)
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
