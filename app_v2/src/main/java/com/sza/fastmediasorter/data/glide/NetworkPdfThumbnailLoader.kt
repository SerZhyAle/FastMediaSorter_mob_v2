package com.sza.fastmediasorter.data.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Glide ModelLoader for network PDF thumbnails.
 * Downloads PDF to UnifiedFileCache, then renders first page.
 * 
 * Used for SMB/SFTP/FTP PDF files where PdfRenderer requires local file access.
 */
class NetworkPdfThumbnailLoader(
    private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedCache: UnifiedFileCache
) : ModelLoader<NetworkFileData, Bitmap> {

    companion object {
        // Option key for full PDF download setting
        val OPTION_FULL_PDF_DOWNLOAD = com.bumptech.glide.load.Option.memory<Boolean>(
            "com.sza.fastmediasorter.glide.fullPdfDownload",
            false
        )
    }

    override fun buildLoadData(
        model: NetworkFileData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap>? {
        // Only handle PDF files
        if (!model.path.endsWith(".pdf", ignoreCase = true)) {
            return null
        }
        
        // Check if full PDF download is enabled (from "Large PDF Thumbnails" setting)
        val downloadFullPdf = options.get(OPTION_FULL_PDF_DOWNLOAD) ?: false
        
        return ModelLoader.LoadData(
            model, // Use NetworkFileData as Key for consistent caching
            NetworkPdfDataFetcher(context, model, smbClient, sftpClient, ftpClient, credentialsRepository, unifiedCache, width, height, downloadFullPdf)
        )
    }

    override fun handles(model: NetworkFileData): Boolean {
        return model.path.endsWith(".pdf", ignoreCase = true) &&
               (model.path.startsWith("smb://") || 
                model.path.startsWith("sftp://") || 
                model.path.startsWith("ftp://"))
    }

    class Factory(
        private val context: Context,
        private val smbClient: SmbClient,
        private val sftpClient: SftpClient,
        private val ftpClient: FtpClient,
        private val credentialsRepository: NetworkCredentialsRepository,
        private val unifiedCache: UnifiedFileCache
    ) : ModelLoaderFactory<NetworkFileData, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<NetworkFileData, Bitmap> {
            return NetworkPdfThumbnailLoader(context, smbClient, sftpClient, ftpClient, credentialsRepository, unifiedCache)
        }

        override fun teardown() {
            // No resources to clean up
        }
    }
}

/**
 * DataFetcher that downloads network PDF to UnifiedFileCache, then renders thumbnail.
 */
private class NetworkPdfDataFetcher(
    private val context: Context,
    private val data: NetworkFileData,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedCache: UnifiedFileCache,
    private val width: Int,
    private val height: Int,
    private val downloadFullPdf: Boolean // If true, download full file; if false, only 5MB
) : DataFetcher<Bitmap> {
    
    @Volatile
    private var isCancelled = false
    private var tempFile: File? = null
    
    companion object {
        // Download only first 5 MB for PDF thumbnail generation (when Large PDF Thumbnails is OFF)
        private const val PDF_THUMBNAIL_MAX_DOWNLOAD = 5 * 1024 * 1024L // 5 MB
    }
    
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val fileName = data.path.substringAfterLast('/')
        Timber.d("NetworkPdfDataFetcher.loadData: Starting PDF download for $fileName")
        
        if (isCancelled) {
            callback.onLoadFailed(Exception("Cancelled before start"))
            return
        }
        
        try {
            // Check UnifiedFileCache first (reuses files from player/metadata)
            val cachedFile = unifiedCache.getCachedFile(data.path, data.size)
            if (cachedFile != null) {
                Timber.d("NetworkPdfDataFetcher: Using cached PDF from UnifiedFileCache: $fileName")
                tempFile = cachedFile
            } else {
                // Legacy check: pdf_thumbnails cache (for backward compatibility)
                val legacyCacheDir = File(context.cacheDir, "pdf_thumbnails")
                val cacheKey = "${data.path.hashCode()}_${data.size}"
                val legacyFile = File(legacyCacheDir, "$cacheKey.pdf")
                
                if (legacyFile.exists() && legacyFile.length() == data.size) {
                    Timber.d("NetworkPdfDataFetcher: Migrating PDF from legacy cache to UnifiedFileCache: $fileName")
                    unifiedCache.putFile(data.path, data.size, legacyFile)
                    tempFile = legacyFile
                } else {
                    // Not in cache - download to UnifiedFileCache
                    tempFile = unifiedCache.getCacheFile(data.path, data.size)
                    Timber.d("NetworkPdfDataFetcher: Downloading PDF to UnifiedFileCache: $fileName (${data.size} bytes)")
                    downloadPdfToFile(tempFile!!)
                }
            }
            
            if (isCancelled) {
                callback.onLoadFailed(Exception("Cancelled after download"))
                return
            }
            
            // Render first page to bitmap
            val bitmap = renderPdfPage(tempFile!!)
            
            if (bitmap != null) {
                Timber.d("NetworkPdfDataFetcher: Successfully rendered PDF thumbnail for $fileName")
                callback.onDataReady(bitmap)
            } else {
                Timber.w("NetworkPdfDataFetcher: Failed to render PDF: $fileName")
                callback.onLoadFailed(Exception("Failed to render PDF page"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "NetworkPdfDataFetcher: Error loading PDF thumbnail for $fileName")
            callback.onLoadFailed(e)
        }
    }
    
    private fun downloadPdfToFile(file: File) {
        runBlocking {
            when {
                data.path.startsWith("smb://") -> downloadFromSmb(file)
                data.path.startsWith("sftp://") -> downloadFromSftp(file)
                data.path.startsWith("ftp://") -> downloadFromFtp(file)
                else -> throw IllegalArgumentException("Unsupported protocol: ${data.path}")
            }
        }
    }
    
    private suspend fun downloadFromSmb(file: File) {
        val uri = data.path.removePrefix("smb://")
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
        
        val resourceKey = "smb://${server}:${port}"
        
        // Use ConnectionThrottleManager to limit concurrent SMB connections
        ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
            resourceKey = resourceKey,
            highPriority = false  // PDF thumbnails are low priority
        ) {
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
            
            // Calculate download size based on setting:
            // - Large PDF Thumbnails ON: download full file
            // - Large PDF Thumbnails OFF: download only 5MB for thumbnail
            val downloadSize = if (downloadFullPdf) {
                data.size // Full file
            } else {
                minOf(data.size, PDF_THUMBNAIL_MAX_DOWNLOAD) // Only 5MB
            }
            
            Timber.d("NetworkPdfDataFetcher: Downloading ${downloadSize} bytes of ${data.size} bytes for PDF thumbnail (fullPdf=$downloadFullPdf)")
            
            val result = smbClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                localOutputStream = FileOutputStream(file),
                fileSize = downloadSize
            )
            
            when (result) {
                is SmbResult.Success -> {
                    // Downloaded successfully
                }
                is SmbResult.Error -> {
                    throw result.exception!!
                }
            }
        }
    }
    
    private suspend fun downloadFromSftp(file: File) {
        val uri = data.path.removePrefix("sftp://")
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
        
        val resourceKey = "sftp://${server}:${port}"
        
        // Use ConnectionThrottleManager to limit concurrent SFTP connections
        ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
            resourceKey = resourceKey,
            highPriority = false  // PDF thumbnails are low priority
        ) {
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
            }
            
            if (credentials == null) throw IOException("No credentials found for SFTP: $server")
            
            val connectionInfo = com.sza.fastmediasorter.data.remote.sftp.SftpClient.SftpConnectionInfo(
                host = server,
                port = port,
                username = credentials.username,
                password = credentials.password
            )
            
            // Calculate download size based on setting
            val downloadSize = if (downloadFullPdf) {
                data.size // Full file
            } else {
                minOf(data.size, PDF_THUMBNAIL_MAX_DOWNLOAD) // Only 5MB
            }
            
            Timber.d("NetworkPdfDataFetcher: Downloading ${downloadSize} bytes of ${data.size} bytes for PDF thumbnail (SFTP, fullPdf=$downloadFullPdf)")
            
            val result = sftpClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                outputStream = FileOutputStream(file),
                fileSize = downloadSize
            )
            
            result.getOrThrow()
        }
    }
    
    private suspend fun downloadFromFtp(file: File) {
        val uri = data.path.removePrefix("ftp://")
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
        
        val resourceKey = "ftp://${server}:${port}"
        
        // Use ConnectionThrottleManager to limit concurrent FTP connections
        ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
            resourceKey = resourceKey,
            highPriority = false  // PDF thumbnails are low priority
        ) {
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("FTP", server, port)
            }
            
            if (credentials == null) throw IOException("No credentials found for FTP: $server")
            
            // FTP client uses connect() to establish connection
            ftpClient.connect(server, port, credentials.username, credentials.password)
            
            // Calculate download size based on setting
            val downloadSize = if (downloadFullPdf) {
                data.size // Full file
            } else {
                minOf(data.size, PDF_THUMBNAIL_MAX_DOWNLOAD) // Only 5MB
            }
            
            Timber.d("NetworkPdfDataFetcher: Downloading ${downloadSize} bytes of ${data.size} bytes for PDF thumbnail (FTP, fullPdf=$downloadFullPdf)")
            
            val result = ftpClient.downloadFile(
                remotePath = remotePath,
                outputStream = FileOutputStream(file),
                fileSize = downloadSize
            )
            
            result.getOrThrow()
        }
    }
    
    private fun renderPdfPage(file: File): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            
            if (renderer.pageCount == 0) {
                Timber.w("NetworkPdfDataFetcher: PDF has no pages")
                return null
            }
            
            page = renderer.openPage(0)
            
            // Calculate dimensions
            val pageWidth = page.width
            val pageHeight = page.height
            val pageAspectRatio = pageWidth.toFloat() / pageHeight.toFloat()
            
            val targetWidth: Int
            val targetHeight: Int
            
            if (width > 0 && height > 0) {
                targetWidth = width
                targetHeight = (width / pageAspectRatio).toInt()
            } else {
                val maxSize = 1024
                targetWidth = if (pageWidth > maxSize) maxSize else pageWidth
                targetHeight = (targetWidth / pageAspectRatio).toInt()
            }
            
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            // Draw page count badge in top-right corner
            val pageCount = renderer.pageCount
            drawPageCountBadge(bitmap, pageCount)
            
            return bitmap
            
        } catch (e: IOException) {
            Timber.e(e, "NetworkPdfDataFetcher: Failed to render PDF page")
            return null
        } catch (e: SecurityException) {
            Timber.w("NetworkPdfDataFetcher: PDF is password-protected")
            return null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }
    
    /**
     * Draw page count badge in top-right corner of PDF thumbnail.
     * Badge is "baked" into bitmap and cached by Glide for instant display.
     */
    private fun drawPageCountBadge(bitmap: Bitmap, pageCount: Int) {
        // Skip badge for single-page PDFs
        if (pageCount == 1) return
        
        val canvas = Canvas(bitmap)
        
        // Text setup (smaller size)
        val text = pageCount.toString()
        val textSize = (bitmap.width * 0.06f).coerceAtLeast(20f) // 6% of width, min 20px
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }
        
        // Measure text
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        
        // Rectangle dimensions with padding
        val paddingH = textSize * 0.3f // Horizontal padding
        val paddingV = textSize * 0.2f // Vertical padding
        val rectWidth = textBounds.width() + paddingH * 2
        val rectHeight = textBounds.height() + paddingV * 2
        
        // Position: centered horizontally, 75% from top
        val rectLeft = (bitmap.width - rectWidth) / 2f // Centered horizontally
        val rectTop = bitmap.height * 0.75f - rectHeight / 2f // 75% from top, centered vertically around this point
        
        // Draw semi-transparent black background (60% opacity)
        val bgPaint = Paint().apply {
            color = Color.argb(153, 0, 0, 0) // 153 = 60% of 255
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(rectLeft, rectTop, rectLeft + rectWidth, rectTop + rectHeight)
        canvas.drawRect(rect, bgPaint)
        
        // Draw text centered in rectangle
        val textX = rectLeft + paddingH
        val textY = rectTop + paddingV - textBounds.top
        canvas.drawText(text, textX, textY, textPaint)
    }
    
    override fun cleanup() {
        // Keep temp file in cache for reuse
        // Android will auto-clean cache when storage is low
    }
    
    override fun cancel() {
        isCancelled = true
    }
    
    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java
    
    override fun getDataSource(): DataSource = DataSource.REMOTE
}
