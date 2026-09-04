package com.sza.fastmediasorter.wear.data.network.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB data source for accessing files on SMB/CIFS network shares.
 * Uses SMBJ library for SMB protocol communication.
 */
class SmbDataSource(
    private val endpointResolver: WearEndpointResolver
) {

    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    // Store connection parameters for reconnection
    private var currentSource: NetworkSource? = null

    private val config = SmbConfig.builder()
        .withTimeout(30, TimeUnit.SECONDS)
        .withSoTimeout(30, TimeUnit.SECONDS)
        .build()

    private val client = SMBClient(config)

    /**
     * Connect to SMB server and authenticate.
     */
    suspend fun connect(sourceIn: NetworkSource): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // S2488: SMB carries no imported alternates today, so the group is one element and the
                // source comes back untouched - the wiring is what lets a future group work.
                val source = endpointResolver.resolve(sourceIn)
                Timber.d("Connecting to SMB: ${source.server}:${source.port}")

                // Disconnect if already connected
                disconnect()

                // Store source for reconnection
                currentSource = source

                // Establish connection
                connection = client.connect(source.server, source.port)

                // Authenticate
                val authContext = AuthenticationContext(
                    source.username,
                    source.password.toCharArray(),
                    null // Domain (null for workgroup)
                )

                session = connection?.authenticate(authContext)

                // Connect to share
                if (source.shareName != null) {
                    share = session?.connectShare(source.shareName) as? DiskShare
                    Timber.d("Connected to share: ${source.shareName}")
                }

                Result.success(Unit)
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to connect to SMB")
                disconnect()
                Result.failure(e)
            }
        }
    }

    /**
     * Ensure connection is alive, reconnect if needed.
     */
    private suspend fun ensureConnected(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Check if connection is still alive
            val isAlive = try {
                connection?.isConnected == true && share != null
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                false
            }

            if (isAlive) {
                Timber.d("SMB connection is alive")
                return@withContext Result.success(Unit)
            }

            // Need to reconnect
            val source = currentSource
            if (source == null) {
                Timber.e("Cannot reconnect - no stored connection parameters")
                return@withContext Result.failure(IllegalStateException("Not connected to share"))
            }

            Timber.d("SMB connection lost, reconnecting...")
            connect(source)
        }
    }

    /**
     * Disconnect from SMB server.
     */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                share?.close()
                session?.close()
                connection?.close()
            } catch (e: Exception) {
                e.errorUnlessCancellation("Error disconnecting from SMB")
            } finally {
                share = null
                session = null
                connection = null
            }
        }
    }

    /**
     * One entry of an SMB directory listing.
     *
     * S1811: size and modified time come free with `DiskShare.list(..)` - the listing used to be
     * mapped down to bare names, so the watch showed "0 B" under every SMB image while the same
     * file over FTP or SFTP showed its real size. Asking per file instead would be one network
     * round trip per entry for data that already arrived.
     */
    data class SmbEntry(
        val name: String,
        val size: Long,
        val modifiedTime: Long
    )

    /**
     * List files in directory.
     *
     * @param path Path relative to share root
     * @return Name, size and modified time of every entry
     */
    suspend fun listFiles(path: String): Result<List<SmbEntry>> = withContext(Dispatchers.IO) {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return@withContext Result.failure(
                connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed")
            )
        }

        try {
            val currentShare = share ?: return@withContext Result.failure(
                IllegalStateException("Not connected to share")
            )

            val cleanPath = path.trim('/').replace('/', '\\')
            Timber.d("Listing files in: $cleanPath")

            val files = currentShare.list(cleanPath).map { fileInfo ->
                SmbEntry(
                    name = fileInfo.fileName,
                    size = fileInfo.endOfFile,
                    modifiedTime = fileInfo.lastWriteTime.toEpochMillis()
                )
            }

            Result.success(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to list files")
            Result.failure(e)
        }
    }

    /**
     * Get input stream for file.
     *
     * @param path Path to file relative to share root
     * @return InputStream for reading file content
     */
    suspend fun getFileStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return@withContext Result.failure(
                connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed")
            )
        }

        try {
            val currentShare = share ?: return@withContext Result.failure(
                IllegalStateException("Not connected to share")
            )

            val cleanPath = path.trim('/').trim('\\')
            Timber.d("Opening file: $cleanPath")

            // Open file with read access using proper SMBJ API
            val file = currentShare.openFile(
                cleanPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            // S1304: closing only the stream leaked the smbj File handle - one open SMB2 handle per
            // viewed media file, held by the server until the session died. Tie the handle's
            // lifetime to the stream the caller actually closes.
            val inputStream = object : FilterInputStream(file.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { file.close() }
                            .onFailure { Timber.w(it, "Failed to close SMB file handle for $cleanPath") }
                    }
                }
            }

            Result.success(inputStream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file stream")
            Result.failure(e)
        }
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return connection?.isConnected == true && share != null
    }
}
