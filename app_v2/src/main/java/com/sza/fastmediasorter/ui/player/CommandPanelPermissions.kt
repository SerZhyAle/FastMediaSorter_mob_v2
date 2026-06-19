package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber
import java.io.File

/** Resolved read/write permission for the current file under a resource. */
internal data class PlayerFilePermissions(val canWrite: Boolean, val canRead: Boolean)

/**
 * Single source of truth for the player's per-file write/read permission.
 *
 * S0532: the keyboard MOVE guard and the move-panel visibility diverged because each derived
 * "can write" independently. Both now call this so a non-writable resource never lets the MOVE
 * shortcut toggle a panel that visibility logic keeps hidden.
 */
internal fun resolvePlayerFilePermissions(
    context: Context,
    resource: MediaResource?,
    currentFilePath: String,
): PlayerFilePermissions {
    val isNetworkResource = resource != null &&
        (resource.type == ResourceType.SMB || resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP)

    var canWrite: Boolean
    val canRead: Boolean
    if (isNetworkResource) {
        canWrite = true; canRead = true
    } else if (currentFilePath.startsWith("content://")) {
        canWrite = resource?.isWritable ?: false
        canRead = try {
            DocumentFile.fromSingleUri(context, Uri.parse(currentFilePath))?.canRead() ?: false
        } catch (e: Exception) {
            Timber.e(e, "CommandPanelController: Error checking SAF URI read permission")
            false
        }
    } else {
        val file = File(currentFilePath)
        canWrite = resource?.isWritable ?: file.canWrite()
        canRead = file.canRead()
    }
    if (resource?.isReadOnly == true) canWrite = false

    return PlayerFilePermissions(canWrite, canRead)
}
