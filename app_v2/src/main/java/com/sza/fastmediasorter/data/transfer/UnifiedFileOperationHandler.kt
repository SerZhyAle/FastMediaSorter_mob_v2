package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.domain.usecase.GetDestinationFreeSpaceUseCase
import com.sza.fastmediasorter.util.directoryLandingPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified file operation handler for all protocols.
 *
 * Every operation - single file and whole directory - routes to the injected
 * [FileOperationStrategy] map, keyed by [transferProtocolKeyFor]; a file crossing protocols goes
 * through [DirectoryTreeTransferManager.copyFile], which knows which strategy spans the pair.
 */
@Singleton
class UnifiedFileOperationHandler @Inject constructor(
    private val errorHandler: FileOperationErrorHandler,
    private val operationStrategies: Map<String, @JvmSuppressWildcards FileOperationStrategy>,
    // S0391: source-availability gate; a remote op on a user-disabled source is refused at dispatch.
    private val remoteSourceGate: RemoteSourceAvailabilityGate,
    // S1325: whole-tree transfer for the source/destination pair no single strategy can serve.
    private val directoryTreeTransferManager: DirectoryTreeTransferManager,
    // S1378: free space of the volume that receives the data, not of built-in storage.
    private val getDestinationFreeSpace: GetDestinationFreeSpaceUseCase,
) {

    /**
     * Copies [sourceFile] into [destResource] under its own name.
     *
     * @return Result with the destination path or error
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun executeCopy(
        sourceFile: MediaFile,
        sourceResource: MediaResource,
        destResource: MediaResource,
        cancelFlag: () -> Boolean = { false }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cancelFlag()) {
                return@withContext Result.failure(Exception("Operation cancelled"))
            }

            refuseWhenCannotFit(destResource.path, sourceFile.size)?.let {
                Timber.w("executeCopy refused: ${it.reason}")
                return@withContext Result.failure(it)
            }

            requireSourceEnabled(sourceFile.path)
            requireSourceEnabled(destResource.path)
            val destPath = generateDestinationPath(destResource.path, sourceFile.name)
            Timber.d("S3972: executeCopy via strategies -> $destPath")
            directoryTreeTransferManager.copyFile(sourceFile.path, destPath).map { destPath }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val errorMsg = errorHandler.handleError(e, "copy", sourceFile.path, destResource.path)
            Timber.e(e, "Copy failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * Create directory at specified path.
     *
     * @param path Full protocol-specific path to create
     * @return Result with the path created
     */
    suspend fun executeCreateDirectory(
        path: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("S3972: executeCreateDirectory via strategy -> $path")
            getStrategy(path).createDirectory(path).map { path }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val errorMsg = errorHandler.handleError(e, "create_directory", path)
            Timber.e(e, "Create directory failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * Creates a new text file in the given parent path using the matching strategy.
     *
     * @param parentPath  protocol-specific path of the parent directory
     * @param fileName    name of the new file (no slashes)
     * @param resourceId  id of the owning resource; forwarded to strategy for staging registration
     * @param content     initial file content (empty string for blank notes)
     * @return [Result] containing the absolute/protocol path of the created file
     */
    suspend fun executeCreateTextFile(
        parentPath: String,
        fileName: String,
        resourceId: Long,
        content: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val strategy = getStrategy(parentPath)
            strategy.createTextFile(parentPath, fileName, content, resourceId)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val errorMsg = errorHandler.handleError(e, "create_text_file", parentPath)
            Timber.e(e, "Create text file failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * S0391: refuse a file operation whose remote source the user has disabled. Throws so the calling
     * operation's standard catch routes it through [errorHandler] into a failure Result. LOCAL paths
     * always pass; the cloud prefix is not provider-specific, so it gates on the cloud group.
     */
    private fun requireSourceEnabled(path: String) {
        val enabled = when (getProtocolKey(path)) {
            "smb" -> remoteSourceGate.isEnabled(RemoteSourceId.SMB)
            "sftp" -> remoteSourceGate.isEnabled(RemoteSourceId.SFTP)
            "ftp" -> remoteSourceGate.isEnabled(RemoteSourceId.FTP)
            "cloud" -> remoteSourceGate.anyCloudEnabled()
            else -> true
        }
        if (!enabled) {
            throw IllegalStateException("Source disabled for path: $path")
        }
    }

    /**
     * Generate destination file path.
     */
    private fun generateDestinationPath(destResourcePath: String, fileName: String): String {
        return if (destResourcePath.endsWith('/')) {
            "$destResourcePath$fileName"
        } else {
            "$destResourcePath/$fileName"
        }
    }

    // ==================== Directory Operations ====================

    /**
     * Delete a directory and all its contents recursively.
     *
     * @param dirPath Protocol-specific path of the directory.
     * @param progressCallback Optional callback (deletedCount, total, currentName).
     * @return Result<Int> - total number of entries deleted.
     */
    suspend fun executeDeleteDirectory(
        dirPath: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val strategy = getStrategy(dirPath)
            strategy.deleteDirectory(dirPath, progressCallback)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = errorHandler.handleError(e, "delete_directory", dirPath)
            Timber.e(e, "Delete directory failed: $msg")
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Rename a directory in place (same parent). [newName] must not contain path separators.
     *
     * @return Result<String> - new full path on success.
     */
    suspend fun executeRenameDirectory(
        oldPath: String,
        newName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val strategy = getStrategy(oldPath)
            val parentDir = oldPath.substringBeforeLast('/')
            val newPath = "$parentDir/$newName"
            strategy.renameDirectory(oldPath, newPath)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = errorHandler.handleError(e, "rename_directory", oldPath, newName)
            Timber.e(e, "Rename directory failed: $msg")
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Copy a directory tree to [destParentPath]/dirName.
     * Returns [Result.failure] when source and destination protocols differ.
     *
     * @return Result<Int> - total files copied.
     */
    suspend fun executeCopyDirectory(
        sourcePath: String,
        destParentPath: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        refuseUnsafeDirectoryOperation(sourcePath, destParentPath, isMove = false)?.let {
            Timber.w("executeCopyDirectory refused: ${it.reason}")
            return@withContext Result.failure(it)
        }
        try {
            val sourceProtocol = getProtocolKey(sourcePath)
            val destProtocol = getProtocolKey(destParentPath)
            val destination = directoryLandingPath(sourcePath, destParentPath)
            if (sourceProtocol != destProtocol) {
                requireSourceEnabled(sourcePath)
                directoryTreeTransferManager.copyTree(sourcePath, destination, progressCallback)
            } else {
                getStrategy(sourcePath).copyDirectory(sourcePath, destination, progressCallback)
            }
        } catch (e: UnsupportedOperationException) {
            Timber.w(e, "executeCopyDirectory: unsupported operation")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = errorHandler.handleError(e, "copy_directory", sourcePath, destParentPath)
            Timber.e(e, "Copy directory failed: $msg")
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Move a directory tree (copy + delete source) to [destParentPath]/dirName.
     * Returns [Result.failure] when source and destination protocols differ.
     *
     * @return Result<Int> - total files moved.
     */
    suspend fun executeMoveDirectory(
        sourcePath: String,
        destParentPath: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        refuseUnsafeDirectoryOperation(sourcePath, destParentPath, isMove = true)?.let {
            Timber.w("executeMoveDirectory refused: ${it.reason}")
            return@withContext Result.failure(it)
        }
        try {
            val sourceProtocol = getProtocolKey(sourcePath)
            val destProtocol = getProtocolKey(destParentPath)
            val destination = directoryLandingPath(sourcePath, destParentPath)
            if (sourceProtocol != destProtocol) {
                requireSourceEnabled(sourcePath)
                directoryTreeTransferManager.moveTree(sourcePath, destination, progressCallback)
            } else {
                getStrategy(sourcePath).moveDirectory(sourcePath, destination, progressCallback)
            }
        } catch (e: UnsupportedOperationException) {
            Timber.w(e, "executeMoveDirectory: unsupported operation")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = errorHandler.handleError(e, "move_directory", sourcePath, destParentPath)
            Timber.e(e, "Move directory failed: $msg")
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * S1325: pre-flight refusal for a whole-tree operation. Runs before the strategy is resolved,
     * so a refused operation never creates a partial structure at the destination and the caller
     * gets a reason it can turn into a specific message instead of a generic failure toast.
     */
    private suspend fun refuseUnsafeDirectoryOperation(
        sourcePath: String,
        destParentPath: String,
        isMove: Boolean,
    ): DirectoryOperationRefusal? = when {
        isDestinationInsideSource(sourcePath, destParentPath) -> DirectoryOperationRefusal(
            DirectoryOperationRefusal.Reason.DESTINATION_INSIDE_SOURCE,
            "Destination $destParentPath is the source directory $sourcePath or lives inside it",
        )
        // Copy is refused here too, not only move: the tree lands under destination/<name>, which
        // for the current parent is the source itself - the per-entry copy would overwrite its own
        // input rather than produce a second copy.
        isSameParent(sourcePath, destParentPath) -> DirectoryOperationRefusal(
            DirectoryOperationRefusal.Reason.SAME_LOCATION,
            "Target $destParentPath already holds the source directory $sourcePath (move=$isMove)",
        )
        else -> refuseWhenCannotFit(destParentPath, sourceTreeSize(sourcePath))
    }

    /**
     * S1378: refusal when [requiredBytes] does not fit at [destPath]. Null on every uncertainty -
     * unknown size, unmeasurable destination - because an operation the check cannot judge must
     * still run; only a measured shortfall may stop it.
     */
    private suspend fun refuseWhenCannotFit(
        destPath: String,
        requiredBytes: Long?,
    ): DirectoryOperationRefusal? {
        val needed = requiredBytes?.takeIf { it > 0 }
        val freeBytes = needed?.let { getDestinationFreeSpace(destPath) }
        return if (needed == null || freeBytes == null || needed <= freeBytes) {
            null
        } else {
            DirectoryOperationRefusal(
                DirectoryOperationRefusal.Reason.INSUFFICIENT_SPACE,
                "Destination $destPath holds $freeBytes free bytes, the operation needs $needed",
                // Second lookup, but only on the refusal path: naming the medium is worth one more
                // registry read when the operation is already stopping.
                destinationLabel = getDestinationFreeSpace.volumeFor(destPath)?.displayName,
                missingBytes = needed - freeBytes,
            )
        }
    }

    /**
     * Bytes the source tree occupies, or null when its protocol cannot report them.
     *
     * Goes through the strategy rather than walking the filesystem here, so a tree on a document
     * provider is measured too - a copy *off* a card would otherwise never be fit-checked. The map
     * is read directly instead of through [getStrategy]: the pre-flight must not trip the
     * source-availability gate, which belongs to the operation and not to this measurement.
     */
    private suspend fun sourceTreeSize(sourcePath: String): Long? =
        operationStrategies[getProtocolKey(sourcePath)]
            ?.getDirectoryInfo(sourcePath)
            ?.getOrNull()
            ?.totalSize

    private fun isDestinationInsideSource(sourcePath: String, destParentPath: String): Boolean =
        asDirectoryPrefix(destParentPath).startsWith(asDirectoryPrefix(sourcePath))

    private fun isSameParent(sourcePath: String, destParentPath: String): Boolean {
        val sourceParent = sourcePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
        return sourceParent.isNotEmpty() &&
            asDirectoryPrefix(sourceParent) == asDirectoryPrefix(destParentPath)
    }

    /** Separator-terminated form so a prefix test cannot match a sibling with a longer name. */
    private fun asDirectoryPrefix(path: String): String = path.trimEnd('/') + "/"

    /**
     * Resolve the [FileOperationStrategy] for [path] based on its protocol prefix.
     */
    private fun getStrategy(path: String): FileOperationStrategy {
        requireSourceEnabled(path)
        val key = getProtocolKey(path)
        return operationStrategies[key]
            ?: throw IllegalStateException("No FileOperationStrategy registered for protocol: $key")
    }

    private fun getProtocolKey(path: String): String = transferProtocolKeyFor(path)
}

/**
 * S1325: a directory copy or move refused before it started. [reason] lets the UI pick the message
 * that names the actual obstacle; the exception message stays technical for the log.
 */
class DirectoryOperationRefusal(
    val reason: Reason,
    message: String,
    // S1378: what the user needs to be told - which medium refused and by how much. Optional
    // because only the space refusal measures anything; the other reasons name no quantity.
    val destinationLabel: String? = null,
    val missingBytes: Long? = null,
) : Exception(message) {
    enum class Reason {
        DESTINATION_INSIDE_SOURCE,
        SAME_LOCATION,
        INSUFFICIENT_SPACE,
    }
}
