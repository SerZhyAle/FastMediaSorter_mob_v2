package com.sza.fastmediasorter.wear.data.repository

import android.net.Uri
import com.sza.fastmediasorter.wear.data.network.WearNetworkDataSources
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkFolderRepository
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/** One window of a level, matching the local walk so the two surfaces scroll alike. */
private const val PAGE_SIZE = 50

/**
 * S2694: the network half of the folder walk.
 *
 * The only place that knows a network level exists. It resolves the source, asks that protocol's
 * data source for one directory listing, and turns each row into the walk's own entry - a directory
 * carrying an address and no uri, a file carrying a uri and no address, which is
 * [WearFolderEntry]'s stated invariant.
 */
class WearNetworkFolderRepositoryImpl @Inject constructor(
    private val sourceRepository: NetworkSourceRepository,
    private val dataSources: WearNetworkDataSources
) : WearNetworkFolderRepository {

    override suspend fun listLevel(
        address: WearFolderAddress.NetworkLevel,
        offset: Int
    ): Result<WearFolderPage> = withContext(Dispatchers.IO) {
        try {
            Timber.d("S2694: listing network level '%s' of source %s", address.path, address.sourceId)
            val source = sourceRepository.getSourceById(address.sourceId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Network source ${address.sourceId} is gone")
                )
            val entries = listEntries(source, address.path).getOrElse { error ->
                Timber.w(error, "Network level unreadable: %s", address.path)
                return@withContext Result.failure(error)
            }
            Result.success(window(entries.map { toFolderEntry(source, address, it) }, offset))
        } catch (e: CancellationException) {
            // A cancelled walk is the wearer leaving the screen, not a level that failed to read.
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Network level unreadable: %s", address.path)
            Result.failure(e)
        }
    }

    /**
     * SMB is connected before it is listed because its data source holds the session; the other two
     * open and close one per call. A failed connect is returned rather than thrown so the caller
     * keeps its trail.
     */
    private suspend fun listEntries(source: NetworkSource, path: String): Result<List<WearNetworkEntry>> =
        when (source.type) {
            NetworkSourceType.SMB -> dataSources.smb.connect(source)
                .mapCatching { dataSources.smb.listEntries(path).getOrThrow() }

            NetworkSourceType.FTP -> runCatching { dataSources.ftp.listEntries(source, path) }
            NetworkSourceType.SFTP -> runCatching { dataSources.sftp.listEntries(source, path) }
        }

    private fun toFolderEntry(
        source: NetworkSource,
        parent: WearFolderAddress.NetworkLevel,
        entry: WearNetworkEntry
    ): WearFolderEntry = WearFolderEntry(
        name = entry.name,
        address = if (entry.isDirectory) {
            WearFolderAddress.NetworkLevel(sourceId = parent.sourceId, path = entry.path)
        } else {
            null
        },
        uri = if (entry.isDirectory) null else Uri.parse(uriFor(source, entry.path)),
        isDirectory = entry.isDirectory,
        mimeType = if (entry.isDirectory) null else MediaMimeTypes.fromFileName(entry.name),
        sizeBytes = entry.sizeBytes,
        dateModifiedEpochSeconds = entry.dateModifiedEpochMillis / MILLIS_PER_SECOND
    )

    /**
     * The address a player is handed for a file.
     *
     * SMB keeps the share-relative form the rest of the watch's SMB path already uses; the other two
     * carry host and port, matching what their flat listing produces for the same file.
     */
    private fun uriFor(source: NetworkSource, path: String): String = when (source.type) {
        NetworkSourceType.SMB -> path
        NetworkSourceType.FTP -> "ftp://${source.server}:${source.port}$path"
        NetworkSourceType.SFTP -> "sftp://${source.server}:${source.port}$path"
    }

    private fun window(entries: List<WearFolderEntry>, offset: Int): WearFolderPage {
        val page = entries.drop(offset).take(PAGE_SIZE)
        val consumed = offset + page.size
        return WearFolderPage(entries = page, nextOffset = consumed.takeIf { it < entries.size })
    }

    private companion object {
        /** The listing carries milliseconds; the walk entry is declared in seconds. */
        const val MILLIS_PER_SECOND = 1000L
    }
}
