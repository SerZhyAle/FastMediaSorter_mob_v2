package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.UriPathResolver
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber
import java.io.File

/**
 * S2370: owns the system folder-picker round trip that reconnects a direct-path local resource.
 *
 * The launcher itself stays with the host activity - the same ownership split
 * [com.sza.fastmediasorter.ui.browse.managers.BrowseFolderPickerHandler] uses, because an activity
 * result contract must be registered before the activity is STARTED and a manager built later cannot
 * do that.
 *
 * Invariant: nothing is written before the user confirms the picked folder. A cancelled picker and a
 * declined mismatch both leave the resource, its favorites and its schedules exactly as they were.
 */
class MainResourceReconnectManager(
    private val activity: FragmentActivity,
    private val launchPicker: (Uri?) -> Unit,
    private val onReconnect: (Long, Uri) -> Unit,
) {

    /**
     * S2374: the fields the round trip actually reads - the id the swap applies to, and the path
     * the folder comparison and the mismatch dialog quote.
     * S2376: also preserves [pickedUriString] and [pickedPath] across host recreation while the
     * mismatch confirmation dialog is visible, so the dialog is restored after recreation instead
     * of dropping the reconnect attempt.
     */
    private data class Pending(
        val resourceId: Long,
        val path: String,
        val pickedUriString: String? = null,
        val pickedPath: String? = null,
    )

    private var pending: Pending? = null

    /** Opens the system folder picker for [resource], starting in its current folder when it resolves. */
    fun request(resource: MediaResource) {
        pending = Pending(resource.id, resource.path)
        launchPicker(initialLocationFor(resource.path))
    }

    /** The system picker's result. A null [uri] means the user backed out and nothing happens. */
    fun onFolderPicked(uri: Uri?) {
        val target = pending
        if (uri == null || target == null) {
            pending = null
            return
        }
        takePersistableGrant(uri)
        val pickedPath = UriPathResolver.getPath(activity, uri)
        if (isSameFolder(target.path, pickedPath)) {
            proceed(target, uri)
        } else {
            val mismatchPending = target.copy(
                pickedUriString = uri.toString(),
                pickedPath = pickedPath,
            )
            pending = mismatchPending
            confirmDifferentFolder(mismatchPending, uri, pickedPath)
        }
    }

    /**
     * S2374/S2376: persists the pending round trip. [android.app.Activity.onSaveInstanceState] is the host
     * for it because the picker result is dispatched when the activity reaches STARTED, and the
     * matching [restoreState] in onCreate is the last point that is reliably earlier than that.
     */
    fun saveState(outState: Bundle) {
        val target = pending ?: return
        outState.putLong(KEY_PENDING_ID, target.resourceId)
        outState.putString(KEY_PENDING_PATH, target.path)
        target.pickedUriString?.let { outState.putString(KEY_PICKED_URI, it) }
        target.pickedPath?.let { outState.putString(KEY_PICKED_PATH, it) }
    }

    /**
     * S2374/S2376: restores the pending round trip after the host was recreated while the system picker or
     * the mismatch confirmation dialog was foreground.
     */
    fun restoreState(savedState: Bundle) {
        val path = savedState.getString(KEY_PENDING_PATH) ?: return
        val resourceId = savedState.getLong(KEY_PENDING_ID, NO_PENDING_ID)
        if (resourceId == NO_PENDING_ID) {
            return
        }
        val pickedUriStr = savedState.getString(KEY_PICKED_URI)
        val pickedPath = savedState.getString(KEY_PICKED_PATH)
        if (pickedUriStr != null) {
            val uri = Uri.parse(pickedUriStr)
            val target = Pending(
                resourceId = resourceId,
                path = path,
                pickedUriString = pickedUriStr,
                pickedPath = pickedPath,
            )
            pending = target
            confirmDifferentFolder(target, uri, pickedPath)
        } else {
            pending = Pending(resourceId, path)
        }
    }

    private fun initialLocationFor(path: String): Uri? = path
        .takeIf { it.isNotBlank() && !it.startsWith(CONTENT_URI_SCHEME_PREFIX) }
        ?.let { runCatching { Uri.fromFile(File(it)) }.getOrNull() }

    private fun takePersistableGrant(uri: Uri) {
        if (uri.scheme != "content") return
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Non-fatal: the picker still returned a usable one-shot grant for this session, and the
            // reconnect is worth attempting - parity with the browse-side picker handler.
            Timber.w(e, "takePersistableUriPermission refused for a reconnect")
        } catch (e: IllegalArgumentException) {
            // A tree no provider will persist. Reachable since S2376 rebuilds the uri from a Bundle,
            // so the provider behind it may be gone by the time the host is recreated. Same non-fatal
            // handling: the scheme guard above already excludes the malformed case.
            Timber.w(e, "takePersistableUriPermission rejected the reconnect tree")
        }
    }

    /**
     * Whether the picked tree resolves to the folder the resource already points at. A tree that does
     * not resolve to a file path at all counts as different: the user is then told which two folders
     * are in play rather than being reconnected silently onto an address nobody compared.
     */
    private fun isSameFolder(currentPath: String, pickedPath: String?): Boolean {
        if (currentPath.isBlank() || pickedPath.isNullOrBlank()) {
            return false
        }
        return canonicalOf(currentPath) == canonicalOf(pickedPath)
    }

    private fun canonicalOf(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    private fun confirmDifferentFolder(target: Pending, uri: Uri, pickedPath: String?) {
        val message = activity.getString(
            R.string.reconnect_different_folder_message,
            displayNameOf(pickedPath, uri),
            displayNameOf(target.path, uri = null),
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.reconnect_different_folder_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> proceed(target, uri) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pending = null }
            .setOnCancelListener { pending = null }
            .showBoundToHost(activity)
    }

    private fun displayNameOf(path: String?, uri: Uri?): String {
        val fromPath = path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val fromUri = uri?.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        return fromPath ?: fromUri ?: path.orEmpty()
    }

    private fun proceed(target: Pending, uri: Uri) {
        pending = null
        onReconnect(target.resourceId, uri)
    }

    private companion object {
        const val CONTENT_URI_SCHEME_PREFIX = "content://"
        const val NO_PENDING_ID = -1L
        const val KEY_PENDING_ID = "s2374_reconnect_pending_id"
        const val KEY_PENDING_PATH = "s2374_reconnect_pending_path"
        const val KEY_PICKED_URI = "s2376_reconnect_picked_uri"
        const val KEY_PICKED_PATH = "s2376_reconnect_picked_path"
    }
}
