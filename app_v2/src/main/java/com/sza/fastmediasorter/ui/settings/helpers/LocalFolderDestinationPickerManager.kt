package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.queryTreeDisplayName
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1010: shared "Local Folder" leading option for every settings write-receiver picker.
 *
 * Hosts prepend [sentinelItem] to their picker list and route the picker's `onSelected` through
 * [wrapOnSelected], naming the setting as a [LocalFolderReceiver]; the manager writes it. Selecting the
 * sentinel opens SAF, write-checks the folder and persists it as a hidden resource (S1009 mechanism),
 * so it becomes the destination without appearing in the general resource list. Constructed manually
 * by each host Fragment, matching the convention of the other managers in this package.
 */
class LocalFolderDestinationPickerManager(
    private val fragment: Fragment,
    private val viewModel: SettingsViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
) {

    // One in-flight pick at a time, correlated across the async SAF round-trip. S3802: the pair is also
    // saved with the host, because the SAF activity can outlive this process and a rebuilt manager
    // would otherwise drop the picked folder silently.
    private var pendingReceiver: LocalFolderReceiver? = null
    private var pendingPreviousId: Long? = null

    init {
        fragment.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { savePendingPick() }
    }

    fun wrapOnSelected(
        receiver: LocalFolderReceiver,
        previousResourceId: Long?,
    ): (MediaResource?) -> Unit = { selected ->
        if (selected != null && isSentinelSelection(selected)) {
            pendingReceiver = receiver
            pendingPreviousId = previousResourceId
            folderPickerLauncher.launch(null)
        } else {
            completeSelection(receiver, previousResourceId, selected)
        }
    }

    /** Call from the host's registered SAF launcher callback. */
    fun onFolderPicked(uri: Uri?) {
        if (pendingReceiver == null) restorePendingPick()
        val receiver = pendingReceiver
        val previousId = pendingPreviousId
        pendingReceiver = null
        pendingPreviousId = null
        if (uri == null || receiver == null) return
        val context = fragment.requireContext()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Could not persist folder permission for %s", uri)
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val path = uri.toString()
            if (!viewModel.isLocalFolderWritable(path)) {
                Toast.makeText(context, R.string.error_folder_not_writable, Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = context.queryTreeDisplayName(uri) ?: uri.lastPathSegment ?: path
            val resolved = viewModel.resolveLocalFolderResource(path, name, isWritable = true)
            completeSelection(receiver, previousId, resolved)
        }
    }

    private fun savePendingPick(): Bundle = Bundle().apply {
        val receiver = pendingReceiver ?: return@apply
        putString(STATE_RECEIVER, receiver.name)
        pendingPreviousId?.let { putLong(STATE_PREVIOUS_ID, it) }
    }

    private fun restorePendingPick() {
        val registry = fragment.savedStateRegistry
        val state = if (registry.isRestored) registry.consumeRestoredStateForKey(STATE_KEY) else null
        val name = state?.getString(STATE_RECEIVER) ?: return
        pendingReceiver = LocalFolderReceiver.entries.firstOrNull { it.name == name }
        pendingPreviousId = if (state.containsKey(STATE_PREVIOUS_ID)) state.getLong(STATE_PREVIOUS_ID) else null
    }

    private fun completeSelection(
        receiver: LocalFolderReceiver,
        previousId: Long?,
        selected: MediaResource?,
    ) {
        viewModel.updateSettings { latest -> receiver.write(latest, selected?.id) }
        // Skip cleanup when dedup reused the very resource just resolved, or it would delete the new destination.
        if (previousId != null && previousId != selected?.id) {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                viewModel.cleanupPreviousHiddenDestination(previousId)
            }
        }
    }

    companion object {
        // Room ids are positive autoincrement and 0L already means "unset", so -1L cannot collide.
        const val LOCAL_FOLDER_RECEIVER_SENTINEL_ID = -1L

        private const val STATE_KEY = "local_folder_destination_picker"
        private const val STATE_RECEIVER = "receiver"
        private const val STATE_PREVIOUS_ID = "previous_id"

        fun sentinelItem(context: Context): MediaResource = MediaResource(
            id = LOCAL_FOLDER_RECEIVER_SENTINEL_ID,
            name = context.getString(R.string.local_folder),
            path = "",
            type = ResourceType.LOCAL,
        )

        fun isSentinelSelection(item: MediaResource): Boolean =
            item.id == LOCAL_FOLDER_RECEIVER_SENTINEL_ID
    }
}
