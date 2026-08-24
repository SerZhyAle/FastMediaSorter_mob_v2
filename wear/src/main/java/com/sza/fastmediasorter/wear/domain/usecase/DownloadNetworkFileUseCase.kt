package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.util.MediaCacheEvictor
import com.sza.fastmediasorter.wear.util.NetworkUriParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

private const val BYTES_PER_MB = 1024L * 1024L
private const val AUDIO_CACHE_CAP_MB = 100L
private const val VIDEO_CACHE_CAP_MB = 300L
private const val IMAGE_CACHE_CAP_MB = 50L

private val LEGACY_CACHE_DIR_NAMES = listOf("smb_audio", "smb_video", "smb_images")

/**
 * Downloads a browsed network file into a bounded cache dir and hands back the local copy.
 *
 * S1687: this is the single place that decides which protocol a network file is read over. Each of
 * the three player view models used to decide for itself and every one of them called SMB, so a file
 * browsed over FTP or SFTP listed correctly and then failed to open. Routing lives here, once,
 * because a decision duplicated three times is a decision that gets fixed in one of them.
 */
class DownloadNetworkFileUseCase @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    private val smbDataSource: SmbDataSource,
    private val ftpDataSource: FtpDataSource,
    private val sftpDataSource: SftpDataSource,
    @ApplicationContext private val context: Context
) {

    /** Media kind being downloaded - fixes the cache dir and how much of it may be kept. */
    enum class Kind(val cacheDirName: String, val capBytes: Long) {
        AUDIO("net_audio", AUDIO_CACHE_CAP_MB * BYTES_PER_MB),
        VIDEO("net_video", VIDEO_CACHE_CAP_MB * BYTES_PER_MB),
        IMAGE("net_images", IMAGE_CACHE_CAP_MB * BYTES_PER_MB)
    }

    suspend operator fun invoke(selected: SelectedMedia, kind: Kind): Result<File> =
        withContext(Dispatchers.IO) {
            resolveSource(selected.sourceId)
                .mapCatching { source -> openStream(source, selected.streamUri).getOrThrow() }
                .mapCatching { stream -> cacheStream(stream, selected, kind) }
                .onFailure { Timber.e(it, "Failed to download network file: ${selected.file.name}") }
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

    private suspend fun openStream(source: NetworkSource, streamUri: String): Result<InputStream> {
        return when (source.type) {
            NetworkSourceType.SMB -> openSmbStream(source, streamUri)
            NetworkSourceType.FTP -> ftpDataSource.getFileStream(source, NetworkUriParser.remotePathOf(streamUri))
            NetworkSourceType.SFTP -> sftpDataSource.getFileStream(source, NetworkUriParser.remotePathOf(streamUri))
            NetworkSourceType.GOOGLE_DRIVE -> Result.failure(
                UnsupportedOperationException("Google Drive playback is not available on Wear")
            )
        }
    }

    /**
     * SMB holds one connected client for the whole session and its paths are share-relative, so the
     * browse screen's connect normally supplies the credentials. Reconnecting here covers the player
     * that outlived that connection, which is what the captured stack trace died on.
     */
    private suspend fun openSmbStream(
        source: NetworkSource,
        streamUri: String
    ): Result<InputStream> {
        val connected = if (smbDataSource.isConnected()) {
            Result.success(Unit)
        } else {
            smbDataSource.connect(source)
        }
        return connected.mapCatching { smbDataSource.getFileStream(streamUri).getOrThrow() }
    }

    private fun cacheStream(stream: InputStream, selected: SelectedMedia, kind: Kind): File {
        purgeLegacyCacheDirs()

        val cacheDir = File(context.cacheDir, kind.cacheDirName)
        cacheDir.mkdirs()
        val tempFile = File(cacheDir, "${selected.streamUri.hashCode()}_${selected.file.name}")

        stream.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        Timber.d("Network file cached: ${tempFile.name}, ${tempFile.length()} bytes")

        MediaCacheEvictor.evictOldestUntilUnderCap(cacheDir, tempFile, kind.capBytes)
        return tempFile
    }

    /**
     * The per-protocol dirs replaced SMB-only ones. Without this the retired dirs stay on watch
     * storage for good, outside every cap, since nothing writes to them any more.
     */
    private fun purgeLegacyCacheDirs() {
        LEGACY_CACHE_DIR_NAMES
            .map { File(context.cacheDir, it) }
            .filter { it.exists() }
            .forEach { dir ->
                if (!dir.deleteRecursively()) {
                    Timber.w("Failed to delete legacy cache dir ${dir.name}")
                }
            }
    }
}
