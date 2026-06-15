package com.sza.fastmediasorter.data.network.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.model.ConnectionKey
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
    private val connectionInfo: SmbConnectionInfo,
    private val context: Context
) : BaseDataSource(true) {
    companion object {
        private const val CHUNK_LOG_BYTES = 10_000_000L // ~10 MB summaries
        private const val BYTES_IN_MEBIBYTE = 1_048_576.0
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024

        // Hard deadline for SmbDataSource.open(). SMBJ's transaction timeouts protect
        // against a dead SMB server, but a TCP socket that was silently dropped by the
        // peer (no RST) can leave the socket write blocked at the OS level for minutes
        // until kernel retransmission gives up. Without this watchdog, ExoPlayer stays
        // in STATE_BUFFERING forever and the player UI shows an endless spinner.
        // 12 s is generous vs. the fast-tier transaction timeout (5 s) and tight enough
        // to surface onPlayerError promptly so the user can retry.
        private const val OPEN_WATCHDOG_TIMEOUT_MS = 12_000L

        // Hard deadline for a single SmbDataSource.read() chunk. ExoPlayer's Loader thread
        // blocks in file.read() until SMBJ completes the SMB2 READ round-trip; SMBJ's
        // 90 s SO_TIMEOUT is too long for interactive playback. 15 s lets a slow NAS serve
        // a 64 KB chunk comfortably while still surfacing a silent-drop hang promptly.
        private const val READ_WATCHDOG_TIMEOUT_MS = 15_000L

        // Daemon executor shared across SmbDataSource instances - cheap to keep alive
        // and avoids spawning a thread per DataSource construction. Used for both open()
        // and read() watchdog wrappers; a cached pool reuses idle threads for each read.
        private val smbWatchdogExecutor = Executors.newCachedThreadPool { r ->
            Thread(r, "SmbDataSource-Watchdog").apply { isDaemon = true }
        }

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
            // Also check message for timeout/interruption indicators.
            // "got interrupted" covers SMBJ's SMBRuntimeException from SequenceWindow
            // when ExoPlayer interrupts the loader thread during player release.
            val message = currentNonNull.message?.lowercase() ?: ""
            if (message.contains("timeout") || message.contains("timed out") ||
                message.contains("got interrupted")) {
                return true
            }
            current = currentNonNull.cause
            depth++
        }
        return false
    }
    
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
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        // Establish the source identity synchronously, before the blocking open work runs on the
        // watchdog worker thread. The media3 stats wrapper reads getUri() right after open() and
        // asserts it is non-null; binding the URI here (and never clearing it on close) keeps that
        // identity stable even when a concurrent close() from rapid file switching races the read.
        this.uri = dataSpec.uri
        val key = connectionKey()
        val tracker = smbClient.playbackConnectionTracker
        // S0148: fail-fast is attempt-scoped - a recent watchdog blocks only a retry of the
        // *same* file (same media-item URI), or any file once the server-escalation threshold
        // is hit. Advancing to a different file on the same server gets an honest attempt.
        val requestUri = dataSpec.uri.toString()
        val failFast = tracker.isRecentWatchdog(key, requestUri)
        if (failFast) {
            Timber.e("[SMB-PLAY] fail-fast: recent watchdog for ${connectionInfo.server} - aborting retry")
            throw IOException("SMB playback fail-fast: watchdog timeout on previous attempt")
        }
        tracker.onConnectionCreated(key)
        // Run the blocking open logic on a worker thread and wait with a hard deadline.
        // If the SMB server silently dropped the pooled connection's TCP socket, the
        // openFile() call inside openInternal blocks at the OS level far longer than any
        // SMBJ timeout. On watchdog trip we invalidate the pooled connection (closing the
        // socket unblocks the background worker) and surface IOException so ExoPlayer
        // exits STATE_BUFFERING instead of hanging indefinitely.
        val future: Future<Long> = smbWatchdogExecutor.submit(Callable { openInternal(dataSpec) })
        return try {
            future.get(OPEN_WATCHDOG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (te: TimeoutException) {
            tracker.recordWatchdog(key, requestUri)
            Timber.e(
                "[SMB-PLAY] open watchdog after ${OPEN_WATCHDOG_TIMEOUT_MS}ms - " +
                "state=${tracker.getStateName(key)}, server=${connectionInfo.server}"
            )
            try {
                smbClient.connectionManager.invalidateExoPlayerConnection(connectionInfo)
            } catch (inv: Exception) {
                Timber.w(inv, "SmbDataSource.open: invalidateExoPlayerConnection failed")
            }
            future.cancel(true)
            try { close() } catch (_: Exception) {}
            throw IOException("SMB open watchdog timeout after ${OPEN_WATCHDOG_TIMEOUT_MS}ms", te)
        } catch (ee: ExecutionException) {
            val cause = ee.cause ?: ee
            if (cause is IOException) throw cause
            throw IOException("SMB open failed: ${cause.message}", cause)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            throw IOException("SMB open interrupted", ie)
        }
    }

    private fun openInternal(dataSpec: DataSpec): Long {
        try {
            val uri = dataSpec.uri
            this.uri = uri
            val finalPath = resolveSmbPath(uri)
            Timber.d("SmbDataSource.open: Opening '$finalPath' relative to share '${connectionInfo.shareName}'")
            
            var pooledConnection = smbClient.connectionManager.getConnectionForExoPlayer(connectionInfo)
            
            // Check if thread was interrupted (e.g., ExoPlayer release during file switching)
            if (Thread.interrupted()) {
                Timber.w("SmbDataSource.open: Thread interrupted before opening file, aborting")
                throw InterruptedException("DataSource thread interrupted")
            }
            
            // Store references
            share = pooledConnection.share
            
            // Open file - retry once if the pooled connection is stale.
            // isConnectionValid() cannot detect a TCP-level silent drop (the server closed the socket
            // without sending FIN/RST), so the pool may return a connection that looks valid but whose
            // DiskShare.openFile() will fail with a SocketException / TransportException ("Broken pipe").
            // On such failure we invalidate the stale entry and fetch a fresh connection.
            file = try {
                share?.openFile(
                    finalPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
            } catch (e: Exception) {
                if (SmbErrorClassifier.isTransportOrBrokenPipe(e)) {
                    Timber.w("SmbDataSource.open: Stale share detected on openFile (${e.javaClass.simpleName}: ${e.message}) - invalidating and retrying")
                    smbClient.connectionManager.invalidateExoPlayerConnection(connectionInfo)
                    pooledConnection = smbClient.connectionManager.getConnectionForExoPlayer(connectionInfo)
                    share = pooledConnection.share
                    share?.openFile(
                        finalPath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                } else {
                    throw e
                }
            } ?: throw IOException("Failed to open file: $finalPath")
            
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
                // Recovered state, not a failure: media3 occasionally reuses a DataSpec whose
                // position equals the file length (seek/re-open artifact). We reset to start and
                // playback continues normally - so log at INFO. Logging it at ERROR would mirror it
                // to the on-screen debug error notification (S0341) as if something broke.
                Timber.i(
                    "SmbDataSource: seek position past EOF (position=$position >= fileLength=$fileLength) " +
                    "for file=$finalPath - reset to start, playback continues (DataSpec reuse)"
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

            val key = connectionKey()
            smbClient.playbackConnectionTracker.clearWatchdog(key)
            smbClient.playbackConnectionTracker.onConnectionValidated(key)

            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                fileLength
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            if (isInterruptionOrTimeout(e)) {
                // Normal during rapid file switching or player release - not an error
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
        // Fast-path: empty / EOF checks don't need the watchdog - they never touch SMB.
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // Wrap each chunk read in a hard deadline, symmetric to open(). SMBJ's blocking
        // file.read() inside readInternal can stall on a silently-dropped TCP socket far
        // longer than the configured 90 s SO_TIMEOUT permits for interactive UX. On timeout
        // we invalidate the pooled ExoPlayer connection (closing the socket unblocks SMBJ)
        // and throw IOException so ExoPlayer surfaces onPlayerError instead of spinning.
        val future: Future<Int> = smbWatchdogExecutor.submit(Callable { readInternal(buffer, offset, length) })
        return try {
            future.get(READ_WATCHDOG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (te: TimeoutException) {
            val key = connectionKey()
            smbClient.playbackConnectionTracker.recordWatchdog(key, uri?.toString().orEmpty())
            Timber.e(
                "[SMB-PLAY] read watchdog after ${READ_WATCHDOG_TIMEOUT_MS}ms at position=$currentPosition " +
                "- state=${smbClient.playbackConnectionTracker.getStateName(key)}, server=${connectionInfo.server}"
            )
            try {
                smbClient.connectionManager.invalidateExoPlayerConnection(connectionInfo)
            } catch (inv: Exception) {
                Timber.w(inv, "SmbDataSource.read: invalidateExoPlayerConnection failed")
            }
            future.cancel(true)
            throw IOException("SMB read watchdog timeout after ${READ_WATCHDOG_TIMEOUT_MS}ms", te)
        } catch (ee: ExecutionException) {
            val cause = ee.cause ?: ee
            if (cause is IOException) throw cause
            throw IOException("SMB read failed: ${cause.message}", cause)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            throw IOException("SMB read interrupted", ie)
        }
    }

    private fun readInternal(buffer: ByteArray, offset: Int, length: Int): Int {
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
                
                // Check if this is a normal interruption (user closed player).
                // Use isInterruptionOrTimeout() to walk the full cause chain - this correctly
                // handles SMBRuntimeException wrapping InterruptedException (SMBJ SequenceWindow).
                if (isInterruptionOrTimeout(e)) {
                    Timber.d("SmbDataSource: Read operation interrupted (player closed) - ${e.javaClass.simpleName}: ${e.message}")
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
        if (totalBytesRead >= nextLogThresholdBytes) {
            // SMB playback can produce thousands of small reads, so keep only coarse transfer summaries.
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
     * Reopen the SMB file at [currentPosition] via the pool manager.
     * Used for recovery from EOF / protocol errors during streaming.
     * Invalidates the stale pool entry so the manager creates a fresh TCP socket.
     */
    private fun reopenConnection() {
        Timber.i("[SMB-PLAY] reopenConnection: file=${uri?.lastPathSegment}, position=$currentPosition")
        try { file?.close() } catch (_: Exception) {}
        file = null
        // share is pool-managed - do not close it here.
        // Invalidate via the manager so it evicts and purges SMBJ's internal cache.
        share = null

        smbClient.connectionManager.invalidateExoPlayerConnection(connectionInfo)
        val pooled = smbClient.connectionManager.getConnectionForExoPlayer(connectionInfo)
        share = pooled.share

        val finalPath = resolveSmbPath(uri ?: throw IOException("Invalid URI after reconnect"))
        file = share?.openFile(
            finalPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ) ?: throw IOException("Failed to reopen file after reconnect: $finalPath")

        internalBufferPosition = 0
        internalBufferValidBytes = 0
        Timber.i("[SMB-PLAY] reopenConnection succeeded for ${uri?.lastPathSegment}")
    }

    private fun connectionKey() = ConnectionKey(
        server = connectionInfo.server,
        port = connectionInfo.port,
        shareName = connectionInfo.shareName,
        username = connectionInfo.username,
        domain = connectionInfo.domain
    )

    private fun resolveSmbPath(uri: Uri): String {
        val path = uri.encodedPath ?: throw IOException("Invalid SMB URI path: $uri")
        val decoded = path.trim('/').split("/").joinToString("/") { Uri.decode(it) }
        return when {
            decoded.startsWith("${connectionInfo.shareName}/", ignoreCase = true) ->
                decoded.substring(connectionInfo.shareName.length + 1)
            decoded.equals(connectionInfo.shareName, ignoreCase = true) -> ""
            else -> decoded
        }
    }
    
    /**
     * Quietly close all resources without logging errors.
     * Used when forcing reconnect due to protocol errors.
     */
    private fun closeQuietly() {
        try { file?.close() } catch (_: Exception) {}
        file = null
        
        // share is managed by SmbConnectionManager pool - only clear the reference
        share = null
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        // Do NOT clear `uri` here. The source URI is identity, not open-state: nulling it on close
        // races the media3 stats wrapper's non-null getUri() check during rapid file switching and
        // surfaces as a spurious Source error (errorCode=2000 / NullPointerException). Resources are
        // still released below; the next open() rebinds the URI to the new request.
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

        // share is managed by SmbConnectionManager pool - only clear the reference
        share = null

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
    private val connectionInfo: SmbConnectionInfo,
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(smbClient, connectionInfo, context)
}
