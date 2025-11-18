package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for FTP network resources.
 * Scans remote FTP servers for media files using FtpClient.
 */
@Singleton
class FtpMediaScanner @Inject constructor(
    private val ftpClient: FtpClient,
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
        credentialsId: String?,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            Timber.d("FTP scanFolder: path=$path, credentialsId=$credentialsId")
            
            // Parse path format: ftp://server:port/remotePath
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format: $path")
                return@withContext emptyList()
            }

            Timber.d("FTP connection info: host=${connectionInfo.host}, port=${connectionInfo.port}, user=${connectionInfo.username}, remotePath=${connectionInfo.remotePath}")

            // Connect and list files
            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP: ${connectResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            // List files in remote path
            val filesResult = ftpClient.listFiles(connectionInfo.remotePath)
            ftpClient.disconnect()

            if (filesResult.isFailure) {
                val exception = filesResult.exceptionOrNull() ?: IOException("Unknown FTP error")
                Timber.e("Failed to list FTP files: ${exception.message}")
                throw IOException("FTP connection error: ${exception.message}", exception)
            }

            // Filter and convert to MediaFile
            filesResult.getOrNull()?.mapNotNull { fileName ->
                val mediaType = getMediaType(fileName)
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    // For now, we don't have size/date info from listFiles()
                    // This would require stat() for each file, which is expensive
                    // Apply size filter only if we have size info
                    // TODO: Extract EXIF from FTP files (requires downloading file header)
                    // For now, EXIF extraction is skipped for network files to avoid slow scanning
                    // TODO: Extract video metadata from FTP files (requires downloading file or partial read)
                    // For now, video metadata extraction is skipped for network files to avoid slow scanning
                    MediaFile(
                        name = fileName,
                        path = buildFullFtpPath(connectionInfo, fileName),
                        size = 0L, // TODO: implement FTPFile attributes to get real size
                        createdDate = 0L, // TODO: implement FTPFile attributes to get real date
                        type = mediaType
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error scanning FTP folder: $path")
            emptyList()
        }
    }

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?
    ): MediaFilePage = withContext(Dispatchers.IO) {
        try {
            // For simplicity, reuse scanFolder and apply offset/limit
            // TODO: optimize FTP client to support native pagination
            val allFiles = scanFolder(path, supportedTypes, sizeFilter, credentialsId)
            
            val pageFiles = allFiles.drop(offset).take(limit)
            val hasMore = offset + limit < allFiles.size
            
            Timber.d("FtpMediaScanner paged: offset=$offset, limit=$limit, returned=${pageFiles.size}, hasMore=$hasMore")
            MediaFilePage(pageFiles, hasMore)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning FTP folder (paged): $path")
            MediaFilePage(emptyList(), false)
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): Int = withContext(Dispatchers.IO) {
        try {
            Timber.d("FTP getFileCount: path=$path, credentialsId=$credentialsId")
            val files = scanFolder(path, supportedTypes, sizeFilter, credentialsId)
            Timber.d("FTP getFileCount result: ${files.size} files")
            files.size
        } catch (e: Exception) {
            Timber.e(e, "Error counting FTP files in: $path")
            0
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("FTP isWritable: path=$path, credentialsId=$credentialsId")
            
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format for isWritable: $path")
                return@withContext false
            }

            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP for writable check")
                return@withContext false
            }

            // For FTP we can't easily check permissions without attempting write
            // Assume writable if connection succeeds
            ftpClient.disconnect()
            Timber.d("FTP isWritable result: true")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error checking FTP writable: $path")
            false
        }
    }

    private suspend fun parseFtpPath(path: String, credentialsId: String?): ConnectionInfo? {
        Timber.d("Parsing FTP path: $path, credentialsId=$credentialsId")
        
        // Format: ftp://host:port/remotePath
        val regex = """ftp://([^:]+):(\d+)(.*)""".toRegex()
        val match = regex.find(path) ?: run {
            Timber.w("Path does not match FTP regex")
            return null
        }

        val host = match.groupValues[1]
        val port = match.groupValues[2].toIntOrNull() ?: 21
        val remotePath = match.groupValues[3].ifEmpty { "/" }
        
        Timber.d("Parsed FTP URL: host=$host, port=$port, remotePath=$remotePath")

        // Get credentials from database
        if (credentialsId == null) {
            Timber.w("No credentials ID provided for FTP connection")
            return null
        }

        val credentials = credentialsDao.getCredentialsById(credentialsId)
        if (credentials == null) {
            Timber.w("Credentials not found for ID: $credentialsId")
            return null
        }
        
        Timber.d("Found credentials: type=${credentials.type}, username=${credentials.username}")

        return ConnectionInfo(
            host = host,
            port = port,
            username = credentials.username,
            password = credentials.password,
            remotePath = remotePath
        )
    }

    private fun buildFullFtpPath(connectionInfo: ConnectionInfo, fileName: String): String {
        val cleanRemotePath = connectionInfo.remotePath.trimEnd('/')
        val cleanFileName = fileName.trimStart('/')
        
        // If remotePath is empty (was "/"), don't add extra slash
        val fullPath = if (cleanRemotePath.isEmpty()) {
            "ftp://${connectionInfo.host}:${connectionInfo.port}/$cleanFileName"
        } else {
            "ftp://${connectionInfo.host}:${connectionInfo.port}$cleanRemotePath/$cleanFileName"
        }
        
        Timber.d("buildFullFtpPath: remotePath='${connectionInfo.remotePath}', fileName='$fileName' -> '$fullPath'")
        return fullPath
    }

    private fun getMediaType(fileName: String): MediaType? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            extension in IMAGE_EXTENSIONS -> MediaType.IMAGE
            extension in GIF_EXTENSIONS -> MediaType.GIF
            extension in VIDEO_EXTENSIONS -> MediaType.VIDEO
            extension in AUDIO_EXTENSIONS -> MediaType.AUDIO
            else -> null
        }
    }

    private data class ConnectionInfo(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )
}
