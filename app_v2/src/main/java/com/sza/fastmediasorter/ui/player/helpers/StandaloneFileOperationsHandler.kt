package com.sza.fastmediasorter.ui.player.helpers

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Owns the file-mutating intent operations for [com.sza.fastmediasorter.ui.player.StandalonePlayerActivity]:
 * delete (file://, SAF, MediaStore R+, MediaStore Q recoverable), share via FileProvider,
 * "Open in FMS" reverse-routing, and rename (SAF rename + MediaStore DISPLAY_NAME update).
 *
 * Pending-delete state is held here; the activity registers the launchers and forwards their
 * results via [handleBatchDeleteResult] / [handleRecoverableDeleteResult].
 *
 * Extracted to keep StandalonePlayerActivity below the 1000-line cap.
 */
class StandaloneFileOperationsHandler(
    private val activity: AppCompatActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val getCurrentMediaFile: () -> MediaFile?,
    private val findResourceForPath: suspend (String?) -> Long?,
    private val onRenameComplete: (Uri, String) -> Unit,
    private val updateAudioMediaItem: (Uri) -> Unit,
    private val batchDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val recoverableDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>
) {

    private var pendingDeleteFileName: String? = null
    private var pendingDeleteUri: Uri? = null

    // ── Delete ────────────────────────────────────────────────────────────

    fun deleteCurrentFile() {
        val file = getCurrentMediaFile() ?: return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(activity.getString(R.string.confirm_delete_standalone, file.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                performDelete((file.contentUri ?: file.path).toUri(), file.name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDelete(uri: Uri, fileName: String) {
        activity.lifecycleScope.launch {
            try {
                when {
                    uri.scheme == "file" -> {
                        val deleted = File(uri.path!!).delete()
                        if (deleted) onDeleteSuccess(fileName)
                        else toastDeleteFailed(fileName)
                    }

                    uri.scheme == "content" && DocumentsContract.isDocumentUri(activity, uri) -> {
                        val deleted = DocumentsContract.deleteDocument(activity.contentResolver, uri)
                        if (deleted) onDeleteSuccess(fileName)
                        else toastDeleteFailed(fileName)
                    }

                    uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && isMediaStoreSpecificUri(uri) -> {
                        // Try direct delete first: succeeds immediately for app-owned items without
                        // a system dialog. createDeleteRequest is for cross-app deletion - calling
                        // it on app-owned files throws IAE on some OEM builds
                        // (observed: API 35, content://media/external/downloads/<id> written by
                        // LinkDownloadWriter; exception originates inside the MediaStore provider
                        // via IPC, not the client-side URI check).
                        val ownedRows = withContext(Dispatchers.IO) {
                            try { activity.contentResolver.delete(uri, null, null) }
                            catch (_: SecurityException) { -1 }
                        }
                        if (ownedRows > 0) {
                            onDeleteSuccess(fileName)
                            return@launch
                        }
                        // File not owned by this app - show system confirmation dialog.
                        val pendingIntent = MediaStore.createDeleteRequest(activity.contentResolver, listOf(uri))
                        pendingDeleteFileName = fileName
                        batchDeleteLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }

                    uri.scheme == "content" -> {
                        // API 26–29: direct delete; on API 29 catch RecoverableSecurityException
                        try {
                            val rows = activity.contentResolver.delete(uri, null, null)
                            if (rows > 0) onDeleteSuccess(fileName)
                            else toastDeleteFailed(fileName)
                        } catch (se: SecurityException) {
                            @Suppress("NewApi")
                            val rse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                se as? android.app.RecoverableSecurityException else null
                            if (rse != null) {
                                pendingDeleteFileName = fileName
                                pendingDeleteUri = uri
                                recoverableDeleteLauncher.launch(
                                    IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build()
                                )
                            } else {
                                throw se
                            }
                        }
                    }

                    else -> {
                        Timber.w("StandalonePlayer: delete not supported for scheme=${uri.scheme}")
                        toastDeleteFailed(fileName)
                    }
                }
            } catch (e: SecurityException) {
                Timber.w(e, "StandalonePlayer: non-recoverable delete permission denied for $fileName")
                binding.btnDeleteCmd.isVisible = false
                Toast.makeText(activity, R.string.delete_permission_denied, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Timber.e(e, "StandalonePlayer: delete failed for $fileName")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.delete_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onDeleteSuccess(fileName: String) {
        Toast.makeText(activity, activity.getString(R.string.file_deleted, fileName), Toast.LENGTH_SHORT).show()
        activity.finish()
    }

    private fun toastDeleteFailed(fileName: String) {
        Toast.makeText(activity, activity.getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
    }

    private fun retryDeleteAfterPermission(uri: Uri, fileName: String) {
        // Called after RecoverableSecurityException recovery on API 29 - permission now granted
        activity.lifecycleScope.launch {
            try {
                val rows = activity.contentResolver.delete(uri, null, null)
                if (rows > 0) onDeleteSuccess(fileName)
                else toastDeleteFailed(fileName)
            } catch (e: Exception) {
                Timber.e(e, "StandalonePlayer: retry delete failed for $fileName")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.delete_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Forward the API 30+ batch-delete launcher result. */
    fun handleBatchDeleteResult(success: Boolean) {
        val name = pendingDeleteFileName ?: return
        if (success) {
            onDeleteSuccess(name)
        } else {
            Timber.w("StandalonePlayer: batch delete denied by user for $name")
            toastDeleteFailed(name)
        }
        pendingDeleteFileName = null
    }

    /** Forward the API 29 recoverable-delete launcher result. */
    fun handleRecoverableDeleteResult(success: Boolean) {
        val name = pendingDeleteFileName ?: return
        if (success) {
            val uri = pendingDeleteUri
            if (uri != null) retryDeleteAfterPermission(uri, name)
        } else {
            Timber.w("StandalonePlayer: recoverable delete denied for $name")
            toastDeleteFailed(name)
        }
        pendingDeleteFileName = null
        pendingDeleteUri = null
    }

    /**
     * Returns true only for MediaStore item URIs that [MediaStore.createDeleteRequest] accepts:
     * authority must be "media" and the last path segment must be a numeric item ID.
     * File-provider, external-share, and other content:// URIs return false and are handled
     * by the [android.content.ContentResolver.delete] branch instead.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun isMediaStoreSpecificUri(uri: Uri): Boolean {
        if (uri.authority != "media") return false
        return uri.lastPathSegment?.toLongOrNull() != null
    }

    // ── Share ─────────────────────────────────────────────────────────────

    fun shareCurrentFile() {
        val file = getCurrentMediaFile() ?: return
        val uri = (file.contentUri ?: file.path).toUri()
        val mimeType = activity.contentResolver.getType(uri) ?: "*/*"

        val shareUri = if (uri.scheme == "file") {
            try {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", File(uri.path!!))
            } catch (e: Exception) {
                Timber.w(e, "StandalonePlayer: FileProvider failed, using original URI")
                uri
            }
        } else {
            uri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_via)))
    }

    // ── Open in FMS ──────────────────────────────────────────────────────

    fun openInFms() {
        val file = getCurrentMediaFile() ?: return
        val uri = (file.contentUri ?: file.path).toUri()
        val localPath = resolveToLocalPath(uri)

        if (localPath != null) {
            val parentDir = File(localPath).parent
            activity.lifecycleScope.launch {
                val resourceId = findResourceForPath(parentDir)
                if (resourceId != null) {
                    activity.startActivity(BrowseActivity.createIntent(
                        activity,
                        resourceId = resourceId,
                        initialFilePath = localPath
                    ))
                } else {
                    launchMainActivity()
                }
                activity.finish()
            }
        } else {
            launchMainActivity()
            activity.finish()
        }
    }

    private fun launchMainActivity() {
        activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
    }

    @Suppress("DEPRECATION")
    private fun resolveToLocalPath(uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path
        "content" -> try {
            activity.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            Timber.d(e, "StandalonePlayer: could not resolve content URI to local path")
            null
        }
        else -> null
    }

    // ── Rename ────────────────────────────────────────────────────────────

    /**
     * Decide rename-button visibility for the current media file.
     * SAF documents: check FLAG_SUPPORTS_RENAME via DocumentsContract query.
     * MediaStore URIs: optimistic - show button, handle failure at attempt time.
     * All other schemes: hidden.
     */
    fun updateRenameButtonVisibility() {
        val uri = getCurrentMediaFile()?.path?.toUri() ?: run {
            binding.btnRenameCmd.isVisible = false
            return
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val canRename = canRenameUri(uri)
            withContext(Dispatchers.Main) {
                binding.btnRenameCmd.isVisible = canRename
            }
        }
    }

    private fun canRenameUri(uri: Uri): Boolean = when {
        DocumentsContract.isDocumentUri(activity, uri) -> {
            try {
                activity.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val flags = cursor.getInt(0)
                        flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                    } else false
                } ?: false
            } catch (e: Exception) {
                Timber.w(e, "StandalonePlayer: canRename query failed for $uri")
                false
            }
        }
        uri.authority?.startsWith("com.android.providers.media") == true ||
            uri.toString().startsWith("content://media/") -> true // optimistic for MediaStore
        else -> false
    }

    fun showStandaloneRenameDialog() {
        val currentFile = getCurrentMediaFile() ?: return
        val currentName = currentFile.name
        val uri = currentFile.path.toUri()

        val input = EditText(activity).apply {
            setText(currentName)
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.rename))
            .setView(input)
            .setPositiveButton(R.string.apply) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank() && newName != currentName) {
                    performStandaloneRename(uri, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performStandaloneRename(uri: Uri, newName: String) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newUri: Uri? = if (DocumentsContract.isDocumentUri(activity, uri)) {
                    DocumentsContract.renameDocument(activity.contentResolver, uri, newName)
                } else {
                    // MediaStore: update DISPLAY_NAME; URI remains unchanged
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                    }
                    val rows = activity.contentResolver.update(uri, values, null, null)
                    if (rows > 0) uri else null  // null = failure (0 rows updated)
                }
                withContext(Dispatchers.Main) {
                    if (newUri != null) {
                        onRenameComplete(newUri, newName)
                        // Audio-specific: SAF rename returns a new URI - update ExoPlayer MediaItem
                        // so re-buffering (seek beyond cache) doesn't hit the invalidated old URI.
                        if (newUri != uri) {
                            updateAudioMediaItem(newUri)
                        }
                        Timber.d("StandalonePlayer: rename succeeded → $newUri")
                    } else {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.rename_failed_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "StandalonePlayer: rename failed for $uri → $newName")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.rename_failed_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
