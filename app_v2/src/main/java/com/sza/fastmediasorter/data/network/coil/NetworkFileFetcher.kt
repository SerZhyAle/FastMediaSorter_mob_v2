package com.sza.fastmediasorter.data.network.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import timber.log.Timber

/**
 * Custom Coil Fetcher for loading images from network paths (SMB/SFTP/FTP).
 * Использует readFileBytes() из SmbClient/SftpClient/FtpClient для получения ByteArray,
 * который Coil может кэшировать на диске и в памяти.
 */
class NetworkFileFetcher(
    private val data: NetworkFileData,
    private val options: Options,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        try {
            val bytes = when {
                data.path.startsWith("smb://") -> fetchFromSmb()
                data.path.startsWith("sftp://") -> fetchFromSftp()
                data.path.startsWith("ftp://") -> fetchFromFtp()
                else -> throw IllegalArgumentException("Unsupported network protocol: ${data.path}")
            }

            if (bytes == null) {
                // Return null bytes triggers Coil's error drawable (don't spam logs)
                Timber.d("Network file not available (timeout or error): ${data.path}")
                throw Exception("Network file unavailable")
            }

            // Convert ByteArray to BufferedSource for Coil
            val buffer = Buffer().write(bytes)
            val source = ImageSource(
                source = buffer,
                context = options.context
            )

            SourceResult(
                source = source,
                mimeType = null, // Let Coil detect mime type
                dataSource = DataSource.NETWORK
            )
        } catch (e: CancellationException) {
            // Normal behavior when Coil cancels fetch during RecyclerView scroll
            // Don't log as error - this happens hundreds of times during fast scrolling
            throw e
        } catch (e: Exception) {
            // Only log non-timeout errors (already logged in fetch* methods)
            if (e.message != "Network file unavailable") {
                Timber.e(e, "NetworkFileFetcher: Failed to fetch ${data.path}")
            }
            throw e
        }
    }

    private suspend fun fetchFromSmb(): ByteArray? {
        // Parse smb://server:port/share/path
        val uri = data.path.removePrefix("smb://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val pathParts = if (parts.size > 1) parts[1] else ""

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 445
        } else {
            server = serverPort
            port = 445
        }

        // Get credentials from database - prefer credentialsId if provided
        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SMB", server, port)
        }
        
        if (credentials == null) return null

        // Extract share name and file path from pathParts
        // pathParts format: "shareName/path/to/file"
        val shareAndPath = pathParts.split("/", limit = 2)
        val shareName = if (shareAndPath.isNotEmpty()) shareAndPath[0] else (credentials.shareName ?: "")
        val remotePath = if (shareAndPath.size > 1) shareAndPath[1] else ""

        if (shareName.isEmpty()) return null

        val connectionInfo = SmbClient.SmbConnectionInfo(
            server = server,
            port = port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )

        // Read file with reasonable limit for thumbnail loading
        // PNG files need more space than JPEG (lossless compression)
        // 3MB covers most thumbnails while keeping memory usage reasonable
        val maxBytes = 3 * 1024 * 1024L // 3 MB for thumbnails (handles PNG better)
        
        // Add timeout to avoid blocking UI on slow network
        val result = try {
            kotlinx.coroutines.withTimeout(10000) { // 10 seconds max for thumbnail load
                smbClient.readFileBytes(connectionInfo, remotePath, maxBytes)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.w("SMB thumbnail load timeout (8s) for: $remotePath")
            return null
        } catch (e: Exception) {
            Timber.w(e, "SMB thumbnail load exception for: $remotePath")
            return null
        }
        
        return when (result) {
            is SmbClient.SmbResult.Success -> result.data
            is SmbClient.SmbResult.Error -> {
                Timber.w("SMB thumbnail error: ${result.message} for: $remotePath")
                null
            }
        }
    }

    private suspend fun fetchFromSftp(): ByteArray? {
        // Parse sftp://server:port/path
        val uri = data.path.removePrefix("sftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 22
        } else {
            server = serverPort
            port = 22
        }

        // Get credentials from database - prefer credentialsId if provided
        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
        }
        
        if (credentials == null) return null

        // Connect to SFTP
        sftpClient.connect(server, port, credentials.username, credentials.password)
        if (!sftpClient.isConnected()) {
            return null
        }

        // Read file with reasonable limit for thumbnail loading
        // PNG files need more space than JPEG (lossless compression)
        val maxBytes = 3 * 1024 * 1024L // 3 MB for thumbnails (handles PNG better)
        
        // Add timeout to avoid blocking UI on slow network
        val result = try {
            kotlinx.coroutines.withTimeout(10000) { // 10 seconds max for thumbnail load
                sftpClient.readFileBytes(remotePath, maxBytes)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.w("SFTP thumbnail load timeout for: $remotePath")
            return null
        }
        
        return result.getOrNull()
    }

    private suspend fun fetchFromFtp(): ByteArray? {
        // Parse ftp://server:port/path
        val uri = data.path.removePrefix("ftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 21
        } else {
            server = serverPort
            port = 21
        }

        // Get credentials from database - prefer credentialsId if provided
        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("FTP", server, port)
        }
        
        if (credentials == null) {
            Timber.e("fetchFromFtp: No credentials found for FTP $server:$port")
            return null
        }

        Timber.d("fetchFromFtp: Downloading $remotePath from $server:$port")
        
        // Use temporary connection for parallel downloads (avoid singleton FTPClient race condition)
        val outputStream = java.io.ByteArrayOutputStream()
        return try {
            // Add timeout to avoid blocking UI on slow network
            kotlinx.coroutines.withTimeout(10000) { // 10 seconds max for thumbnail load
                val downloadResult = ftpClient.downloadFileWithNewConnection(
                    host = server,
                    port = port,
                    username = credentials.username,
                    password = credentials.password,
                    remotePath = remotePath,
                    outputStream = outputStream
                )
                
                if (downloadResult.isSuccess) {
                    outputStream.toByteArray()
                } else {
                    null
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.w("FTP thumbnail load timeout for: $remotePath")
            null
        } finally {
            outputStream.close()
        }
    }

    /**
     * Factory для создания NetworkFileFetcher.
     */
    class Factory(
        private val smbClient: SmbClient,
        private val sftpClient: SftpClient,
        private val ftpClient: FtpClient,
        private val credentialsRepository: NetworkCredentialsRepository
    ) : Fetcher.Factory<NetworkFileData> {

        override fun create(
            data: NetworkFileData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return NetworkFileFetcher(data, options, smbClient, sftpClient, ftpClient, credentialsRepository)
        }
    }
}

/**
 * Data class для передачи network path в Fetcher.
 */
data class NetworkFileData(
    val path: String, // smb:// or sftp:// URL
    val credentialsId: String? = null // Optional credentialsId to use specific credentials
)
