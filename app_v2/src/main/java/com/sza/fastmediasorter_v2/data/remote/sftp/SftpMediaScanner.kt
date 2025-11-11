package com.sza.fastmediasorter_v2.data.remote.sftp

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.usecase.MediaScanner
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for SFTP network resources.
 * Scans remote SFTP servers for media files using SftpClient.
 */
@Singleton
class SftpMediaScanner @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsDao: NetworkCredentialsDao
) : MediaScanner {

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        private val GIF_EXTENSIONS = setOf("gif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "opus")
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // Parse path format: sftp://server:port/remotePath
            val connectionInfo = parseSftpPath(path, credentialsId) ?: run {
                Timber.w("Invalid SFTP path format: $path")
                return@withContext emptyList()
            }

            // Connect and list files
            val connectResult = sftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to SFTP: ${connectResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            // List files in remote path
            val filesResult = sftpClient.listFiles(connectionInfo.remotePath)
            sftpClient.disconnect()

            if (filesResult.isFailure) {
                Timber.e("Failed to list SFTP files: ${filesResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            // Filter and convert to MediaFile
            filesResult.getOrNull()?.mapNotNull { fileName ->
                val mediaType = getMediaType(fileName)
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    // For now, we don't have size/date info from listFiles()
                    // This would require stat() for each file, which is expensive
                    // Apply size filter only if we have size info
                    MediaFile(
                        name = fileName,
                        path = buildFullSftpPath(connectionInfo, fileName),
                        size = 0L, // TODO: implement stat() to get real size
                        createdDate = 0L, // TODO: implement stat() to get real date
                        type = mediaType
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SFTP folder: $path")
            emptyList()
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): Int = withContext(Dispatchers.IO) {
        try {
            val files = scanFolder(path, supportedTypes, sizeFilter, credentialsId)
            files.size
        } catch (e: Exception) {
            Timber.e(e, "Error counting SFTP files in: $path")
            0
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val connectionInfo = parseSftpPath(path, credentialsId) ?: return@withContext false

            // Test connection
            val result = sftpClient.testConnection(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            result.isSuccess
        } catch (e: Exception) {
            Timber.e(e, "Error checking SFTP write access for: $path")
            false
        }
    }

    /**
     * Parse SFTP path format: sftp://server:port/remotePath
     * Retrieves credentials from database based on host
     */
    private suspend fun parseSftpPath(path: String, credentialsId: String?): SftpConnectionInfo? {
        return try {
            if (!path.startsWith("sftp://")) return null

            // Format: sftp://server:port/remotePath
            val withoutProtocol = path.removePrefix("sftp://")
            val parts = withoutProtocol.split("/", limit = 2)

            if (parts.isEmpty()) return null

            val serverPart = parts[0]
            val remotePath = if (parts.size > 1) "/" + parts[1] else "/"

            // Parse server:port
            val serverPort = serverPart.split(":", limit = 2)
            val host = serverPort[0]
            val port = if (serverPort.size > 1) serverPort[1].toIntOrNull() ?: 22 else 22

            if (host.isEmpty()) return null

            // Try to get credentials from database using credentialsId first
            val credentials = if (credentialsId != null) {
                credentialsDao.getCredentialsById(credentialsId)
            } else {
                // Fallback to old behavior for backward compatibility
                credentialsDao.getByTypeServerAndPort("SFTP", host, port)
            }

            if (credentials == null) {
                Timber.w("No SFTP credentials found for host: $host:$port")
                return null
            }

            SftpConnectionInfo(
                host = host,
                port = port,
                username = credentials.username,
                password = credentials.password,
                remotePath = remotePath
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SFTP path: $path")
            null
        }
    }

    private fun buildFullSftpPath(connectionInfo: SftpConnectionInfo, fileName: String): String {
        val remotePath = connectionInfo.remotePath.removeSuffix("/")
        return "sftp://${connectionInfo.host}:${connectionInfo.port}$remotePath/$fileName"
    }

    private fun getMediaType(fileName: String): MediaType? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            IMAGE_EXTENSIONS.contains(extension) -> MediaType.IMAGE
            GIF_EXTENSIONS.contains(extension) -> MediaType.GIF
            VIDEO_EXTENSIONS.contains(extension) -> MediaType.VIDEO
            AUDIO_EXTENSIONS.contains(extension) -> MediaType.AUDIO
            else -> null
        }
    }

    private data class SftpConnectionInfo(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )
}
