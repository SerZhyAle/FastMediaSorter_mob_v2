package com.sza.fastmediasorter.wear.data.network.ftp

import android.net.Network
import android.net.Uri
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.files.WearFileNameConflictResolver
import com.sza.fastmediasorter.wear.domain.files.WearRemoteFileNames
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.PARENT_ENTRY
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.SELF_ENTRY
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import javax.inject.Inject

/**
 * S4230: the server answered a store or a rename with a negative reply. The code is kept as a field and not read
 * back out of the message, so the failure classifier maps it without parsing text.
 */
internal class FtpStoreRefusedException(val replyCode: Int) : IOException("FTP refused the store (code=$replyCode)")

/** How long a send waits on a silent server, for connect, control replies and the data connection alike. */
private const val TRANSFER_TIMEOUT_MS = 30_000

class FtpDataSource @Inject constructor(
    private val endpointResolver: WearEndpointResolver
) {

    suspend fun listDirectory(sourceIn: NetworkSource, path: String): List<WearMediaFile> {
        // S2694: resolved once here and handed down, never resolved again inside the listing. Two
        // resolutions of the same call could pick different endpoints of an address group, and the
        // uri below would then name a host the listing did not come from.
        val source = endpointResolver.resolve(sourceIn)
        return listEntriesResolved(source, path).mapIndexed { index, entry ->
            WearMediaFile(
                id = index.toLong(),
                name = entry.name,
                uri = Uri.parse("ftp://${source.server}:${source.port}${entry.path}"),
                mimeType = MediaMimeTypes.fromFileName(entry.name),
                size = entry.sizeBytes,
                dateModified = entry.dateModifiedEpochMillis
            )
        }
    }

    /**
     * S2694: the directory listing with the directory flag kept, which [listDirectory] then flattens.
     *
     * @param path Absolute server path
     */
    suspend fun listEntries(sourceIn: NetworkSource, path: String): List<WearNetworkEntry> =
        // S2488: FTP carries no imported alternates today, so the group is one element and this
        // returns the source untouched - the wiring is what lets a future group work.
        listEntriesResolved(endpointResolver.resolve(sourceIn), path)

    /**
     * The pure half of [listEntries], separated so the join and the flag can be tested without a
     * server. Its subject is a mapping, and a mapping that needs a socket to be checked is a mapping
     * nothing checks.
     */
    internal fun toNetworkEntries(path: String, files: List<FTPFile>): List<WearNetworkEntry> {
        val parent = path.trimEnd('/')
        return files
            .filterNot { it.name == SELF_ENTRY || it.name == PARENT_ENTRY }
            .map { ftpFile ->
                WearNetworkEntry(
                    name = ftpFile.name,
                    path = "$parent/${ftpFile.name}",
                    isDirectory = ftpFile.isDirectory,
                    sizeBytes = ftpFile.size,
                    dateModifiedEpochMillis = ftpFile.timestamp?.timeInMillis ?: 0L
                )
            }
    }

    private suspend fun listEntriesResolved(source: NetworkSource, path: String): List<WearNetworkEntry> =
        withContext(Dispatchers.IO) {
            val client = FTPClient()
            try {
                openSession(client, source)

                val files = client.listFiles(path)
                    ?: error("FTP listFiles returned null for path=$path")
                toNetworkEntries(path, files.toList())
            } finally {
                runCatching { if (client.isConnected) client.disconnect() }
            }
        }

    /**
     * Open a file for reading. The caller owns the returned stream and closes it; the control
     * connection is torn down with it.
     */
    suspend fun getFileStream(source: NetworkSource, path: String): Result<InputStream> =
        handingOffCloseable { handOff ->
            withContext(Dispatchers.IO) {
                val client = FTPClient()
                try {
                    openSession(client, source)
                    // S1687: without binary mode commons-net transfers in ASCII and silently corrupts
                    // every media file it downloads.
                    client.setFileType(FTP.BINARY_FILE_TYPE)
                    Timber.d("Opening FTP file: $path")

                    val stream = client.retrieveFileStream(path)
                        ?: error("FTP retrieveFileStream returned null for path=$path (code=${client.replyCode})")

                    Result.success(handOff.track(streamClosingClient(stream, client, path)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    failStream(e, client, path)
                } catch (e: IllegalStateException) {
                    failStream(e, client, path)
                }
            }
        }

    /**
     * Remove a file from the server.
     *
     * S3359: a session of its own, opened and torn down inside this call, because unlike a download
     * nothing survives it that the caller could close. A server that answers "no" - a read-only
     * account, a locked file - returns a failure rather than reporting a removal that never happened.
     */
    suspend fun deleteFile(source: NetworkSource, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = FTPClient()
            try {
                openSession(client, source)
                Timber.d("Deleting FTP file: $path")
                if (client.deleteFile(path)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("FTP refused to delete path=$path (code=${client.replyCode})"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.w(e, "Failed to delete FTP file for path=$path")
                Result.failure(e)
            } finally {
                runCatching { client.logout() }
                runCatching { if (client.isConnected) client.disconnect() }
            }
        }

    /**
     * S4230: store [input] in [directory] and return the name it ended up with.
     *
     * Binary mode first, or commons-net corrupts every media file in ASCII; the bytes go under a partial name and
     * are renamed only after the server confirmed the whole transfer, and a name the folder already holds is
     * replaced by a free one so nothing the watch did not create is overwritten. The control and data connections
     * both ride the granted [network].
     */
    suspend fun storeFile(
        source: NetworkSource,
        directory: String,
        intendedName: String,
        input: InputStream,
        network: Network? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = WearRemoteFileNames.newToken()
        val partial = WearRemoteFileNames.joinRemote(directory, WearRemoteFileNames.partialName(token))
        val client = FTPClient()
        // The listing path keeps the client's own defaults, but a send the wearer is waiting on must give up:
        // an unreachable host and a data connection that stalls would otherwise hold the radio indefinitely.
        client.connectTimeout = TRANSFER_TIMEOUT_MS
        client.setDataTimeout(Duration.ofMillis(TRANSFER_TIMEOUT_MS.toLong()))
        var renamed = false
        try {
            client.openFtpSession(endpointResolver.resolve(source, network), network)
            client.soTimeout = TRANSFER_TIMEOUT_MS
            client.setFileType(FTP.BINARY_FILE_TYPE)
            val finalName = freeNameIn(client, directory, intendedName)
            if (!client.storeFile(partial, input)) {
                throw FtpStoreRefusedException(client.replyCode)
            }
            if (!client.rename(partial, WearRemoteFileNames.joinRemote(directory, finalName))) {
                throw FtpStoreRefusedException(client.replyCode)
            }
            renamed = true
            Result.success(finalName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Failed to store FTP file")
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Failed to store FTP file")
            Result.failure(e)
        } finally {
            if (!renamed) runCatching { client.dele(partial) }
            runCatching { client.logout() }
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun freeNameIn(client: FTPClient, directory: String, intendedName: String): String {
        val names = client.listNames(directory).orEmpty().map { it.substringAfterLast('/') }.toSet()
        return WearFileNameConflictResolver.resolveAmong(names, intendedName).first
    }

    private fun failStream(
        cause: Exception,
        client: FTPClient,
        path: String
    ): Result<InputStream> {
        Timber.e(cause, "Failed to open FTP file stream for path=$path")
        runCatching { if (client.isConnected) client.disconnect() }
        return Result.failure(cause)
    }

    private fun openSession(client: FTPClient, source: NetworkSource) = client.openFtpSession(source)

    /**
     * The control connection has to outlive the data stream: commons-net only finishes the transfer
     * when completePendingCommand runs after the caller has closed the stream, and a client
     * disconnected any earlier truncates the download.
     */
    private fun streamClosingClient(
        stream: InputStream,
        client: FTPClient,
        path: String
    ): InputStream = object : FilterInputStream(stream) {
        override fun close() {
            try {
                super.close()
                if (!client.completePendingCommand()) {
                    Timber.w("FTP transfer did not complete cleanly for path=$path")
                }
            } finally {
                runCatching { client.logout() }
                runCatching { if (client.isConnected) client.disconnect() }
            }
        }
    }
}
