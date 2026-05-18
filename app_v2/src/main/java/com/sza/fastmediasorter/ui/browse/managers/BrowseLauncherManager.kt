package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import timber.log.Timber

interface BrowseLauncherCallbacks {
    /**
     * Invoked when PlayerActivity returns RESULT_OK. No payload is passed - the Browse
     * Reconciler reads the [com.sza.fastmediasorter.domain.mutation.MutationJournal]
     * independently on its own onResume (S0242 Phase 02 → Phase 03). This callback is kept
     * so the note save-and-close flow (`PlayerViewerFactory.finishActivity()`) and any
     * future "Player returned successfully" hook still has a single entry point.
     */
    fun onPlayerActivityReturned()
    fun onEditResourceReturned()
    fun onDeletePermissionGranted()
    fun onPermissionDenied()
    fun clearPendingMoveOperation()
    fun onFolderPicked(uri: Uri?)
}

class BrowseLauncherManager(
    activity: ComponentActivity,
    private val callbacks: BrowseLauncherCallbacks
) {
    // S0200 Phase 04c: googleSignInLauncher removed - Credential Manager replaces the
    // activity-result handshake. Drive sign-in now goes through GoogleIdentityRepository.signInPrimary.

    val playerActivityLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // S0242 Phase 02: no Intent payload is read anymore - the Browse Reconciler will
        // read the MutationJournal on its own onResume cycle (wired in Phase 03).
        if (result.resultCode == RESULT_OK) {
            callbacks.onPlayerActivityReturned()
        }
    }

    val editResourceLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            callbacks.onEditResourceReturned()
        }
    }

    val permissionRequestLauncher: ActivityResultLauncher<IntentSenderRequest> = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("Permission granted by user, updating UI state")
            callbacks.onDeletePermissionGranted()
            callbacks.clearPendingMoveOperation()
        } else {
            Timber.w("Permission denied by user")
            callbacks.clearPendingMoveOperation()
            callbacks.onPermissionDenied()
        }
    }

    val folderPickerLauncher: ActivityResultLauncher<Uri?> = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        callbacks.onFolderPicked(uri)
    }
}
