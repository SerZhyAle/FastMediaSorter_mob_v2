package com.sza.fastmediasorter.data.local

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
    // EXIF/video metadata extraction removed from scanning - loaded on-demand in PlayerActivity
) : MediaScanner {

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        private val GIF_EXTENSIONS = setOf("gif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "opus")
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // Check if path is a content:// URI (SAF)
            if (path.startsWith("content://")) {
                return@withContext scanFolderSAF(path, supportedTypes, sizeFilter, scanSubdirectories)
            }
            
            // Legacy file:// path handling
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) {
                Timber.w("Folder does not exist or is not a directory: $path")
                return@withContext emptyList()
            }

            val files = if (scanSubdirectories) {
                collectFilesRecursively(folder)
            } else {
                folder.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
            }
            val totalFiles = files.size
            var processedCount = 0
            var lastProgressReportTime = System.currentTimeMillis()
            
            val result = files.mapNotNull { file ->
                if (file.isFile) {
                    val mediaType = getMediaType(file)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(file.length(), mediaType, sizeFilter)) {
                            return@mapNotNull null
                        }
                        
                        // Metadata (EXIF, video codec/duration) extracted on-demand in PlayerActivity FileInfoDialog
                        // Not needed for scanning - saves ~90% scan time on large folders
                        
                        MediaFile(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            createdDate = file.lastModified(),
                            type = mediaType,
                            duration = null,
                            width = null,
                            height = null,
                            exifOrientation = null,
                            exifDateTime = null,
                            exifLatitude = null,
                            exifLongitude = null,
                            videoCodec = null,
                            videoBitrate = null,
                            videoFrameRate = null,
                            videoRotation = null
                        ).also {
                            // Report progress: every 2 seconds or on last file (time-based for better performance)
                            processedCount++
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastReport = currentTime - lastProgressReportTime
                            if (processedCount == totalFiles || timeSinceLastReport >= 2000) {
                                onProgress?.let { callback ->
                                    kotlinx.coroutines.runBlocking {
                                        callback.onProgress(processedCount, file.name)
                                    }
                                }
                                lastProgressReportTime = currentTime
                            }
                        }
                    } else null
                } else null
            }
            
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Error scanning folder: $path")
            emptyList()
        }
    }
    
    /**
     * Scan folder with progress callback support
     * Reports progress every PROGRESS_REPORT_INTERVAL files
     */
    suspend fun scanFolderWithProgress(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        @Suppress("UNUSED_PARAMETER") credentialsId: String?,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // Check if path is a content:// URI (SAF)
            if (path.startsWith("content://")) {
                return@withContext scanFolderSAFWithProgress(path, supportedTypes, sizeFilter, progressCallback)
            }
            
            // Legacy file:// path handling
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) {
                Timber.w("Folder does not exist or is not a directory: $path")
                return@withContext emptyList()
            }

            val files = folder.listFiles() ?: return@withContext emptyList()
            val result = mutableListOf<MediaFile>()
            var scannedCount = 0
            
            files.forEach { file ->
                if (file.isFile) {
                    val mediaType = getMediaType(file)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(file.length(), mediaType, sizeFilter)) {
                            return@forEach
                        }
                        
                        // Metadata extracted on-demand - not needed for scanning
                        
                        result.add(
                            MediaFile(
                                name = file.name,
                                path = file.absolutePath,
                                type = mediaType,
                                size = file.length(),
                                createdDate = file.lastModified(),
                                duration = null,
                                width = null,
                                height = null,
                                exifOrientation = null,
                                exifDateTime = null,
                                exifLatitude = null,
                                exifLongitude = null,
                                videoCodec = null,
                                videoBitrate = null,
                                videoFrameRate = null,
                                videoRotation = null
                            )
                        )
                        
                        scannedCount++
                        // Report progress every 10 files
                        if (scannedCount % 10 == 0) {
                            progressCallback?.onProgress(scannedCount, file.name)
                        }
                    }
                }
            }
            
            val durationMs = System.currentTimeMillis() - startTime
            progressCallback?.onComplete(result.size, durationMs)
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Error scanning folder with progress: $path")
            val durationMs = System.currentTimeMillis() - startTime
            progressCallback?.onComplete(0, durationMs)
            emptyList()
        }
    }
    
    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?,
        scanSubdirectories: Boolean
    ): MediaFilePage = withContext(Dispatchers.IO) {
        try {
            // Check if path is a content:// URI (SAF)
            if (path.startsWith("content://")) {
                return@withContext scanFolderPagedSAF(path, supportedTypes, sizeFilter, offset, limit)
            }
            
            // Legacy file:// path handling
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) {
                Timber.w("Folder does not exist or is not a directory: $path")
                return@withContext MediaFilePage(emptyList(), false)
            }

            val files = folder.listFiles() ?: return@withContext MediaFilePage(emptyList(), false)
            
            // Filter and map all matching files first
            val allMediaFiles = files.mapNotNull { file ->
                if (file.isFile) {
                    val mediaType = getMediaType(file)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(file.length(), mediaType, sizeFilter)) {
                            return@mapNotNull null
                        }
                        
                        // Metadata extracted on-demand
                        
                        MediaFile(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            createdDate = file.lastModified(),
                            type = mediaType,
                            duration = null,
                            width = null,
                            height = null,
                            exifOrientation = null,
                            exifDateTime = null,
                            exifLatitude = null,
                            exifLongitude = null,
                            videoCodec = null,
                            videoBitrate = null,
                            videoFrameRate = null,
                            videoRotation = null
                        )
                    } else null
                } else null
            }
            
            // Apply offset and limit
            val pageFiles = allMediaFiles.drop(offset).take(limit)
            val hasMore = offset + limit < allMediaFiles.size
            
            Timber.d("LocalMediaScanner paged: offset=$offset, limit=$limit, returned=${pageFiles.size}, hasMore=$hasMore")
            MediaFilePage(pageFiles, hasMore)
            
        } catch (e: Exception) {
            Timber.e(e, "Error scanning folder (paged): $path")
            MediaFilePage(emptyList(), false)
        }
    }
    
    private suspend fun scanFolderPagedSAF(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int
    ): MediaFilePage = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val folder = DocumentFile.fromTreeUri(context, uri)
            
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                Timber.w("Invalid SAF folder URI or folder doesn't exist: $uriString")
                return@withContext MediaFilePage(emptyList(), false)
            }
            
            val files = folder.listFiles()
            if (files.isEmpty()) {
                return@withContext MediaFilePage(emptyList(), false)
            }
            
            // Filter and map all matching files first
            val allMediaFiles = files.mapNotNull { file ->
                if (file.isFile) {
                    val mimeType = file.type
                    val mediaType = getMediaTypeFromMime(mimeType)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        val fileSize = file.length()
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(fileSize, mediaType, sizeFilter)) {
                            return@mapNotNull null
                        }
                        
                        // Metadata extracted on-demand
                        
                        MediaFile(
                            name = file.name ?: "unknown",
                            path = file.uri.toString(),
                            size = fileSize,
                            createdDate = file.lastModified(),
                            type = mediaType,
                            duration = null,
                            width = null,
                            height = null,
                            exifOrientation = null,
                            exifDateTime = null,
                            exifLatitude = null,
                            exifLongitude = null,
                            videoCodec = null,
                            videoBitrate = null,
                            videoFrameRate = null,
                            videoRotation = null
                        )
                    } else null
                } else null
            }
            
            // Apply offset and limit
            val pageFiles = allMediaFiles.drop(offset).take(limit)
            val hasMore = offset + limit < allMediaFiles.size
            
            Timber.d("LocalMediaScanner SAF paged: offset=$offset, limit=$limit, returned=${pageFiles.size}, hasMore=$hasMore")
            MediaFilePage(pageFiles, hasMore)
            
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SAF folder (paged): $uriString")
            MediaFilePage(emptyList(), false)
        }
    }
    
    private suspend fun scanFolderSAF(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val folder = DocumentFile.fromTreeUri(context, uri)
            
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                Timber.w("Invalid SAF folder URI or folder doesn't exist: $uriString")
                return@withContext emptyList()
            }
            
            val files = if (scanSubdirectories) {
                collectDocumentFilesRecursively(folder)
            } else {
                folder.listFiles().filter { it.isFile }
            }
            
            if (files.isEmpty()) {
                return@withContext emptyList()
            }
            
            files.mapNotNull { file ->
                if (file.isFile) {
                    val mimeType = file.type
                    val mediaType = getMediaTypeFromMime(mimeType)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        val fileSize = file.length()
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(fileSize, mediaType, sizeFilter)) {
                            return@mapNotNull null
                        }
                        
                        // Metadata extracted on-demand
                        
                        MediaFile(
                            name = file.name ?: "unknown",
                            path = file.uri.toString(),
                            size = fileSize,
                            createdDate = file.lastModified(),
                            type = mediaType,
                            duration = null,
                            width = null,
                            height = null,
                            exifOrientation = null,
                            exifDateTime = null,
                            exifLatitude = null,
                            exifLongitude = null,
                            videoCodec = null,
                            videoBitrate = null,
                            videoFrameRate = null,
                            videoRotation = null
                        )
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SAF folder: $uriString")
            emptyList()
        }
    }
    
    /**
     * Scan SAF folder with progress callback support
     * Reports progress every 10 files
     */
    private suspend fun scanFolderSAFWithProgress(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val uri = Uri.parse(uriString)
            val folder = DocumentFile.fromTreeUri(context, uri)
            
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                Timber.w("Invalid SAF folder URI or folder doesn't exist: $uriString")
                return@withContext emptyList()
            }
            
            val files = folder.listFiles()
            if (files.isEmpty()) {
                return@withContext emptyList()
            }
            
            val result = mutableListOf<MediaFile>()
            var scannedCount = 0
            
            files.forEach { file ->
                if (file.isFile) {
                    val mimeType = file.type
                    val mediaType = getMediaTypeFromMime(mimeType)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        val fileSize = file.length()
                        // Apply size filter if provided
                        if (sizeFilter != null && !isFileSizeInRange(fileSize, mediaType, sizeFilter)) {
                            return@forEach
                        }
                        
                        // Metadata extracted on-demand
                        
                        result.add(
                            MediaFile(
                                name = file.name ?: "unknown",
                                path = file.uri.toString(),
                                size = fileSize,
                                createdDate = file.lastModified(),
                                type = mediaType,
                                duration = null,
                                width = null,
                                height = null,
                                exifOrientation = null,
                                exifDateTime = null,
                                exifLatitude = null,
                                exifLongitude = null,
                                videoCodec = null,
                                videoBitrate = null,
                                videoFrameRate = null,
                                videoRotation = null
                            )
                        )
                        
                        scannedCount++
                        // Report progress every 10 files
                        if (scannedCount % 10 == 0) {
                            progressCallback?.onProgress(scannedCount, file.name)
                        }
                    }
                }
            }
            
            val durationMs = System.currentTimeMillis() - startTime
            progressCallback?.onComplete(result.size, durationMs)
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SAF folder with progress: $uriString")
            val durationMs = System.currentTimeMillis() - startTime
            progressCallback?.onComplete(0, durationMs)
            emptyList()
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): Int = withContext(Dispatchers.IO) {
        try {
            // Check if path is a content:// URI (SAF)
            if (path.startsWith("content://")) {
                return@withContext getFileCountSAF(path, supportedTypes, sizeFilter)
            }
            
            // Legacy file:// path handling
            val folder = File(path)
            if (!folder.exists() || !folder.isDirectory) {
                return@withContext 0
            }

            val files = folder.listFiles() ?: return@withContext 0
            
            files.count { file ->
                if (file.isFile) {
                    val mediaType = getMediaType(file)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter if provided
                        if (sizeFilter != null) {
                            isFileSizeInRange(file.length(), mediaType, sizeFilter)
                        } else {
                            true
                        }
                    } else false
                } else false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting files in: $path")
            0
        }
    }
    
    private suspend fun getFileCountSAF(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): Int = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val folder = DocumentFile.fromTreeUri(context, uri)
            
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                return@withContext 0
            }
            
            val files = folder.listFiles()
            if (files.isEmpty()) {
                return@withContext 0
            }
            
            files.count { file ->
                if (file.isFile) {
                    val mimeType = file.type
                    val mediaType = getMediaTypeFromMime(mimeType)
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter if provided
                        if (sizeFilter != null) {
                            isFileSizeInRange(file.length(), mediaType, sizeFilter)
                        } else {
                            true
                        }
                    } else false
                } else false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting files in SAF folder: $uriString")
            0
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if path is a content:// URI (SAF)
            if (path.startsWith("content://")) {
                return@withContext isWritableSAF(path)
            }
            
            // Legacy file:// path handling
            val folder = File(path)
            folder.exists() && folder.canWrite()
        } catch (e: Exception) {
            Timber.e(e, "Error checking write access for: $path")
            false
        }
    }
    
    private suspend fun isWritableSAF(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val folder = DocumentFile.fromTreeUri(context, uri)
            
            if (folder == null || !folder.exists() || !folder.isDirectory) {
                Timber.w("Invalid SAF folder URI or folder doesn't exist: $uriString")
                return@withContext false
            }
            
            // Check if folder is writable by checking canWrite permission
            folder.canWrite()
        } catch (e: Exception) {
            Timber.e(e, "Error checking SAF write access for: $uriString")
            false
        }
    }
    
    private fun collectFilesRecursively(folder: File): List<File> {
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(folder)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles() ?: continue
            
            for (child in children) {
                when {
                    child.isFile -> result.add(child)
                    child.isDirectory -> queue.add(child)
                }
            }
        }
        
        return result
    }
    
    private fun collectDocumentFilesRecursively(folder: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(folder)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles()
            
            for (child in children) {
                when {
                    child.isFile -> result.add(child)
                    child.isDirectory -> queue.add(child)
                }
            }
        }
        
        return result
    }

    private fun getMediaType(file: File): MediaType? {
        val extension = file.extension.lowercase()
        return when {
            IMAGE_EXTENSIONS.contains(extension) -> MediaType.IMAGE
            GIF_EXTENSIONS.contains(extension) -> MediaType.GIF
            VIDEO_EXTENSIONS.contains(extension) -> MediaType.VIDEO
            AUDIO_EXTENSIONS.contains(extension) -> MediaType.AUDIO
            else -> null
        }
    }
    
    private fun getMediaTypeFromMime(mimeType: String?): MediaType? {
        if (mimeType == null) return null
        return when {
            mimeType == "image/gif" -> MediaType.GIF
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            else -> null
        }
    }
    
    private fun isFileSizeInRange(size: Long, mediaType: MediaType, filter: SizeFilter): Boolean {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> size in filter.imageSizeMin..filter.imageSizeMax
            MediaType.VIDEO -> size in filter.videoSizeMin..filter.videoSizeMax
            MediaType.AUDIO -> size in filter.audioSizeMin..filter.audioSizeMax
        }
    }
}