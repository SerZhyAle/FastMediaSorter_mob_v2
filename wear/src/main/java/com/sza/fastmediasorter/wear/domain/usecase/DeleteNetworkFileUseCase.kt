package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.util.NetworkUriParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Removes a browsed network file from the share it lives on.
 *
 * The mirror image of [DownloadNetworkFileUseCase] and routed the same way for the same reason
 * (S1687): which protocol serves a file is decided in one place, never at the call site. Keeping the
 * two apart rather than adding a verb to the download is what keeps the destructive half namable - a
 * caller reaches it by asking for it, not by passing a flag to a reader.
 *
 * Every refusal comes back as a failed [Result]: a server that would not let go of the file is an
 * ordinary answer on a read-only account, and the move above this reports it as "the copy was kept".
 */
class DeleteNetworkFileUseCase @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    private val smbDataSource: SmbDataSource,
    private val ftpDataSource: FtpDataSource,
    private val sftpDataSource: SftpDataSource
) {

    /**
     * [streamUri] is the address the listing handed out, in the same shape the download reads from:
     * share-relative for SMB, a full `ftp://` or `sftp://` URI for the other two.
     */
    suspend operator fun invoke(sourceId: String?, streamUri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            resolveSource(sourceId)
                .mapCatching { source -> deleteFrom(source, streamUri).getOrThrow() }
                .onFailure { Timber.w(it, "Failed to delete network file: $streamUri") }
        }

    private suspend fun resolveSource(sourceId: String?): Result<NetworkSource> {
        val source = sourceId?.let { networkSourceRepository.getSourceById(it) }
        return when {
            sourceId == null ->
                Result.failure(IllegalStateException("No network source recorded for this file"))
            source == null ->
                Result.failure(IllegalStateException("Network source is no longer configured"))
            else -> Result.success(source)
        }
    }

    private suspend fun deleteFrom(source: NetworkSource, streamUri: String): Result<Unit> =
        when (source.type) {
            NetworkSourceType.SMB -> deleteOverSmb(source, streamUri)
            NetworkSourceType.FTP ->
                ftpDataSource.deleteFile(source, NetworkUriParser.remotePathOf(streamUri))
            NetworkSourceType.SFTP ->
                sftpDataSource.deleteFile(source, NetworkUriParser.remotePathOf(streamUri))
        }

    /**
     * SMB holds one connected client for the whole session, so the removal reconnects the same way a
     * read does: the browse screen's connect normally supplied the credentials, and a player that
     * outlived that connection is the case this covers.
     */
    private suspend fun deleteOverSmb(source: NetworkSource, streamUri: String): Result<Unit> {
        val connected = if (smbDataSource.isConnected()) {
            Result.success(Unit)
        } else {
            smbDataSource.connect(source)
        }
        return connected.mapCatching { smbDataSource.deleteFile(streamUri).getOrThrow() }
    }
}
