package com.sza.fastmediasorter.data.network.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
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

    private val verboseNetworkLogging = BuildConfig.LOG_NETWORK_THUMBNAILS

    companion object {
        private const val THUMBNAIL_TIMEOUT_MS = 2_000L
        private const val FULL_IMAGE_TIMEOUT_MS = 60_000L  // 60 seconds for full image (PlayerActivity may compete with thumbnail requests)
    }

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        try {
            val bytes = when {
                data.path.startsWith("smb://") -> fetchFromSmb()
                data.path.startsWith("sftp://") -> fetchFromSftp()
                data.path.startsWith("ftp://") -> fetchFromFtp()
                else -> throw IllegalArgumentException("Unsupported network protocol: ${data.path}")
            }

            if (bytes == null) {
                Timber.d("Network file unavailable: ${data.path}")
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
            if (e.message != "Network file unavailable") {
                if (verboseNetworkLogging) {
                    Timber.d(e, "NetworkFileFetcher: Failed to fetch ${data.path}")
                } else {
                    Timber.d("NetworkFileFetcher: Failed to fetch ${data.path} (${e.message})")
                }
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
        
        // Build resource key for throttling
        val resourceKey = "smb://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
            resourceKey = resourceKey,
            highPriority = data.highPriority  // PlayerActivity requests jump the queue
        ) {

            // Get credentials from database - prefer credentialsId if provided
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("SMB", server, port)
            }
            
            if (credentials == null) return@withThrottle null

            // Extract share name and file path from pathParts
            // pathParts format: "shareName/path/to/file"
            val shareAndPath = pathParts.split("/", limit = 2)
            val shareName = if (shareAndPath.isNotEmpty()) shareAndPath[0] else (credentials.shareName ?: "")
            val remotePath = if (shareAndPath.size > 1) shareAndPath[1] else ""

            if (shareName.isEmpty()) return@withThrottle null

            val connectionInfo = SmbClient.SmbConnectionInfo(
                server = server,
                port = port,
                shareName = shareName,
                username = credentials.username,
                password = credentials.password,
                domain = credentials.domain
            )

            // Determine load strategy based on context:
            // - Thumbnails: 512KB sufficient for JPEG compression (fast load)
            // - Fullscreen: load complete file for high quality
            // - PNG/WebP always full: lossless compression or unknown mode needs complete data
            val maxBytes = if (data.loadFullImage) {
                Long.MAX_VALUE // Fullscreen view - load complete file
            } else {
                // Thumbnail view - limit to 512KB for JPEG (adequate for preview)
                // PNG/WebP files need full load even for thumbnails (checked by extension)
                val extension = remotePath.substringAfterLast('.', "").lowercase()
                if (connectionInfo.shareName.isNotEmpty() && (extension == "png" || extension == "webp")) {
                    Long.MAX_VALUE // PNG/WebP require full file
                } else {
                    512 * 1024L // 512KB for JPEG thumbnails
                }
            }
            
            val timeoutMs = if (data.loadFullImage) FULL_IMAGE_TIMEOUT_MS else THUMBNAIL_TIMEOUT_MS
            
            // Retry logic for full image loading (PlayerActivity) when connection pool is exhausted
            val maxRetries = if (data.loadFullImage) 1 else 0
            var lastException: Exception? = null
            
            for (attempt in 0..maxRetries) {
                val result = try {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        smbClient.readFileBytes(connectionInfo, remotePath, maxBytes)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    lastException = e
                    if (data.loadFullImage && attempt < maxRetries) {
                        Timber.w("SMB full image load timeout (${timeoutMs}ms), retrying (attempt ${attempt + 1}/$maxRetries) for: $remotePath")
                        kotlinx.coroutines.delay(500) // Brief delay before retry
                        continue
                    } else {
                        Timber.d("SMB thumbnail load timeout (${timeoutMs}ms) for: $remotePath")
                        return@withThrottle null
                    }
                } catch (e: CancellationException) {
                    Timber.d("SMB thumbnail request cancelled (${e::class.simpleName}) for: $remotePath")
                    return@withThrottle null
                } catch (e: Exception) {
                    lastException = e
                    // Check if connection-related error and retry if full image
                    val isConnectionError = e.message?.contains("Connection", ignoreCase = true) == true ||
                                           e.message?.contains("Timeout", ignoreCase = true) == true
                    if (data.loadFullImage && attempt < maxRetries && isConnectionError) {
                        Timber.w("SMB full image connection error, retrying (attempt ${attempt + 1}/$maxRetries): ${e.message} for: $remotePath")
                        kotlinx.coroutines.delay(500)
                        continue
                    } else {
                        if (verboseNetworkLogging) {
                            Timber.d(e, "SMB thumbnail load exception for: $remotePath")
                        } else {
                            Timber.d("SMB thumbnail load exception for: $remotePath (${e.message})")
                        }
                        return@withThrottle null
                    }
                }
                
                // Success - return result
                return@withThrottle when (result) {
                    is SmbClient.SmbResult.Success -> result.data
                    is SmbClient.SmbResult.Error -> {
                        Timber.d("SMB thumbnail error: ${result.message} for: $remotePath")
                        null
                    }
                }
            }
            
            // All retries failed
            Timber.e(lastException, "SMB full image load failed after $maxRetries retries for: $remotePath")
            null
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
        
        // Build resource key for throttling
        val resourceKey = "sftp://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
            resourceKey = resourceKey,
            highPriority = data.highPriority  // PlayerActivity requests jump the queue
        ) {

        // Get credentials from database - prefer credentialsId if provided
        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
        }
        
        if (credentials == null) return@withThrottle null

        // Connect to SFTP
        sftpClient.connect(server, port, credentials.username, credentials.password)
        if (!sftpClient.isConnected()) {
            return@withThrottle null
        }

        // Determine load strategy: thumbnails vs fullscreen, JPEG vs PNG/WebP
        val maxBytes = if (data.loadFullImage) {
            Long.MAX_VALUE // Fullscreen view
        } else {
            val extension = remotePath.substringAfterLast('.', "").lowercase()
            if (extension == "png" || extension == "webp") {
                Long.MAX_VALUE // PNG/WebP require full file
            } else {
                512 * 1024L // 512KB for JPEG thumbnails
            }
        }
        
        val timeoutMs = if (data.loadFullImage) FULL_IMAGE_TIMEOUT_MS else THUMBNAIL_TIMEOUT_MS
        val result = try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                sftpClient.readFileBytes(remotePath, maxBytes)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.d("SFTP thumbnail load timeout (${timeoutMs}ms) for: $remotePath")
            return@withThrottle null
        } catch (e: CancellationException) {
            Timber.d("SFTP thumbnail request cancelled (${e::class.simpleName}) for: $remotePath")
            return@withThrottle null
        } catch (e: Exception) {
            if (verboseNetworkLogging) {
                Timber.d(e, "SFTP thumbnail load exception for: $remotePath")
            } else {
                Timber.d("SFTP thumbnail load exception for: $remotePath (${e.message})")
            }
            return@withThrottle null
        }
        
            result.getOrNull()
        }
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
        
        // Build resource key for throttling
        val resourceKey = "ftp://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
            resourceKey = resourceKey,
            highPriority = data.highPriority  // PlayerActivity requests jump the queue
        ) {

        // Get credentials from database - prefer credentialsId if provided
        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("FTP", server, port)
        }
        
        if (credentials == null) {
            Timber.e("fetchFromFtp: No credentials found for FTP $server:$port")
            return@withThrottle null
        }

        // Use temporary connection for parallel downloads (avoid singleton FTPClient race condition)
        val outputStream = java.io.ByteArrayOutputStream()
        val timeoutMs = if (data.loadFullImage) FULL_IMAGE_TIMEOUT_MS else THUMBNAIL_TIMEOUT_MS
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
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
                    if (verboseNetworkLogging) {
                        Timber.d("FTP thumbnail download failed for: $remotePath (${downloadResult.exceptionOrNull()?.message ?: "unknown"})")
                    }
                    null
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.d("FTP thumbnail load timeout (${timeoutMs}ms) for: $remotePath")
            null
        } catch (e: CancellationException) {
            Timber.d("FTP thumbnail request cancelled (${e::class.simpleName}) for: $remotePath")
            null
        } catch (e: Exception) {
            if (verboseNetworkLogging) {
                Timber.d(e, "FTP thumbnail load exception for: $remotePath")
            } else {
                Timber.d("FTP thumbnail load exception for: $remotePath (${e.message})")
            }
            null
        } finally {
            outputStream.close()
        }
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
    val credentialsId: String? = null, // Optional credentialsId to use specific credentials
    val loadFullImage: Boolean = false, // true for fullscreen view, false for thumbnails
    val highPriority: Boolean = false // true for PlayerActivity (user clicked), false for background thumbnails
)
