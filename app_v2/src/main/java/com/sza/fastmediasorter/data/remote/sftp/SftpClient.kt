package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.Vector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for file attributes retrieved via SFTP stat()
 */
data class SftpFileAttributes(
    val size: Long,
    val modifiedDate: Long, // Unix timestamp in milliseconds
    val accessDate: Long,   // Unix timestamp in milliseconds
    val isDirectory: Boolean
)

/**
 * Low-level SFTP client wrapper using JSch library
 * JSch has built-in KEX implementations (including ECDH) without requiring EC KeyPairGenerator from BouncyCastle
 * This solves Android BouncyCastle limitations with modern SSH servers
 * 
 * SECURITY NOTE - SFTP Host Verification:
 * ========================================
 * This implementation sets StrictHostKeyChecking to "no" for usability reasons.
 * This means the client will NOT verify the server's host key fingerprint.
 * 
 * RISK: Man-in-the-Middle (MITM) Attack
 * An attacker on the same network could intercept the SFTP connection and present
 * a fake server. The client would blindly connect and send credentials.
 * 
 * ACCEPTED FOR:
 * - Trusted local networks (home/office LANs)
 * - Scenarios where network security is ensured through other means (VPN, etc.)
 * - Quick testing and development
 * 
 * NOT RECOMMENDED FOR:
 * - Public Wi-Fi networks
 * - Untrusted networks
 * - Production environments with strict security requirements
 * 
 * FUTURE IMPROVEMENT:
 * Implement "Trust on First Use" (TOFU) pattern:
 * - Store server's host key fingerprint on first connection
 * - Verify fingerprint matches on subsequent connections
 * - Allow user to manually verify/update fingerprints
 * - See JSch's HostKeyRepository for implementation
 */
@Singleton
class SftpClient @Inject constructor() {

    companion object {
        private const val CONNECTION_TIMEOUT = 10000 // 10 seconds (reduced from 15s for faster error feedback)
        private const val SOCKET_TIMEOUT = 30000 // 30 seconds (long timeout for slow file operations)
        private const val MAX_CONCURRENT_CONNECTIONS = 15 // Increased for channel pooling
        private const val MAX_CHANNELS_PER_SESSION = 5 // Max channels per session
        // Connection pool settings
        private const val IDLE_TIMEOUT_MS = 25000L // 25 seconds (slightly longer than SOCKET_TIMEOUT)
    }

    data class SftpConnectionInfo(
        val host: String,
        val port: Int = 22,
        val username: String,
        val password: String = "",
        val privateKey: String? = null,
        val passphrase: String? = null
    )

    private data class PooledConnection(
        val session: Session,
        val jsch: JSch,
        val channels: MutableList<ChannelSftp> = mutableListOf(),
        val channelMutexes: MutableList<Mutex> = mutableListOf(),
        val sessionMutex: Mutex = Mutex(),
        var lastUsed: Long = System.currentTimeMillis()
    )

    private data class ConnectionKey(
        val host: String,
        val port: Int,
        val username: String
    )

    private val connectionPool = java.util.concurrent.ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = java.util.concurrent.Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val poolMutex = Mutex()

    /**
     * Execute block with an SFTP channel from the pool.
     * Now supports multiple channels per session for parallel operations.
     */
    private suspend fun <T> withConnection(
        info: SftpConnectionInfo,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username)
        
        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateConnection(key, info)
                pooled.lastUsed = System.currentTimeMillis()
                
                // Get or create a channel from the pool
                val (channel, mutex) = getOrCreateChannel(pooled, info)
                
                try {
                    // Serialize operations on the same channel to prevent race conditions
                    mutex.withLock {
                        block(channel)
                    }
                } catch (e: Exception) {
                    // If channel failed, remove it from pool
                    if (!channel.isConnected) {
                        Timber.w("SFTP channel lost, removing from pool: ${e.message}")
                        removeChannel(pooled, channel, mutex)
                    }
                    
                    // If session also failed, invalidate entire connection
                    if (!pooled.session.isConnected) {
                        Timber.w("SFTP session lost, retrying: ${e.message}")
                        invalidateConnection(key)
                        val newPooled = getOrCreateConnection(key, info)
                        newPooled.lastUsed = System.currentTimeMillis()
                        val (newChannel, newMutex) = getOrCreateChannel(newPooled, info)
                        return@withContext newMutex.withLock {
                            block(newChannel)
                        }
                    }
                    throw e
                }
            } finally {
                connectionSemaphore.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP operation failed")
            Result.failure(e)
        }
    }
    
    /**
     * Get or create a channel from the session's channel pool
     */
    private suspend fun getOrCreateChannel(pooled: PooledConnection, info: SftpConnectionInfo): Pair<ChannelSftp, Mutex> {
        return pooled.sessionMutex.withLock {
            // Find an available connected channel
            pooled.channels.forEachIndexed { index, channel ->
                if (channel.isConnected) {
                    return@withLock channel to pooled.channelMutexes[index]
                }
            }
            
            // No available channels, create new one if under limit
            if (pooled.channels.size < MAX_CHANNELS_PER_SESSION) {
                val newChannel = pooled.session.openChannel("sftp") as ChannelSftp
                newChannel.connect(CONNECTION_TIMEOUT)
                val newMutex = Mutex()
                
                pooled.channels.add(newChannel)
                pooled.channelMutexes.add(newMutex)
                
                Timber.d("Created new SFTP channel (${pooled.channels.size}/${MAX_CHANNELS_PER_SESSION}) for ${info.host}")
                return@withLock newChannel to newMutex
            }
            
            // All channels in use, return first one (will wait on mutex)
            Timber.d("All SFTP channels in use for ${info.host}, reusing first channel")
            pooled.channels[0] to pooled.channelMutexes[0]
        }
    }
    
    /**
     * Remove a failed channel from the pool
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun removeChannel(pooled: PooledConnection, channel: ChannelSftp, mutex: Mutex) {
        pooled.sessionMutex.withLock {
            val index = pooled.channels.indexOf(channel)
            if (index >= 0) {
                try {
                    channel.disconnect()
                } catch (e: Exception) {
                    Timber.w("Error disconnecting channel: ${e.message}")
                }
                pooled.channels.removeAt(index)
                pooled.channelMutexes.removeAt(index)
                Timber.d("Removed failed channel, ${pooled.channels.size} remaining")
            }
        }
    }

    private suspend fun getOrCreateConnection(key: ConnectionKey, info: SftpConnectionInfo): PooledConnection {
        poolMutex.lock()
        try {
            val existing = connectionPool[key]
            if (existing != null && existing.session.isConnected) {
                // Check if at least one channel is alive
                val hasAliveChannel = existing.channels.any { it.isConnected }
                if (hasAliveChannel) {
                    return existing
                }
            }
            
            // Remove invalid connection if exists
            if (existing != null) {
                try {
                    existing.channels.forEach { it.disconnect() }
                    existing.session.disconnect()
                } catch (e: Exception) {
                    Timber.w("Error closing invalid connection: ${e.message}")
                }
                connectionPool.remove(key)
            }

            // Create new connection with initial channel
            val jsch = JSch()
            
            // Add private key if provided
            if (info.privateKey != null) {
                val name = "key_${System.currentTimeMillis()}"
                if (info.passphrase != null) {
                    jsch.addIdentity(name, info.privateKey.toByteArray(), null, info.passphrase.toByteArray())
                } else {
                    jsch.addIdentity(name, info.privateKey.toByteArray(), null, null)
                }
            }

            val session = jsch.getSession(info.username, info.host, info.port)
            
            if (info.privateKey != null) {
                // Private key auth
                if (info.passphrase != null) {
                    session.userInfo = object : com.jcraft.jsch.UserInfo {
                        override fun getPassphrase(): String = info.passphrase
                        override fun getPassword(): String? = null
                        override fun promptPassword(message: String?): Boolean = false
                        override fun promptPassphrase(message: String?): Boolean = true
                        override fun promptYesNo(message: String?): Boolean = true
                        override fun showMessage(message: String?) {}
                    }
                }
                val config = java.util.Properties()
                config["StrictHostKeyChecking"] = "no"
                config["PreferredAuthentications"] = "publickey"
                session.setConfig(config)
            } else {
                // Password auth
                session.setPassword(info.password)
                session.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String? = null
                    override fun getPassword(): String = info.password
                    override fun promptPassword(message: String?): Boolean = true
                    override fun promptPassphrase(message: String?): Boolean = false
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
                val config = java.util.Properties()
                config["StrictHostKeyChecking"] = "no"
                config["PreferredAuthentications"] = "keyboard-interactive,password"
                session.setConfig(config)
            }

            session.timeout = SOCKET_TIMEOUT
            session.connect(CONNECTION_TIMEOUT)

            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECTION_TIMEOUT)

            // Initialize PooledConnection with channel pool
            val pooled = PooledConnection(
                session = session,
                jsch = jsch,
                channels = mutableListOf(channel),
                channelMutexes = mutableListOf(Mutex()),
                sessionMutex = Mutex()
            )
            connectionPool[key] = pooled
            
            Timber.d("Created new SFTP connection to ${info.host} with channel pool")
            return pooled
        } finally {
            poolMutex.unlock()
        }
    }

    private suspend fun invalidateConnection(key: ConnectionKey) {
        poolMutex.lock()
        try {
            connectionPool.remove(key)?.let { pooled ->
                try {
                    // Disconnect all channels in the pool
                    pooled.channels.forEach { channel ->
                        if (channel.isConnected) {
                            channel.disconnect()
                        }
                    }
                    pooled.session.disconnect()
                    Timber.d("Invalidated SFTP connection with ${pooled.channels.size} channels")
                } catch (e: Exception) {
                    Timber.w("Error closing invalidated connection: ${e.message}")
                }
            }
        } finally {
            poolMutex.unlock()
        }
    }

    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = connectionPool.filter { (_, conn) -> 
            now - conn.lastUsed > IDLE_TIMEOUT_MS 
        }.keys

        if (keysToRemove.isNotEmpty()) {
            // Launch cleanup in background to avoid blocking
            CoroutineScope(Dispatchers.IO).launch {
                poolMutex.withLock {
                    keysToRemove.forEach { key ->
                        connectionPool.remove(key)?.let { pooled ->
                            try {
                                // Disconnect all channels
                                pooled.channels.forEach { channel ->
                                    if (channel.isConnected) {
                                        channel.disconnect()
                                    }
                                }
                                pooled.session.disconnect()
                                Timber.d("Closed idle SFTP connection to ${key.host} with ${pooled.channels.size} channels")
                            } catch (e: Exception) {
                                Timber.w("Error closing idle connection: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== ExoPlayer DataSource Support ====================

    /**
     * Data class for ExoPlayer pooled connection.
     * Contains session and channel that should NOT be closed by caller.
     * Connection is managed by pool and will be reused.
     */
    data class ExoPlayerConnection(
        val session: Session,
        val channel: ChannelSftp
    )

    /**
     * Get a pooled SFTP connection for ExoPlayer DataSource.
     * This method is BLOCKING (not suspend) because ExoPlayer DataSource.open() is blocking.
     * 
     * IMPORTANT: Caller must NOT close session or channel!
     * Connection is managed by the pool and will be reused.
     * Only the InputStream from channel.get() should be closed.
     * 
     * @param connectionInfo SFTP connection parameters
     * @return ExoPlayerConnection with session and channel from pool
     * @throws IOException if connection fails
     */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: SftpConnectionInfo): ExoPlayerConnection {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        
        try {
            connectionSemaphore.acquire()
            
            // Try to get existing connection from pool
            val existing = connectionPool[key]
            if (existing != null && existing.session.isConnected) {
                existing.lastUsed = System.currentTimeMillis()
                
                // Try to find or create an available channel
                synchronized(existing) {
                    // Find connected channel
                    existing.channels.firstOrNull { it.isConnected }?.let { channel ->
                        Timber.d("SFTP ExoPlayer: Reusing pooled connection to ${connectionInfo.host}")
                        return ExoPlayerConnection(existing.session, channel)
                    }
                    
                    // No connected channel - create new one if under limit
                    if (existing.channels.size < MAX_CHANNELS_PER_SESSION) {
                        val newChannel = existing.session.openChannel("sftp") as ChannelSftp
                        newChannel.connect(CONNECTION_TIMEOUT)
                        existing.channels.add(newChannel)
                        existing.channelMutexes.add(Mutex())
                        Timber.d("SFTP ExoPlayer: Created new channel (${existing.channels.size}/$MAX_CHANNELS_PER_SESSION)")
                        return ExoPlayerConnection(existing.session, newChannel)
                    }
                    
                    // All channels busy - return first one (caller will wait on I/O)
                    Timber.d("SFTP ExoPlayer: All channels busy, reusing first channel")
                    return ExoPlayerConnection(existing.session, existing.channels[0])
                }
            }
            
            // Need to create new connection
            Timber.d("SFTP ExoPlayer: Creating new pooled connection to ${connectionInfo.host}")
            
            val jsch = JSch()
            
            // Add private key if provided
            if (connectionInfo.privateKey != null) {
                val name = "exoplayer_key_${System.currentTimeMillis()}"
                if (connectionInfo.passphrase != null) {
                    jsch.addIdentity(name, connectionInfo.privateKey.toByteArray(), null, connectionInfo.passphrase.toByteArray())
                } else {
                    jsch.addIdentity(name, connectionInfo.privateKey.toByteArray(), null, null)
                }
            }
            
            val session = jsch.getSession(connectionInfo.username, connectionInfo.host, connectionInfo.port)
            
            if (connectionInfo.privateKey != null) {
                // Private key auth
                if (connectionInfo.passphrase != null) {
                    session.userInfo = object : com.jcraft.jsch.UserInfo {
                        override fun getPassphrase(): String = connectionInfo.passphrase
                        override fun getPassword(): String? = null
                        override fun promptPassword(message: String?): Boolean = false
                        override fun promptPassphrase(message: String?): Boolean = true
                        override fun promptYesNo(message: String?): Boolean = true
                        override fun showMessage(message: String?) {}
                    }
                }
                val config = java.util.Properties()
                config["StrictHostKeyChecking"] = "no"
                config["PreferredAuthentications"] = "publickey"
                session.setConfig(config)
            } else {
                // Password auth
                session.setPassword(connectionInfo.password)
                session.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String? = null
                    override fun getPassword(): String = connectionInfo.password
                    override fun promptPassword(message: String?): Boolean = true
                    override fun promptPassphrase(message: String?): Boolean = false
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
                val config = java.util.Properties()
                config["StrictHostKeyChecking"] = "no"
                config["PreferredAuthentications"] = "keyboard-interactive,password"
                session.setConfig(config)
            }
            
            session.timeout = SOCKET_TIMEOUT
            session.connect(CONNECTION_TIMEOUT)
            
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECTION_TIMEOUT)
            
            // Add to pool
            val pooled = PooledConnection(
                session = session,
                jsch = jsch,
                channels = mutableListOf(channel),
                channelMutexes = mutableListOf(Mutex()),
                sessionMutex = Mutex()
            )
            connectionPool[key] = pooled
            
            Timber.d("SFTP ExoPlayer: New connection added to pool for ${connectionInfo.host}")
            return ExoPlayerConnection(session, channel)
            
        } catch (e: Exception) {
            connectionSemaphore.release()
            Timber.e(e, "SFTP ExoPlayer: Failed to get connection for ${connectionInfo.host}")
            throw IOException("Failed to establish SFTP connection: ${e.message}", e)
        }
    }

    /**
     * Release connection semaphore after ExoPlayer is done.
     * Call this in DataSource.close() to return connection slot to pool.
     */
    fun releaseExoPlayerConnection() {
        connectionSemaphore.release()
        cleanupIdleConnections()
    }

    /**
     * List files and directories in remote path
     * @param recursive If true, scans all subdirectories recursively
     */
    suspend fun listFiles(
        connectionInfo: SftpConnectionInfo,
        remotePath: String = "/",
        recursive: Boolean = true
    ): Result<List<String>> = withConnection(connectionInfo) { channel ->
        try {
            val allFiles = mutableListOf<String>()
            
            if (recursive) {
                listFilesRecursive(channel, remotePath, allFiles)
            } else {
                listFilesSingleLevel(channel, remotePath, allFiles)
            }
            
            Result.success(allFiles)
        } catch (e: Exception) {
            Timber.e(e, "SFTP list files failed: $remotePath")
            Result.failure(e)
        }
    }
    
    /**
     * List files in single directory level (non-recursive)
     */
    private fun listFilesSingleLevel(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<String>
    ) {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
        
        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }
                
                // Only add files, skip directories
                if (!entry.attrs.isDir) {
                    results.add(fullPath)
                }
            }
        }
    }
    
    /**
     * List files recursively in all subdirectories
     */
    private fun listFilesRecursive(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<String>
    ) {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
        
        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }
                
                if (entry.attrs.isDir) {
                    // Recursively scan subdirectory
                    listFilesRecursive(channel, fullPath, results)
                } else {
                    // Add file to results
                    results.add(fullPath)
                }
            }
        }
    }

    /**
     * Read file bytes from SFTP server
     */
    suspend fun readFileBytes(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        
        // First attempt
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
            invalidateConnection(key)
            
            withConnection(connectionInfo) { channel ->
                try {
                    val outputStream = java.io.ByteArrayOutputStream()
                    channel.get(remotePath).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Result.success(outputStream.toByteArray())
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
        length: Long
    ): Result<ByteArray> {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        
        // First attempt
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
                Timber.e(e, "SFTP read bytes range failed: $remotePath offset=$offset length=$length")
                Result.failure(e)
            }
        }
        
        // Retry with fresh connection if retriable error
        val exception = firstResult.exceptionOrNull()
        val shouldRetry = exception is IOException ||
                         (exception is SftpException && (exception.id == ChannelSftp.SSH_FX_FAILURE || exception.id == ChannelSftp.SSH_FX_BAD_MESSAGE))
        
        return if (firstResult.isFailure && shouldRetry) {
            Timber.d("SFTP: Invalidating connection and retrying readFileBytesRange: $remotePath")
            invalidateConnection(key)
            
            withConnection(connectionInfo) { channel ->
                try {
                    val buffer = ByteArray(length.toInt())
                    val inputStream = channel.get(remotePath)
                    
                    inputStream.use {
                        it.skip(offset)
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
                    Timber.e(e, "SFTP read bytes range failed (retry): $remotePath offset=$offset length=$length")
                    Result.failure(e)
                }
            }
        } else {
            firstResult
        }
    }

    /**
     * Download file from SFTP server to OutputStream
     */
    suspend fun downloadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        
        // First attempt
        val firstResult = withConnection(connectionInfo) { channel ->
            try {
                channel.get(remotePath).use { inputStream ->
                    if (progressCallback != null && fileSize > 0) {
                        inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
                    } else {
                        inputStream.copyTo(outputStream)
                    }
                }
                Result.success(Unit)
            } catch (e: IndexOutOfBoundsException) {
                Timber.w("SFTP downloadFile got IndexOutOfBoundsException, will retry with new connection")
                Result.failure(e)
            } catch (e: SftpException) {
                // SSH_FX_FAILURE (4) and SSH_FX_BAD_MESSAGE (5) often indicate corrupted channel state
                if (e.id == ChannelSftp.SSH_FX_FAILURE || e.id == ChannelSftp.SSH_FX_BAD_MESSAGE) {
                    Timber.w("SFTP downloadFile got SftpException ${e.id}, will retry with new connection")
                    Result.failure(e)
                } else {
                    Timber.e(e, "SFTP download file failed: $remotePath")
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "SFTP download file failed: $remotePath")
                Result.failure(e)
            }
        }
        
        // Retry with fresh connection if retriable error
        val exception = firstResult.exceptionOrNull()
        val shouldRetry = exception is IndexOutOfBoundsException || 
                         (exception is SftpException && (exception.id == ChannelSftp.SSH_FX_FAILURE || exception.id == ChannelSftp.SSH_FX_BAD_MESSAGE))
        
        return if (firstResult.isFailure && shouldRetry) {
            Timber.d("SFTP: Invalidating connection and retrying download: $remotePath")
            invalidateConnection(key)
            
            // Clear outputStream if possible (for ByteArrayOutputStream)
            if (outputStream is java.io.ByteArrayOutputStream) {
                outputStream.reset()
            }
            
            withConnection(connectionInfo) { channel ->
                try {
                    channel.get(remotePath).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.e(e, "SFTP download file failed (retry): $remotePath")
                    Result.failure(e)
                }
            }
        } else {
            firstResult
        }
    }

    /**
     * Upload file to SFTP server from byte array
     */
    suspend fun uploadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        data: ByteArray
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            // Ensure parent directory exists
            val parentDir = remotePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                ensureDirectoryExists(channel, parentDir)
            }
            
            data.inputStream().use { inputStream ->
                channel.put(inputStream, remotePath)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP upload file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Upload file to SFTP server from InputStream
     */
    suspend fun uploadFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String,
        inputStream: java.io.InputStream,
        fileSize: Long = 0,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            // Ensure parent directory exists
            val parentDir = remotePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                ensureDirectoryExists(channel, parentDir)
            }
            
            // Use OutputStream to support progress callback
            channel.put(remotePath).use { outputStream ->
                if (progressCallback != null && fileSize > 0) {
                    inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
                } else {
                    inputStream.copyTo(outputStream)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP upload file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Get file attributes
     */
    suspend fun stat(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<SftpFileAttributes> = withConnection(connectionInfo) { channel ->
        try {
            val attrs = channel.stat(remotePath)
            val attributes = SftpFileAttributes(
                size = attrs.size,
                modifiedDate = attrs.mTime * 1000L,
                accessDate = attrs.aTime * 1000L,
                isDirectory = attrs.isDir
            )
            Result.success(attributes)
        } catch (e: Exception) {
            Timber.e(e, "SFTP stat failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Check if path exists
     */
    suspend fun exists(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Boolean> = withConnection(connectionInfo) { channel ->
        try {
            try {
                channel.stat(remotePath)
                Result.success(true)
            } catch (e: com.jcraft.jsch.SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    Result.success(false)
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP exists check failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Create directory
     */
    suspend fun mkdir(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            channel.mkdir(remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP mkdir failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete file
     */
    suspend fun deleteFile(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            channel.rm(remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP delete file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete directory recursively
     */
    suspend fun deleteDirectory(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            // Helper function for recursion within the same channel
            fun deleteRecursive(path: String) {
                @Suppress("UNCHECKED_CAST")
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
        } catch (e: Exception) {
            Timber.e(e, "SFTP delete directory failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Rename/move file or directory
     */
    suspend fun rename(
        connectionInfo: SftpConnectionInfo,
        oldPath: String,
        newPath: String
    ): Result<Unit> = withConnection(connectionInfo) { channel ->
        try {
            channel.rename(oldPath, newPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP rename failed: $oldPath -> $newPath")
            Result.failure(e)
        }
    }

    /**
     * Rename file (convenience method)
     */
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
    suspend fun createDirectory(connectionInfo: SftpConnectionInfo, remotePath: String) = mkdir(connectionInfo, remotePath)
    suspend fun getFileAttributes(connectionInfo: SftpConnectionInfo, remotePath: String) = stat(connectionInfo, remotePath)

    /**
     * Disconnect all sessions (e.g. on app shutdown)
     */
    suspend fun disconnectAll() {
        poolMutex.lock()
        try {
            connectionPool.values.forEach { pooled ->
                try {
                    // Disconnect all channels
                    pooled.channels.forEach { channel ->
                        if (channel.isConnected) {
                            channel.disconnect()
                        }
                    }
                    pooled.session.disconnect()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            connectionPool.clear()
        } finally {
            poolMutex.unlock()
        }
    }

    /**
     * Test connection to SFTP server (stateless)
     */
    suspend fun testConnection(
        host: String,
        port: Int = 22,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        var testChannel: ChannelSftp? = null
        try {
            val testJsch = JSch()
            testSession = testJsch.getSession(username, host, port)
            testSession.setPassword(password)
            
            testSession.userInfo = object : com.jcraft.jsch.UserInfo {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = password
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = false
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            testSession.setConfig(config)
            
            testSession.timeout = CONNECTION_TIMEOUT
            testSession.connect(CONNECTION_TIMEOUT)
            
            testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(CONNECTION_TIMEOUT)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                testChannel?.disconnect()
                testSession?.disconnect()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Test connection with private key (stateless)
     */
    suspend fun testConnectionWithPrivateKey(
        host: String,
        port: Int = 22,
        username: String,
        privateKey: String,
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        var testChannel: ChannelSftp? = null
        try {
            val testJsch = JSch()
            if (passphrase != null) {
                testJsch.addIdentity("test_key", privateKey.toByteArray(), null, passphrase.toByteArray())
            } else {
                testJsch.addIdentity("test_key", privateKey.toByteArray(), null, null)
            }
            
            testSession = testJsch.getSession(username, host, port)
            
            if (passphrase != null) {
                testSession.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String = passphrase
                    override fun getPassword(): String? = null
                    override fun promptPassword(message: String?): Boolean = false
                    override fun promptPassphrase(message: String?): Boolean = true
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
            }
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "publickey"
            testSession.setConfig(config)
            
            testSession.timeout = CONNECTION_TIMEOUT
            testSession.connect(CONNECTION_TIMEOUT)
            
            testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(CONNECTION_TIMEOUT)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                testChannel?.disconnect()
                testSession?.disconnect()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Ensure directory exists, create if missing (recursive)
     */
    private fun ensureDirectoryExists(channel: ChannelSftp, remotePath: String) {
        try {
            // Try to change to directory - if exists, this succeeds
            channel.cd(remotePath)
            return
        } catch (e: Exception) {
            // Directory doesn't exist - create parent first
            val parent = remotePath.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                ensureDirectoryExists(channel, parent)
            }
            
            // Create this directory
            try {
                channel.mkdir(remotePath)
                Timber.d("Created SFTP directory: $remotePath")
            } catch (mkdirEx: Exception) {
                // Directory might exist now (race condition) - check
                try {
                    channel.cd(remotePath)
                } catch (cdEx: Exception) {
                    throw mkdirEx // Re-throw original mkdir exception
                }
            }
        }
    }

    /**
     * Open InputStream for reading file from SFTP.
     * Opens a NEW channel for the stream to ensure thread safety.
     * Caller is responsible for closing the stream.
     */
    suspend fun openInputStream(
        connectionInfo: SftpConnectionInfo,
        remotePath: String
    ): Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        try {
            // Acquire permit to ensure we don't exceed max connections
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateConnection(key, connectionInfo)
                pooled.lastUsed = System.currentTimeMillis()
                
                // Verify session is still alive
                if (!pooled.session.isConnected) {
                    Timber.w("SFTP session disconnected, recreating")
                    connectionPool.remove(key)
                    val newPooled = getOrCreateConnection(key, connectionInfo)
                    pooled.session.disconnect()
                    // Continue with new session below
                    newPooled.lastUsed = System.currentTimeMillis()
                }
                
                // Open a NEW channel for this stream
                val channel = pooled.session.openChannel("sftp") as ChannelSftp
                
                // Always connect channel (channels are not reused)
                if (!channel.isConnected) {
                    try {
                        channel.connect(CONNECTION_TIMEOUT)
                    } catch (e: com.jcraft.jsch.JSchException) {
                        // Channel connection failed, try to recreate session
                        Timber.w(e, "SFTP channel connection failed, recreating session")
                        connectionPool.remove(key)
                        pooled.session.disconnect()
                        
                        // Retry with fresh connection
                        val newPooled = getOrCreateConnection(key, connectionInfo)
                        val newChannel = newPooled.session.openChannel("sftp") as ChannelSftp
                        newChannel.connect(CONNECTION_TIMEOUT)
                        
                        // Build wrapper for new channel
                        val stream = newChannel.get(remotePath)
                        val wrapper = object : java.io.FilterInputStream(stream) {
                            override fun close() {
                                try {
                                    super.close()
                                } finally {
                                    try {
                                        newChannel.disconnect()
                                    } catch (e: Exception) {
                                        Timber.w("Error closing SFTP stream channel: ${e.message}")
                                    }
                                }
                            }
                        }
                        return@withContext Result.success(wrapper)
                    }
                }
                
                try {
                    val stream = channel.get(remotePath)
                    
                    // Return wrapper that disconnects channel
                    val wrapper = object : java.io.FilterInputStream(stream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                try {
                                    channel.disconnect()
                                } catch (e: Exception) {
                                    Timber.w("Error closing SFTP stream channel: ${e.message}")
                                }
                            }
                        }
                    }
                    Result.success(wrapper)
                } catch (e: Exception) {
                    channel.disconnect()
                    throw e
                }
            } finally {
                connectionSemaphore.release()
                cleanupIdleConnections()
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP openInputStream failed: $remotePath")
            Result.failure(e)
        }
    }
    
    /**
     * Create directory on SFTP server
     */

}
