package com.sza.fastmediasorter.data.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.sza.fastmediasorter.core.util.EpubLazyReader
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Glide ModelLoader for network EPUB cover images.
 * Downloads EPUB to temporary cache file, then extracts cover image.
 *
 * Used for SMB/SFTP/FTP EPUB files where epub4j requires local file access.
 */
class NetworkEpubCoverLoader(
    private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : ModelLoader<NetworkFileData, Bitmap> {

    override fun buildLoadData(
        model: NetworkFileData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap>? {
        // Only handle EPUB files
        if (!model.path.endsWith(".epub", ignoreCase = true)) {
            return null
        }

        return ModelLoader.LoadData(
            model, // Use NetworkFileData as Key for consistent caching
            NetworkEpubDataFetcher(
                context,
                model,
                smbClient,
                sftpClient,
                ftpClient,
                credentialsRepository,
                width,
                height
            )
        )
    }

    override fun handles(model: NetworkFileData): Boolean {
        return model.path.endsWith(".epub", ignoreCase = true) &&
            (
                model.path.startsWith("smb://", ignoreCase = true) ||
                    model.path.startsWith("sftp://", ignoreCase = true) ||
                    model.path.startsWith("ftp://", ignoreCase = true)
                )
    }

    class Factory(
        private val context: Context,
        private val smbClient: SmbClient,
        private val sftpClient: SftpClient,
        private val ftpClient: FtpClient,
        private val credentialsRepository: NetworkCredentialsRepository
    ) : ModelLoaderFactory<NetworkFileData, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<NetworkFileData, Bitmap> {
            return NetworkEpubCoverLoader(context, smbClient, sftpClient, ftpClient, credentialsRepository)
        }

        override fun teardown() {
            // No resources to clean up
        }
    }
}

/**
 * DataFetcher that downloads network EPUB to temp cache, then extracts cover image.
 */
private class NetworkEpubDataFetcher(
    private val context: Context,
    private val data: NetworkFileData,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val width: Int,
    private val height: Int
) : DataFetcher<Bitmap> {

    @Volatile
    private var isCancelled = false
    private var tempFile: File? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val fileName = data.path.substringAfterLast('/')
        Timber.d("NetworkEpubDataFetcher.loadData: Starting EPUB download for $fileName")

        if (isCancelled) {
            callback.onLoadFailed(Exception("Cancelled before start"))
            return
        }

        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            callback.onLoadFailed(LocalNetworkPermissionDeniedException())
            return
        }

        try {
            val cacheDir = File(context.cacheDir, "epub_covers")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // A per-fetch scratch copy, deleted in cleanup(): keeping every book's full bytes for a
            // cover Glide already caches doubled a remote library on the device with no eviction.
            val scratch = File.createTempFile("cover_", ".epub", cacheDir)
            tempFile = scratch
            Timber.d("NetworkEpubDataFetcher: Downloading EPUB for cover: $fileName (${data.size} bytes)")
            downloadEpubToFile(scratch)

            if (isCancelled) {
                callback.onLoadFailed(Exception("Cancelled after download"))
                return
            }

            // Extract cover from EPUB
            val bitmap = extractCoverImage(tempFile!!)

            if (bitmap != null) {
                Timber.d("NetworkEpubDataFetcher: Successfully extracted EPUB cover for $fileName")
                callback.onDataReady(bitmap)
            } else {
                Timber.w("NetworkEpubDataFetcher: Failed to extract cover: $fileName")
                callback.onLoadFailed(Exception("Failed to extract EPUB cover"))
            }
        } catch (e: Exception) {
            Timber.e(e, "NetworkEpubDataFetcher: Error loading EPUB cover for $fileName")
            callback.onLoadFailed(e)
        }
    }

    private fun downloadEpubToFile(file: File) {
        runBlocking {
            when {
                data.path.startsWith("smb://", ignoreCase = true) -> downloadFromSmb(file)
                data.path.startsWith("sftp://", ignoreCase = true) -> downloadFromSftp(file)
                data.path.startsWith("ftp://", ignoreCase = true) -> downloadFromFtp(file)
                else -> throw IllegalArgumentException("Unsupported protocol: ${data.path}")
            }
        }
    }

    private suspend fun downloadFromSmb(file: File) {
        val uri = data.path.replaceFirst(Regex("^smb://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid SMB path")

        val serverPort = parts[0]
        val pathParts = if (parts.size > 1) parts[1] else ""

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 445
        } else {
            server = serverPort
            port = 445
        }

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SMB", server, port)
        }

        if (credentials == null) throw IOException("No credentials found for SMB: $server")

        val remoteParts = pathParts.split("/", limit = 2)
        val shareName = remoteParts[0]
        val remotePath = if (remoteParts.size > 1) remoteParts[1] else ""

        val connectionInfo = SmbConnectionInfo(
            server = server,
            port = port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )

        val result = FileOutputStream(file).use { output ->
            smbClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                localOutputStream = output,
                fileSize = data.size
            )
        }

        when (result) {
            is SmbResult.Success -> {
                // Downloaded successfully
            }
            is SmbResult.Error -> {
                throw result.exception!!
            }
        }
    }

    private suspend fun downloadFromSftp(file: File) {
        val uri = data.path.replaceFirst(Regex("^sftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid SFTP path")

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/" + parts[1] else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 22
        } else {
            server = serverPort
            port = 22
        }

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
        }

        if (credentials == null) throw IOException("No credentials found for SFTP: $server")

        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password
        )

        val result = FileOutputStream(file).use { output ->
            sftpClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                outputStream = output,
                fileSize = data.size
            )
        }

        result.getOrThrow()
    }

    private suspend fun downloadFromFtp(file: File) {
        val uri = data.path.replaceFirst(Regex("^ftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid FTP path")

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/" + parts[1] else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 21
        } else {
            server = serverPort
            port = 21
        }

        val credentials = if (data.credentialsId != null) {
            credentialsRepository.getByCredentialId(data.credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("FTP", server, port)
        }

        if (credentials == null) throw IOException("No credentials found for FTP: $server")

        // FTP client uses connect() to establish connection
        ftpClient.connect(server, port, credentials.username, credentials.password)

        val result = FileOutputStream(file).use { output ->
            ftpClient.downloadFile(
                remotePath = remotePath,
                outputStream = output,
                fileSize = data.size
            )
        }

        result.getOrThrow()
    }

    private fun extractCoverImage(file: File): Bitmap? {
        try {
            val imageData = EpubLazyReader.withBook(file) { it.coverImage?.data }

            if (imageData == null) {
                Timber.w("NetworkEpubDataFetcher: No cover image found in EPUB: ${file.name}")
                return null
            }

            val options = BitmapFactory.Options()

            // First decode to get dimensions
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            // Calculate sample size for downscaling if needed
            var sampleSize = 1
            if (width > 0 && height > 0) {
                // Calculate inSampleSize to scale down image
                while (originalWidth / sampleSize > width || originalHeight / sampleSize > height) {
                    sampleSize *= 2
                }
            }

            // Decode actual bitmap with sample size
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory

            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

            if (bitmap == null) {
                Timber.w("NetworkEpubDataFetcher: Failed to decode cover image for: ${file.name}")
                return null
            }

            Timber.d("NetworkEpubDataFetcher: Extracted cover (${bitmap.width}x${bitmap.height})")

            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "NetworkEpubDataFetcher: Failed to extract cover from EPUB: ${file.name}")
            return null
        }
    }

    override fun cleanup() {
        tempFile?.let { file ->
            if (!file.delete() && file.exists()) {
                Timber.w("NetworkEpubDataFetcher: Could not delete scratch EPUB ${file.name}")
            }
        }
        tempFile = null
    }

    override fun cancel() {
        isCancelled = true
    }

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.REMOTE
}
