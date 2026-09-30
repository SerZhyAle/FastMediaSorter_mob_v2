package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.core.util.LocalFileProbe
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.utils.SafDocumentProbe
import kotlinx.coroutines.CancellationException
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
 *
 * S3790: the content:// branch is a SAF document query - a binder IPC to the provider - so it must
 * not run on the main dispatcher. The availability pass calls [resolvePlayerFilePermissionsAsync];
 * the synchronous [resolvePlayerFilePermissions] stays for rare on-demand guards, and
 * [playerFilePermissionsWithoutSafProbe] answers the pass that runs while the probe is in flight.
 */
internal suspend fun resolvePlayerFilePermissionsAsync(
    context: Context,
    resource: MediaResource?,
    currentFilePath: String,
): PlayerFilePermissions {
    if (!currentFilePath.startsWith("content://")) {
        // S3884: local stat calls are disk I/O too, so the async pass takes them off Main as well.
        val localAccess = LocalFileProbe.query(File(currentFilePath), query = ::statLocalFile)
        return resolvePlayerFilePermissions(resource, currentFilePath, { false }, { localAccess })
    }
    // The probe is the only branch that needs the binder round-trip - fetch it off the main
    // dispatcher inside [SafDocumentProbe], then reuse the shared branch logic.
    val safCanRead = try {
        SafDocumentProbe.canRead(context, Uri.parse(currentFilePath))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "CommandPanelController: Error checking SAF URI read permission")
        false
    }
    return resolvePlayerFilePermissions(resource, currentFilePath, { safCanRead }, { NO_LOCAL_ACCESS })
}

internal fun resolvePlayerFilePermissions(
    context: Context,
    resource: MediaResource?,
    currentFilePath: String,
): PlayerFilePermissions = resolvePlayerFilePermissions(
    resource,
    currentFilePath,
    readSafDocument = {
        try {
            DocumentFile.fromSingleUri(context, Uri.parse(currentFilePath))?.canRead() ?: false
        } catch (e: Exception) {
            Timber.e(e, "CommandPanelController: Error checking SAF URI read permission")
            false
        }
    },
    readLocalFile = { statLocalFile(File(currentFilePath)) },
)

/**
 * Same branches as [resolvePlayerFilePermissions] with both the SAF read probe and the local file
 * stat stubbed to false - the conservative synchronous answer for the pass that runs while the IO
 * probe is in flight (S3790, S3884), so this pass never touches the disk or a provider.
 */
internal fun playerFilePermissionsWithoutSafProbe(
    resource: MediaResource?,
    currentFilePath: String,
): PlayerFilePermissions = resolvePlayerFilePermissions(resource, currentFilePath, { false }, { NO_LOCAL_ACCESS })

private val NO_LOCAL_ACCESS = PlayerFilePermissions(canWrite = false, canRead = false)

private fun statLocalFile(file: File): PlayerFilePermissions =
    PlayerFilePermissions(canWrite = file.canWrite(), canRead = file.canRead())

private fun resolvePlayerFilePermissions(
    resource: MediaResource?,
    currentFilePath: String,
    readSafDocument: () -> Boolean,
    readLocalFile: () -> PlayerFilePermissions,
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
        canRead = readSafDocument()
    } else {
        val localAccess = readLocalFile()
        // Raw file with no resource context: fall back to the filesystem's own writability.
        if (resource == null) canWrite = localAccess.canWrite
        canRead = localAccess.canRead
    }

    return PlayerFilePermissions(canWrite, canRead)
}
