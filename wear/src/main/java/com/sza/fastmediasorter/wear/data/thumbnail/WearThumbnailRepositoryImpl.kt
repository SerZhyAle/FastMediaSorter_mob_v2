package com.sza.fastmediasorter.wear.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

private const val IMAGE_PREFIX = "image/"
private const val VIDEO_PREFIX = "video/"

/**
 * Obtains a cell picture for either origin the watch can show.
 *
 * Network reads are serialised: the SMB, FTP and SFTP data sources each carry one connection, so
 * letting a scrolling list fan out would fight the listing request the same screen is making.
 * The cache holds the definite-absence answer as well as bitmaps, which is what stops a folder of
 * documents from reopening a connection on every scroll back.
 */
class WearThumbnailRepositoryImpl(
    private val context: Context,
    private val networkSourceRepository: NetworkSourceRepository,
    private val smbDataSource: SmbDataSource,
    private val ftpDataSource: FtpDataSource,
    private val sftpDataSource: SftpDataSource,
    private val previewReader: EmbeddedPreviewReader
) : WearThumbnailRepository {

    private val cache = LruCache<String, WearThumbnail>(WearThumbnailBudget.MAX_CACHED_THUMBNAILS)
    private val networkTurn = Mutex()

    override suspend fun thumbnailFor(file: WearMediaFile, sourceId: String?): WearThumbnail {
        if (!canCarryPreview(file.mimeType, isNetwork = sourceId != null)) {
            return WearThumbnail.Unavailable
        }
        val key = cacheKey(file, sourceId)
        val answer = cache.get(key) ?: load(file, sourceId).also { cache.put(key, it) }
        Timber.d("S1730: thumbnail for ${file.name} -> ${answer::class.simpleName}")
        return answer
    }

    /**
     * Asking the type first is what keeps the budget honest: a document never opens a connection.
     * A video frame is reachable only where the system storage already holds one, because the
     * watch reads just the head of a network file and a video keeps no preview there.
     */
    private fun canCarryPreview(mimeType: String?, isNetwork: Boolean): Boolean {
        val type = mimeType ?: return false
        return type.startsWith(IMAGE_PREFIX) || (!isNetwork && type.startsWith(VIDEO_PREFIX))
    }

    private fun cacheKey(file: WearMediaFile, sourceId: String?): String =
        "${sourceId ?: "local"}|${file.uri}"

    private suspend fun load(file: WearMediaFile, sourceId: String?): WearThumbnail {
        val bitmap = if (sourceId == null) {
            localThumbnail(file)
        } else {
            networkTurn.withLock { networkThumbnail(file, sourceId) }
        }
        return bitmap?.let { WearThumbnail.Ready(it) } ?: WearThumbnail.Unavailable
    }

    private suspend fun localThumbnail(file: WearMediaFile): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val edge = WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX
                context.contentResolver.loadThumbnail(file.uri, Size(edge, edge), null)
            } else {
                // Before the system thumbnail call existed, the cheapest picture on the watch is
                // the same embedded preview the network path reads.
                context.contentResolver.openInputStream(file.uri)?.let { previewReader.read(it) }
            }
        } catch (e: IOException) {
            Timber.w(e, "Local thumbnail unavailable for ${file.name}")
            null
        }
    }

    /**
     * The IO context is claimed for `previewReader.read(..)`, which drains the stream and decodes
     * EXIF on this thread - work that needs confining no matter which source produced the stream.
     * S1808: the three data sources now switch for themselves, so this is no longer compensating
     * for one of them.
     */
    private suspend fun networkThumbnail(file: WearMediaFile, sourceId: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val source = networkSourceRepository.getSourceById(sourceId)
            val stream = source?.let { openStream(it, file.uri.toString()) }
            stream?.let { previewReader.read(it) }
        }

    private suspend fun openStream(source: NetworkSource, path: String): InputStream? =
        when (source.type) {
            NetworkSourceType.SMB -> smbStream(source, path)
            NetworkSourceType.FTP -> ftpDataSource.getFileStream(source, path).getOrNull()
            NetworkSourceType.SFTP -> sftpDataSource.getFileStream(source, path).getOrNull()
            NetworkSourceType.GOOGLE_DRIVE -> null
        }

    private suspend fun smbStream(source: NetworkSource, path: String): InputStream? {
        val connected = smbDataSource.connect(source)
        return if (connected.isFailure) {
            Timber.w("SMB connect refused while fetching a thumbnail")
            null
        } else {
            smbDataSource.getFileStream(path).getOrNull()
        }
    }
}
