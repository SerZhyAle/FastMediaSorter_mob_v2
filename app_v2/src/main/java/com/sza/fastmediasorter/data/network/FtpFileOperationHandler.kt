package com.sza.fastmediasorter.data.network

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy
import com.sza.fastmediasorter.data.transfer.BaseFileOperationHandler
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.utils.FtpPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Handler for FTP file operations. SAF sources supported; cross-protocol via temp file. */
@Singleton
class FtpFileOperationHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val ftpClient: FtpClient,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : BaseFileOperationHandler(context) {

    private val ftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(FtpOperationStrategy(context, ftpClient, credentialsRepository), enableAtomic = true)
    private val smbStrategy: FileOperationStrategy = AtomicFileOperationStrategy(SmbOperationStrategy(context, smbClient, credentialsRepository), enableAtomic = true)
    private val sftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(SftpOperationStrategy(context, sftpClient, credentialsRepository), enableAtomic = true)
    private val localStrategy: FileOperationStrategy = AtomicFileOperationStrategy(LocalOperationStrategy(context), enableAtomic = true)

    override fun getStrategies(): List<FileOperationStrategy> {
        return listOf(ftpStrategy, smbStrategy, sftpStrategy, localStrategy)
    }

    internal data class FtpConnectionInfoWithPath(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )

    override suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        val destinationPath = operation.destination.path

        if (destinationPath.startsWith("ftp:", ignoreCase = true)) {
            // NO pre-flight check! Upload first, then delete.
            // Delete uses createDeleteRequest which auto-deletes after user grants permission.
            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0
            val pendingDeletePaths = mutableListOf<String>()

            operation.sources.forEachIndexed { index, source ->
                val sourcePath = source.path
                val fileName = extractFileName(sourcePath, source.name)
                val destFilePath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" else "$destinationPath/$fileName"

                when {
                    sourcePath.startsWith("ftp:", ignoreCase = true) -> {
                        val result = ftpStrategy.moveFile(sourcePath, destFilePath)
                        if (result.isSuccess) { movedPaths.add(destFilePath); successCount++ }
                        else errors.add("Failed to move $fileName: ${result.exceptionOrNull()?.message}")
                    }
                    sourcePath.startsWith("sftp:", ignoreCase = true) || sourcePath.startsWith("smb:", ignoreCase = true) -> {
                        val tempFile = File(context.cacheDir, "bridge_${System.currentTimeMillis()}_$fileName")
                        try {
                            val downloadResult = if (sourcePath.startsWith("sftp:", ignoreCase = true))
                                sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, progressCallback)
                            else smbStrategy.copyFile(sourcePath, tempFile.absolutePath, true, progressCallback)
                            if (downloadResult.isFailure) {
                                errors.add("Failed to download $fileName: ${downloadResult.exceptionOrNull()?.message}")
                            } else {
                                val uploadedPath = uploadToFtp(source = tempFile, ftpPath = destFilePath, overwrite = operation.overwrite, progressCallback = progressCallback)
                                if (uploadedPath != null) {
                                    val deleteResult = if (sourcePath.startsWith("sftp:", ignoreCase = true)) sftpStrategy.deleteFile(sourcePath) else smbStrategy.deleteFile(sourcePath)
                                    if (deleteResult.isSuccess) { movedPaths.add(uploadedPath); successCount++ }
                                    else errors.add("Uploaded $fileName but failed to delete source: ${deleteResult.exceptionOrNull()?.message}")
                                } else errors.add("Failed to upload $fileName to FTP")
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                    else -> {
                        val uploadedPath = uploadToFtp(source = File(sourcePath), ftpPath = destFilePath, overwrite = operation.overwrite, progressCallback = progressCallback)
                        if (uploadedPath != null) {
                            val deleteSuccess = if (sourcePath.startsWith("content:/")) deleteWithSaf(sourcePath)
                            else try { deleteFile(sourcePath).isSuccess }
                            catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
                                // File already uploaded — collect for batch delete
                                pendingDeletePaths.add(sourcePath); movedPaths.add(uploadedPath); successCount++; true
                            }
                            if (deleteSuccess && !pendingDeletePaths.contains(sourcePath)) { movedPaths.add(uploadedPath); successCount++ }
                            else if (!pendingDeletePaths.contains(sourcePath)) errors.add("Uploaded $fileName but failed to delete source at $sourcePath")
                        } else errors.add("Failed to upload $fileName to FTP")
                    }
                }
            }
            
            // After all uploads complete, check if any files need permission for batch delete.
            // requestBatchDeletePermission uses MediaStore.createDeleteRequest (API 30+); skip on older devices.
            if (pendingDeletePaths.isNotEmpty() &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Timber.i("FTP executeMove: All ${pendingDeletePaths.size} files uploaded, requesting batch delete permission")
                requestBatchDeletePermission(pendingDeletePaths)
            }
            
            return buildMoveResult(successCount, operation, movedPaths, errors)
        }
        
        return super.executeMove(operation, progressCallback)
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        try {
            val ftpPath = operation.file.path
            if (!ftpPath.startsWith("ftp:", ignoreCase = true))
                return@withContext FileOperationResult.Failure("Not an FTP file: $ftpPath")
            val connectionInfo = parseFtpPath(ftpPath)
                ?: return@withContext FileOperationResult.Failure("Invalid FTP path: $ftpPath")
            val directory = connectionInfo.remotePath.substringBeforeLast('/', "")
            val newRemotePath = when {
                directory.isNotEmpty() -> "$directory/${operation.newName}"
                connectionInfo.remotePath.startsWith("/") -> "/${operation.newName}"
                else -> operation.newName
            }
            val existsResult = ftpClient.existsWithNewConnection(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password, newRemotePath)
            if (existsResult.getOrDefault(false))
                return@withContext FileOperationResult.Failure("File with name '${operation.newName}' already exists")
            val renameResult = ftpClient.renameFileWithNewConnection(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password, connectionInfo.remotePath, operation.newName)
            if (renameResult.isSuccess) FileOperationResult.Success(1, operation, listOf("${ftpPath.substringBeforeLast('/')}/${operation.newName}"))
            else FileOperationResult.Failure("${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${renameResult.exceptionOrNull()?.message ?: "Rename failed"}")
        } catch (e: Exception) {
            Timber.e(e, "FTP executeRename: EXCEPTION")
            FileOperationResult.Failure("${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${FileOperationError.extractErrorMessage(e)}")
        }
    }

    override suspend fun copyFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val source = normalizeNetworkPath(sourcePath)
        val destination = normalizeNetworkPath(destPath)

        val isSourceFtp = source.startsWith("ftp:", ignoreCase = true)
        val isDestFtp = destination.startsWith("ftp:", ignoreCase = true)
        val isDestSftp = destination.startsWith("sftp:", ignoreCase = true)
        val isDestSmb = destination.startsWith("smb:", ignoreCase = true)

        if (!overwrite) {
            val existsResult = existsAtDestination(destination)
            if (existsResult.isFailure) return Result.failure(existsResult.exceptionOrNull() ?: Exception("Exists check failed"))
            if (existsResult.getOrDefault(false)) {
                val fileName = destination.substringAfterLast('/').ifBlank {
                    extractFileName(source, File(source).name)
                }
                return Result.failure(
                    FileExistsException(
                        fileName = fileName,
                        destinationPath = destination,
                        isMove = false
                    )
                )
            }
        }

        return when {
            isSourceFtp && isDestSftp -> copyFtpToSftp(source, destination)
            isSourceFtp && isDestSmb -> copyFtpToSmb(source, destination, progressCallback)
            isSourceFtp && isDestFtp -> copyFtpToFtp(source, destination, overwrite)?.let { Result.success(it) }
                ?: Result.failure(Exception("Failed to copy between FTP servers"))
            isSourceFtp && !isDestFtp -> {
                val localFile = File(destination)
                downloadFromFtp(source, localFile)?.let { Result.success(destination) }
                    ?: Result.failure(Exception("Failed to download from FTP"))
            }
            !isSourceFtp && isDestFtp -> uploadToFtp(
                source = File(source),
                ftpPath = destination,
                overwrite = overwrite,
                progressCallback = progressCallback
            )?.let { Result.success(it) }
                ?: Result.failure(Exception("Failed to upload to FTP"))
            else -> Result.failure(IllegalArgumentException("Unsupported operation: $source -> $destination"))
        }
    }

    override suspend fun deleteFile(filePath: String): Result<Unit> {
        val normalized = normalizeNetworkPath(filePath)

        return when {
            normalized.startsWith("ftp:", ignoreCase = true) -> {
                if (deleteFromFtp(normalized)) Result.success(Unit)
                else Result.failure(Exception("Failed to delete from FTP"))
            }
            normalized.startsWith("content:/") -> {
                if (deleteWithSaf(normalized)) Result.success(Unit)
                else Result.failure(Exception("Failed to delete content URI"))
            }
            else -> super.deleteFile(normalized)
        }
    }

    override suspend fun createTrashFolder(firstFilePath: String): String? {
        val normalized = normalizeNetworkPath(firstFilePath)
        if (!normalized.startsWith("ftp:", ignoreCase = true)) {
            return super.createTrashFolder(firstFilePath)
        }

        val parentDir = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentDir.isEmpty()) return null

        val trashDirPath = "$parentDir/.trash_${System.currentTimeMillis()}"
        val connectionInfo = parseFtpPath(trashDirPath) ?: return null

        val createResult = ftpClient.createDirectoryWithNewConnection(
            connectionInfo.host,
            connectionInfo.port,
            connectionInfo.username,
            connectionInfo.password,
            connectionInfo.remotePath
        )

        return if (createResult.isSuccess) trashDirPath else null
    }

    override suspend fun moveToTrash(sourcePath: String, trashPath: String, fileName: String): Result<Unit> {
        val source = normalizeNetworkPath(sourcePath)
        val trash = normalizeNetworkPath(trashPath)

        if (!source.startsWith("ftp:", ignoreCase = true)) {
            return super.moveToTrash(sourcePath, trashPath, fileName)
        }

        val sourceInfo = parseFtpPath(source)
            ?: return Result.failure(IllegalArgumentException("Invalid FTP path: $source"))

        val trashDirName = trash.substringBeforeLast('/').substringAfterLast('/')
        if (trashDirName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid trash path: $trash"))
        }

        val relativeNewName = "$trashDirName/$fileName"
        return ftpClient.renameFileWithNewConnection(
            sourceInfo.host,
            sourceInfo.port,
            sourceInfo.username,
            sourceInfo.password,
            sourceInfo.remotePath,
            relativeNewName
        )
    }

    private suspend fun downloadFromFtp(ftpPath: String, localFile: File): File? {
        // Use file output stream directly to avoid OOM with large files
        val connectionInfo = parseFtpPath(ftpPath) ?: return null
        return try {
            localFile.outputStream().use { outputStream ->
                val downloadResult = ftpClient.downloadFileWithNewConnection(
                    connectionInfo.host, connectionInfo.port,
                    connectionInfo.username, connectionInfo.password,
                    connectionInfo.remotePath, outputStream
                )
                if (downloadResult.isSuccess) {
                    MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "ftp-download")
                    localFile
                } else {
                    localFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "downloadFromFtp: Exception")
            localFile.delete()
            null
        }
    }

    private suspend fun uploadToFtp(
        source: File,
        ftpPath: String,
        overwrite: Boolean = false,
        progressCallback: ByteProgressCallback? = null
    ): String? {
        val connectionInfo = parseFtpPath(ftpPath) ?: return null
        if (overwrite && !prepareFtpDestinationForOverwrite(connectionInfo, ftpPath)) return null

        val isSaf = source.path.startsWith("content:/")
        val normalizedUri: Uri? = if (isSaf) Uri.parse(source.path.let { if (it.startsWith("content://")) it else it.replaceFirst("content:/", "content://") }) else null

        val inputStream = if (isSaf) {
            try { context.contentResolver.openInputStream(normalizedUri!!) }
            catch (e: Exception) { Timber.e(e, "uploadToFtp: Failed to open SAF URI"); null }
        } else {
            if (!source.exists()) { Timber.e("uploadToFtp: File does not exist: ${source.path}"); return null }
            try { source.inputStream() } catch (e: Exception) { Timber.e(e, "uploadToFtp: Failed to open file"); null }
        } ?: return null

        val fileSize = if (isSaf) {
            runCatching { context.contentResolver.openAssetFileDescriptor(normalizedUri!!, "r")?.use { it.length } }.getOrNull()
                ?: inputStream.available().toLong()
        } else source.length()

        return try {
            inputStream.use { stream ->
                val uploadResult = ftpClient.uploadFileWithNewConnection(
                    connectionInfo.host, connectionInfo.port,
                    connectionInfo.username, connectionInfo.password,
                    connectionInfo.remotePath, stream, fileSize, progressCallback
                )
                if (uploadResult.isSuccess) ftpPath else null
            }
        } catch (e: Exception) {
            Timber.e(e, "uploadToFtp: Exception during upload")
            null
        }
    }

    private suspend fun deleteFromFtp(ftpPath: String): Boolean {
        val connectionInfo = parseFtpPath(ftpPath) ?: return false
        return ftpClient.deleteFileWithNewConnection(
            connectionInfo.host, connectionInfo.port,
            connectionInfo.username, connectionInfo.password, connectionInfo.remotePath
        ).isSuccess
    }

    private suspend fun copyFtpToFtp(sourcePath: String, destPath: String, overwrite: Boolean): String? {
        val sourceInfo = parseFtpPath(sourcePath) ?: return null
        val tempFile = File.createTempFile("ftp_copy_", ".tmp", context.cacheDir)
        try {
            val downloadResult = tempFile.outputStream().use { outputStream ->
                ftpClient.downloadFileWithNewConnection(sourceInfo.host, sourceInfo.port, sourceInfo.username, sourceInfo.password, sourceInfo.remotePath, outputStream)
            }
            if (downloadResult.isFailure) return null
            val destInfo = parseFtpPath(destPath) ?: return null
            if (overwrite && !prepareFtpDestinationForOverwrite(destInfo, destPath)) return null
            val uploadResult = tempFile.inputStream().use { inputStream ->
                ftpClient.uploadFileWithNewConnection(destInfo.host, destInfo.port, destInfo.username, destInfo.password, destInfo.remotePath, inputStream)
            }
            return if (uploadResult.isSuccess) destPath else null
        } catch (e: Exception) {
            Timber.e(e, "copyFtpToFtp: Exception")
            return null
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun prepareFtpDestinationForOverwrite(connectionInfo: FtpConnectionInfoWithPath, ftpPath: String): Boolean {
        val existsResult = ftpClient.existsWithNewConnection(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password, connectionInfo.remotePath)
        if (existsResult.isFailure) return false
        if (!existsResult.getOrDefault(false)) return true
        return ftpClient.deleteFileWithNewConnection(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password, connectionInfo.remotePath).isSuccess
    }

    internal suspend fun parseFtpPath(path: String): FtpConnectionInfoWithPath? {
        return try {
            val (host, port, remotePath) = FtpPathUtils.parseFtpPath(path) ?: return null
            val credentials = credentialsRepository.getByTypeServerAndPort("FTP", host, port)
                ?: credentialsRepository.getCredentialsByHost(host)
                ?: return null
            FtpConnectionInfoWithPath(host = host, port = port, username = credentials.username, password = credentials.password, remotePath = remotePath)
        } catch (e: Exception) {
            Timber.e(e, "parseFtpPath: Exception parsing path: $path")
            null
        }
    }

    private fun normalizeNetworkPath(path: String): String {
        val normalized = path.replace('\\', '/')

        return when {
            normalized.startsWith("ftp:", ignoreCase = true) -> FtpPathUtils.normalizeFtpPath(normalized)
            normalized.startsWith("sftp:/", ignoreCase = true) -> "sftp://" + normalized.substringAfter("sftp:/").trimStart('/')
            normalized.startsWith("smb:/", ignoreCase = true) -> "smb://" + normalized.substringAfter("smb:/").trimStart('/')
            else -> normalized
        }
    }

    private suspend fun existsAtDestination(destinationPath: String): Result<Boolean> {
        return when {
            destinationPath.startsWith("ftp:", ignoreCase = true) -> {
                val connectionInfo = parseFtpPath(destinationPath)
                    ?: return Result.failure(IllegalArgumentException("Invalid FTP path: $destinationPath"))
                ftpClient.existsWithNewConnection(
                    connectionInfo.host,
                    connectionInfo.port,
                    connectionInfo.username,
                    connectionInfo.password,
                    connectionInfo.remotePath
                )
            }
            destinationPath.startsWith("sftp://") -> {
                val (connectionInfo, remotePath) = parseSftpDestination(destinationPath)
                    ?: return Result.failure(IllegalArgumentException("Invalid SFTP path: $destinationPath"))
                sftpClient.exists(connectionInfo, remotePath)
            }
            destinationPath.startsWith("smb://") -> {
                val (connectionInfo, remotePath) = parseSmbDestination(destinationPath)
                    ?: return Result.failure(IllegalArgumentException("Invalid SMB path: $destinationPath"))
                when (val existsResult = smbClient.exists(connectionInfo, remotePath)) {
                    is SmbResult.Success -> Result.success(existsResult.data)
                    is SmbResult.Error -> Result.failure(Exception(existsResult.message, existsResult.exception))
                }
            }
            else -> Result.success(File(destinationPath).exists())
        }
    }

    private suspend fun copyFtpToSftp(sourceFtpPath: String, destSftpPath: String): Result<String> {
        val ftpConnectionInfo = parseFtpPath(sourceFtpPath)
            ?: return Result.failure(IllegalArgumentException("Invalid FTP path: $sourceFtpPath"))

        val (sftpConnectionInfo, sftpRemotePath) = parseSftpDestination(destSftpPath)
            ?: return Result.failure(IllegalArgumentException("Invalid SFTP path: $destSftpPath"))

        val tempFile = File.createTempFile("ftp_sftp_copy_", ".tmp", context.cacheDir)
        return try {
            val downloadResult = tempFile.outputStream().use { outputStream ->
                ftpClient.downloadFileWithNewConnection(
                    ftpConnectionInfo.host,
                    ftpConnectionInfo.port,
                    ftpConnectionInfo.username,
                    ftpConnectionInfo.password,
                    ftpConnectionInfo.remotePath,
                    outputStream
                )
            }
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("FTP download failed"))
            }

            val uploadResult = tempFile.inputStream().use { inputStream ->
                sftpClient.uploadFile(
                    sftpConnectionInfo,
                    sftpRemotePath,
                    inputStream,
                    tempFile.length()
                )
            }

            if (uploadResult.isSuccess) Result.success(destSftpPath)
            else Result.failure(uploadResult.exceptionOrNull() ?: Exception("SFTP upload failed"))
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun copyFtpToSmb(
        sourceFtpPath: String,
        destSmbPath: String,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val ftpConnectionInfo = parseFtpPath(sourceFtpPath)
            ?: return Result.failure(IllegalArgumentException("Invalid FTP path: $sourceFtpPath"))

        val (smbConnectionInfo, smbRemotePath) = parseSmbDestination(destSmbPath)
            ?: return Result.failure(IllegalArgumentException("Invalid SMB path: $destSmbPath"))

        val tempFile = File.createTempFile("ftp_smb_copy_", ".tmp", context.cacheDir)
        return try {
            val downloadResult = tempFile.outputStream().use { outputStream ->
                ftpClient.downloadFileWithNewConnection(
                    ftpConnectionInfo.host,
                    ftpConnectionInfo.port,
                    ftpConnectionInfo.username,
                    ftpConnectionInfo.password,
                    ftpConnectionInfo.remotePath,
                    outputStream
                )
            }
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("FTP download failed"))
            }

            val uploadResult = tempFile.inputStream().use { inputStream ->
                smbClient.uploadFile(
                    smbConnectionInfo,
                    smbRemotePath,
                    inputStream,
                    tempFile.length(),
                    progressCallback
                )
            }

            when (uploadResult) {
                is SmbResult.Success -> Result.success(destSmbPath)
                is SmbResult.Error -> Result.failure(Exception(uploadResult.message, uploadResult.exception))
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun parseSftpDestination(path: String): Pair<SftpClient.SftpConnectionInfo, String>? {
        val parts = path.removePrefix("sftp://").split("/", limit = 2)
        if (parts.isEmpty() || parts[0].isBlank()) return null

        val hostPort = parts[0].split(":", limit = 2)
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 22 else 22
        val remotePath = if (parts.size > 1) "/" + parts[1] else "/"

        val credentials = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            ?: credentialsRepository.getCredentialsByHost(host)
            ?: return null

        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = host,
            port = port,
            username = credentials.username,
            password = credentials.password,
            privateKey = credentials.decryptedSshPrivateKey,
            passphrase = null
        )

        return connectionInfo to remotePath
    }

    private suspend fun parseSmbDestination(path: String): Pair<SmbConnectionInfo, String>? {
        val parts = path.removePrefix("smb://").split("/", limit = 3)
        if (parts.size < 2) return null

        val serverWithPort = parts[0]
        val shareName = parts[1]
        val remotePath = if (parts.size > 2) parts[2] else ""

        val server = if (serverWithPort.contains(':')) serverWithPort.substringBefore(':') else serverWithPort
        val port = if (serverWithPort.contains(':')) serverWithPort.substringAfter(':').toIntOrNull() ?: 445 else 445

        val credentials = credentialsRepository.getByServerAndShare(server, shareName)
            ?: credentialsRepository.getCredentialsByHost(server)
            ?: return null

        val connectionInfo = SmbConnectionInfo(
            server = server,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain,
            port = port
        )

        return connectionInfo to remotePath
    }
}
