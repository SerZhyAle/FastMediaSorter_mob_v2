package com.sza.fastmediasorter_v2.data.network.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.sza.fastmediasorter_v2.data.network.SmbClient
import com.sza.fastmediasorter_v2.data.remote.ftp.FtpClient
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
import com.sza.fastmediasorter_v2.domain.repository.NetworkCredentialsRepository
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
                throw Exception("Failed to read file from network: ${data.path}")
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
        } catch (e: Exception) {
            Timber.e(e, "NetworkFileFetcher: Failed to fetch ${data.path}")
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

        // Read file with smaller buffer for faster thumbnail loading
        // Thumbnails only need first 512KB (enough for compressed JPEG data at 256px)
        val maxBytes = 512 * 1024L // 512 KB for thumbnails
        
        val result = smbClient.readFileBytes(connectionInfo, remotePath, maxBytes)
        return when (result) {
            is SmbClient.SmbResult.Success -> result.data
            is SmbClient.SmbResult.Error -> null
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

        // Read file with smaller buffer for faster thumbnail loading
        val maxBytes = 2 * 1024 * 1024L // 2 MB for thumbnails
        val result = sftpClient.readFileBytes(remotePath, maxBytes)
        
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
        
        // Connect and download file to ByteArrayOutputStream
        val result = ftpClient.connect(server, port, credentials.username, credentials.password)
        if (result.isFailure) {
            Timber.e("fetchFromFtp: Connection failed")
            return null
        }
        
        val outputStream = java.io.ByteArrayOutputStream()
        val downloadResult = ftpClient.downloadFile(remotePath, outputStream)
        
        ftpClient.disconnect()
        
        return if (downloadResult.isSuccess) {
            outputStream.toByteArray()
        } else {
            null
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
