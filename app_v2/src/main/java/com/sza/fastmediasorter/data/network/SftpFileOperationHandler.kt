package com.sza.fastmediasorter.data.network

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpDownloadExhaustedException
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure
import com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy
import com.sza.fastmediasorter.data.transfer.BaseFileOperationHandler
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.utils.SftpPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for SFTP file operations built on BaseFileOperationHandler + strategies.
 */
@Singleton
class SftpFileOperationHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val sftpClient: SftpClient,
    private val smbClient: SmbClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val endpointResolver: SftpEndpointResolver,
    private val stagingDir: com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider,
    private val stagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter
) : BaseFileOperationHandler(context) {

    private val sftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        SftpOperationStrategy(
            context, sftpClient, credentialsRepository, endpointResolver,
            stagingDir, stagingRegistry, destinationClassifier, destinationWriter
        ),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val smbStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        SmbOperationStrategy(context, smbClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val ftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        FtpOperationStrategy(context, ftpClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val localStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        LocalOperationStrategy(context, stagingRegistry),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    override fun getStrategies(): List<FileOperationStrategy> {
        return listOf(sftpStrategy, smbStrategy, ftpStrategy, localStrategy)
    }

    override suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult = super.executeCopy(operation, progressCallback)

    override suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult = withContext(Dispatchers.IO) {
        // S1813: the base declares this method's body as withContext(Dispatchers.IO); an override
        // replaces that body, so the confinement has to be restated here or it is silently lost.
        val destinationPath = operation.destination.path

        // Handle Local/SAF -> SFTP move explicitly
        if (destinationPath.startsWith("sftp:", ignoreCase = true)) {
            Timber.d("SFTP executeMove: Starting move of ${operation.sources.size} files to $destinationPath")
            
            // NO pre-flight check! Upload first, then delete.
            // Delete uses createDeleteRequest which auto-deletes after user grants permission.
            
            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0
            
            // Collect files that need permission for batch delete after all uploads
            val pendingDeletePaths = mutableListOf<String>()

            operation.sources.forEachIndexed { index, source ->
                val sourcePath = source.path
                val fileName = extractFileName(sourcePath, source.name)
                val destFilePath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" else "$destinationPath/$fileName"
                
                Timber.d("SFTP executeMove: [${index + 1}/${operation.sources.size}] Moving $fileName")
                
                // 1. Upload via copyFile (which we should enable to handle SAF if not already)
                // Need to ensure sftpStrategy.copyFile handles SAF or we do it here manually via sftpClient
                // sftpStrategy uses SftpClient.uploadFile which takes InputStream. 
                // We should check if sftpStrategy resolves SAF.
                // Assuming it might NOT, we should implement a helper here similar to Ftp.
                
                val uploadResult = copyFile(sourcePath, destFilePath, true, progressCallback)
                
                if (uploadResult.isSuccess) {
                    val uploadedPath = uploadResult.getOrNull() ?: destFilePath
                    
                    // 2. Delete Source - may require permission on Android 11+
                    val deleteSuccess = if (sourcePath.startsWith("content:/")) {
                        deleteWithSaf(sourcePath)
                    } else {
                        try {
                            deleteFile(sourcePath).isSuccess
                        } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
                            // File is already uploaded! Collect for batch delete after all uploads.
                            Timber.i("SFTP executeMove: File uploaded, permission needed for delete - $fileName")
                            pendingDeletePaths.add(sourcePath)
                            movedPaths.add(uploadedPath)
                            successCount++
                            true // Consider delete "pending" - will be done after batch permission grant
                        }
                    }
                    
                    if (deleteSuccess && !pendingDeletePaths.contains(sourcePath)) {
                        movedPaths.add(uploadedPath)
                        successCount++
                        Timber.i("SFTP executeMove: SUCCESS - moved $fileName")
                    } else if (!pendingDeletePaths.contains(sourcePath)) {
                        val error = "Uploaded $fileName but failed to delete source at $sourcePath"
                        Timber.e("SFTP executeMove: FAILED - $error")
                        errors.add(error)
                        // Do NOT add to movedPaths or increment successCount
                        // File was copied but not moved (source still exists)
                    }
                } else {
                    val error = "Failed to upload $fileName to SFTP: ${uploadResult.exceptionOrNull()?.message}"
                    errors.add(error)
                }
            }
            
            // After all uploads complete, check if any files need permission for batch delete.
            // requestBatchDeletePermission uses MediaStore.createDeleteRequest (API 30+); skip on older devices.
            if (pendingDeletePaths.isNotEmpty() &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Timber.i("SFTP executeMove: All ${pendingDeletePaths.size} files uploaded, requesting batch delete permission")
                requestBatchDeletePermission(pendingDeletePaths)
            }
            
            return@withContext buildMoveResult(successCount, operation, movedPaths, errors)
        }
        
        // Handle SFTP -> FTP move via temp file bridging
        val sourcePath = operation.sources.firstOrNull()?.path
        if (sourcePath?.startsWith("sftp:", ignoreCase = true) == true && 
            destinationPath.startsWith("ftp:", ignoreCase = true)) {
            
            Timber.d("SFTP executeMove: Cross-protocol SFTP->FTP move via temp file bridging")
            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0
            
            operation.sources.forEachIndexed { index, source ->
                val sftpPath = source.path
                val fileName = extractFileName(sftpPath, source.name)
                val ftpDestPath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" 
                                 else "$destinationPath/$fileName"
                
                Timber.d("SFTP executeMove: [${index + 1}/${operation.sources.size}] Bridging move $fileName")
                
                // Create temp file for bridging
                val tempFile = File(context.cacheDir, "bridge_${System.currentTimeMillis()}_$fileName")
                
                try {
                    // 1. Download from SFTP to temp
                    Timber.d("SFTP executeMove: Downloading from SFTP to temp: ${tempFile.absolutePath}")
                    val downloadResult = sftpStrategy.copyFile(sftpPath, tempFile.absolutePath, true, progressCallback)
                    
                    if (downloadResult.isSuccess) {
                        // 2. Upload temp to FTP
                        Timber.d("SFTP executeMove: Uploading from temp to FTP: $ftpDestPath")
                        val uploadResult = ftpStrategy.copyFile(tempFile.absolutePath, ftpDestPath, true, progressCallback)
                        
                        if (uploadResult.isSuccess) {
                            // 3. Delete SFTP source
                            Timber.d("SFTP executeMove: Deleting SFTP source: $sftpPath")
                            val deleteResult = sftpStrategy.deleteFile(sftpPath)
                            
                            if (deleteResult.isSuccess) {
                                movedPaths.add(ftpDestPath)
                                successCount++
                                Timber.i("SFTP executeMove: SUCCESS - bridged move of $fileName to FTP")
                            } else {
                                // Classify the SFTP delete failure so the user sees the right message.
                                // copyCompleted=true: the file exists at FTP destination.
                                val deleteEx = deleteResult.exceptionOrNull() ?: Exception("Delete source failed")
                                val failure = SftpOperationFailure.fromThrowable(deleteEx, operationKind = "move", copyCompleted = true)
                                val spec = SftpOperationMessageResolver.resolve(failure)
                                Timber.e("SFTP executeMove: move-copy-ok-delete-failed operation=move category=${failure.category} statusCode=${failure.statusCode} copyCompleted=true")
                                errors.add(context.getString(spec.messageResId))
                            }
                        } else {
                            val error = "Failed to upload $fileName to FTP: ${uploadResult.exceptionOrNull()?.message}"
                            Timber.e("SFTP executeMove: $error")
                            errors.add(error)
                        }
                    } else {
                        val error = "Failed to download $fileName from SFTP: ${downloadResult.exceptionOrNull()?.message}"
                        Timber.e("SFTP executeMove: $error")
                        errors.add(error)
                    }
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    val error = "Exception during bridged move of $fileName: ${e.message}"
                    Timber.e(e, "SFTP executeMove: $error")
                    errors.add(error)
                } finally {
                    // Cleanup temp file
                    if (tempFile.exists()) {
                        tempFile.delete()
                        Timber.d("SFTP executeMove: Cleaned up temp file")
                    }
                }
            }
            
            return@withContext buildMoveResult(successCount, operation, movedPaths, errors)
        }
        
        return@withContext super.executeMove(operation, progressCallback)
    }

    /**
     * Override copyFile to handle cross-protocol transfers via strategies.
     * Optimization: Explicitly handle Local <-> SFTP to use the optimized SftpOperationStrategy methods.
     */
    override suspend fun copyFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        // Optimization: If operation involves SFTP, use SFTP strategy directly
        if (sourcePath.startsWith("sftp:", ignoreCase = true) || destPath.startsWith("sftp:", ignoreCase = true)) {
            
            // Check for cross-protocol (e.g. SFTP -> FTP, SMB -> SFTP)
            val isSourceNetwork = sourcePath.startsWith("smb:") || sourcePath.startsWith("ftp:") || sourcePath.startsWith("sftp:")
            val isDestNetwork = destPath.startsWith("smb:") || destPath.startsWith("ftp:") || destPath.startsWith("sftp:")
            
            if (isSourceNetwork && isDestNetwork) {
                 // Cross-protocol bridge: Download -> Temp -> Upload
                 Timber.d("SftpFileOperationHandler: Bridging copy $sourcePath -> $destPath")
                 return try {
                     val tempFile = File.createTempFile("bridge_copy", ".tmp", context.cacheDir)
                     try {
                         // 1. Download to temp
                         val downloadResult: Result<String> = if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
                             sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         } else if (sourcePath.startsWith("smb:", ignoreCase = true)) {
                             smbStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         } else {
                             ftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
                         }

                         if (downloadResult.isFailure) {
                             val cause = downloadResult.exceptionOrNull()
                             val host = runCatching {
                                 java.net.URI(sourcePath).host ?: sourcePath
                             }.getOrElse { sourcePath }
                             val msg = when {
                                 cause is SftpDownloadExhaustedException ->
                                     context.getString(R.string.error_sftp_copy_failed_server, host)
                                 cause is com.jcraft.jsch.JSchException ->
                                     context.getString(R.string.error_sftp_connection_limit, host)
                                 cause?.message?.contains("permission denied", ignoreCase = true) == true ||
                                 cause?.message?.contains("access denied", ignoreCase = true) == true ->
                                     context.getString(R.string.error_sftp_copy_access_denied)
                                 // FTP socket NPE = connection dropped during active transfer
                                 cause is NullPointerException && sourcePath.startsWith("ftp:", ignoreCase = true) ->
                                     context.getString(R.string.error_ftp_connection_dropped, host)
                                 sourcePath.startsWith("sftp:", ignoreCase = true) ->
                                     context.getString(R.string.error_sftp_copy_failed_server, host)
                                 else -> cause?.message ?: "Bridge copy source download failed"
                             }
                             return Result.failure(Exception(msg, cause))
                         }
                         
                         // 2. Upload from temp
                         val uploadResult = if (destPath.startsWith("sftp:", ignoreCase = true)) {
                             sftpStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
                         } else if (destPath.startsWith("smb:", ignoreCase = true)) {
                             smbStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
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
            
            return sftpStrategy.copyFile(sourcePath, destPath, overwrite, progressCallback)
        }
        return super.copyFile(sourcePath, destPath, overwrite, progressCallback)
    }

    override suspend fun moveFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        // Optimization: If operation involves SFTP, use SFTP strategy directly
        if (sourcePath.startsWith("sftp:", ignoreCase = true) || destPath.startsWith("sftp:", ignoreCase = true)) {
            val result = sftpStrategy.moveFile(sourcePath, destPath)
            return result.map { destPath }
        }
        return super.moveFile(sourcePath, destPath, overwrite, progressCallback)
    }

    override suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult {
        return super.executeDelete(operation)
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("SFTP executeRename: Renaming ${operation.file.name} to ${operation.newName}")

        try {
            val sftpPath = operation.file.path
            if (!sftpPath.startsWith("sftp:", ignoreCase = true)) {
                Timber.e("SFTP executeRename: File is not SFTP path: $sftpPath")
                return@withContext FileOperationResult.Failure("Not an SFTP file: $sftpPath")
            }

            val connectionInfo = parseSftpPath(sftpPath)
                ?: return@withContext FileOperationResult.Failure("Invalid SFTP path: $sftpPath")

            Timber.d(
                "SFTP executeRename: Parsed - host=${connectionInfo.host}:${connectionInfo.port}, remotePath=${connectionInfo.remotePath}"
            )

            val directory = connectionInfo.remotePath.substringBeforeLast('/', "")
            val newRemotePath = when {
                directory.isNotEmpty() -> "$directory/${operation.newName}"
                connectionInfo.remotePath.startsWith("/") -> "/${operation.newName}"
                else -> operation.newName
            }

            val existsResult = sftpClient.exists(connectionInfo.toClientInfo(), newRemotePath)
            if (existsResult.getOrDefault(false)) {
                val error = "File with name '${operation.newName}' already exists"
                Timber.w("SFTP executeRename: SKIPPED - $error")
                return@withContext FileOperationResult.Failure(error)
            }

            val renameResult = sftpClient.renameFile(
                connectionInfo.toClientInfo(),
                connectionInfo.remotePath,
                operation.newName
            )

            return@withContext when {
                renameResult.isSuccess -> {
                    val directoryPath = sftpPath.substringBeforeLast('/')
                    val newPath = "$directoryPath/${operation.newName}"
                    Timber.i("SFTP executeRename: SUCCESS - renamed to $newPath")
                    FileOperationResult.Success(1, operation, listOf(newPath))
                }

                else -> {
                    val renameEx = renameResult.exceptionOrNull()
                    val failure = SftpOperationFailure.fromThrowable(renameEx ?: Exception("Rename failed"), operationKind = "rename")
                    val spec = SftpOperationMessageResolver.resolve(failure)
                    Timber.e("SFTP executeRename: FAILED category=${failure.category} statusCode=${failure.statusCode}")
                    FileOperationResult.Failure(
                        error = renameEx?.message ?: "Rename failed",
                        errorRes = spec.messageResId
                    )
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val error = "${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${FileOperationError.extractErrorMessage(e)}"
            Timber.e(e, "SFTP executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    internal data class SftpConnectionInfoWithPath(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val privateKey: String?,
        val remotePath: String
    ) {
        fun toClientInfo(): SftpClient.SftpConnectionInfo {
            return SftpClient.SftpConnectionInfo(
                host = host,
                port = port,
                username = username,
                password = password,
                privateKey = privateKey,
                passphrase = password.ifEmpty { null }
            )
        }
    }

    internal suspend fun parseSftpPath(path: String): SftpConnectionInfoWithPath? {
        return try {
            val pathInfo = SftpPathUtils.parseSftpPath(path)
            if (pathInfo == null) {
                Timber.e("parseSftpPath: Failed to parse SFTP path: $path")
                return null
            }

            val (rawHost, rawPort, remotePath) = pathInfo
            // S1006: resolve to the reachable endpoint before the by-host credential lookup.
            val resolved = endpointResolver.resolve(rawHost, rawPort)
            val host = resolved.host
            val port = resolved.port
            Timber.d("parseSftpPath: Extracted host=$host, port=$port, remotePath=$remotePath")

            var credentials = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            if (credentials == null) {
                credentials = credentialsRepository.getCredentialsByHost(host)
            }

            if (credentials == null) {
                Timber.e("parseSftpPath: No credentials found for host: $host")
                return null
            }

            SftpConnectionInfoWithPath(
                host = host,
                port = port,
                username = credentials.username,
                password = credentials.password,
                privateKey = credentials.decryptedSshPrivateKey,
                remotePath = remotePath
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "parseSftpPath: Exception parsing path: $path")
            null
        }
    }
}
