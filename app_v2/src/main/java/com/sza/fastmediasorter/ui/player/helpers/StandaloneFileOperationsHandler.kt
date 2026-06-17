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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import android.view.View
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.OpenInFmsTarget
import com.sza.fastmediasorter.domain.usecase.ResolveOpenInFmsTargetUseCase
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.share.SendToMenuManager
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
    private val root: View,
    private val getCurrentMediaFile: () -> MediaFile?,
    private val resolveOpenInFmsTarget: ResolveOpenInFmsTargetUseCase,
    private val onRenameComplete: (Uri, String) -> Unit,
    private val updateAudioMediaItem: (Uri) -> Unit,
    private val batchDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val recoverableDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val sendToMenuManager: SendToMenuManager,
    private val getCurrentSettings: suspend () -> AppSettings
) {

    private val safeViews = PlayerBindingSafeViews(root)

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
                        else toastDeleteFailed()
                    }

                    uri.scheme == "content" && DocumentsContract.isDocumentUri(activity, uri) -> {
                        val deleted = DocumentsContract.deleteDocument(activity.contentResolver, uri)
                        if (deleted) onDeleteSuccess(fileName)
                        else toastDeleteFailed()
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
                            else toastDeleteFailed()
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
                        toastDeleteFailed()
                    }
                }
            } catch (e: SecurityException) {
                Timber.w(e, "StandalonePlayer: non-recoverable delete permission denied for $fileName")
                safeViews.setVisibleIfPresent(R.id.btnDeleteCmd, false)
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

    private fun toastDeleteFailed() {
        // R.string.delete_failed has no format placeholder, so no argument is passed.
        Toast.makeText(activity, activity.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
    }

    private fun retryDeleteAfterPermission(uri: Uri, fileName: String) {
        // Called after RecoverableSecurityException recovery on API 29 - permission now granted
        activity.lifecycleScope.launch {
            try {
                val rows = activity.contentResolver.delete(uri, null, null)
                if (rows > 0) onDeleteSuccess(fileName)
                else toastDeleteFailed()
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
            toastDeleteFailed()
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
            toastDeleteFailed()
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

    /**
     * Open the unified «Send to..» menu for the current standalone file (S0459 Phase 06).
     * The shared btnShareCmd routes through here for every standalone host; receiver applicability
     * (not host type) decides which receivers appear.
     */
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

        activity.lifecycleScope.launch {
            val settings = getCurrentSettings()
            val content = ShareableContent(
                uris = listOf(shareUri),
                mime = mimeType,
                mediaType = file.type,
                displayName = file.name,
                mediaFile = file,
            )
            sendToMenuManager.show(activity, content, settings)
        }
    }

    // ── Open in FMS ──────────────────────────────────────────────────────

    fun openInFms() {
        val file = getCurrentMediaFile() ?: return
        val uri = (file.contentUri ?: file.path).toUri()
        activity.lifecycleScope.launch {
            val target = resolveOpenInFmsTarget(uri, file.type)
            when (target) {
                is OpenInFmsTarget.Resolved -> {
                    activity.startActivity(
                        PlayerActivity.createPanelIntent(
                            activity,
                            resourceId = target.resourceId,
                            skipAvailabilityCheck = true,
                            initialFilePath = target.absoluteFilePath
                        )
                    )
                    activity.finish()
                }

                OpenInFmsTarget.NotResolvable -> {
                    Toast.makeText(
                        activity,
                        R.string.open_in_fms_external_file_notice,
                        Toast.LENGTH_LONG
                    ).show()
                    launchMainActivity()
                    activity.finish()
                }
            }
        }
    }

    private fun launchMainActivity() {
        activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
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
            safeViews.setVisibleIfPresent(R.id.btnRenameCmd, false)
            return
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val canRename = canRenameUri(uri)
            withContext(Dispatchers.Main) {
                safeViews.setVisibleIfPresent(R.id.btnRenameCmd, canRename)
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
