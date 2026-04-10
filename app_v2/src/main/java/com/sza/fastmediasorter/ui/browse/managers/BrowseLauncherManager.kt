package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.sza.fastmediasorter.ui.player.PlayerActivity
import timber.log.Timber

interface BrowseLauncherCallbacks {
    fun handleGoogleSignInResult(data: Intent?)
    fun onPlayerActivityReturned(modifiedPaths: ArrayList<String>)
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
    val googleSignInLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        callbacks.handleGoogleSignInResult(result.data)
    }

    val playerActivityLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(PlayerActivity.EXTRA_MODIFIED_FILES)?.let { modifiedPaths ->
                callbacks.onPlayerActivityReturned(modifiedPaths)
            }
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
