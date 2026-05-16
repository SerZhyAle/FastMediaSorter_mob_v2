package com.sza.fastmediasorter.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SafHelper
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository
) : MediaScanner {

    companion object {
        const val VIRTUAL_PATH_RECENT = "virtual://recent"
        const val VIRTUAL_PATH_ALL_AUDIO = "virtual://all_audio"
        const val VIRTUAL_PATH_ALL_VIDEO = "virtual://all_video"
        const val VIRTUAL_PATH_ALL_IMAGES = "virtual://all_images"
        const val VIRTUAL_PATH_ALL_DOCS = "virtual://all_docs"
        const val VIRTUAL_PATH_CAMERA_PHOTOS = "virtual://camera_photos"
        private const val RECENT_FILES_LIMIT = 1000
        const val VIRTUAL_ALL_FILES_LIMIT = 10_000
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("LocalMediaScanner.scanFolder: START - path='$path'")

        if (path == VIRTUAL_PATH_RECENT) {
            return@withContext scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, onProgress)
        }

        if (path == VIRTUAL_PATH_ALL_AUDIO) {
            return@withContext scanAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles, onProgress)
        }
        if (path == VIRTUAL_PATH_ALL_VIDEO) {
            return@withContext scanAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles, onProgress)
        }
        if (path == VIRTUAL_PATH_ALL_IMAGES) {
            val imageTypes = imageTypesFromSettings(supportedTypes)
            return@withContext if (imageTypes.isNotEmpty()) {
                scanAllByTypes(imageTypes, sizeFilter, showHiddenFiles, onProgress)
            } else {
                onProgress?.onComplete(0, 0)
                emptyList()
            }
        }
        if (path == VIRTUAL_PATH_ALL_DOCS) {
            val docTypes = docTypesFromSettings(supportedTypes)
            return@withContext if (docTypes.isNotEmpty()) {
                scanAllByTypes(docTypes, sizeFilter, showHiddenFiles, onProgress)
            } else {
                onProgress?.onComplete(0, 0)
                emptyList()
            }
        }
        
        if (path == VIRTUAL_PATH_CAMERA_PHOTOS) {
            val cameraPath = mediaStoreRepository.findCameraFolderPath()
                ?: return@withContext emptyList<MediaFile>().also {
                    Timber.w("LocalMediaScanner: Camera folder not found on this device")
                }
            Timber.d("LocalMediaScanner: Scanning camera path='$cameraPath'")
            try {
                val files = mediaStoreRepository.getFilesInFolder(cameraPath, supportedTypes, scanSubdirectories, showHiddenFiles)
                if (files.isNotEmpty()) {
                    val filtered = files.filter { file ->
                        sizeFilter == null || file.size <= 0L || MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
                    }
                    if (filtered.isNotEmpty() || sizeFilter == null) {
                        onProgress?.onComplete(filtered.size, 0)
                        return@withContext filtered
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "MediaStore scan failed for Camera folder")
            }
            return@withContext scanFolderLegacy(cameraPath, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress)
        }
        
        // SAF handling - use fast cursor-based scanning
        if (path.startsWith("content://")) {
            return@withContext scanFolderSAFFast(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress)
        }
        
        // Try MediaStore Repository first (Android 10+ compliant)
        Timber.d("LocalMediaScanner: Querying MediaStore for path='$path'")
        try {
            val files = mediaStoreRepository.getFilesInFolder(path, supportedTypes, scanSubdirectories, showHiddenFiles)
            Timber.d("LocalMediaScanner: MediaStore query returned ${files.size} files")
            if (files.isNotEmpty()) {
                val filtered = files.filter { file ->
                    sizeFilter == null || file.size <= 0L || MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
                }
                if (filtered.isNotEmpty() || sizeFilter == null) {
                    onProgress?.onComplete(filtered.size, 0)
                    Timber.d("LocalMediaScanner: MediaStore returned ${filtered.size} files (showHiddenFiles=$showHiddenFiles)")
                    return@withContext filtered
                }
                Timber.d("MediaStore returned files but size filter removed all. Falling back to legacy File API.")
            } else {
                Timber.d("LocalMediaScanner: MediaStore returned empty list, falling back to legacy File API")
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaStore scan failed, falling back to legacy File API")
        }

        // Fallback to File API
        Timber.d("LocalMediaScanner: Falling back to scanFolderLegacy for path='$path'")
        val legacyResult = scanFolderLegacy(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress)
        Timber.d("LocalMediaScanner: scanFolderLegacy returned ${legacyResult.size} files")
        legacyResult
    }

    private suspend fun scanRecentFiles(
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        return try {
            val files = mediaStoreRepository.getRecentFiles(RECENT_FILES_LIMIT, supportedTypes)
            val visibleFiles = if (showHiddenFiles) {
                files
            } else {
                files.filter { !File(it.path).name.startsWith(".") }
            }

            val filteredFiles = visibleFiles.filter { file ->
                sizeFilter == null || file.size <= 0L || MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
            }

            onProgress?.onComplete(filteredFiles.size, 0)
            filteredFiles
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to scan recent files")
            onProgress?.onComplete(0, 1)
            emptyList()
        }
    }

    private suspend fun scanAllByTypes(
        allowedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        return try {
            val files = mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = allowedTypes,
                limit = VIRTUAL_ALL_FILES_LIMIT,
                showHiddenFiles = showHiddenFiles
            )

            val filteredFiles = files.filter { file ->
                sizeFilter == null || file.size <= 0L ||
                    MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
            }

            onProgress?.onComplete(filteredFiles.size, 0)
            filteredFiles
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to scan all files by types: $allowedTypes")
            onProgress?.onComplete(0, 1)
            emptyList()
        }
    }

    private suspend fun countAllByTypes(
        allowedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): Int {
        return try {
            mediaStoreRepository.countAllFilesByTypes(
                allowedTypes = allowedTypes,
                limit = VIRTUAL_ALL_FILES_LIMIT,
                showHiddenFiles = showHiddenFiles,
                sizeFilter = sizeFilter
            )
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to count all files by types: $allowedTypes")
            0
        }
    }

    private fun imageTypesFromSettings(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { it in setOf(MediaType.IMAGE, MediaType.GIF) }.toSet()
    }

    private fun docTypesFromSettings(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { it in setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB) }.toSet()
    }

    private suspend fun scanFolderLegacy(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        Timber.d("LocalMediaScanner.scanFolderLegacy: START - path='$path'")
        val folder = File(path)
        if (!folder.exists()) {
            Timber.w("LocalMediaScanner.scanFolderLegacy: Folder does not exist: $path")
            return emptyList()
        }
        if (!folder.isDirectory) {
            Timber.w("LocalMediaScanner.scanFolderLegacy: Path is not a directory: $path")
            return emptyList()
        }

        val files = if (scanSubdirectories) {
            collectFilesRecursively(folder)
        } else {
            folder.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
        }
        
        if (!showHiddenFiles) Timber.d("LocalMediaScanner: Filtering hidden files")
        val visibleFiles = if (showHiddenFiles) files else files.filter { !it.isHidden }
        
        var processed = 0
        if (path.contains("mp3", ignoreCase = true)) {
            Timber.d("LocalMediaScanner: Scanning mp3 folder: $path. Found ${visibleFiles.size} visible files.")
        }

        val result = mutableListOf<MediaFile>()
        for (file in visibleFiles) {
            processed++
            if (processed % 50 == 0) {
                onProgress?.onProgress(processed, file.name)
                // Check if caller wants to stop (for incremental loading)
                if (onProgress?.shouldStop() == true) {
                    Timber.d("LocalMediaScanner: Early stop at $processed/${visibleFiles.size} files (${result.size} matched)")
                    break
                }
            }
            
            val mediaType = MediaTypeUtils.getMediaType(file.name)
            if (mediaType != null && mediaType in supportedTypes) {
                if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), mediaType, sizeFilter)) {
                    if (mediaType == MediaType.AUDIO && file.name.endsWith(".mp3", ignoreCase = true)) {
                        Timber.d("LocalMediaScanner: Found MP3: ${file.name} (Size: ${file.length()})")
                    }
                    result.add(MediaFile(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        createdDate = file.lastModified(),
                        type = mediaType
                    ))
                } else {
                    if (file.name.endsWith(".mp3", ignoreCase = true)) {
                        Timber.d("LocalMediaScanner: Filtered MP3 by size: ${file.name} (Size: ${file.length()})")
                    }
                }
            } else {
                 if (file.name.endsWith(".mp3", ignoreCase = true)) {
                      Timber.d("LocalMediaScanner: Filtered MP3 by type or unsupported: ${file.name} (Type: $mediaType)")
                 }
            }
        }
        
        onProgress?.onComplete(result.size, 0)
        Timber.d("LocalMediaScanner.scanFolderLegacy: COMPLETE - found ${result.size} files (processed $processed/${visibleFiles.size})")
        return result
    }

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): MediaFilePage = withContext(Dispatchers.IO) {
        // Reuse scanFolder logic (handles virtual paths, SAF, MediaStore, and legacy)
        val allFiles = scanFolder(path, supportedTypes, sizeFilter, credentialsId, scanSubdirectories, showHiddenFiles, null)
        val page = allFiles.drop(offset).take(limit)
        MediaFilePage(page, offset + limit < allFiles.size)
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (path == VIRTUAL_PATH_RECENT) {
            return@withContext scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, null).size
        }

        if (path == VIRTUAL_PATH_ALL_AUDIO) {
            return@withContext countAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles)
        }
        if (path == VIRTUAL_PATH_ALL_VIDEO) {
            return@withContext countAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles)
        }
        if (path == VIRTUAL_PATH_ALL_IMAGES) {
            val imageTypes = imageTypesFromSettings(supportedTypes)
            return@withContext if (imageTypes.isNotEmpty()) {
                countAllByTypes(imageTypes, sizeFilter, showHiddenFiles)
            } else 0
        }
        if (path == VIRTUAL_PATH_ALL_DOCS) {
            val docTypes = docTypesFromSettings(supportedTypes)
            return@withContext if (docTypes.isNotEmpty()) {
                countAllByTypes(docTypes, sizeFilter, showHiddenFiles)
            } else 0
        }

        if (path == VIRTUAL_PATH_CAMERA_PHOTOS) {
            val cameraPath = mediaStoreRepository.findCameraFolderPath() ?: return@withContext 0
            try {
                val files = mediaStoreRepository.getFilesInFolder(cameraPath, supportedTypes, scanSubdirectories, showHiddenFiles)
                if (files.isNotEmpty()) {
                    return@withContext files.count { file ->
                       sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            val folder = File(cameraPath)
            if (!folder.exists()) return@withContext 0
            val children = folder.listFiles() ?: return@withContext 0
            val visibleFiles = if (showHiddenFiles) children.toList() else children.filter { !it.isHidden }
            return@withContext visibleFiles.count { file ->
                val type = MediaTypeUtils.getMediaType(file.name) ?: return@count false
                if (type !in supportedTypes) return@count false
                sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), type, sizeFilter)
            }
        }

        if (path.startsWith("content://")) {
            return@withContext getFileCountSAF(path, supportedTypes, sizeFilter, scanSubdirectories)
        }
        
        try {
            val files = mediaStoreRepository.getFilesInFolder(path, supportedTypes, scanSubdirectories, showHiddenFiles)
            if (files.isNotEmpty()) {
                return@withContext files.count { file ->
                   sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)
                }
            }
        } catch (e: Exception) {
             // ignore
        }
        
        // Fallback using File API, respecting scanSubdirectories
        val folder = File(path)
        if (!folder.exists()) return@withContext 0
        val allFiles = if (scanSubdirectories) {
            collectFilesRecursively(folder)
        } else {
            val children = folder.listFiles() ?: return@withContext 0
            children.filter { it.isFile }
        }
        val visibleFiles = if (showHiddenFiles) allFiles else allFiles.filter { !it.isHidden }
        visibleFiles.count { file ->
            val type = MediaTypeUtils.getMediaType(file.name) ?: return@count false
            if (type !in supportedTypes) return@count false
            sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), type, sizeFilter)
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        if (VirtualPathUtils.isVirtualPath(path)) return@withContext false
        if (path.startsWith("content://")) return@withContext isWritableSAF(path)
        val folder = File(path)
        folder.exists() && folder.canWrite()
    }
    
    /** List directory contents; returns files and folders (isDirectory=true for folders). */
    override suspend fun listDirectoryContents(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        showHiddenFiles: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("LocalMediaScanner.listDirectoryContents: path='$path'")

        // Virtual resources have no browsable directories
        if (VirtualPathUtils.isVirtualPath(path)) return@withContext emptyList()
        
        // SAF handling
        if (path.startsWith("content://")) {
            return@withContext listDirectoryContentsSAF(path, supportedTypes, sizeFilter, showHiddenFiles)
        }
        
        // Legacy File API
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            Timber.w("LocalMediaScanner.listDirectoryContents: Invalid path '$path'")
            return@withContext emptyList()
        }
        
        val children = folder.listFiles() ?: return@withContext emptyList()
        val visibleChildren = if (showHiddenFiles) children.toList() else children.filter { !it.isHidden }

        visibleChildren.mapNotNull { file ->
            if (file.isDirectory) {
                // Keep recognised trash containers out of normal folder listings.
                if (TrashFolderContract.matchesTrashSegment(file.name)) return@mapNotNull null
                MediaFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = 0L,
                    createdDate = file.lastModified(),
                    type = MediaType.IMAGE, // Placeholder type for folders
                    isDirectory = true,
                    childCount = file.listFiles()?.size ?: 0
                )
            } else {
                val mediaType = MediaTypeUtils.getMediaType(file.name) ?: return@mapNotNull null
                if (mediaType !in supportedTypes) return@mapNotNull null
                if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(file.length(), mediaType, sizeFilter)) return@mapNotNull null
                MediaFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    createdDate = file.lastModified(),
                    type = mediaType,
                    isDirectory = false
                )
            }
        }.sortedWith(compareBy<MediaFile> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    
    private suspend fun listDirectoryContentsSAF(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            val uri = SafHelper.parseUri(uriString)
            if (context.contentResolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return@withContext emptyList()
            
            val folder = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()
            
            val children = folder.listFiles()
            val visibleChildren = if (showHiddenFiles) children.toList() else children.filter { !it.name.orEmpty().startsWith(".") }
            
            visibleChildren.mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                if (file.isDirectory) {
                    if (TrashFolderContract.matchesTrashSegment(name)) return@mapNotNull null
                    MediaFile(
                        name = name,
                        path = file.uri.toString(),
                        size = 0L,
                        createdDate = file.lastModified(),
                        type = MediaType.IMAGE, // Placeholder type for folders
                        isDirectory = true,
                        childCount = file.listFiles().size
                    )
                } else {
                    val mime = file.type
                    val mediaType = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mime, name) ?: return@mapNotNull null
                    if (mediaType !in supportedTypes) return@mapNotNull null
                    if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(file.length(), mediaType, sizeFilter)) return@mapNotNull null
                    
                    MediaFile(
                        name = name,
                        path = file.uri.toString(),
                        size = file.length(),
                        createdDate = file.lastModified(),
                        type = mediaType,
                        isDirectory = false
                    )
                }
            }.sortedWith(compareBy<MediaFile> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } catch (e: Exception) {
            Timber.e(e, "Error listing SAF directory contents")
            emptyList()
        }
    }

    /** Fast SAF scan via cursor query (10-20× faster than DocumentFile.listFiles); falls back on failure. */
    private suspend fun scanFolderSAFFast(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val treeUri = SafHelper.parseUri(uriString)
            if (context.contentResolver.persistedUriPermissions.none { it.uri == treeUri && it.isReadPermission }) {
                Timber.w("LocalMediaScanner.scanFolderSAFFast: No permission for $uriString")
                return@withContext emptyList()
            }
            
            val results = mutableListOf<MediaFile>()
            val foldersToScan = ArrayDeque<Uri>()

            if (uriString.contains("mp3", ignoreCase = true)) {
                 Timber.d("LocalMediaScanner: (SAF Fast) Scanning mp3 folder: $uriString")
            }
            
            // Get document ID from tree URI to build child documents URI
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
            foldersToScan.add(rootChildrenUri)
            
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            
            var processedCount = 0
            
            while (foldersToScan.isNotEmpty()) {
                val childrenUri = foldersToScan.removeFirst()
                
                context.contentResolver.query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIndex)
                        val name = cursor.getString(nameIndex) ?: continue
                        val mime = cursor.getString(mimeIndex)
                        val size = cursor.getLong(sizeIndex)
                        val lastModified = cursor.getLong(modifiedIndex)
                        
                        if (!showHiddenFiles && name.startsWith(".")) continue
                        val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR

                        if (isDirectory) {
                            if (TrashFolderContract.matchesTrashSegment(name)) continue
                            if (scanSubdirectories) {
                                val subFolderUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                                foldersToScan.add(subFolderUri)
                            }
                        } else {
                            val mediaType = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mime, name)
                            if (mediaType != null && mediaType in supportedTypes) {
                                if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(size, mediaType, sizeFilter)) {
                                    if (mediaType == MediaType.AUDIO && name.endsWith(".mp3", ignoreCase = true)) {
                                        Timber.d("LocalMediaScanner: (SAF Fast) Found MP3: $name")
                                    }
                                    results.add(MediaFile(
                                        name = name,
                                        path = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString(),
                                        size = size,
                                        createdDate = lastModified,
                                        type = mediaType
                                    ))
                                    
                                    processedCount++
                                    if (processedCount % 50 == 0) {
                                        onProgress?.onProgress(processedCount, name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Timber.d("LocalMediaScanner.scanFolderSAFFast: Scanned ${results.size} files in ${elapsed}ms (fast cursor)")
            
            results
        } catch (e: Exception) {
            Timber.w(e, "LocalMediaScanner.scanFolderSAFFast: Fast scan failed, falling back to slow method")
            // Fallback to slow DocumentFile-based scanning
            scanFolderSAF(uriString, supportedTypes, sizeFilter, scanSubdirectories, onProgress)
        }
    }

    private suspend fun scanFolderSAF(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        onProgress: ScanProgressCallback? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            val uri = SafHelper.parseUri(uriString)
            if (context.contentResolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return@withContext emptyList()

            val folder = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

            val files = if (scanSubdirectories) collectDocumentFilesRecursivelyParallel(folder) else folder.listFiles().filter { it.isFile }

            var processedCount = 0
            files.mapNotNull { file ->
                 if (++processedCount % 10 == 0) onProgress?.onProgress(processedCount, file.name)

                 val mime = file.type
                 val type = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mime, file.name ?: "")
                 if (type != null && type in supportedTypes) {
                     if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), type, sizeFilter)) {
                         MediaFile(
                             name = file.name ?: "unknown",
                             path = file.uri.toString(),
                             size = file.length(),
                             createdDate = file.lastModified(),
                             type = type
                         )
                     } else null
                 } else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SAF")
            emptyList()
        }
    }
    
    private suspend fun getFileCountSAF(uriString: String, supportedTypes: Set<MediaType>, sizeFilter: SizeFilter?, scanSubdirectories: Boolean = false): Int {
         return scanFolderSAF(uriString, supportedTypes, sizeFilter, scanSubdirectories, null).size
    }

    private suspend fun isWritableSAF(uriString: String): Boolean {
         val uri = SafHelper.parseUri(uriString)
         val folder = DocumentFile.fromTreeUri(context, uri)
         return folder != null && folder.exists() && folder.canWrite()
    }

    private fun collectDocumentFilesRecursivelyParallel(folder: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(folder)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            curr.listFiles().forEach {
                if (it.isDirectory && it.name?.let(TrashFolderContract::matchesTrashSegment) != true) queue.add(it)
                else if (it.isFile) result.add(it)
            }
        }
        return result
    }
    
    private fun collectFilesRecursively(folder: File): List<File> {
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(folder)
        while (queue.isNotEmpty()) {
             val curr = queue.removeFirst()
             curr.listFiles()?.forEach { 
                 if (it.isDirectory && !TrashFolderContract.matchesTrashSegment(it.name)) queue.add(it)
                 else if (it.isFile) result.add(it)
             }
        }
        return result
    }
}