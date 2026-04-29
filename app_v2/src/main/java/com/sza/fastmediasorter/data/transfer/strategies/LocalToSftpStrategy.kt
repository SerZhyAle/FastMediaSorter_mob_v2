package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalToSftpStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceLocal = sourceScheme == "file" || sourceScheme == "content" || sourceScheme == null
        val isDestSftp = destScheme == "sftp"
        return isSourceLocal && isDestSftp
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean = withContext(Dispatchers.IO) {
        // Always dispatch to IO — SftpClient does not switch dispatchers internally.
        // Resolve destination credentials
        val destCredentials = credentialsResolver.getCredentials(destination.toString())
        if (destCredentials == null) {
            Timber.e("LocalToSftpStrategy.copy: No credentials for destination $destination")
            return@withContext false
        }
        
        val destRemotePath = extractRemotePath(destination, destCredentials.server, destCredentials.port)
        
        // Get source input stream and file size
        val (inputStream, fileSize) = try {
            when (source.scheme) {
                "file", null -> {
                    val sourceFile = java.io.File(source.path ?: source.toString())
                    if (!sourceFile.exists()) {
                        Timber.e("LocalToSftpStrategy.copy: Source file does not exist")
                        return@withContext false
                    }
                    sourceFile.inputStream() to sourceFile.length()
                }
                "content" -> {
                    val stream = context.contentResolver.openInputStream(source)
                        ?: throw IllegalStateException("Cannot open content:// for reading")
                    val size = try {
                        context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (cursor.moveToFirst() && sizeIndex >= 0) {
                                cursor.getLong(sizeIndex)
                            } else 0L
                        } ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    stream to size
                }
                else -> {
                    Timber.e("LocalToSftpStrategy.copy: Unsupported source scheme ${source.scheme}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalToSftpStrategy.copy: Failed to open source")
            return@withContext false
        }
        
        // Upload to SFTP
        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = destCredentials.server,
            port = destCredentials.port,
            username = destCredentials.username,
            password = destCredentials.password,
            privateKey = destCredentials.privateKey
        )
        
        return@withContext try {
            inputStream.use { input ->
                val uploadResult = sftpClient.uploadFile(
                    connectionInfo = connectionInfo,
                    remotePath = destRemotePath,
                    inputStream = input,
                    fileSize = fileSize,
                    progressCallback = progressCallback
                )
                uploadResult.isSuccess
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalToSftpStrategy.copy: Upload failed")
            false
        }
    }
    
    /**
     * Extract remote path from SFTP URI
     * Example: sftp://server:22/path/to/file.txt -> /path/to/file.txt
     */
    private fun extractRemotePath(uri: Uri, host: String, port: Int): String {
        val full = uri.toString()
        val prefix = "sftp://$host:$port"
        return full.removePrefix(prefix).ifBlank { "/" }
    }
}
