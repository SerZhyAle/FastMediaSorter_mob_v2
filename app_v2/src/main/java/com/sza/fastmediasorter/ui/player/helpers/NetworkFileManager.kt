package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages network file downloads/uploads for PlayerActivity:
 * - Downloads network files (SMB/S/FTP/Cloud) to temp cache for reading (TEXT/PDF)
 * - Downloads network files for editing (with upload tracking)
 * - Uploads edited files back to network locations
 * - Supports SMB, SFTP, FTP, Google Drive, Dropbox, OneDrive
 */
class NetworkFileManager(
    private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val sftpFileOperationHandler: SftpFileOperationHandler,
    private val ftpFileOperationHandler: FtpFileOperationHandler,
    private val cloudFileOperationHandler: CloudFileOperationHandler,
    private val unifiedCache: UnifiedFileCache,
    private val callback: NetworkFileCallback
) {
    
    interface NetworkFileCallback {
        fun getCurrentResource(): MediaResource?
        fun showError(message: String)
    }
    
    // Track temp file for network edits (uploaded on save)
    private var currentEditingFile: MediaFile? = null
    private var currentEditingTempFile: File? = null
    
    /**
     * Prepare file for reading. Returns File object (local or downloaded temp file).
     * Throws Exception if download fails.
     */
    suspend fun prepareFileForRead(mediaFile: MediaFile): File {
        // Local files - return directly
        if (mediaFile.path.startsWith("/")) {
            val f = File(mediaFile.path)
            if (f.exists()) return f

            // Fallback 1: MediaStore contentUri - available for media files on API 29+.
            // Files in Android/data/<other_package>/ may not have one, but worth checking.
            val contentUri = mediaFile.contentUri
            if (!contentUri.isNullOrEmpty()) {
                Timber.w("NetworkFileManager: File.exists()=false, trying contentUri fallback: $contentUri")
                return copyUriToTemp(contentUri, mediaFile.name)
            }

            // Fallback 2: try direct FileInputStream - handles the Android FUSE quirk where
            // File.exists() returns false (e.g. stat() denied on parent) but the file itself
            // is still accessible. Also surfaces EACCES as IOException for better diagnostics.
            try {
                return copyFileToTemp(f, mediaFile.name)
            } catch (e: java.io.IOException) {
                Timber.w(e, "NetworkFileManager: direct stream fallback failed for ${mediaFile.path}")
                throw java.io.FileNotFoundException("Local file not found: ${mediaFile.path}")
            }
        }

        // Content URIs from external intents (e.g. "Open With") - copy to temp file
        if (mediaFile.path.startsWith("content://")) {
            return copyUriToTemp(mediaFile.path, mediaFile.name)
        }

        // Network files - download to temp cache
        return downloadNetworkFileForRead(mediaFile)
    }

    private suspend fun copyUriToTemp(uriString: String, fileName: String): File = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(uriString)
        val ext = fileName.substringAfterLast('.', "bin")
        val tempFile = File(context.cacheDir, "standalone_content_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("Cannot open content URI: $uriString")
        Timber.d("NetworkFileManager: content URI copied to temp file: ${tempFile.path}")
        tempFile
    }

    private suspend fun copyFileToTemp(file: File, fileName: String): File = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "bin")
        val tempFile = File(context.cacheDir, "local_fallback_${System.currentTimeMillis()}.$ext")
        file.inputStream().use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        Timber.d("NetworkFileManager: local file copied to temp via InputStream (File.exists() was false): ${tempFile.path}")
        tempFile
    }

    private suspend fun copyContentUriToTemp(mediaFile: MediaFile): File =
        copyUriToTemp(mediaFile.path, mediaFile.name)
    
    /**
     * Prepare file for writing. Returns temp file for network files (will be uploaded after edit).
     * Returns null if resource is read-only.
     */
    suspend fun prepareFileForWrite(mediaFile: MediaFile): File? {
        val resource = callback.getCurrentResource()
        
        // Check if resource is writable
        if (resource == null || !resource.isWritable) {
            Timber.w("prepareFileForWrite: Resource is read-only")
            withContext(Dispatchers.Main) {
                callback.showError(context.getString(R.string.error_reason_read_only))
            }
            return null
        }
        
        // Local files - return directly if writable
        if (mediaFile.path.startsWith("/")) {
            val f = File(mediaFile.path)
            if (f.exists() && f.canWrite()) return f
            Timber.w("prepareFileForWrite: Local file not writable: ${mediaFile.path}")
            return null
        }
        
        // Network files - download and mark for upload
        return downloadNetworkFileForWrite(mediaFile)
    }
    
    /**
     * Upload edited temp file back to network location
     */
    suspend fun uploadEditedFile(mediaFile: MediaFile, tempFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Extract parent directory from network path
                val networkPath = mediaFile.path
                val parentPath = networkPath.substringBeforeLast("/")
                
                // Create dummy destination folder for FileOperation
                val destinationFolder = object : File(parentPath) {
                    override fun getAbsolutePath(): String = parentPath
                    override fun getPath(): String = parentPath
                }
                
                val copyOperation = FileOperation.Copy(
                    sources = listOf(tempFile),
                    destination = destinationFolder,
                    overwrite = true
                )
                
                val result = when {
                    networkPath.startsWith("smb://") -> smbFileOperationHandler.executeCopy(copyOperation)
                    networkPath.startsWith("sftp://") -> sftpFileOperationHandler.executeCopy(copyOperation)
                    networkPath.startsWith("ftp://") -> ftpFileOperationHandler.executeCopy(copyOperation)
                    networkPath.startsWith("cloud://") -> cloudFileOperationHandler.executeCopy(copyOperation)
                    else -> {
                        Timber.e("Unsupported protocol for upload: $networkPath")
                        FileOperationResult.Failure("Unsupported protocol")
                    }
                }
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Timber.d("Successfully uploaded edited file to $networkPath")
                        true
                    }
                    else -> {
                        Timber.e("Failed to upload edited file: $result")
                        false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload edited file")
                false
            }
        }
    }
    
    /**
     * Clear editing temp file tracking
     */
    fun clearEditingCache() {
        currentEditingFile = null
        currentEditingTempFile?.delete()
        currentEditingTempFile = null
    }
    
    // ========== Private Helper Methods ==========
    
    private suspend fun downloadNetworkFileForRead(mediaFile: MediaFile): File {
        return withContext(Dispatchers.IO) {
            // Check UnifiedFileCache first (reuses files from player/metadata extraction)
            val cachedFile = unifiedCache.getCachedFile(mediaFile.path, mediaFile.size)
            if (cachedFile != null) {
                Timber.d("NetworkFileManager: Reusing cached file for viewing: ${mediaFile.name} (${mediaFile.size / 1024 / 1024} MB)")
                return@withContext cachedFile
            }
            
            // Legacy check: For PDFs, check old pdf_thumbnails cache (for backward compatibility)
            if (mediaFile.type == com.sza.fastmediasorter.domain.model.MediaType.PDF) {
                val pdfCacheDir = File(context.cacheDir, "pdf_thumbnails")
                val cacheKey = "${mediaFile.path.hashCode()}_${mediaFile.size}"
                val cachedPdfFile = File(pdfCacheDir, "$cacheKey.pdf")
                
                // Reuse cached PDF if exists and size matches (validation)
                if (cachedPdfFile.exists() && cachedPdfFile.length() == mediaFile.size) {
                    Timber.d("NetworkFileManager: Migrating PDF from old cache to UnifiedFileCache: ${cachedPdfFile.absolutePath}")
                    // Store in UnifiedFileCache for future use
                    unifiedCache.putFile(mediaFile.path, mediaFile.size, cachedPdfFile)
                    return@withContext cachedPdfFile
                } else if (cachedPdfFile.exists()) {
                    Timber.w("NetworkFileManager: Cached PDF size mismatch - deleting stale cache file")
                    cachedPdfFile.delete()
                }
            }
            
            // Not in cache - download using UnifiedFileCache path
            val cacheFile = unifiedCache.getCacheFile(mediaFile.path, mediaFile.size)
            
            val path = mediaFile.path
            try {
                when {
                    path.startsWith("smb://") -> downloadSmbFileForRead(path, cacheFile)
                    path.startsWith("sftp://") -> downloadSftpFileForRead(path, cacheFile)
                    path.startsWith("ftp://") -> downloadFtpFileForRead(path, cacheFile)
                    path.startsWith("cloud://") -> downloadCloudFileForRead(path, cacheFile)
                    else -> throw IllegalArgumentException("Unsupported protocol: $path")
                }
                
                if (cacheFile.exists()) {
                    Timber.d("NetworkFileManager: Downloaded and cached file: ${mediaFile.name} (${cacheFile.length() / 1024 / 1024} MB)")
                    cacheFile
                } else {
                    throw java.io.IOException("Download failed: File not created")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading network file")
                throw e
            }
        }
    }
    
    private suspend fun downloadNetworkFileForWrite(mediaFile: MediaFile): File {
        // Download file first
        val tempFile = downloadNetworkFileForRead(mediaFile)
        
        // Track for upload after editing
        currentEditingFile = mediaFile
        currentEditingTempFile = tempFile
        
        return tempFile
    }
    
    private suspend fun downloadSmbFileForRead(path: String, tempFile: File) {
        val resource = callback.getCurrentResource() ?: throw IllegalStateException("Resource not found")
        if (resource.credentialsId == null) throw IllegalStateException("Credentials not found")
        
        val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: throw IllegalStateException("Credentials not found in DB")
        // Encode # before parsing to prevent it being treated as fragment identifier
        val encodedPath = path.replace("#", "%23")
        val uri = android.net.Uri.parse(encodedPath)
        val host = uri.host ?: throw IllegalArgumentException("Invalid host")
        val pathSegments = uri.pathSegments
        if (pathSegments == null || pathSegments.size < 2) throw IllegalArgumentException("Invalid path")
        
        val shareName = pathSegments[0]
        val filePath = "/" + pathSegments.drop(1).joinToString("/")
        
        tempFile.outputStream().use { outputStream ->
            val result = smbClient.downloadFile(
                SmbConnectionInfo(
                    server = host,
                    shareName = shareName,
                    username = credentials.username,
                    password = credentials.password,
                    domain = credentials.domain,
                    port = if (uri.port > 0) uri.port else 445
                ),
                remotePath = filePath,
                localOutputStream = outputStream
            )
            if (result !is SmbResult.Success) {
                val error = (result as? SmbResult.Error)?.exception?.message ?: "Unknown SMB error"
                throw java.io.IOException("SMB Download failed: $error")
            }
        }
    }
    
    private suspend fun downloadSftpFileForRead(path: String, tempFile: File) {
        val resource = callback.getCurrentResource() ?: throw IllegalStateException("Resource not found")
        if (resource.credentialsId == null) throw IllegalStateException("Credentials not found")
        
        val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: throw IllegalStateException("Credentials not found in DB")
        // Encode # before parsing to prevent it being treated as fragment identifier
        val encodedPath = path.replace("#", "%23")
        val uri = android.net.Uri.parse(encodedPath)
        val host = uri.host ?: throw IllegalArgumentException("Invalid host")
        val port = if (uri.port > 0) uri.port else 22
        val sftpPath = uri.path ?: throw IllegalArgumentException("Invalid path")
        
        tempFile.outputStream().use { outputStream ->
            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = host,
                port = port,
                username = credentials.username,
                password = credentials.password
            )
            val result = sftpClient.downloadFile(connectionInfo, sftpPath, outputStream)
            if (!result.isSuccess) {
                throw java.io.IOException("SFTP Download failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    private suspend fun downloadFtpFileForRead(path: String, tempFile: File) {
        val resource = callback.getCurrentResource() ?: throw IllegalStateException("Resource not found")
        if (resource.credentialsId == null) throw IllegalStateException("Credentials not found")
        
        val credentials = credentialsRepository.getByCredentialId(resource.credentialsId) ?: throw IllegalStateException("Credentials not found in DB")
        // Encode # before parsing to prevent it being treated as fragment identifier
        val encodedPath = path.replace("#", "%23")
        val uri = android.net.Uri.parse(encodedPath)
        val host = uri.host ?: throw IllegalArgumentException("Invalid host")
        val port = if (uri.port > 0) uri.port else 21
        val ftpPath = uri.path ?: throw IllegalArgumentException("Invalid path")
        
        ftpClient.connect(host, port, credentials.username, credentials.password)
        try {
            tempFile.outputStream().use { outputStream ->
                val result = ftpClient.downloadFile(ftpPath, outputStream)
                if (!result.isSuccess) {
                    throw java.io.IOException("FTP Download failed: ${result.exceptionOrNull()?.message}")
                }
            }
        } finally {
            ftpClient.disconnect()
        }
    }
    
    private suspend fun downloadCloudFileForRead(path: String, tempFile: File) {
        // Parse cloud URI: cloud://google_drive/{fileId} or cloud://dropbox/{path} or cloud://onedrive/{itemId}
        // Encode # before parsing to prevent it being treated as fragment identifier
        val encodedPath = path.replace("#", "%23")
        val uri = android.net.Uri.parse(encodedPath)
        val provider = uri.host?.lowercase() ?: throw IllegalArgumentException("Invalid cloud provider")
        val fileIdOrPath = uri.path?.removePrefix("/") ?: throw IllegalArgumentException("Invalid cloud path")
        
        Timber.d("downloadCloudFile: provider=$provider, fileId=$fileIdOrPath")
        
        tempFile.outputStream().use { outputStream ->
            val result = when (provider) {
                "google_drive", "googledrive" -> googleDriveClient.downloadFile(fileIdOrPath, outputStream)
                "dropbox" -> dropboxClient.downloadFile(fileIdOrPath, outputStream)
                "onedrive" -> oneDriveClient.downloadFile(fileIdOrPath, outputStream)
                else -> throw IllegalArgumentException("Unknown cloud provider: $provider")
            }
            
            if (result !is CloudResult.Success) {
                val error = (result as? CloudResult.Error)?.message ?: "Unknown cloud error"
                throw java.io.IOException("Cloud Download failed: $error")
            }
        }
    }
}
