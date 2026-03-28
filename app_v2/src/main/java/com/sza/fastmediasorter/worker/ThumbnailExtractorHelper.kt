package com.sza.fastmediasorter.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.util.NetworkFileDownloader
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts thumbnail JPEGs from network files without Glide.
 * Supports video (via MediaMetadataRetriever) and PDF (via PdfRenderer).
 * Downloads the remote file to UnifiedFileCache first, then extracts the thumbnail.
 * Temp files in UnifiedFileCache are reused if already downloaded by the player.
 *
 * Part of X.11: Background Thumbnail Preload.
 */
@Singleton
class ThumbnailExtractorHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedFileCache: UnifiedFileCache
) {
    private val thumbnailDir: File by lazy {
        File(context.filesDir, "thumbnails").also { it.mkdirs() }
    }

    private val downloader: NetworkFileDownloader by lazy {
        NetworkFileDownloader(
            context = context,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository,
            unifiedCache = unifiedFileCache
        )
    }

    /**
     * Download [file] from network and extract a thumbnail JPEG.
     * Returns null on any error; never throws.
     */
    suspend fun extract(file: MediaFile): File? {
        return try {
            val localFile = when {
                file.path.startsWith("file://") || !file.path.contains("://") -> {
                    val path = file.path.removePrefix("file://")
                    File(path).takeIf { it.exists() }
                }
                else -> downloader.downloadToTemp(file.path, file.type, file.size)
            } ?: run {
                Timber.d("ThumbnailExtractor: download failed for ${file.path}")
                return null
            }

            val outputFile = File(thumbnailDir, "${UUID.randomUUID()}.jpg")
            val success = when (file.type) {
                MediaType.VIDEO -> extractVideoThumbnail(localFile, outputFile)
                MediaType.PDF -> extractPdfThumbnail(localFile, outputFile)
                else -> false
            }

            if (success) outputFile else {
                outputFile.delete()
                null
            }
        } catch (e: OutOfMemoryError) {
            Timber.e("ThumbnailExtractor: OOM extracting thumbnail for ${file.path}")
            null
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailExtractor: failed for ${file.path}")
            null
        }
    }

    private fun extractVideoThumbnail(localFile: File, outputFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(localFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return false
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailExtractor: video extraction failed")
            false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractPdfThumbnail(localFile: File, outputFile: File): Boolean {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return false
            page = renderer.openPage(0)
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailExtractor: PDF extraction failed")
            false
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
