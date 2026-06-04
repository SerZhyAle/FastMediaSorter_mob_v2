package com.sza.fastmediasorter.data.network.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * ExoPlayer DataSource for SFTP streaming via connection pool.
 * On a transient read error performs one transparent reconnect before propagating to ExoPlayer.
 */
class SftpDataSource(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val context: Context
) : BaseDataSource(true) {

    companion object {
        // IdleDisconnectPolicy expires SFTP transports after 30s. Long-running playback reads can
        // stream continuously without re-entering SftpClient, so refresh the timer from the active
        // DataSource before it falsely invalidates a live ExoPlayer transport.
        private const val PLAYBACK_IDLE_HEARTBEAT_MS = 15_000L
    }

    // Connection from pool - DO NOT close; lifecycle managed by SftpClient pool
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    // Only this is owned and closed by this DataSource
    private var inputStream: InputStream? = null

    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L
    private var readCallCount = 0L
    private var openTimeMs = 0L
    private var openPosition: Long = 0L        // absolute file offset when open() was called
    private var connectionAcquired = false
    private var channelBroken: Boolean = false
    private var lastPlaybackTouchMs = 0L

    override fun open(dataSpec: DataSpec): Long {
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            uri = dataSpec.uri
            val encodedPath = uri?.encodedPath ?: throw IOException("Invalid URI path")
            val remotePath = Uri.decode(encodedPath)

            Timber.d("SftpDataSource: Opening SFTP file - encoded=$encodedPath, decoded=$remotePath")

            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = host, port = port, username = username, password = password
            )
            val pooledConnection = sftpClient.getConnectionForExoPlayer(connectionInfo)
            session = pooledConnection.session
            channel = pooledConnection.channel
            connectionAcquired = true

            Timber.d("SftpDataSource: Got pooled connection - session=${session?.isConnected}, channel=${channel?.isConnected}")

            if (channel == null || !channel!!.isConnected) {
                throw IOException("Failed to get connected SFTP channel from pool")
            }

            return attemptOpen(channel!!, remotePath, dataSpec)
        } catch (e: Exception) {
            val isJSchEx = generateSequence<Throwable>(e) { it.cause }.any { it is JSchException }
            if (isJSchEx && connectionAcquired) {
                Timber.d("SftpDataSource: retrying open() after JSchException - ${e.cause?.message}")
                // Release broken connection before retry
                val brokenCh = channel
                channel = null; session = null
                sftpClient.releaseExoPlayerConnection(brokenCh, broken = true)
                connectionAcquired = false
                try {
                    val retryInfo = SftpClient.SftpConnectionInfo(host, port, username, password)
                    val retryConn = sftpClient.getConnectionForExoPlayer(retryInfo)
                    session = retryConn.session
                    channel = retryConn.channel
                    connectionAcquired = true
                    if (channel == null || !channel!!.isConnected) throw IOException("SFTP retry: channel not connected")
                    val encodedPath = uri?.encodedPath ?: throw IOException("SFTP retry: URI unavailable")
                    return attemptOpen(channel!!, Uri.decode(encodedPath), dataSpec)
                } catch (retryEx: Exception) {
                    // Retry failed - rethrow original exception to preserve ExoPlayer's error code
                    if (connectionAcquired) channelBroken = true
                    Timber.e(e, "SftpDataSource: Error opening SFTP file (retry also failed)")
                    close()
                    throw IOException("Failed to open SFTP file: ${e.message}", e)
                }
            }
            if (connectionAcquired) channelBroken = true
            Timber.e(e, "SftpDataSource: Error opening SFTP file")
            close()
            throw IOException("Failed to open SFTP file: ${e.message}", e)
        }
    }

    /**
     * Executes the stat + openStream logic after a connection is acquired.
     * Sets all open-state fields and returns bytes remaining.
     * Extracted to allow clean retry in [open] without duplicating logic.
     */
    private fun attemptOpen(ch: ChannelSftp, remotePath: String, dataSpec: DataSpec): Long {
        val fileAttributes = ch.stat(remotePath)
        val rawLength = fileAttributes?.size ?: 0L
        val fileLength = if (rawLength > 0) rawLength else C.LENGTH_UNSET.toLong()
        val position = dataSpec.position
        inputStream = openStream(ch, remotePath, position)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (fileLength != C.LENGTH_UNSET.toLong()) {
            fileLength - position
        } else {
            C.LENGTH_UNSET.toLong()
        }
        opened = true
        openPosition = position
        readCallCount = 0L
        openTimeMs = System.currentTimeMillis()
        lastPlaybackTouchMs = openTimeMs
        transferStarted(dataSpec)
        Timber.d("SftpDataSource: Opened - position=$position, bytesRemaining=$bytesRemaining, fileLength=$fileLength")
        return if (bytesRemaining == C.LENGTH_UNSET.toLong()) fileLength else bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
            else minOf(bytesRemaining, length.toLong()).toInt()

        val bytesRead = try {
            inputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT
        } catch (e: InterruptedIOException) {
            // ExoPlayer's Loader interrupts its worker thread on cancel/seek; JSch's
            // PipedInputStream.read() then throws InterruptedIOException. Reconnecting here is
            // futile (acquire would also throw) - restore the interrupt flag, mark the channel
            // broken so the pool evicts it, propagate without noise.
            Thread.currentThread().interrupt()
            channelBroken = true
            throw e
        } catch (e: Exception) {
            // One transparent reconnect - same strategy as SftpClientFirstResult on SftpException 4.
            // If the reconnect also fails, propagate so ExoPlayer handles it.
            Timber.w("SftpDataSource: transient read error, reconnecting - ${e.message}")
            try {
                reconnectStream(openPosition + totalBytesRead)
                inputStream!!.read(buffer, offset, bytesToRead)
            } catch (retryEx: Exception) {
                channelBroken = true
                Timber.e(retryEx, "SftpDataSource: Error reading from SFTP file")
                throw IOException("Failed to read from SFTP file: ${retryEx.message}", retryEx)
            }
        }

        if (bytesRead < 0) return C.RESULT_END_OF_INPUT
        if (bytesRead == 0) return 0

        totalBytesRead += bytesRead
        readCallCount++
        // JSch caps individual SFTP reads to small chunks, so per-read playback logs quickly flood logcat.
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        maybeTouchPlaybackTransport()
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        // Keep `uri` as stable source identity - clearing it on close races the media3 stats
        // wrapper's non-null getUri() check during rapid file switching (same NPE class as SMB).
        // The next open() rebinds it; resources are released below regardless.
        Timber.d("S0343: SFTP data source close preserves URI identity")
        lastPlaybackTouchMs = 0L

        // Only close the InputStream - session and channel are managed by the pool
        val wasAlreadyBroken = channelBroken
        try {
            inputStream?.close()
        } catch (e: InterruptedIOException) {
            // ExoPlayer's Loader interrupts its worker thread on cancel/seek; JSch's
            // PipedInputStream.read() inside _sendCLOSE then throws this. Channel state
            // is undefined after a half-finished CLOSE - return it to the pool as broken.
            channelBroken = true
            Thread.currentThread().interrupt()
            Timber.d("SftpDataSource: InputStream close interrupted by ExoPlayer cancel")
        } catch (e: Exception) {
            channelBroken = true
            val failure = SftpOperationFailure.fromStreamCloseThrowable(
                throwable = e,
                channelAlreadyBroken = wasAlreadyBroken,
                streamWasOpen = opened,
            )
            if (failure.category == SftpFailureCategory.EXPECTED_STREAM_CLOSE) {
                Timber.d(
                    "SftpDataSource: expected InputStream close outcome (${failure.originalMessage})"
                )
            } else {
                Timber.e(e, "SftpDataSource: Error closing InputStream")
            }
        } finally {
            inputStream = null
        }

        // DO NOT close session or channel - they are pooled!
        val released = channel
        channel = null
        session = null

        if (connectionAcquired) {
            sftpClient.releaseExoPlayerConnection(released, channelBroken)
            connectionAcquired = false
        }

        if (opened) {
            opened = false
            transferEnded()
        }

        val elapsedMs = System.currentTimeMillis() - openTimeMs
        Timber.d(
            "SftpDataSource: Closed - totalRead=${totalBytesRead}B, calls=$readCallCount, elapsed=${elapsedMs}ms, connection returned to pool"
        )
    }

    private fun maybeTouchPlaybackTransport() {
        val now = System.currentTimeMillis()
        if (now - lastPlaybackTouchMs < PLAYBACK_IDLE_HEARTBEAT_MS) return
        sftpClient.touchPlaybackTransport(
            host = host,
            port = port,
            username = username,
        )
        lastPlaybackTouchMs = now
        Timber.d("SftpDataSource: playback heartbeat refreshed idle timer")
    }

    /** Releases the broken connection and opens a fresh one at [resumePosition]. */
    private fun reconnectStream(resumePosition: Long) {
        try { inputStream?.close() } catch (_: Exception) {}
        inputStream = null

        val brokenChannel = channel
        channel = null
        session = null
        if (connectionAcquired) {
            sftpClient.releaseExoPlayerConnection(brokenChannel, true)
            connectionAcquired = false
        }

        val connInfo = SftpClient.SftpConnectionInfo(host, port, username, password)
        val pooled = sftpClient.getConnectionForExoPlayer(connInfo)
        session = pooled.session
        channel = pooled.channel
        connectionAcquired = true

        val ch = channel ?: throw IOException("SFTP reconnect: no channel available")
        if (!ch.isConnected) throw IOException("SFTP reconnect: channel not connected")

        val encodedPath = uri?.encodedPath ?: throw IOException("SFTP reconnect: URI unavailable")
        inputStream = openStream(ch, Uri.decode(encodedPath), resumePosition)
        channelBroken = false
        Timber.d("SftpDataSource: Reconnected at position $resumePosition")
    }

    private fun openStream(ch: ChannelSftp, remotePath: String, position: Long): InputStream {
        var rawStream: java.io.InputStream? = null
        return try {
            rawStream = ch.get(remotePath, null, position)
            val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize("sftp://$host:$port")
            Timber.d("SftpDataSource: Using BufferedInputStream with size ${bufferSize / 1024} KB (JSch chunk cap: 1024 B)")
            // JSch's ChannelSftp stream crashes with ArrayIndexOutOfBoundsException when a
            // read() is issued with len > its internal SFTP packet size (1024 bytes): it calls
            // this.skip() internally, which allocates a skipBuf of min(n,2048) bytes, then does
            // arraycopy(src[1024], 0, skipBuf[2048], 0, 2048) → AIOOBE. Capping each individual
            // read at 1024 keeps JSch's skip buffer ≤ 1024, avoiding the bug.
            val safeJschStream = object : java.io.FilterInputStream(rawStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    `in`.read(b, off, minOf(len, 1024))
            }
            rawStream = null
            java.io.BufferedInputStream(safeJschStream, bufferSize)
        } catch (e: Exception) {
            rawStream?.close()
            throw e
        }
    }
}

/**
 * Factory for creating SftpDataSource instances
 */
class SftpDataSourceFactory(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SftpDataSource(
        sftpClient, host, port, username, password, context
    )
}
