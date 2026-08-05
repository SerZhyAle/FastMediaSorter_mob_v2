package com.sza.fastmediasorter.data.network

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy
import com.sza.fastmediasorter.data.transfer.BaseFileOperationHandler
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for SMB file operations.
 * Handles copy, move, delete operations for SMB resources.
 * 
 * Now extends BaseFileOperationHandler to eliminate duplicate code.
 * Uses strategy pattern for protocol-specific operations.
 */
@Singleton
// LongParameterList: DI aggregator - each parameter is an independently-injected collaborator;
// folding them into a params object would hide the wiring without reducing real coupling.
@Suppress("LongParameterList")
class SmbFileOperationHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: com.sza.fastmediasorter.data.remote.sftp.SftpClient,
    private val ftpClient: com.sza.fastmediasorter.data.remote.ftp.FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val endpointResolver: com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver,
    private val stagingDir: com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider,
    private val stagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter
) : BaseFileOperationHandler(context) {

    // Strategy instances wrapped with atomic transfer support
    private val smbStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        SmbOperationStrategy(context, smbClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val sftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy(
            context,
            sftpClient,
            credentialsRepository,
            endpointResolver,
            stagingDir,
            stagingRegistry,
            destinationClassifier,
            destinationWriter
        ),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val ftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy(context, ftpClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val localStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        LocalOperationStrategy(context, stagingRegistry),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )
    
    override fun getStrategies(): List<FileOperationStrategy> {
        return listOf(smbStrategy, sftpStrategy, ftpStrategy, localStrategy)
    }
    
    /**
     * Override copyFile to handle cross-protocol transfers via strategies.
     * The base class will now automatically route to the correct strategy.
     * 
     * Legacy cross-protocol methods (copySftpToSmb, copyFtpToSmb, etc.) are kept
     * temporarily for any remaining direct calls, but will be removed in Phase 2.3.3.
     */
    /**
     * Override copyFile to handle cross-protocol transfers via strategies.
     * The base class will now automatically route to the correct strategy.
     * 
     * Optimization: Explicitly handle Local <-> SMB to use the optimized SmbOperationStrategy methods.
     * Legacy: Handles direct transfers from SFTP/FTP to SMB.
     */
    override suspend fun copyFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        // Optimization: If operation involves SMB, use SMB strategy directly
        if (sourcePath.startsWith("smb:", ignoreCase = true) || destPath.startsWith("smb:", ignoreCase = true)) {
            
            // Check for cross-protocol (e.g. FTP -> SMB, SMB -> FTP)
            val isSourceNetwork = sourcePath.startsWith("smb:") || sourcePath.startsWith("ftp:") || sourcePath.startsWith("sftp:")
            val isDestNetwork = destPath.startsWith("smb:") || destPath.startsWith("ftp:") || destPath.startsWith("sftp:")
            
            if (isSourceNetwork && isDestNetwork) {
                 // Cross-protocol bridge: Download -> Temp -> Upload
                 Timber.d("SmbFileOperationHandler: Bridging copy $sourcePath -> $destPath")
                 return try {
                     val tempFile = File.createTempFile("bridge_copy_smb_", ".tmp", context.cacheDir)
                     try {
                         // 1. Download to temp
                         val downloadResult = if (sourcePath.startsWith("smb:", ignoreCase = true)) {
                             smbStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         } else if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
                             sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         } else {
                             ftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         }
                         
                         if (downloadResult.isFailure) {
                             val error = downloadResult.exceptionOrNull()
                             Timber.e(error, "Bridge copy: Failed to download source $sourcePath")
                             return Result.failure(Exception("Failed to download source for bridge copy: ${error?.message ?: "Unknown error"}", error))
                         }
                         
                         Timber.d("Bridge copy: Downloaded ${tempFile.length()} bytes from $sourcePath to temp")
                         
                         // 2. Upload from temp
                         val uploadResult = if (destPath.startsWith("smb:", ignoreCase = true)) {
                             smbStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
                         } else if (destPath.startsWith("sftp:", ignoreCase = true)) {
                             sftpStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
                         } else {
                             ftpStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
                         }
                         
                         uploadResult
                     } finally {
                         tempFile.delete()
                     }
                 } catch (e: Exception) {
                     e.rethrowIfCancellation()
                     Result.failure(e)
                 }
            }
            
            return smbStrategy.copyFile(sourcePath, destPath, overwrite, progressCallback)
        }
        
        // Delegate to base class for others
        return super.copyFile(sourcePath, destPath, overwrite, progressCallback)
    }

    // ... (existing executeMove implementation) ...

    // ... (existing helper methods) ...

    /**
     * Legacy method to copy from SFTP to SMB directly.
     * Temporarily restored for lost improvements.
     * Uses PipedStreams to bridge SFTP download and SMB upload.
     */



    private sealed interface MoveOutcome {
        data class Success(val destFilePath: String) : MoveOutcome
        data class Failure(val error: String) : MoveOutcome
        data class PermissionRequired(val sourcePath: String, val destFilePath: String) : MoveOutcome

        // S1180: a local source already moved+deleted by a concurrent scheduled op is gone by upload
        // time. That is not a move failure - the file already reached the destination - so route it
        // through buildMoveResult's skip path instead of errors, or overlapping ops report false moves.
        data class Skipped(val fileName: String) : MoveOutcome
    }

    private suspend fun moveSmbToSmb(
        sourcePath: String,
        destFilePath: String,
        fileName: String
    ): MoveOutcome {
        val result = smbStrategy.moveFile(sourcePath, destFilePath)
        return if (result.isSuccess) {
            Timber.i("SMB executeMove: SUCCESS - moved $fileName via strategy")
            MoveOutcome.Success(destFilePath)
        } else {
            val error = "Failed to move $fileName: ${result.exceptionOrNull()?.message}"
            MoveOutcome.Failure(error)
        }
    }

    private suspend fun moveBridgeToSmb(
        sourcePath: String,
        destFilePath: String,
        fileName: String,
        progressCallback: ByteProgressCallback?
    ): MoveOutcome {
        val tempFile = File(context.cacheDir, "bridge_${System.currentTimeMillis()}_$fileName")
        return try {
            // 1. Download source to temp
            val downloadResult = if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
                sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, progressCallback)
            } else {
                ftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, progressCallback)
            }
            
            if (downloadResult.isFailure) {
                val error = "Failed to download $fileName: ${downloadResult.exceptionOrNull()?.message}"
                MoveOutcome.Failure(error)
            } else {
                // 2. Upload to SMB
                val uploadResult = uploadToSmb(tempFile, destFilePath, progressCallback)
                if (uploadResult is SmbResult.Success) {
                    // 3. Delete network source
                    val deleteResult = if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
                        sftpStrategy.deleteFile(sourcePath)
                    } else {
                        ftpStrategy.deleteFile(sourcePath)
                    }
                    
                    if (deleteResult.isSuccess) {
                        Timber.i("SMB executeMove: SUCCESS - moved $fileName via bridge")
                        MoveOutcome.Success(destFilePath)
                    } else {
                        val error = "Uploaded $fileName but failed to delete source: ${deleteResult.exceptionOrNull()?.message}"
                        MoveOutcome.Failure(error)
                    }
                } else {
                    val error = "Failed to upload $fileName: ${(uploadResult as SmbResult.Error).message}"
                    MoveOutcome.Failure(error)
                }
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun moveLocalToSmb(
        sourcePath: String,
        destFilePath: String,
        fileName: String,
        progressCallback: ByteProgressCallback?
    ): MoveOutcome {
        // S1180: a concurrent scheduled op with an overlapping source folder may have already moved and
        // deleted this local original. A vanished non-SAF source is that race, not a fault - skip it so
        // overlapping ops do not report false "move failed" errors.
        if (!sourcePath.startsWith("content:/") && !File(sourcePath).exists()) {
            return MoveOutcome.Skipped(fileName)
        }

        // Upload first, then delete the source - deletion may require permission on Android 11+.
        return try {
            val uploadResult = uploadToSmb(File(sourcePath), destFilePath, progressCallback)
            if (uploadResult !is SmbResult.Success) {
                MoveOutcome.Failure("Failed to upload $fileName: ${(uploadResult as SmbResult.Error).message}")
            } else {
                val deleteSuccess = if (sourcePath.startsWith("content:/")) {
                    deleteWithSaf(sourcePath)
                } else {
                    deleteFile(sourcePath).isSuccess
                }
                if (deleteSuccess) {
                    Timber.i("SMB executeMove: SUCCESS - moved $fileName")
                    MoveOutcome.Success(destFilePath)
                } else {
                    val error = "Uploaded $fileName but failed to delete source at $sourcePath"
                    Timber.e("SMB executeMove: FAILED - $error")
                    MoveOutcome.Failure(error)
                }
            }
        } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
            // File is already uploaded - collect for batch delete after all uploads complete.
            Timber.i("SMB executeMove: File uploaded, permission needed for delete - $fileName")
            MoveOutcome.PermissionRequired(sourcePath, destFilePath)
        }
    }

    override suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        Timber.i("SmbFileOperationHandler.executeMove: ENTRY - sources=${operation.sources.size}, destination=${operation.destination.path}")
        operation.sources.forEachIndexed { index, source ->
            Timber.d("SmbFileOperationHandler.executeMove: Source[$index]: ${source.path}")
        }
        
        val destinationPath = operation.destination.path

        // Check for -> SMB move
        if (destinationPath.startsWith("smb:", ignoreCase = true)) {
            Timber.i("SMB executeMove: Destination is SMB, starting move of ${operation.sources.size} files to $destinationPath")
            
            // NO pre-flight check! Upload first, then delete.
            // Delete uses createDeleteRequest which auto-deletes after user grants permission.
            // This way files are safely on server before any deletion attempt.
            
            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0
            var skippedCount = 0

            // Collect files that need permission for batch delete after all uploads
            val pendingDeletePaths = mutableListOf<String>()
            val pendingDeleteDestPaths = mutableListOf<String>()

            operation.sources.forEachIndexed { index, source ->
                val sourcePath = source.path
                val fileName = extractFileName(sourcePath, source.name)
                // Ensure proper path construction
                val destFilePath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" else "$destinationPath/$fileName"
                
                Timber.d("SMB executeMove: [${index + 1}/${operation.sources.size}] Moving $fileName to $destFilePath")
                progressCallback?.onFileStarted(index + 1, fileName, operation.sources.size)

                val outcome = when {
                    sourcePath.startsWith("smb:", ignoreCase = true) -> {
                        moveSmbToSmb(sourcePath, destFilePath, fileName)
                    }
                    sourcePath.startsWith("sftp:", ignoreCase = true) ||
                    sourcePath.startsWith("ftp:", ignoreCase = true) -> {
                        moveBridgeToSmb(sourcePath, destFilePath, fileName, progressCallback)
                    }
                    else -> {
                        moveLocalToSmb(sourcePath, destFilePath, fileName, progressCallback)
                    }
                }

                when (outcome) {
                    is MoveOutcome.Success -> {
                        movedPaths.add(outcome.destFilePath)
                        successCount++
                    }
                    is MoveOutcome.Failure -> {
                        errors.add(outcome.error)
                    }
                    is MoveOutcome.PermissionRequired -> {
                        pendingDeletePaths.add(outcome.sourcePath)
                        pendingDeleteDestPaths.add(outcome.destFilePath)
                        movedPaths.add(outcome.destFilePath)
                        successCount++
                    }
                    is MoveOutcome.Skipped -> skippedCount++
                }
            }
            
            // After all uploads complete, check if any files need permission for batch delete.
            // requestBatchDeletePermission uses MediaStore.createDeleteRequest (API 30+); skip on older devices.
            if (pendingDeletePaths.isNotEmpty() &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Timber.i("SMB executeMove: All ${pendingDeletePaths.size} files uploaded, requesting batch delete permission")
                // Create batch delete request for all pending files
                requestBatchDeletePermission(pendingDeletePaths)
                // Note: requestBatchDeletePermission throws BatchDeletePermissionRequiredException
                // which will be caught by FileOperationUseCase and converted to PermissionRequired result
            }
            
            Timber.i("SMB executeMove done - success=$successCount, skipped=$skippedCount, errors=${errors.size}")
            return buildMoveResult(successCount, operation, movedPaths, errors, skippedCount)
        }

        // Optimization: If operation involves SMB, use SMB strategy directly
        if (destinationPath.startsWith("smb:", ignoreCase = true)) {
             // Logic above handled the move
             // If we reached here without returning, something is wrong with the flow control above
             // But actually, the code above returns.
             Timber.e("SMB executeMove: CRITICAL - Reached unreachable code after destination SMB check!")
        }
        
        // Check sources for SMB to use optimized move
        val firstSource = operation.sources.firstOrNull()?.path
        Timber.d("SMB executeMove: Fallback check - firstSource=$firstSource")
        if (firstSource != null && firstSource.startsWith("smb:", ignoreCase = true) && destinationPath.startsWith("smb:", ignoreCase = true)) {
            Timber.d("SMB executeMove: Using SMB strategy for single source move")
            val result = smbStrategy.moveFile(firstSource, destinationPath)
            if (result.isSuccess) {
                 Timber.i("SMB executeMove: Strategy move success")
                 return FileOperationResult.Success(1, operation, listOf(destinationPath))
            } else {
                 Timber.e("SMB executeMove: Strategy move failed - ${result.exceptionOrNull()?.message}")
                 return FileOperationResult.Failure(result.exceptionOrNull()?.message ?: "Move failed")
            }
        }

        Timber.d("SMB executeMove: Delegating to super.executeMove")
        val superResult = super.executeMove(operation, progressCallback)
        Timber.i("SMB executeMove: EXIT with result ${superResult.javaClass.simpleName}")
        return superResult
    }

    /**
     * Legacy optimized moveFile - kept for strategy use but prefer executeMove.
     */
    override suspend fun moveFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        return super.moveFile(sourcePath, destPath, overwrite, progressCallback)
    }

    /**
     * Execute rename operation (not yet in base class).
     * Delegates to executeMove since rename is essentially a move within the same folder.
     */
    /**
     * Execute rename operation.
     * Directly calls strategy instead of hacking executeMove.
     */
    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult {
        val sourcePath = operation.file.path
        
        // Construct destination path
        val destinationPath = if (sourcePath.startsWith("smb:", ignoreCase = true)) {
            val lastSlashIndex = sourcePath.lastIndexOf('/')
            if (lastSlashIndex == -1) {
                return FileOperationResult.Failure("Invalid SMB path: $sourcePath")
            }
            val parentPath = sourcePath.substring(0, lastSlashIndex)
            "$parentPath/${operation.newName}"
        } else {
            // For local paths (though usually handled by LocalFileProvider/Strategy)
            val parentDir = operation.file.parentFile 
                ?: return FileOperationResult.Failure("Cannot get parent directory")
            java.io.File(parentDir, operation.newName).path
        }
        
        Timber.d("executeRename: Renaming $sourcePath -> $destinationPath")
        
        // Use strategy directly
        val result = smbStrategy.moveFile(sourcePath, destinationPath)
        
        return result.fold(
            onSuccess = {
                FileOperationResult.Success(1, operation, listOf(destinationPath))
            },
            onFailure = { e ->
                val errorMsg = FileOperationError.formatTransferError(
                    operation.file.name,
                    sourcePath,
                    destinationPath,
                    e.message ?: "Unknown error"
                )
                Timber.e("executeRename: FAILED - $errorMsg")
                FileOperationResult.Failure(errorMsg)
            }
        )
    }

    // ==================== HELPER METHODS ====================

    private suspend fun downloadFromSmb(
        smbPath: String,
        localFile: File,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<File> {
        Timber.d("downloadFromSmb: $smbPath → ${localFile.absolutePath}")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            val msg = "Failed to parse SMB path: $smbPath"
            Timber.e("downloadFromSmb: $msg")
            return SmbResult.Error(msg)
        }
        
        Timber.d("downloadFromSmb: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, path=${connectionInfo.remotePath}")
        
        // File size is unknown for SMB downloads, pass 0L
        // Progress will still work, just without percentage
        
        return try {
            localFile.outputStream().use { outputStream ->
                val resourceKey = "smb://${connectionInfo.connectionInfo.server}:${connectionInfo.connectionInfo.port}"
                when (val result = ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
                    resourceKey = resourceKey,
                    highPriority = true
                ) {
                    smbClient.downloadFile(
                        connectionInfo.connectionInfo,
                        connectionInfo.remotePath,
                        outputStream,
                        fileSize = 0L,
                        progressCallback = progressCallback
                    )
                }) {
                    is SmbResult.Success -> {
                        Timber.i("downloadFromSmb: SUCCESS - ${localFile.length()} bytes written to ${localFile.name}")
                        MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "smb-download")
                        SmbResult.Success(localFile)
                    }
                    is SmbResult.Error -> {
                        Timber.e("downloadFromSmb: FAILED - ${result.message}")
                        result
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = "Failed to write local file: ${e.message}"
            Timber.e(e, "downloadFromSmb: $msg")
            SmbResult.Error(msg, e)
        }
    }

    private suspend fun uploadToSmb(
        localFile: File,
        smbPath: String,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<String> {
        Timber.d("uploadToSmb: ${localFile.path} → $smbPath")
        
        val isSaf = localFile.path.startsWith("content:/")
        
        if (!isSaf && !localFile.exists()) {
            // S1138: an external producer (e.g. a downloader app) can delete the source between batch
            // enumeration and upload. That is an expected race, not an app fault - log at warn, not error.
            val msg = "Local file does not exist: ${localFile.absolutePath}"
            Timber.w("uploadToSmb: $msg")
            return SmbResult.Error(msg)
        }
        
        val fileSize = if (isSaf) {
             try {
                // Normalize content URI for parsing
                val normalizedUri = if (localFile.path.startsWith("content://")) localFile.path 
                                   else localFile.path.replaceFirst("content:/", "content://")
                val uri = Uri.parse(normalizedUri)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } catch (e: Exception) {
                Timber.w(e, "uploadToSmb: Failed to get SAF file size")
                0L
            }
        } else {
            localFile.length()
        }
        Timber.d("uploadToSmb: Local file size=$fileSize bytes")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            val msg = "Failed to parse SMB path: $smbPath"
            Timber.e("uploadToSmb: $msg")
            return SmbResult.Error(msg)
        }
        
        Timber.d("uploadToSmb: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, path=${connectionInfo.remotePath}")
        
        return try {
            val inputStream = if (isSaf) {
                val normalizedUri = if (localFile.path.startsWith("content://")) localFile.path 
                                   else localFile.path.replaceFirst("content:/", "content://")
                context.contentResolver.openInputStream(Uri.parse(normalizedUri))
            } else {
                localFile.inputStream()
            }
            
            if (inputStream == null) {
                return SmbResult.Error("Failed to open input stream for ${localFile.path}")
            }

            // S1138: capture the upload outcome, then close the local source in a finally that swallows a
            // close() I/O error. Previously inputStream.use { } closed the FUSE-backed source after a
            // successful uploadFile, and an EIO on that close propagated and turned the success into a
            // spurious FAILURE - inflating the scheduled-sync error rate. A close must not override upload.
            try {
                when (val result = smbClient.uploadFile(
                    connectionInfo.connectionInfo,
                    connectionInfo.remotePath,
                    inputStream,
                    fileSize,
                    progressCallback
                )) {
                    is SmbResult.Success -> {
                        Timber.i("uploadToSmb: SUCCESS - uploaded ${localFile.name}")
                        SmbResult.Success(smbPath)
                    }
                    is SmbResult.Error -> {
                        Timber.e("uploadToSmb: FAILED - ${result.message}")
                        result
                    }
                }
            } finally {
                closeLocalSourceQuietly(inputStream)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = "Failed to read local file: ${e.message}"
            Timber.e(e, "uploadToSmb: $msg")
            SmbResult.Error(msg, e)
        }
    }

    private suspend fun deleteFromSmb(smbPath: String): SmbResult<Unit> {
        Timber.d("deleteFromSmb: START - path=$smbPath")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            val msg = "Failed to parse SMB path: $smbPath"
            Timber.e("deleteFromSmb: FAILED - $msg")
            return SmbResult.Error(msg)
        }
        
        Timber.d("deleteFromSmb: Parsed SMB path - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, remotePath='${connectionInfo.remotePath}', port=${connectionInfo.connectionInfo.port}")
        Timber.d("deleteFromSmb: Credentials - username=${connectionInfo.connectionInfo.username}, domain=${connectionInfo.connectionInfo.domain}")

        return when (val result = smbClient.deleteFile(connectionInfo.connectionInfo, connectionInfo.remotePath)) {
            is SmbResult.Success -> {
                Timber.i("deleteFromSmb: SUCCESS - File deleted: $smbPath")
                SmbResult.Success(Unit)
            }
            is SmbResult.Error -> {
                Timber.e("deleteFromSmb: FAILED - Error: ${result.message}, Exception: ${result.exception?.javaClass?.simpleName}, Message: ${result.exception?.message}")
                result.exception?.printStackTrace()
                result
            }
        }
    }

    private suspend fun copySmbToSmb(sourcePath: String, destPath: String): SmbResult<String> {
        Timber.d("copySmbToSmb: $sourcePath → $destPath")
        
        // Download to memory then upload
        val connectionInfo = parseSmbPath(sourcePath)
        if (connectionInfo == null) {
            val msg = "Failed to parse source SMB path: $sourcePath"
            Timber.e("copySmbToSmb: $msg")
            return SmbResult.Error(msg)
        }
        
        Timber.d("copySmbToSmb: Source parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}")
        
        val buffer = ByteArrayOutputStream()

        when (val downloadResult = smbClient.downloadFile(connectionInfo.connectionInfo, connectionInfo.remotePath, buffer)) {
            is SmbResult.Success -> {
                val bytes = buffer.toByteArray()
                Timber.d("copySmbToSmb: Downloaded ${bytes.size} bytes from source")
                
                val destConnectionInfo = parseSmbPath(destPath)
                if (destConnectionInfo == null) {
                    val msg = "Failed to parse dest SMB path: $destPath"
                    Timber.e("copySmbToSmb: $msg")
                    return SmbResult.Error(msg)
                }
                
                Timber.d("copySmbToSmb: Dest parsed - server=${destConnectionInfo.connectionInfo.server}, share=${destConnectionInfo.connectionInfo.shareName}")
                
                val inputStream = ByteArrayInputStream(bytes)

                return when (val uploadResult = smbClient.uploadFile(destConnectionInfo.connectionInfo, destConnectionInfo.remotePath, inputStream)) {
                    is SmbResult.Success -> {
                        Timber.i("copySmbToSmb: SUCCESS - copied ${bytes.size} bytes between SMB shares")
                        SmbResult.Success(destPath)
                    }
                    is SmbResult.Error -> {
                        Timber.e("copySmbToSmb: Upload FAILED - ${uploadResult.message}")
                        uploadResult
                    }
                }
            }
            is SmbResult.Error -> {
                Timber.e("copySmbToSmb: Download FAILED - ${downloadResult.message}")
                return downloadResult
            }
        }
    }



    internal data class SmbConnectionInfoWithPath(
        val connectionInfo: SmbConnectionInfo,
        val remotePath: String
    )

    internal suspend fun parseSmbPath(path: String): SmbConnectionInfoWithPath? {
        return try {
            // Parse path to extract server and share
            val tempInfo = SmbPathUtils.parseSmbPath(path) ?: return null
            
            val server = tempInfo.connectionInfo.server
            val share = tempInfo.connectionInfo.shareName
            
            Timber.d("SmbFileOperationHandler: Looking up credentials for server='$server', share='$share'")
            val credentials = resolveSmbCredentials(server, share)
            Timber.d("SmbFileOperationHandler: Credential lookup result: ${if (credentials != null) "FOUND (user='${credentials.username}')" else "NULL"}")
            
            // Return path info with actual credentials
            SmbConnectionInfoWithPath(
                connectionInfo = SmbConnectionInfo(
                    server = server,
                    shareName = share,
                    username = credentials?.username ?: "",
                    password = credentials?.password ?: "",
                    domain = credentials?.domain ?: "",
                    port = tempInfo.connectionInfo.port
                ),
                remotePath = tempInfo.remotePath
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    private suspend fun resolveSmbCredentials(
        server: String,
        shareName: String
    ): com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity? {
        val normalizedShare = shareName.trim().trim('/', '\\')
        val firstSegment = normalizedShare.substringBefore('/', normalizedShare)

        val shareCandidates = linkedSetOf<String>().apply {
            add(shareName)
            if (normalizedShare.isNotEmpty()) {
                add(normalizedShare)
            }
            if (firstSegment.isNotEmpty()) {
                add(firstSegment)
            }
        }

        for (candidate in shareCandidates) {
            val candidateCredentials = credentialsRepository.getByServerAndShare(server, candidate)
            if (candidateCredentials != null) {
                Timber.d("SmbFileOperationHandler: Credentials resolved for server='$server', share='$candidate'")
                return candidateCredentials
            }
        }

        val hostCredentials = credentialsRepository.getCredentialsByHost(server)
        if (hostCredentials != null && hostCredentials.type.equals("SMB", ignoreCase = true)) {
            Timber.w("SmbFileOperationHandler: Share-specific credentials not found for '$server/$shareName', using SMB host credentials")
            return hostCredentials
        }

        Timber.w("SmbFileOperationHandler: No SMB credentials found for '$server/$shareName'")
        return null
    }
}

/**
 * Close a local source stream after an SMB upload without letting a close() I/O error override an
 * already-successful upload. S1138: an EIO on close() of a FUSE-backed /storage source (concurrently
 * deleted/truncated by an external producer) previously turned a completed upload into a spurious
 * FAILURE. A close failure is expected in that race - log it at warn and swallow it, never raise.
 */
internal fun closeLocalSourceQuietly(stream: Closeable) {
    try {
        stream.close()
    } catch (e: IOException) {
        Timber.w(e, "uploadToSmb: local source close failed after upload (ignored)")
    }
}
