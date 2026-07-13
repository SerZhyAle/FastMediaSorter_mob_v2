package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
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

    // S1019: resource-level write permission from the shared resolver so the player and browse never
    // diverge. Policy (isReadOnly) and the per-type rule (network -> policy, local/cloud -> probe) live there.
    var canWrite: Boolean = resource?.allowsWriteOperations() ?: false
    val canRead: Boolean
    if (isNetworkResource) {
        canRead = true
    } else if (currentFilePath.startsWith("content://")) {
        canRead = try {
            DocumentFile.fromSingleUri(context, Uri.parse(currentFilePath))?.canRead() ?: false
        } catch (e: Exception) {
            Timber.e(e, "CommandPanelController: Error checking SAF URI read permission")
            false
        }
    } else {
        val file = File(currentFilePath)
        // Raw file with no resource context: fall back to the filesystem's own writability.
        if (resource == null) canWrite = file.canWrite()
        canRead = file.canRead()
    }

    return PlayerFilePermissions(canWrite, canRead)
}
