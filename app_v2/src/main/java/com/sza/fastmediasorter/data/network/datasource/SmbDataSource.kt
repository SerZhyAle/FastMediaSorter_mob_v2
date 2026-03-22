package com.sza.fastmediasorter.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbConnectionManager
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Custom DataSource for streaming video from SMB server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses SMBJ library's InputStream API for reading file chunks.
 * Supports seeking for video scrubbing.
 * Uses SmbConnectionManager for connection pooling to avoid creating new connections on each file.
 */
class SmbDataSource(
    private val smbClient: SmbClient,
    private val connectionInfo: SmbConnectionInfo
) : BaseDataSource(true) {
    companion object {
        private const val CHUNK_LOG_BYTES = 10_000_000L // ~10 MB summaries
        private const val BYTES_IN_MEBIBYTE = 1_048_576.0
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }

    /**
     * Check if error is an interruption or timeout during cleanup.
     * These are normal during player close and should not be logged as errors.
     */
    private fun isInterruptionOrTimeout(error: Throwable?): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val currentNonNull = current // immutable local for smart cast
            if (currentNonNull is InterruptedException ||
                currentNonNull is java.util.concurrent.TimeoutException) {
                return true
            }
            // Also check message for timeout indicators
            val message = currentNonNull.message?.lowercase() ?: ""
            if (message.contains("timeout") || message.contains("timed out")) {
                return true
            }
            current = currentNonNull.cause
            depth++
        }
        return false
    }
    
    // Keep old name for backward compatibility
    private fun isInterruption(error: Throwable?): Boolean = isInterruptionOrTimeout(error)
    
    private var connection: com.hierynomus.smbj.connection.Connection? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var share: DiskShare? = null
    private var file: File? = null
    private var currentPosition: Long = 0
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L
    private var nextLogThresholdBytes = CHUNK_LOG_BYTES
    
    // Internal buffer for reading larger SMB chunks than ExoPlayer requests
    private var internalBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    private var currentBufferSize = DEFAULT_BUFFER_SIZE
    private var internalBufferPosition = 0
    private var internalBufferValidBytes = 0

    override fun open(dataSpec: DataSpec): Long {
        try {
            val uri = dataSpec.uri
            this.uri = uri
            // Expected format: smb://server/share/path/to/file or smb://server/path/to/file (if share inferred)
            val path = uri.path ?: throw IOException("Invalid SMB path: ${uri}")
            
            // Decode path segments
            val decodedPath = path.trim('/').split("/").joinToString("/") { 
                Uri.decode(it) 
            }
            
            Timber.d("SmbDataSource.open: Decoded path=$decodedPath, Share from ConnectionInfo=${connectionInfo.shareName}")

            // If the connection info already has a share name, we must be careful.
            // VideoPlayerManager now passes /ShareName/Path/To/File as the URI path.
            // SmbConnectionInfo also has the share name.
            
            val finalPath = if (decodedPath.startsWith("${connectionInfo.shareName}/", ignoreCase = true)) {
                // If path starts with share name, strip it to get relative path
                decodedPath.substring(connectionInfo.shareName.length + 1)
            } else if (decodedPath.equals(connectionInfo.shareName, ignoreCase = true)) {
                // Path IS the share name (root)
                ""
            } else {
                // Path doesn't start with share name, assume it's already relative
                decodedPath
            }
            
            Timber.d("SmbDataSource.open: Opening file relative to share '${connectionInfo.shareName}': '$finalPath'")
            
            // Get pooled connection (blocking)
            val pooledConnection = smbClient.connectionManager.getConnectionForExoPlayer(connectionInfo)
            
            // Check if thread was interrupted (e.g., ExoPlayer release during file switching)
            if (Thread.interrupted()) {
                Timber.w("SmbDataSource.open: Thread interrupted before opening file, aborting")
                throw InterruptedException("DataSource thread interrupted")
            }
            
            // Store references
            share = pooledConnection.share
            
            // Open file
            // Use DiskShare.openFile which returns a File handle
            file = share?.openFile(
                finalPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ) ?: throw IOException("Failed to open file: $finalPath")
            
            // Check again before network call
            if (Thread.interrupted()) {
                Timber.w("SmbDataSource.open: Thread interrupted before getFileInformation, aborting")
                throw InterruptedException("DataSource thread interrupted")
            }
            
            val rawLength = file?.fileInformation?.standardInformation?.endOfFile ?: 0L
            Timber.d("SmbDataSource: File opened successfully, size=$rawLength")
            val fileLength = if (rawLength > 0) rawLength else C.LENGTH_UNSET.toLong()

            // Get adaptive buffer size from ThrottleManager
            val resourceKey = "smb://${connectionInfo.server}:${connectionInfo.port}"
            val recommendedSize = com.sza.fastmediasorter.data.network.ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)
            
            if (currentBufferSize != recommendedSize) {
                internalBuffer = ByteArray(recommendedSize)
                currentBufferSize = recommendedSize
                Timber.d("SmbDataSource: Using adaptive buffer size: ${recommendedSize / 1024} KB")
            } else {
                Timber.d("SmbDataSource: Using cached buffer size: ${currentBufferSize / 1024} KB")
            }

            // Handle range request (for seeking)
            val position = dataSpec.position
            
            // CRITICAL: Validate seek position to prevent negative bytesRemaining
            // ExoPlayer may reuse DataSpec with position from previous file
            if (fileLength != C.LENGTH_UNSET.toLong() && position >= fileLength) {
                Timber.e(
                    "SmbDataSource: INVALID SEEK POSITION - position=$position >= fileLength=$fileLength " +
                    "for file=$finalPath. Resetting to start. (This suggests ExoPlayer DataSpec reuse bug)"
                )
                currentPosition = 0
                bytesRemaining = fileLength
            } else {
                currentPosition = position
                bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.length
                } else if (fileLength != C.LENGTH_UNSET.toLong()) {
                    fileLength - position
                } else {
                    C.LENGTH_UNSET.toLong()
                }
            }

            opened = true
            totalBytesRead = 0L
            nextLogThresholdBytes = CHUNK_LOG_BYTES
            internalBufferPosition = 0
            internalBufferValidBytes = 0
            transferStarted(dataSpec)

            Timber.d(
                "SmbDataSource: Opened - position=$position, bytesRemaining=$bytesRemaining, fileLength=$fileLength"
            )

            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                fileLength
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            if (isInterruptionOrTimeout(e)) {
                // Normal during rapid file switching or player release — not an error
                Timber.d("SmbDataSource.open: Interrupted/timeout during open (player released?) - ${e.message}")
                // Restore interrupt flag consumed by Thread.interrupted() earlier
                if (e is InterruptedException) Thread.currentThread().interrupt()
            } else {
                Timber.e(e, "SmbDataSource: Error opening SMB file")
            }
            close()
            throw IOException("Failed to open SMB file: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        // Calculate how many bytes we can fulfill from request
        val maxBytesToReturn = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        var bytesCopied = 0

        // Copy from internal buffer first if we have cached data
        if (internalBufferValidBytes > 0) {
            val bytesFromCache = minOf(maxBytesToReturn, internalBufferValidBytes)
            System.arraycopy(internalBuffer, internalBufferPosition, buffer, offset, bytesFromCache)
            
            internalBufferPosition += bytesFromCache
            internalBufferValidBytes -= bytesFromCache
            bytesCopied = bytesFromCache
            
            currentPosition += bytesFromCache
            totalBytesRead += bytesFromCache
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesFromCache.toLong()
            }
            
            // Log progress
            logProgress(bytesFromCache)
            bytesTransferred(bytesFromCache)
            
            // If we satisfied the full request from cache, return early
            if (bytesCopied >= maxBytesToReturn) {
                return bytesCopied
            }
        }

        // Need more data - read a full chunk from SMB into internal buffer
        var attempts = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (attempts < maxRetries) {
            try {
                // Reset internal buffer for new chunk
                internalBufferPosition = 0
                internalBufferValidBytes = 0
                
                // Read large SMB chunk to minimize protocol overhead
                // This is much more efficient than reading tiny ExoPlayer-sized chunks
                val chunkToRead = minOf(currentBufferSize.toLong(), 
                                        if (bytesRemaining == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else bytesRemaining)
                
                val bytesRead = file?.read(internalBuffer, currentPosition, 0, chunkToRead.toInt()) ?: -1

                if (bytesRead < 0 || bytesRead == 0) {
                    // End of stream
                    return if (bytesCopied > 0) bytesCopied else C.RESULT_END_OF_INPUT
                }

                // Update buffer state
                internalBufferValidBytes = bytesRead
                
                // Copy what we need from the fresh chunk
                val bytesToCopyNow = minOf(maxBytesToReturn - bytesCopied, bytesRead)
                System.arraycopy(internalBuffer, 0, buffer, offset + bytesCopied, bytesToCopyNow)
                
                internalBufferPosition = bytesToCopyNow
                internalBufferValidBytes -= bytesToCopyNow
                bytesCopied += bytesToCopyNow
                
                currentPosition += bytesToCopyNow
                totalBytesRead += bytesToCopyNow
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesToCopyNow.toLong()
                }
                
                logProgress(bytesToCopyNow)
                bytesTransferred(bytesToCopyNow)

                return bytesCopied
                
            } catch (e: Exception) {
                lastException = e
                
                // Check if this is a normal interruption (user closed player)
                val isInterruption = e is InterruptedException || 
                                     (e.cause is InterruptedException) ||
                                     e.message?.contains("InterruptedException", ignoreCase = true) == true
                
                if (isInterruption) {
                    Timber.d("SmbDataSource: Read operation interrupted (player closed)")
                    throw IOException("Read interrupted", e)
                }

                // Check if this is genuine EOF
                val isEOF = e is EOFException ||
                           e.message?.contains("EOF", ignoreCase = true) == true ||
                           e.message?.contains("end of stream", ignoreCase = true) == true

                if (isEOF) {
                    val nearEndOfFile = bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining < 1024
                    
                    if (nearEndOfFile || bytesCopied > 0) {
                        // Normal EOF or we already copied some data
                        Timber.d("SmbDataSource: Reached end of file (bytesRemaining=$bytesRemaining, copied=$bytesCopied)")
                        return if (bytesCopied > 0) bytesCopied else C.RESULT_END_OF_INPUT
                    } else if (attempts < maxRetries) {
                        Timber.w("SmbDataSource: Unexpected EOF (attempt ${attempts + 1}/$maxRetries) at position $currentPosition")
                        attempts++
                        
                        try {
                            reopenConnection()
                            Thread.sleep((100 * attempts).toLong())
                            continue
                        } catch (reconnectError: Exception) {
                            Timber.e(reconnectError, "SmbDataSource: Reconnection attempt $attempts failed")
                        }
                    } else {
                        Timber.e(e, "SmbDataSource: Failed after $maxRetries retries")
                        break
                    }
                } else {
                    // Check for protocol-level parsing errors
                    val isProtocolError = e.message?.contains("Invalid uint32", ignoreCase = true) == true ||
                                         e.message?.contains("Invalid uint64", ignoreCase = true) == true ||
                                         e is IllegalArgumentException && e.message?.contains("value:") == true

                    // DiskShare closed mid-read: pool returned a share that was already programmatically
                    // closed (race: eviction/reset on another thread). isConnected check doesn't catch this.
                    val isShareClosed = e.message?.contains("already been closed", ignoreCase = true) == true

                    if ((isProtocolError || isShareClosed) && attempts < maxRetries) {
                        if (isShareClosed) {
                            Timber.w("SmbDataSource: DiskShare closed during read, reconnecting (attempt ${attempts + 1}/$maxRetries)")
                        } else {
                            Timber.e(e, "SmbDataSource: Protocol parse error - forcing reconnect (attempt ${attempts + 1}/$maxRetries)")
                        }
                        attempts++
                        
                        try {
                            // Close current connection to force pool cleanup
                            closeQuietly()
                            reopenConnection()
                            Thread.sleep((200 * attempts).toLong())
                            continue
                        } catch (reconnectError: Exception) {
                            Timber.e(reconnectError, "SmbDataSource: Reconnection attempt $attempts failed")
                        }
                    }
                    
                    Timber.e(e, "SmbDataSource: Non-recoverable read error")
                    throw IOException("Read error: ${e.message}", e)
                }
            }
        }

        throw IOException("Failed to read from SMB after $attempts attempts: ${lastException?.message}", lastException)
    }
    
    private fun logProgress(bytesJustRead: Int) {
        if (BuildConfig.LOG_SMB_IO) {
            Timber.d("SmbDataSource: READ - bytes=$bytesJustRead total=$totalBytesRead remaining=${formatRemaining()} file=${uri?.lastPathSegment}")
        } else if (totalBytesRead >= nextLogThresholdBytes) {
            val transferredMb = totalBytesRead / BYTES_IN_MEBIBYTE
            Timber.d(
                String.format(
                    Locale.US,
                    "SmbDataSource: streamed %.2f MB (remaining=%s, file=%s)",
                    transferredMb,
                    formatRemaining(),
                    uri?.lastPathSegment
                )
            )
            nextLogThresholdBytes += CHUNK_LOG_BYTES
        }
    }

    /**
     * Attempt to reopen the SMB connection and file at the current position.
     * Used for recovery from network interruptions during streaming.
     */
    private fun reopenConnection() {
        Timber.i("SmbDataSource: Reopening connection - file=${uri?.lastPathSegment}, position=$currentPosition")
        
        // Close existing resources
        try {
            file?.close()
        } catch (e: Exception) {
            Timber.d("Error closing file during reconnect: ${e.message}")
        }
        file = null

        try {
            share?.close()
        } catch (e: Exception) {
            Timber.d("Error closing share during reconnect: ${e.message}")
        }
        share = null

        try {
            session?.close()
        } catch (e: Exception) {
            Timber.d("Error closing session during reconnect: ${e.message}")
        }
        session = null

        try {
            connection?.close()
        } catch (e: Exception) {
            Timber.d("Error closing connection during reconnect: ${e.message}")
        }
        connection = null

        // Reestablish connection with timeouts optimized for video streaming
        val config = SmbConfig.builder()
            .withTimeout(10000, TimeUnit.MILLISECONDS) // 10s connect timeout
            .withSoTimeout(60000, TimeUnit.MILLISECONDS) // 60s read timeout for video buffering
            .withMultiProtocolNegotiate(true)
            .build()
        
        val client = SMBClient(config)
        connection = client.connect(connectionInfo.server, connectionInfo.port)
        
        val finalDomain = connectionInfo.domain.trim().ifEmpty { null }
        val authContext = if (connectionInfo.username.isNotEmpty()) {
            AuthenticationContext(
                connectionInfo.username,
                connectionInfo.password.toCharArray(),
                finalDomain
            )
        } else {
            AuthenticationContext.anonymous()
        }
        
        session = connection?.authenticate(authContext)
        share = session?.connectShare(connectionInfo.shareName) as? DiskShare
            ?: throw IOException("Failed to reconnect to share: ${connectionInfo.shareName}")

        // Reopen file
        val remotePath = uri?.encodedPath ?: throw IOException("Invalid URI path")
        val decodedPath = Uri.decode(remotePath)
        val pathWithoutLeadingSlash = decodedPath.removePrefix("/")
        val sharePrefix = "${connectionInfo.shareName}/"
        val smbPath = if (pathWithoutLeadingSlash.startsWith(sharePrefix)) {
            pathWithoutLeadingSlash.substring(sharePrefix.length)
        } else {
            pathWithoutLeadingSlash
        }

        file = share?.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ) ?: throw IOException("Failed to reopen file: $smbPath")
        
        // Invalidate internal buffer after reconnection
        internalBufferPosition = 0
        internalBufferValidBytes = 0

        Timber.i("SmbDataSource: Reconnection successful")
    }
    
    /**
     * Quietly close all resources without logging errors.
     * Used when forcing reconnect due to protocol errors.
     */
    private fun closeQuietly() {
        try { file?.close() } catch (_: Exception) {}
        file = null
        
        // DO NOT close share/session/connection - they are managed by SmbConnectionManager pool
        share = null
        session = null
        connection = null
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null

        try {
            file?.close()
        } catch (e: Exception) {
            if (isInterruptionOrTimeout(e)) {
                Timber.d("SmbDataSource: File close skipped (interrupted/timeout, normal during playback stop)")
                Thread.currentThread().interrupt()
            } else {
                Timber.w(e, "SmbDataSource: Error closing File (non-critical)")
            }
        } finally {
            file = null
        }

        // DO NOT close share/session/connection - they are managed by SmbConnectionManager pool
        // Just clear references to avoid accidental reuse
        share = null
        session = null
        connection = null

        if (opened) {
            opened = false
            transferEnded()
        }

        Timber.d("SmbDataSource: Closed SMB data source (totalRead=$totalBytesRead bytes) - connection returned to pool")
    }

    private fun formatRemaining(): String = when (bytesRemaining) {
        C.LENGTH_UNSET.toLong() -> "unknown"
        else -> bytesRemaining.toString()
    }
}

/**
 * Factory for creating SmbDataSource instances
 */
class SmbDataSourceFactory(
    private val smbClient: SmbClient,
    private val connectionInfo: SmbConnectionInfo
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(smbClient, connectionInfo)
}
