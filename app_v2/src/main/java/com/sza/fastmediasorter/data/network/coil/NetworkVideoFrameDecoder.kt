package com.sza.fastmediasorter.data.network.coil

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Coil Decoder for extracting video frames from network files (SMB/SFTP/FTP).
 * Downloads video to temp file, extracts first frame, then cleans up.
 */
class NetworkVideoFrameDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val data: NetworkFileData,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : Decoder {

    override suspend fun decode(): DecodeResult = withContext(Dispatchers.IO) {
        val tempFile = File(options.context.cacheDir, "temp_video_${System.currentTimeMillis()}.tmp")
        
        try {
            // Download video to temp file
            val downloadSuccess = when {
                data.path.startsWith("smb://") -> downloadFromSmb(tempFile)
                data.path.startsWith("sftp://") -> downloadFromSftp(tempFile)
                data.path.startsWith("ftp://") -> downloadFromFtp(tempFile)
                else -> false
            }
            
            if (!downloadSuccess || !tempFile.exists()) {
                throw Exception("Failed to download video from network: ${data.path}")
            }
            
            // Extract first frame using MediaMetadataRetriever
            val bitmap = extractVideoFrame(tempFile)
                ?: throw Exception("Failed to extract video frame from ${tempFile.absolutePath}")
            
            DecodeResult(
                drawable = bitmap.toDrawable(options.context.resources),
                isSampled = false
            )
        } finally {
            // Clean up temp file
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    
    private suspend fun downloadFromSmb(localFile: File): Boolean {
        val uri = data.path.removePrefix("smb://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return false

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

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SMB", server, port)
        } ?: return false

        val shareAndPath = pathParts.split("/", limit = 2)
        val shareName = if (shareAndPath.isNotEmpty()) shareAndPath[0] else (credentials.shareName ?: "")
        val remotePath = if (shareAndPath.size > 1) shareAndPath[1] else ""

        if (shareName.isEmpty()) return false

        val connectionInfo = SmbClient.SmbConnectionInfo(
            server = server,
            port = port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )

        localFile.outputStream().use { outputStream ->
            val result = smbClient.downloadFile(connectionInfo, remotePath, outputStream)
            return result is SmbClient.SmbResult.Success
        }
    }
    
    private suspend fun downloadFromSftp(localFile: File): Boolean {
        val uri = data.path.removePrefix("sftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return false

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

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
        } ?: return false

        sftpClient.connect(server, port, credentials.username, credentials.password)
        if (!sftpClient.isConnected()) return false

        localFile.outputStream().use { outputStream ->
            sftpClient.downloadFile(remotePath, outputStream)
            sftpClient.disconnect()
        }
        
        return localFile.exists() && localFile.length() > 0
    }
    
    private suspend fun downloadFromFtp(localFile: File): Boolean {
        val uri = data.path.removePrefix("ftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return false

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

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("FTP", server, port)
        } ?: return false

        ftpClient.connect(server, port, credentials.username, credentials.password)
        if (!ftpClient.isConnected()) return false

        localFile.outputStream().use { outputStream ->
            ftpClient.downloadFile(remotePath, outputStream)
            ftpClient.disconnect()
        }
        
        return localFile.exists() && localFile.length() > 0
    }
    
    private fun extractVideoFrame(videoFile: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            
            // Extract frame at 1 second (or first frame if video is shorter)
            val frameTime = 1_000_000L // 1 second in microseconds
            retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0) // Fallback to first frame
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract video frame from ${videoFile.absolutePath}")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.w(e, "Failed to release MediaMetadataRetriever")
            }
        }
    }
    
    private fun Bitmap.toDrawable(resources: android.content.res.Resources): android.graphics.drawable.BitmapDrawable {
        return android.graphics.drawable.BitmapDrawable(resources, this)
    }

    class Factory(
        private val smbClient: SmbClient,
        private val sftpClient: SftpClient,
        private val ftpClient: FtpClient,
        private val credentialsRepository: NetworkCredentialsRepository
    ) : Decoder.Factory {
        
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            // Only handle NetworkFileData for video files
            val data = options.parameters.value(NETWORK_FILE_DATA_KEY) as? NetworkFileData
                ?: return null
            
            // Check if this is a video file by extension
            val extension = data.path.substringAfterLast('.', "").lowercase()
            val isVideo = extension in setOf("mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg")
            
            if (!isVideo) return null
            
            return NetworkVideoFrameDecoder(
                source = result.source,
                options = options,
                data = data,
                smbClient = smbClient,
                sftpClient = sftpClient,
                ftpClient = ftpClient,
                credentialsRepository = credentialsRepository
            )
        }
        
        companion object {
            // Key for passing NetworkFileData through Coil request parameters
            const val NETWORK_FILE_DATA_KEY = "network_file_data"
        }
    }
}
