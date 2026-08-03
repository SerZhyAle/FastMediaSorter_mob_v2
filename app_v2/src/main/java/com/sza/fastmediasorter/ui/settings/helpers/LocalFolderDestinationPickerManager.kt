package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1010: shared "Local Folder" leading option for every settings write-receiver picker.
 *
 * Hosts prepend [sentinelItem] to their picker list and route the picker's `onSelected` through
 * [wrapOnSelected]. Selecting the sentinel opens SAF, write-checks the folder and persists it as a
 * hidden resource (S1009 mechanism), so it becomes the destination without appearing in the
 * general resource list. Constructed manually by each host Fragment, matching the convention of the
 * other managers in this package.
 */
class LocalFolderDestinationPickerManager(
    private val fragment: Fragment,
    private val viewModel: SettingsViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
) {

    // One in-flight pick at a time, correlated across the async SAF round-trip.
    private var pendingCompletion: ((MediaResource?) -> Unit)? = null
    private var pendingPreviousId: Long? = null

    fun wrapOnSelected(
        previousResourceId: Long?,
        onPicked: (MediaResource?) -> Unit,
    ): (MediaResource?) -> Unit = { selected ->
        if (selected != null && isSentinelSelection(selected)) {
            pendingCompletion = onPicked
            pendingPreviousId = previousResourceId
            folderPickerLauncher.launch(null)
        } else {
            completeSelection(previousResourceId, selected, onPicked)
        }
    }

    /** Call from the host's registered SAF launcher callback. */
    fun onFolderPicked(uri: Uri?) {
        val completion = pendingCompletion
        val previousId = pendingPreviousId
        pendingCompletion = null
        pendingPreviousId = null
        if (uri == null || completion == null) return
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
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: path
            val resolved = viewModel.resolveLocalFolderResource(path, name, isWritable = true)
            completeSelection(previousId, resolved, completion)
        }
    }

    private fun completeSelection(
        previousId: Long?,
        selected: MediaResource?,
        onPicked: (MediaResource?) -> Unit,
    ) {
        onPicked(selected)
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
