package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import java.util.EnumSet
import timber.log.Timber

/**
 * SMB file mutation coordinator — rename and move of single files plus the shared
 * `mkdir -p` primitive that both depend on.
 *
 * Extracted from `SmbClient` (S0002 Wave 47) to keep the facade lean. No behaviour
 * change: error wrapping, path normalisation, share-target existence checks, and
 * SMBJ open/copy/delete sequencing match the original implementations exactly.
 *
 * `ensureSmbDirectoryExists` is delegated to [SmbClientErrorFormatter] (where the
 * race-tolerant retry lives) and exposed publicly so [SmbClient.createDirectory]
 * can keep using it without an extra wrapper.
 */
class SmbFileMutationCoordinator(
    private val connectionManager: SmbConnectionManager
) {

    suspend fun renameFile(
        connectionInfo: SmbConnectionInfo,
        oldPath: String,
        newName: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedOldPath = oldPath.trim('/', '\\')
                // Parse newName: if contains '/', treat it as full path from share root.
                // Otherwise, keep in same directory.
                val newPath = if (newName.contains('/')) {
                    newName.trim('/', '\\')
                } else {
                    val directory = fixedOldPath.substringBeforeLast('/', "")
                    if (directory.isEmpty()) newName else "$directory/$newName"
                }

                Timber.d("Renaming SMB file: oldPath='$fixedOldPath' → newPath='$newPath'")

                // Validate new name (no invalid SMB characters: \ / : * ? " < > |)
                val invalidChars = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
                val newFileName = newPath.substringAfterLast('/')
                if (newFileName.any { it in invalidChars }) {
                    Timber.e("SMB rename: Invalid characters in new name: $newFileName")
                    return@withConnection SmbResult.Error("New name contains invalid characters: ${invalidChars.filter { it in newFileName }}")
                }

                // Check if target exists
                val targetExists = try {
                    share.fileExists(newPath)
                } catch (e: Exception) {
                    Timber.w(e, "SMB rename: Error checking target existence, assuming not exists")
                    false
                }

                if (targetExists) {
                    Timber.e("SMB rename: Target file already exists: $newPath")
                    return@withConnection SmbResult.Error("File already exists at target location")
                }

                // Open source file for rename
                val file = try {
                    share.openFile(
                        fixedOldPath,
                        EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                } catch (e: Exception) {
                    Timber.e(e, "SMB rename: Failed to open source file: $fixedOldPath")
                    return@withConnection SmbResult.Error("Failed to open source file: ${e.message}")
                }

                file.use {
                    try {
                        // SMBJ rename() accepts full path relative to share root
                        it.rename(newPath, false)
                        Timber.i("Successfully renamed SMB file to: $newPath")
                    } catch (e: Exception) {
                        Timber.e(e, "SMB rename: rename() call failed for $fixedOldPath → $newPath")
                        throw e
                    }
                }

                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rename file on SMB")
            SmbResult.Error("Failed to rename file: ${e.message}", e)
        }
    }

    /**
     * Move file to different location on SMB share (copy + delete).
     * Use this instead of renameFile when moving to subdirectories.
     */
    suspend fun moveFile(
        connectionInfo: SmbConnectionInfo,
        sourcePath: String,
        destinationPath: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedSource = sourcePath.trim('/', '\\')
                val fixedDest = destinationPath.trim('/', '\\')
                Timber.d("Moving SMB file: sourcePath='$fixedSource' → destinationPath='$fixedDest'")

                // Check if source exists
                if (!share.fileExists(fixedSource)) {
                    return@withConnection SmbResult.Error("Source file does not exist: $fixedSource")
                }

                // Check if destination exists
                if (share.fileExists(fixedDest)) {
                    return@withConnection SmbResult.Error("Destination file already exists: $fixedDest")
                }

                // Ensure parent directory exists for destination.
                // Handle both forward and back slashes by replacing backslashes with forward slashes.
                val normalizedDest = fixedDest.replace('\\', '/')
                val destParent = normalizedDest.substringBeforeLast('/', "")
                Timber.d("MoveFile: fixedDest='$fixedDest', destParent='$destParent', isEmpty=${destParent.isEmpty()}")
                if (destParent.isNotEmpty()) {
                    Timber.d("Ensuring parent directory exists for move: $destParent")
                    ensureSmbDirectoryExists(share, destParent)
                    // Verify directory was created
                    if (!share.folderExists(destParent)) {
                        return@withConnection SmbResult.Error("Failed to create destination directory: $destParent")
                    }
                    Timber.d("Verified parent directory exists: $destParent")
                }

                // Open source file for reading
                val sourceFile = share.openFile(
                    fixedSource,
                    EnumSet.of(AccessMask.GENERIC_READ, AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                try {
                    // Open destination file for writing
                    val destFile = share.openFile(
                        fixedDest,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        null
                    )

                    try {
                        // Copy data
                        sourceFile.inputStream.use { input ->
                            destFile.outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Delete source file after successful copy
                        sourceFile.deleteOnClose()

                        Timber.i("Successfully moved SMB file to: $fixedDest")
                        SmbResult.Success(Unit)
                    } finally {
                        destFile.close()
                    }
                } finally {
                    sourceFile.close()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to move file on SMB")
            SmbResult.Error("Failed to move file: ${e.message}", e)
        }
    }

    /**
     * Recursively create directory structure on SMB share.
     * Handles race conditions where directory might be created by another process.
     */
    fun ensureSmbDirectoryExists(share: DiskShare, path: String) =
        SmbClientErrorFormatter.ensureSmbDirectoryExists(share, path)
}
