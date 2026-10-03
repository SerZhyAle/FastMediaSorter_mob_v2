package com.sza.fastmediasorter.wear.data.network.smb

import androidx.annotation.WorkerThread
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
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
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.PARENT_ENTRY
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.SELF_ENTRY
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

private const val SMB_TIMEOUT_SECONDS = 30L

/** One open connection, its authenticated session and the share on it, closed as one unit. */
internal interface SmbLink : Closeable {
    val share: DiskShare?
    val isAlive: Boolean

    @WorkerThread
    override fun close()
}

/** The smbj seam: the lock discipline of [SmbDataSource] is testable only with the socket behind it. */
internal fun interface SmbLinkOpener {
    suspend fun open(source: NetworkSource): SmbLink
}

/**
 * SMB data source for accessing files on SMB/CIFS network shares.
 * Uses SMBJ library for SMB protocol communication.
 */
class SmbDataSource internal constructor(
    private val endpointResolver: WearEndpointResolver,
    private val linkOpener: SmbLinkOpener
) {

    constructor(endpointResolver: WearEndpointResolver) : this(endpointResolver, SmbjLinkOpener())

    /**
     * S3830: this instance is a singleton shared by browse, the audio player and the thumbnails.
     * Unserialized, two callers that both found the link dead both reconnected, each reconnect first
     * closed the share the other was reading, and the losing connection was overwritten unclosed.
     * Every write of [link] and [currentSource] goes through this lock; [isConnected] only reads.
     */
    private val linkMutex = Mutex()

    @Volatile
    private var link: SmbLink? = null

    private var currentSource: NetworkSource? = null

    /**
     * Connect to SMB server and authenticate.
     */
    suspend fun connect(sourceIn: NetworkSource): Result<Unit> = withContext(Dispatchers.IO) {
        linkMutex.withLock { connectLocked(sourceIn) }
    }

    private suspend fun connectLocked(sourceIn: NetworkSource): Result<Unit> = try {
        // S2488: SMB carries no imported alternates today, so the group is one element and the
        // source comes back untouched - the wiring is what lets a future group work.
        val source = endpointResolver.resolve(sourceIn)
        Timber.d("Connecting to SMB: ${source.server}:${source.port}")
        closeLinkLocked()
        currentSource = source
        link = linkOpener.open(source)
        Result.success(Unit)
    } catch (e: Exception) {
        e.errorUnlessCancellation("Failed to connect to SMB")
        closeLinkLocked()
        Result.failure(e)
    }

    /**
     * The share to read through, reconnecting first when the link is dead. Returned rather than read
     * from the field afterwards, so the caller keeps the share this call checked even when another
     * caller reconnects right after it.
     */
    private suspend fun ensureShare(): Result<DiskShare> = withContext(Dispatchers.IO) {
        linkMutex.withLock {
            val liveShare = link?.takeIf { isAliveQuietly(it) }?.share
            if (liveShare != null) Result.success(liveShare) else reconnectLocked()
        }
    }

    private suspend fun reconnectLocked(): Result<DiskShare> {
        val source = currentSource ?: run {
            Timber.e("Cannot reconnect - no stored connection parameters")
            return Result.failure(IllegalStateException("Not connected to share"))
        }
        Timber.d("SMB connection lost, reconnecting..")
        val connected = connectLocked(source)
        val share = link?.share
        return when {
            connected.isFailure -> Result.failure(
                connected.exceptionOrNull() ?: IllegalStateException("Connection failed")
            )
            share == null -> Result.failure(IllegalStateException("Not connected to share"))
            else -> Result.success(share)
        }
    }

    private fun isAliveQuietly(open: SmbLink): Boolean = try {
        open.isAlive
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        false
    }

    /**
     * Disconnect from SMB server.
     */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            linkMutex.withLock { closeLinkLocked() }
        }
    }

    private fun closeLinkLocked() {
        val open = link ?: return
        link = null
        try {
            open.close()
        } catch (e: Exception) {
            e.errorUnlessCancellation("Error disconnecting from SMB")
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
        val modifiedTime: Long,
        // S2694: read from the listing record's attribute bits. A name heuristic was refused there -
        // it calls an extension-less file a directory and a dotted directory a file, silently.
        val isDirectory: Boolean = false
    )

    /**
     * List files in directory.
     *
     * @param path Path relative to share root
     * @return Name, size and modified time of every entry
     */
    suspend fun listFiles(path: String): Result<List<SmbEntry>> = withContext(Dispatchers.IO) {
        val currentShare = ensureShare().getOrElse { return@withContext Result.failure(it) }

        try {
            val cleanPath = path.trim('/').replace('/', '\\')
            Timber.d("Listing files in: $cleanPath")

            val files = currentShare.list(cleanPath).map { fileInfo ->
                SmbEntry(
                    name = fileInfo.fileName,
                    size = fileInfo.endOfFile,
                    modifiedTime = fileInfo.lastWriteTime.toEpochMillis(),
                    isDirectory = fileInfo.fileAttributes and
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
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
     * S2694: the same listing as [listFiles], in the protocol-neutral shape the folder walk consumes.
     *
     * The self and parent entries are dropped here rather than in [listFiles]: a walk that showed
     * them would offer the wearer a row leading to the level already on screen, while the flat
     * listing never displayed them anyway - neither carries a mime type the listing filter can place.
     *
     * @param path Path relative to share root; the empty string is the share root
     */
    suspend fun listEntries(path: String): Result<List<WearNetworkEntry>> =
        listFiles(path).map { entries -> toNetworkEntries(path, entries) }

    /**
     * The pure half of [listEntries], separated so the join and the flag can be tested without a
     * share. Its subject is a mapping, and a mapping that needs a socket to be checked is a mapping
     * nothing checks.
     */
    internal fun toNetworkEntries(path: String, entries: List<SmbEntry>): List<WearNetworkEntry> {
        val parent = path.trim('/')
        return entries
            .filterNot { it.name == SELF_ENTRY || it.name == PARENT_ENTRY }
            .map { entry ->
                WearNetworkEntry(
                    name = entry.name,
                    path = if (parent.isEmpty()) entry.name else "$parent/${entry.name}",
                    isDirectory = entry.isDirectory,
                    sizeBytes = entry.size,
                    dateModifiedEpochMillis = entry.modifiedTime
                )
            }
    }

    /**
     * Get input stream for file.
     *
     * @param path Path to file relative to share root
     * @return InputStream for reading file content
     */
    suspend fun getFileStream(path: String): Result<InputStream> = handingOffCloseable { handOff ->
        withContext(Dispatchers.IO) {
            val currentShare = ensureShare().getOrElse { return@withContext Result.failure(it) }

            try {
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

                Result.success(handOff.track(inputStream))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to open file stream")
                Result.failure(e)
            }
        }
    }

    /**
     * Remove a file from the share.
     *
     * S3359: the one verb that destroys something on the server, so it never opens a connection of its
     * own - it reuses the session [getFileStream] reads through, and a share that refuses the removal
     * comes back as a failure rather than an exception the caller has to read a stack trace for.
     *
     * @param path Path to file relative to share root
     */
    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureShare().fold(
            onSuccess = { currentShare -> removeFromShare(currentShare, path) },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * The broad catch mirrors [getFileStream]: smbj reports every server-side refusal as an unchecked
     * `SMBApiException`, so the type that reaches here is the library's and not a set this class can name.
     */
    private fun removeFromShare(currentShare: DiskShare, path: String): Result<Unit> = try {
        val cleanPath = path.trim('/').trim('\\')
        currentShare.rm(cleanPath)
        Timber.d("Deleted SMB file: $cleanPath")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Failed to delete SMB file")
        Result.failure(e)
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean = link?.let(::isAliveQuietly) == true
}

private class SmbjLinkOpener : SmbLinkOpener {

    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    )

    /** A half-open link is closed here: nothing else holds its connection to close it later. */
    override suspend fun open(source: NetworkSource): SmbLink = handingOffCloseable { handOff ->
        withContext(Dispatchers.IO) {
            val connection = client.connect(source.server, source.port)
            // finally rather than a catch-all: every failure - cancellation included - still closes the
            // half-open connection and propagates untouched, with no catch arm that could swallow it.
            var linked = false
            try {
                // A null domain is the workgroup login.
                val session = connection.authenticate(
                    AuthenticationContext(source.username, source.password.toCharArray(), null)
                )
                val share = source.shareName?.let { name -> session.connectShare(name) as? DiskShare }
                if (share != null) Timber.d("Connected to share: ${source.shareName}")
                handOff.track(SmbjLink(connection, session, share)).also { linked = true }
            } finally {
                if (!linked) {
                    runCatching { connection.close() }
                        .onFailure { closeError -> Timber.w(closeError, "Failed to close a half-open SMB connection") }
                }
            }
        }
    }
}

private class SmbjLink(
    private val connection: Connection,
    private val session: Session,
    override val share: DiskShare?
) : SmbLink {

    override val isAlive: Boolean
        get() = connection.isConnected && share != null

    @WorkerThread
    override fun close() {
        try {
            share?.close()
            session.close()
        } finally {
            connection.close()
        }
    }
}
