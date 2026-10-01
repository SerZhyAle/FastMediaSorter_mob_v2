package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for file attributes retrieved via SFTP stat()
 */
data class SftpFileAttributes(
    val size: Long,
    val modifiedDate: Long, // Unix timestamp in milliseconds
    val accessDate: Long, // Unix timestamp in milliseconds
    val isDirectory: Boolean
)

/**
 * Lightweight listing entry returned by [SftpClient.listFiles].
 * Attributes are populated from [ChannelSftp.LsEntry.attrs] - no extra stat() call needed.
 */
data class SftpFileListing(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val modifiedDate: Long // Unix timestamp in milliseconds (mtime * 1000)
)

/**
 * Low-level SFTP client wrapper using JSch library
 * JSch has built-in KEX implementations (including ECDH) without requiring EC KeyPairGenerator from BouncyCastle
 * This solves Android BouncyCastle limitations with modern SSH servers
 *
 * Host verification is trust-on-first-use (FMSCFG, SHARE-SESSION rule 7): [SftpHostKeyPinRegistry]
 * fills the stored pin into every connection, a pinned session refuses a changed key with
 * [HostKeyMismatchException] and never accepts it, and the first connect of an unpinned resource
 * stores the key it was offered. Only that first connect runs without verification.
 */
@Singleton
class SftpClient @Inject constructor(
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate,
    private val lifecycleBootstrapper:
    dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
    private val idleDisconnectPolicy: com.sza.fastmediasorter.data.network.IdleDisconnectPolicy,
    private val networkStateMonitor: com.sza.fastmediasorter.core.network.NetworkStateMonitor,
    private val pinRegistry: SftpHostKeyPinRegistry,
) {

    companion object {
        // Connection pool settings
        private const val IDLE_TIMEOUT_MS = 30_000L
    }

    data class SftpConnectionInfo(
        val host: String,
        val port: Int = 22,
        val username: String,
        val password: String = "",
        val privateKey: String? = null,
        val passphrase: String? = null,
        val expectedFingerprint: String? = null
    )

    private val pool = SftpConnectionPool(onFirstUseHostKey = pinRegistry::recordFirstUse)

    // newSetFromMap, not newKeySet(): KeySetView#clear and #size are API 24 and legacy ships to API 23.
    private val trackedTransportKeys: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    init {
        // Parity with SmbConnectionManager: invalidate the SFTP pool on a network handover so a
        // half-open socket on the dead interface is force-closed and a parked listing unblocks.
        networkStateMonitor.registerCallback(object : com.sza.fastmediasorter.core.network.NetworkStateMonitor.NetworkChangeCallback {
            override fun onNetworkChanged() {
                pool.disconnectAllOnNetworkChange()
            }

            override fun onNetworkLost() {
                pool.disconnectAllOnNetworkChange()
            }
        })
    }

    /** S0195: trigger network lifecycle bootstrap on first SFTP use. */
    private suspend fun <T> withConnection(
        callerInfo: SftpConnectionInfo,
        replayable: Boolean = true,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("SFTP")
        // The pinned info flows on into the idle-disconnect arm so invalidation hits the same pool key.
        val info = pinRegistry.withPin(callerInfo)
        val transportKey = rememberTransportKey(info)
        idleDisconnectPolicy.touch(transportKey)
        // S0219 Pillar C: rearm the idle timer on every completion path (success or failure), not
        // only success. A failed op still leaves a transport that should stay under idle-policy
        // supervision; only CancellationException (user-initiated cancel, S0205) skips rearm.
        var cancelled = false
        return try {
            pool.withConnection(info, replayable, block)
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            if (!cancelled && trackedTransportKeys.contains(transportKey)) {
                armTransport(info)
            }
        }
    }

    // ExoPlayer connection management lives in SftpConnectionPool.
    /** S0195: trigger network lifecycle bootstrap on first SFTP use. */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: SftpConnectionInfo): SftpConnectionPool.ExoPlayerConnection {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("SFTP")
        val pinned = pinRegistry.withPinBlocking(connectionInfo)
        val transportKey = rememberTransportKey(pinned)
        idleDisconnectPolicy.touch(transportKey)
        return pool.getConnectionForExoPlayer(pinned).also {
            armTransport(pinned)
        }
    }

    /**
     * Playback reads can stay active for minutes without going through the higher-level client
     * entrypoints again. Refresh the idle timer from the DataSource itself so the transport is not
     * considered idle in the middle of a long-running ExoPlayer session.
     */
    fun touchPlaybackTransport(host: String, port: Int, username: String) {
        val connectionInfo = SftpConnectionInfo(host = host, port = port, username = username)
        val transportKey = rememberTransportKey(connectionInfo)
        idleDisconnectPolicy.touch(transportKey)
    }

    /** S0067: close all pooled SFTP sessions (UI lifecycle hook). */
    suspend fun disconnectAllPool() {
        disarmAllTrackedTransports()
        pool.disconnectAll()
    }

    fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) =
        pool.releaseExoPlayerConnection(channel, broken)

    /**
     * List files and directories in remote path.
     * @param recursive If true, scans all subdirectories recursively
     * @param includeDirectories If true, directory entries are also included in the result (non-recursive mode only)
     * @return List of [SftpFileListing] with path and attrs from the ls response - no extra stat() per file
     */
    suspend fun listFiles(
        connectionInfo: SftpConnectionInfo,
        remotePath: String = "/",
        recursive: Boolean = true,
        includeDirectories: Boolean = false
    ): Result<List<SftpFileListing>> = withConnection(connectionInfo) { channel ->
        // S0219: SftpException/IOException now propagate so SftpConnectionPool runs the
        // dead-transport retry (S0147). CancellationException re-throw is preserved
        // explicitly per S0205 to keep the intent visible at the call site.
        try {
            val allFiles = mutableListOf<SftpFileListing>()
            if (recursive) {
                val skipped = mutableListOf<String>()
                listFilesRecursive(channel, remotePath, allFiles, skipped)
                if (skipped.isNotEmpty()) {
                    Timber.w(
                        "SFTP: listed ${allFiles.size} file(s) under '$remotePath', " +
                            "skipped ${skipped.size} unreadable director(y/ies): ${skipped.joinToString()}"
                    )
                }
            } else {
                listFilesSingleLevel(channel, remotePath, allFiles, includeDirectories)
            }
            Result.success(allFiles)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // List files in single directory level (non-recursive)
    private fun listFilesSingleLevel(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<SftpFileListing>,
        includeDirectories: Boolean = false
    ) {
        val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>

        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }

                if (entry.attrs.isDir && !includeDirectories) {
                    // Skip directories in scan mode (caller does not want them)
                    return@forEach
                }

                results.add(
                    SftpFileListing(
                        path = fullPath,
                        size = entry.attrs.size,
                        isDirectory = entry.attrs.isDir,
                        modifiedDate = entry.attrs.mTime.toLong() * 1000L
                    )
                )
            }
        }
    }

    // List files recursively in all subdirectories
    private fun listFilesRecursive(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<SftpFileListing>,
        skipped: MutableList<String>,
        isRoot: Boolean = true
    ) {
        val entries = try {
            channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
        } catch (e: SftpException) {
            // S2315: one unreadable subdirectory used to abort the whole walk, so a share with a
            // single ACL-broken folder reported zero files and read as an empty resource. The root
            // is different: failing there means the resource itself is gone, which the caller must
            // see as an error rather than as an empty folder.
            if (isRoot) throw e
            skipped.add(remotePath)
            Timber.w("SFTP: skipping unreadable directory '$remotePath' - ${e.message}")
            return
        }

        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }

                if (entry.attrs.isDir) {
                    // Recursively scan subdirectory - directory itself is not added to results
                    listFilesRecursive(channel, fullPath, results, skipped, isRoot = false)
                } else {
                    results.add(
                        SftpFileListing(
                            path = fullPath,
                            size = entry.attrs.size,
                            isDirectory = false,
                            modifiedDate = entry.attrs.mTime.toLong() * 1000L
                        )
                    )
                }
            }
        }
    }

    // Read file bytes from SFTP server
    suspend fun readFileBytes(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> {
        val firstResult = withConnection(connectionInfo) { channel ->
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                if (maxBytes < Long.MAX_VALUE) {
                    channel.get(remotePath).use { inputStream ->
                        val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                        var totalRead = 0L

                        while (totalRead < maxBytes) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            val toWrite = minOf(bytesRead.toLong(), maxBytes - totalRead).toInt()
                            outputStream.write(buffer, 0, toWrite)
                            totalRead += toWrite
                        }
                    }
                } else {
                    channel.get(remotePath).use { inputStream ->
                        inputStream.copyTo(outputStream, bufferSize = 65536) // 64KB buffer
                    }
                }

                val bytes = outputStream.toByteArray()
                Result.success(bytes)
            } catch (e: IndexOutOfBoundsException) {
                Timber.w("SFTP readFileBytes got IndexOutOfBoundsException, will retry with new connection")
                Result.failure(e)
            } catch (e: SftpException) {
                // SSH_FX_FAILURE (4) and SSH_FX_BAD_MESSAGE (5) often indicate corrupted channel state
                if (e.id == ChannelSftp.SSH_FX_FAILURE || e.id == ChannelSftp.SSH_FX_BAD_MESSAGE) {
                    Timber.w("SFTP readFileBytes got SftpException ${e.id}, will retry with new connection")
                    Result.failure(e)
                } else {
                    Timber.e(e, "SFTP read file bytes failed: $remotePath")
                    Result.failure(e)
                }
            } catch (e: CancellationException) {
                // S0205: never swallow cooperative coroutine cancellation
                throw e
            } catch (e: IOException) {
                // S0205: IOException on intentional teardown (e.g. ConnectionThrottle ON_STOP) is
                // expected - W level only. E level is reserved for non-IO logic failures.
                Timber.w("SFTP read file bytes interrupted (io): ${e.message} | $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "SFTP read file bytes failed: $remotePath")
                Result.failure(e)
            }
        }

        // Retry with fresh connection if retriable error
        val exception = firstResult.exceptionOrNull()
        val shouldRetry = exception is IndexOutOfBoundsException ||
            (exception is SftpException && (exception.id == ChannelSftp.SSH_FX_FAILURE || exception.id == ChannelSftp.SSH_FX_BAD_MESSAGE))

        return if (firstResult.isFailure && shouldRetry) {
            Timber.d("SFTP: Invalidating connection and retrying: $remotePath")
            disconnectTransport(connectionInfo)

            withConnection(connectionInfo) { channel ->
                try {
                    val outputStream = java.io.ByteArrayOutputStream()
                    channel.get(remotePath).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Result.success(outputStream.toByteArray())
                } catch (e: CancellationException) {
                    // S0205: never swallow cooperative coroutine cancellation
                    throw e
                } catch (e: IOException) {
                    // S0205: IOException on retry is still transient/teardown - W level
                    Timber.w("SFTP read file bytes retry interrupted (io): ${e.message} | $remotePath")
                    Result.failure(e)
                } catch (e: Exception) {
                    Timber.e(e, "SFTP read file bytes failed (retry): $remotePath")
                    Result.failure(e)
                }
            }
        } else {
            firstResult
        }
    }

    /**
     * Read byte range from SFTP file (for sparse video reading).
     * Uses SFTP channel's seek capability.
     */
    suspend fun readFileBytesRange(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        offset: Long,
        length: Long,
        allowRetry: Boolean = true
    ): Result<ByteArray> {
        val firstResult = withConnection(connectionInfo) { channel ->
            try {
                val buffer = ByteArray(length.toInt())
                // Use get(path, offset) to start reading directly from offset position
                // This is more efficient than skip() which reads and discards bytes
                val inputStream = channel.get(remotePath, null, offset)

                inputStream.use {
                    // Read requested bytes directly (no skip needed)
                    var totalRead = 0
                    while (totalRead < length) {
                        val read = it.read(buffer, totalRead, (length - totalRead).toInt())
                        if (read == -1) break
                        totalRead += read
                    }

                    // Return only bytes read (may be less than requested if EOF)
                    if (totalRead < length) {
                        Result.success(buffer.copyOf(totalRead))
                    } else {
                        Result.success(buffer)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "SFTP read bytes range IOException: $remotePath offset=$offset length=$length, will retry")
                Result.failure(e)
            } catch (e: SftpException) {
                // SSH_FX_FAILURE (4) and SSH_FX_BAD_MESSAGE (5) often indicate corrupted channel state
                if (e.id == ChannelSftp.SSH_FX_FAILURE || e.id == ChannelSftp.SSH_FX_BAD_MESSAGE) {
                    Timber.w("SFTP read bytes range got SftpException ${e.id}, will retry")
                    Result.failure(e)
                } else {
                    Timber.e(e, "SFTP read bytes range failed: $remotePath offset=$offset length=$length")
                    Result.failure(e)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "SFTP read bytes range failed: $remotePath offset=$offset length=$length")
                Result.failure(e)
            }
        }

        // Retry with fresh connection if retriable error (skip for thumbnail reads)
        val exception = firstResult.exceptionOrNull()
        val shouldRetry = allowRetry && (
            exception is IOException ||
                (exception is SftpException && (exception.id == ChannelSftp.SSH_FX_FAILURE || exception.id == ChannelSftp.SSH_FX_BAD_MESSAGE))
            )

        return if (firstResult.isFailure && shouldRetry) {
            Timber.d("SFTP: Invalidating connection and retrying readFileBytesRange: $remotePath")
            disconnectTransport(connectionInfo)

            withConnection(connectionInfo) { channel ->
                try {
                    val buffer = ByteArray(length.toInt())
                    // Retry must preserve direct-offset semantics; skip(offset) replays the failing path.
                    val inputStream = channel.get(remotePath, null, offset)

                    inputStream.use {
                        var totalRead = 0
                        while (totalRead < length) {
                            val read = it.read(buffer, totalRead, (length - totalRead).toInt())
                            if (read == -1) break
                            totalRead += read
                        }

                        if (totalRead < length) {
                            Result.success(buffer.copyOf(totalRead))
                        } else {
                            Result.success(buffer)
                        }
                    }
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    Timber.e(e, "SFTP read bytes range failed (retry): $remotePath offset=$offset length=$length")
                    Result.failure(e)
                }
            }
        } else {
            firstResult
        }
    }

    // Download file from SFTP server to OutputStream
    suspend fun downloadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0,
        progressCallback: ByteProgressCallback? = null,
        verifyLength: Boolean = false
    ): Result<Unit> {
        val request = DownloadRequest(connectionInfo, remotePath, fileSize, progressCallback, verifyLength)
        val retryDelaysMs = longArrayOf(1_000, 2_000, 4_000)
        var lastException: Exception? = null
        var lastWasDeadTransport = false
        val sink = RewindableDownloadSink(outputStream)
        var outcome: Result<Unit>? = null
        var attempt = 0

        while (outcome == null && attempt <= retryDelaysMs.size) {
            if (attempt > 0) {
                Timber.d("SFTP [FILE_OPS] download retry $attempt/${retryDelaysMs.size} for $remotePath")
                disconnectTransport(connectionInfo)
                if (!sink.rewind()) {
                    Timber.w("SFTP [FILE_OPS] download not retried - destination cannot be rewound: $remotePath")
                    outcome = Result.failure(lastException ?: IOException("SFTP download failed: $remotePath"))
                } else if (!lastWasDeadTransport) {
                    // S0466: a dead-transport failure (stale pooled session after a long scan, e.g.
                    // "inputstream is closed") is already cured by the disconnectTransport reconnect
                    // above, so the exponential backoff only burns the audio pre-cache startup budget
                    // and pushes the retry past the 5s timeout. Retry immediately on the fresh session;
                    // reserve backoff for genuinely transient server errors.
                    delay(retryDelaysMs[attempt - 1])
                }
            }

            if (outcome == null) {
                val result = downloadAttempt(request, sink, attempt)
                val ex = result.exceptionOrNull()
                if (ex == null || !isRetriableDownloadFailure(ex)) {
                    outcome = result
                } else {
                    lastWasDeadTransport = SftpConnectionPool.isDeadTransport(ex)
                    lastException = ex as? Exception ?: Exception(ex.message)
                }
            }
            attempt++
        }

        return outcome ?: Result.failure<Unit>(SftpDownloadExhaustedException(remotePath, lastException)).also {
            Timber.e("SFTP [FILE_OPS] download exhausted all retries: $remotePath")
        }
    }

    private class DownloadRequest(
        val connectionInfo: SftpConnectionInfo,
        val remotePath: String,
        val fileSize: Long,
        val progressCallback: ByteProgressCallback?,
        val verifyLength: Boolean
    )

    private fun isRetriableDownloadFailure(ex: Throwable): Boolean =
        ex is IndexOutOfBoundsException ||
            (ex is SftpException && (ex.id == ChannelSftp.SSH_FX_FAILURE || ex.id == ChannelSftp.SSH_FX_BAD_MESSAGE)) ||
            ex is IOException

    private suspend fun downloadAttempt(
        request: DownloadRequest,
        sink: RewindableDownloadSink,
        attempt: Int
    ): Result<Unit> = withConnection(request.connectionInfo) { channel ->
        val remotePath = request.remotePath
        val fileSize = request.fileSize
        try {
            channel.get(remotePath).use { inputStream ->
                if (request.progressCallback != null && fileSize > 0) {
                    inputStream.copyToWithProgress(sink, fileSize, request.progressCallback)
                } else {
                    inputStream.copyTo(sink)
                }
            }
            // S4035: a move deletes its source after this returns, so a short stream must fail
            // here; IOException keeps it inside the retry loop. Opt-in because some callers pass
            // a capped read size rather than the real file size.
            if (request.verifyLength && fileSize > 0 && sink.written != fileSize) {
                Result.failure(IOException("SFTP download length mismatch: $remotePath $fileSize/${sink.written}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: IndexOutOfBoundsException) {
            Timber.w("SFTP [FILE_OPS] IndexOutOfBoundsException attempt $attempt: $remotePath")
            Result.failure(e)
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_FAILURE || e.id == ChannelSftp.SSH_FX_BAD_MESSAGE) {
                Timber.w("SFTP [FILE_OPS] SftpException ${e.id} attempt $attempt: $remotePath")
            } else {
                Timber.e(e, "SFTP [FILE_OPS] download failed: $remotePath")
            }
            Result.failure(e)
        } catch (e: IOException) {
            Timber.w("SFTP [FILE_OPS] IOException attempt $attempt: $remotePath - ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            // copyToWithProgress calls ensureActive per buffer, so leaving the screen mid
            // download lands here as a CancellationException; without the rethrow it became
            // an E-log plus a non-retriable failure instead of cooperative cancellation.
            e.rethrowIfCancellation()
            Timber.e(e, "SFTP [FILE_OPS] download failed: $remotePath")
            Result.failure(e)
        }
    }

    // Upload file to SFTP server from byte array
    suspend fun uploadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        data: ByteArray
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        // S0219: exceptions propagate to SftpConnectionPool for dead-transport retry.
        val parentDir = remotePath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            ensureDirectoryExists(channel, parentDir)
        }
        data.inputStream().use { inputStream ->
            channel.put(inputStream, remotePath)
        }
        Result.success(Unit)
    }

    // Upload file to SFTP server from InputStream
    suspend fun uploadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        inputStream: java.io.InputStream,
        fileSize: Long = 0,
        progressCallback: ByteProgressCallback? = null,
        verifyLength: Boolean = false
    ): Result<Unit> = withConnection(connectionInfo, replayable = false) { channel ->
        // S4033: exceptions propagate so SftpConnectionPool drops a dead transport, but this mutation
        // is never replayed - its reply may have been lost after the server already acted.
        val parentDir = remotePath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            ensureDirectoryExists(channel, parentDir)
        }
        // Use OutputStream to support progress callback
        val sent = channel.put(remotePath).use { outputStream ->
            if (progressCallback != null && fileSize > 0) {
                inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
            } else {
                inputStream.copyTo(outputStream)
            }
        }
        // S4035: the server may keep a truncated file after a dropped write; a move would then
        // delete its source, so compare what landed with what was sent.
        val stored = if (verifyLength) channel.stat(remotePath).size else sent
        Timber.d("S4035: upload verify=$verifyLength sent=$sent stored=$stored $remotePath")
        if (stored != sent) {
            Result.failure(IOException("SFTP upload length mismatch: $remotePath sent=$sent stored=$stored"))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun stat(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<SftpFileAttributes> = withConnection(connectionInfo) { channel ->
        // S0219: exceptions propagate to SftpConnectionPool for dead-transport retry.
        val attrs = channel.stat(remotePath)
        Result.success(
            SftpFileAttributes(
                size = attrs.size,
                modifiedDate = attrs.mTime * 1000L,
                accessDate = attrs.aTime * 1000L,
                isDirectory = attrs.isDir
            )
        )
    }

    // Check if path exists
    suspend fun exists(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Boolean> = withConnection(connectionInfo) { channel ->
        // S0219: only SSH_FX_NO_SUCH_FILE is a benign protocol response (path missing).
        // All other SftpException ids, any IOException, and CancellationException propagate
        // so SftpConnectionPool can run the dead-transport retry (S0147) and coroutine
        // cancellation stays cooperative (S0205).
        try {
            channel.stat(remotePath)
            Result.success(true)
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) Result.success(false) else throw e
        }
    }

    suspend fun mkdir(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo, replayable = false) { channel ->
        // S4033: exceptions propagate so SftpConnectionPool drops a dead transport, but this mutation
        // is never replayed - its reply may have been lost after the server already acted.
        channel.mkdir(remotePath)
        Result.success(Unit)
    }

    // Delete file
    suspend fun deleteFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo, replayable = false) { channel ->
        // S4033: exceptions propagate so SftpConnectionPool drops a dead transport, but this mutation
        // is never replayed - its reply may have been lost after the server already acted.
        channel.rm(remotePath)
        Result.success(Unit)
    }

    // Delete directory recursively
    suspend fun deleteDirectory(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo, replayable = false) { channel ->
        // S4033: exceptions propagate so SftpConnectionPool drops a dead transport, but this mutation
        // is never replayed - its reply may have been lost after the server already acted.
        // Helper function for recursion within the same channel
        fun deleteRecursive(path: String) {
            val files = channel.ls(path) as Vector<ChannelSftp.LsEntry>

            files.forEach { entry ->
                if (entry.filename == "." || entry.filename == "..") return@forEach

                val fullPath = "$path/${entry.filename}"
                if (entry.attrs.isDir) {
                    deleteRecursive(fullPath)
                } else {
                    channel.rm(fullPath)
                }
            }
            channel.rmdir(path)
        }

        deleteRecursive(remotePath)
        Result.success(Unit)
    }

    // Rename/move file or directory
    suspend fun rename(
        connectionInfo: SftpConnectionInfo,
        oldPath: String,
        newPath: String
    ): Result<Unit> = withConnection(connectionInfo, replayable = false) { channel ->
        // S4033: exceptions propagate so SftpConnectionPool drops a dead transport, but this mutation
        // is never replayed - its reply may have been lost after the server already acted.
        channel.rename(oldPath, newPath)
        Result.success(Unit)
    }

    // Rename file (convenience method)
    suspend fun renameFile(
        connectionInfo: SftpConnectionInfo,
        oldPath: String,
        newName: String
    ): Result<Unit> {
        val parentPath = oldPath.substringBeforeLast('/', "")
        val newPath = when {
            parentPath.isNotEmpty() -> "$parentPath/$newName"
            oldPath.startsWith("/") -> "/$newName"
            else -> newName
        }
        return rename(connectionInfo, oldPath, newPath)
    }

    // Aliases for compatibility
    suspend fun createDirectory(connectionInfo: SftpConnectionInfo, remotePath: String) = mkdir(
        connectionInfo,
        remotePath
    )
    suspend fun getFileAttributes(connectionInfo: SftpConnectionInfo, remotePath: String) = stat(
        connectionInfo,
        remotePath
    )

    // Disconnect all sessions (e.g. on app shutdown)
    suspend fun disconnectAll() {
        disarmAllTrackedTransports()
        pool.disconnectAll()
    }

    suspend fun testConnection(
        host: String,
        port: Int = 22,
        username: String,
        password: String,
        expectedFingerprint: String? = null
    ): Result<String?> =
        SftpConnectionTester.testConnection(host, port, username, password, expectedFingerprint)

    suspend fun testConnectionWithPrivateKey(
        host: String,
        port: Int = 22,
        username: String,
        privateKey: String,
        passphrase: String? = null,
        expectedFingerprint: String? = null
    ): Result<String?> = SftpConnectionTester.testConnectionWithPrivateKey(
        host,
        port,
        username,
        privateKey,
        passphrase,
        expectedFingerprint
    )

    private fun ensureDirectoryExists(channel: ChannelSftp, remotePath: String) =
        SftpConnectionTester.ensureDirectoryExists(channel, remotePath)

    suspend fun openInputStream(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<java.io.InputStream> {
        val pinned = pinRegistry.withPin(connectionInfo)
        val transportKey = rememberTransportKey(pinned)
        idleDisconnectPolicy.touch(transportKey)
        // S0219 Pillar C: rearm idle timer on every non-cancellation completion path.
        // Note: the InputStream lifetime extends past this function, but idle-disconnect concerns
        // the pool's session state - activeBorrowCount (Phase 02) keeps the session alive while
        // the stream is open regardless of the idle timer.
        var cancelled = false
        return try {
            pool.openInputStream(pinned, remotePath)
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            if (!cancelled && trackedTransportKeys.contains(transportKey)) {
                armTransport(pinned)
            }
        }
    }
    // Create directory on SFTP server

    private fun transportKey(connectionInfo: SftpConnectionInfo): String {
        return "sftp@${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.username}"
    }

    private fun rememberTransportKey(connectionInfo: SftpConnectionInfo): String {
        return transportKey(connectionInfo).also(trackedTransportKeys::add)
    }

    private fun armTransport(connectionInfo: SftpConnectionInfo) {
        val transportKey = rememberTransportKey(connectionInfo)
        idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) {
            disconnectTransport(connectionInfo, disarm = false)
        }
    }

    private suspend fun disconnectTransport(
        connectionInfo: SftpConnectionInfo,
        disarm: Boolean = true,
    ) {
        val transportKey = transportKey(connectionInfo)
        if (disarm) {
            idleDisconnectPolicy.disarm(transportKey)
        }
        trackedTransportKeys.remove(transportKey)
        pool.invalidate(connectionInfo)
    }

    private fun disarmAllTrackedTransports() {
        trackedTransportKeys.forEach(idleDisconnectPolicy::disarm)
        trackedTransportKeys.clear()
    }
}
