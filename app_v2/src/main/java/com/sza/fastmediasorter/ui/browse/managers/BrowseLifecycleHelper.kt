package com.sza.fastmediasorter.ui.browse.managers

import android.Manifest
import android.app.Activity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.StoragePermissionRule
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber

/**
 * Handles BrowseActivity lifecycle tasks:
 * - onResume scroll restoration (secondary path when list unchanged)
 * - Storage permission check & dialog for local resources on Android 11+
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseLifecycleHelper(
    private val activity: Activity,
    private val recyclerView: RecyclerView,
    private val adapter: MediaFileAdapter,
    private val listSubmitManager: BrowseListSubmitManager
) {
    /** Whether storage permission dialog was shown in this session. */
    var hasShownStoragePermissionDialog = false
    /** Whether storage permission was missing when Activity started. */
    var wasStoragePermissionMissing = false

    /**
     * Attempt scroll restoration on resume when the file list has NOT changed
     * (submitList callback won't fire). Posts the scroll to the next frame.
     */
    fun restoreScrollOnResume(state: BrowseState) {
        if (!listSubmitManager.shouldScrollToLastViewed || adapter.itemCount <= 0) return

        val lastViewedPath = state.resource?.lastViewedFile
        val pathPosition = if (lastViewedPath != null)
            state.mediaFiles.indexOfFirst { it.path == lastViewedPath }
        else -1
        val targetPosition = if (pathPosition >= 0) {
            pathPosition
        } else {
            val savedPos = state.resource?.lastScrollPosition ?: 0
            if (savedPos > 0 && savedPos < adapter.itemCount) savedPos else -1
        }
        if (targetPosition >= 0) {
            listSubmitManager.shouldScrollToLastViewed = false
            recyclerView.post {
                if (activity.isDestroyed || activity.isFinishing) return@post
                val lm = recyclerView.layoutManager
                when (lm) {
                    is LinearLayoutManager -> lm.scrollToPositionWithOffset(targetPosition, 0)
                    is GridLayoutManager -> lm.scrollToPositionWithOffset(targetPosition, 0)
                    else -> recyclerView.scrollToPosition(targetPosition)
                }
                Timber.i("onResume: Restored scroll to position $targetPosition (pathBased=${pathPosition >= 0}, file=${state.mediaFiles.getOrNull(targetPosition)?.name})")
            }
        } else {
            listSubmitManager.shouldScrollToLastViewed = false
            Timber.d("onResume: No valid restore position (lastViewedPath=$lastViewedPath, itemCount=${adapter.itemCount})")
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE is granted for local resources on Android 11+.
     * Shows an explanation dialog once per session.
     *
     * S2012: gated on the merged manifest, not on the SDK level. A store build declares no
     * all-files access, so entering Browse must not open a dialog whose only action leads to a
     * grant that build can never hold - its local resources are read through SAF instead.
     *
     * @return true if permission was just re-granted and files should reload
     */
    fun checkAndRequestStoragePermission(
        resource: com.sza.fastmediasorter.domain.model.MediaResource?,
        onReloadFiles: () -> Unit
    ) {
        if (resource == null) return
        if (resource.type != ResourceType.LOCAL) return
        if (!StoragePermissionRule.requiresAllFilesAccess(activity)) return

        val hasPermission = com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(activity)

        if (wasStoragePermissionMissing && hasPermission) {
            Timber.i("BrowseActivity: MANAGE_EXTERNAL_STORAGE granted after Settings, reloading files")
            wasStoragePermissionMissing = false
            onReloadFiles()
            return
        }

        if (!hasPermission && !hasShownStoragePermissionDialog) {
            hasShownStoragePermissionDialog = true
            wasStoragePermissionMissing = true
            Timber.w("BrowseActivity: MANAGE_EXTERNAL_STORAGE not granted, showing dialog")
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.permissions_required_title)
                .setMessage(
                    activity.permissionRationale(
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        PermissionTask.FOLDER_PICKING,
                    )
                )
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    com.sza.fastmediasorter.core.util.PermissionHelper.requestAllFilesAccessPermission(activity)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(true)
                .showBoundToHost(activity)
        } else if (!hasPermission) {
            wasStoragePermissionMissing = true
        }
    }
}
