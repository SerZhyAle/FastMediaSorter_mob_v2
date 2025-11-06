package com.sza.fastmediasorter.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class LocalImageInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
)

/**
 * Client for accessing local storage media files using dual access patterns.
 * Supports both MediaStore API for standard Android folders and Storage Access Framework
 * for user-selected custom folders. Provides efficient file scanning with progress callbacks
 * and supports images and videos with size filtering.
 *
 * @property context Android context for accessing content resolver and file operations
 */
class LocalStorageClient(
    private val context: Context,
) {
    /**
     * Callback for reporting progress during file scanning
     */
    interface ScanProgressCallback {
        /**
         * Called periodically during scanning with current progress
         * @param scannedCount Number of files scanned so far
         * @param totalCount Total number of files (if known, -1 otherwise)
         * @param currentBatch Current batch being processed
         */
        fun onProgress(
            scannedCount: Int,
            totalCount: Int,
            currentBatch: Int,
        )

        /**
         * Called when scanning is complete
         * @param totalFiles Total number of media files found
         * @param durationMs Time taken for scanning in milliseconds
         */
        fun onComplete(
            totalFiles: Int,
            durationMs: Long,
        )
    }

    /**
     * Get image files from local storage using dual-access pattern:
     * 1. MediaStore access (by bucketName) - for folders found via SCAN
     * 2. SAF/DocumentFile access (by folderUri) - for user-selected folders
     *
     * @param folderUri TreeUri from SAF (Storage Access Framework), or null for MediaStore
     * @param bucketName BUCKET_DISPLAY_NAME from MediaStore, or null for SAF
     * @param isVideoEnabled Whether to include video files
     * @param maxVideoSizeMb Maximum video file size in MB
     * @param progressCallback Optional callback for progress reporting during SAF scanning
     * @param batchSize Size of batches for progressive scanning (default 100)
     * @return List of media files (images and optionally videos)
     */
    suspend fun getImageFiles(
        folderUri: Uri?,
        bucketName: String? = null,
        isVideoEnabled: Boolean = false,
        maxVideoSizeMb: Int = 100,
        progressCallback: ScanProgressCallback? = null,
        batchSize: Int = 100,
    ): List<LocalImageInfo> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            Logger.d(
                "LocalStorageClient",
                "getImageFiles - bucketName: $bucketName, folderUri: $folderUri, batchSize: $batchSize",
            )

            // Priority 1: MediaStore access (for SCAN-discovered folders)
            if (bucketName != null) {
                val result = getImageFilesByBucketName(bucketName, isVideoEnabled, maxVideoSizeMb)
                val duration = System.currentTimeMillis() - startTime
                Logger.d("LocalStorageClient", "MediaStore query took ${duration}ms, found ${result.size} files")
                progressCallback?.onComplete(result.size, duration)
                return@withContext result
            }

            // Priority 2: SAF/DocumentFile access (for user-selected folders)
            if (folderUri == null) {
                Logger.d("LocalStorageClient", "No folder URI provided")
                progressCallback?.onComplete(0, System.currentTimeMillis() - startTime)
                return@withContext emptyList()
            }

            val maxVideoSizeBytes = maxVideoSizeMb * 1024L * 1024L

            try {
                Logger.d("LocalStorageClient", "Starting progressive DocumentFile scan...")
                val folder = DocumentFile.fromTreeUri(context, folderUri)

                if (folder == null || !folder.exists() || !folder.isDirectory) {
                    Logger.e("LocalStorageClient", "Invalid folder URI or folder doesn't exist")
                    progressCallback?.onComplete(0, System.currentTimeMillis() - startTime)
                    return@withContext emptyList()
                }

                // Get all files first (unfortunately DocumentFile doesn't support lazy loading)
                val allFiles = folder.listFiles()
                if (allFiles.isNullOrEmpty()) {
                    Logger.d("LocalStorageClient", "No files in folder")
                    progressCallback?.onComplete(0, System.currentTimeMillis() - startTime)
                    return@withContext emptyList()
                }

                Logger.d(
                    "LocalStorageClient",
                    "Found ${allFiles.size} total files, processing in batches of $batchSize",
                )

                val result = mutableListOf<LocalImageInfo>()
                var batchNumber = 0
                var totalScanned = 0

                // Process files in batches to prevent UI blocking
                allFiles.asSequence().chunked(batchSize).forEach { batch ->
                    batchNumber++

                    // Process current batch
                    val batchResults =
                        batch.mapNotNull { file ->
                            totalScanned++

                            if (!file.isFile) return@mapNotNull null

                            val mimeType = file.type ?: return@mapNotNull null
                            val isImage = mimeType.startsWith("image/")
                            val isVideo = mimeType.startsWith("video/")

                            when {
                                isImage ->
                                    LocalImageInfo(
                                        uri = file.uri,
                                        name = file.name ?: "unknown",
                                        size = file.length(),
                                        dateModified = file.lastModified(),
                                    )
                                isVideo && isVideoEnabled -> {
                                    val fileSize = file.length()
                                    if (fileSize <= maxVideoSizeBytes) {
                                        LocalImageInfo(
                                            uri = file.uri,
                                            name = file.name ?: "unknown",
                                            size = fileSize,
                                            dateModified = file.lastModified(),
                                        )
                                    } else {
                                        null
                                    }
                                }
                                else -> null
                            }
                        }

                    result.addAll(batchResults)

                    // Report progress after each batch
                    progressCallback?.onProgress(totalScanned, allFiles.size, batchNumber)

                    // Yield control to prevent blocking UI for too long
                    // This allows other coroutines to run and UI to remain responsive
                    if (batchNumber % 5 == 0) { // Every 5 batches
                        delay(1) // Minimal delay to yield
                    }
                }

                // Sort results by name
                val sortedResult = result.sortedBy { it.name }

                val totalDuration = System.currentTimeMillis() - startTime
                Logger.d(
                    "LocalStorageClient",
                    "DocumentFile scan completed in ${totalDuration}ms, found ${sortedResult.size} media files from ${allFiles.size} total files",
                )

                progressCallback?.onComplete(sortedResult.size, totalDuration)

                sortedResult
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Logger.e("LocalStorageClient", "Failed after ${duration}ms", e)
                progressCallback?.onComplete(0, duration)
                emptyList()
            }
        }

    suspend fun scanStandardFolders(): Map<String, Pair<Uri?, Int>> =
        withContext(Dispatchers.IO) {
            val folders = mutableMapOf<String, Pair<Uri?, Int>>()
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                )
            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            try {
                context
                    .contentResolver
                    .query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder,
                    )?.use { cursor ->
                        val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        val counts = mutableMapOf<String, Int>()
                        while (cursor.moveToNext()) {
                            val bucket = cursor.getString(bucketColumn) ?: continue
                            counts[bucket] = (counts[bucket] ?: 0) + 1
                        }
                        val standardFolders =
                            mapOf(
                                "Camera" to "📷",
                                "Screenshots" to "📸",
                                "Pictures" to "🖼️",
                                "Download" to "📥",
                            )
                        standardFolders.forEach { (folderName, _) ->
                            val count = counts[folderName] ?: 0
                            folders[folderName] = Pair(null, count)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext folders
        }

    suspend fun scanAllImageFolders(): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val folders = mutableMapOf<String, Int>()
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                )
            val sortOrder = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
            try {
                context
                    .contentResolver
                    .query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder,
                    )?.use { cursor ->
                        val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val bucket = cursor.getString(bucketColumn) ?: continue
                            folders[bucket] = (folders[bucket] ?: 0) + 1
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext folders
        }

    suspend fun getImageFilesByBucketName(
        bucketName: String,
        isVideoEnabled: Boolean = false,
        maxVideoSizeMb: Int = 100,
    ): List<LocalImageInfo> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            Logger.d("LocalStorageClient", "MediaStore query for bucket '$bucketName'")

            val mediaFiles = mutableListOf<LocalImageInfo>()
            val maxVideoSizeBytes = maxVideoSizeMb * 1024L * 1024L

            // Determine query method based on bucket name
            val standardFolders = setOf("Camera", "Screenshots", "Pictures", "Download")
            val useBucketDisplayName = bucketName in standardFolders

            val selection: String
            val selectionArgs: Array<String>

            if (useBucketDisplayName) {
                // Use BUCKET_DISPLAY_NAME for standard Android folders
                selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
                selectionArgs = arrayOf(bucketName)
            } else {
                // Use DATA LIKE for custom/user folders
                selection = "${MediaStore.Images.Media.DATA} LIKE ?"
                selectionArgs = arrayOf("%/$bucketName/%")
            }

            val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

            var imageCount = 0
            var videoCount = 0
            var skippedLargeVideo = 0

            // Query images
            val imageProjection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                )

            try {
                context
                    .contentResolver
                    .query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        imageProjection,
                        selection,
                        selectionArgs,
                        sortOrder,
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                        imageCount = cursor.count

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                            mediaFiles.add(
                                LocalImageInfo(
                                    uri = uri,
                                    name = cursor.getString(nameColumn),
                                    size = cursor.getLong(sizeColumn),
                                    dateModified = cursor.getLong(dateColumn) * 1000,
                                ),
                            )
                        }
                    }
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Image query failed", e)
            } // Query videos if enabled
            if (isVideoEnabled) {
                val videoProjection =
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media.DATA,
                    )
                try {
                    context
                        .contentResolver
                        .query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            videoProjection,
                            selection,
                            selectionArgs,
                            sortOrder,
                        )?.use { cursor ->
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                            while (cursor.moveToNext()) {
                                val videoSize = cursor.getLong(sizeColumn)

                                if (videoSize <= maxVideoSizeBytes) {
                                    val id = cursor.getLong(idColumn)
                                    val uri =
                                        Uri.withAppendedPath(
                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            id.toString(),
                                        )
                                    mediaFiles.add(
                                        LocalImageInfo(
                                            uri = uri,
                                            name = cursor.getString(nameColumn),
                                            size = videoSize,
                                            dateModified = cursor.getLong(dateColumn) * 1000,
                                        ),
                                    )
                                    videoCount++
                                } else {
                                    skippedLargeVideo++
                                }
                            }
                        }
                } catch (e: Exception) {
                    Logger.e("LocalStorageClient", "Video query failed", e)
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Logger.d("LocalStorageClient", "Query completed in ${totalDuration}ms, found ${mediaFiles.size} files")

            return@withContext mediaFiles.sortedBy { it.name }
        }

    suspend fun downloadImage(uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(16384)
                        var count: Int
                        while (input.read(data, 0, data.size).also { count = it } != -1) {
                            buffer.write(data, 0, count)
                        }
                        buffer.toByteArray()
                    }
                result
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Error reading image from URI: $uri", e)
                null
            }
        }

    fun getFileInfo(uri: Uri): LocalImageInfo? {
        return try {
            val file = DocumentFile.fromSingleUri(context, uri)

            if (file != null && file.exists()) {
                LocalImageInfo(
                    uri = uri,
                    name = file.name ?: "unknown",
                    size = file.length(),
                    dateModified = file.lastModified(),
                )
            } else {
                Logger.e("LocalStorageClient", "File not found: $uri")
                null
            }
        } catch (e: Exception) {
            Logger.e("LocalStorageClient", "Error getting file info for URI: $uri", e)
            null
        }
    }

    suspend fun deleteImage(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Determine URI type and use appropriate deletion method
                val uriString = uri.toString()

                if (uriString.startsWith("content://com.android.externalstorage.documents/")) {
                    // SAF/DocumentFile URI - use DocumentFile API
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        val deleted = documentFile.delete()
                        return@withContext deleted
                    } else {
                        Logger.e("LocalStorageClient", "DocumentFile is null or doesn't exist for SAF URI")
                        return@withContext false
                    }
                } else {
                    // MediaStore URI - use ContentResolver
                    val deleted = context.contentResolver.delete(uri, null, null)
                    return@withContext deleted > 0
                }
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Exception during delete: ${e.javaClass.simpleName}: ${e.message}", e)
                return@withContext false
            }
        }

    companion object {
        fun hasMediaPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android
                        .content
                        .pm
                        .PackageManager
                        .PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android
                        .content
                        .pm
                        .PackageManager
                        .PERMISSION_GRANTED
            }
        }
    }

    suspend fun renameFile(
        uri: Uri,
        newFileName: String,
    ): Pair<Boolean, String>? =
        withContext(Dispatchers.IO) {
            try {
                // Determine URI type and use appropriate rename method
                val uriString = uri.toString()

                if (uriString.startsWith("content://com.android.externalstorage.documents/")) {
                    // SAF/DocumentFile URI - use DocumentFile API
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        val renamed = documentFile.renameTo(newFileName)
                        if (renamed) {
                            // Return the same URI since DocumentFile.renameTo() modifies the file in place
                            return@withContext Pair(true, uri.toString())
                        } else {
                            return@withContext Pair(false, uri.toString())
                        }
                    } else {
                        Logger.e("LocalStorageClient", "DocumentFile is null or doesn't exist for SAF URI")
                        return@withContext Pair(false, uri.toString())
                    }
                } else {
                    // MediaStore URI - use ContentResolver approach
                    // Get current file info
                    val projection =
                        arrayOf(
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                ._ID,
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .DISPLAY_NAME,
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .RELATIVE_PATH,
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .MIME_TYPE,
                        )

                    val cursor = context.contentResolver.query(uri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val idColumn =
                                it.getColumnIndexOrThrow(
                                    android
                                        .provider
                                        .MediaStore
                                        .MediaColumns
                                        ._ID,
                                )
                            val nameColumn =
                                it.getColumnIndexOrThrow(
                                    android
                                        .provider
                                        .MediaStore
                                        .MediaColumns
                                        .DISPLAY_NAME,
                                )
                            val pathColumn =
                                it.getColumnIndexOrThrow(
                                    android
                                        .provider
                                        .MediaStore
                                        .MediaColumns
                                        .RELATIVE_PATH,
                                )
                            val mimeColumn =
                                it.getColumnIndexOrThrow(
                                    android
                                        .provider
                                        .MediaStore
                                        .MediaColumns
                                        .MIME_TYPE,
                                )

                            val id = it.getLong(idColumn)
                            val oldName = it.getString(nameColumn)
                            val relativePath = it.getString(pathColumn)
                            val mimeType = it.getString(mimeColumn)

                            // Check if file with new name already exists
                            val collection =
                                when {
                                    mimeType.startsWith("image/") ->
                                        android
                                            .provider
                                            .MediaStore
                                            .Images
                                            .Media
                                            .EXTERNAL_CONTENT_URI
                                    mimeType.startsWith("video/") ->
                                        android
                                            .provider
                                            .MediaStore
                                            .Video
                                            .Media
                                            .EXTERNAL_CONTENT_URI
                                    else -> return@withContext Pair(false, uri.toString())
                                }

                            val checkSelection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                            val checkArgs = arrayOf(newFileName, relativePath)
                            val checkCursor =
                                context.contentResolver.query(
                                    collection,
                                    arrayOf(
                                        android
                                            .provider
                                            .MediaStore
                                            .MediaColumns
                                            ._ID,
                                    ),
                                    checkSelection,
                                    checkArgs,
                                    null,
                                )
                            val exists = checkCursor?.use { it.count > 0 } ?: false
                            checkCursor?.close()

                            if (exists) {
                                return@withContext Pair(false, uri.toString())
                            }

                            // Perform rename using ContentValues
                            val values =
                                android.content.ContentValues().apply {
                                    put(
                                        android
                                            .provider
                                            .MediaStore
                                            .MediaColumns
                                            .DISPLAY_NAME,
                                        newFileName,
                                    )
                                }

                            val updated = context.contentResolver.update(uri, values, null, null)
                            if (updated > 0) {
                                // Create new URI for renamed file
                                val newUri = android.content.ContentUris.withAppendedId(collection, id)
                                return@withContext Pair(true, newUri.toString())
                            } else {
                                return@withContext Pair(false, uri.toString())
                            }
                        }
                    }

                    return@withContext Pair(false, uri.toString())
                }
            } catch (securityException: android.app.RecoverableSecurityException) {
                Logger.e("LocalStorageClient", "RecoverableSecurityException - need user permission", securityException)
                // Re-throw to be handled by caller
                throw securityException
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Error renaming file", e)
                return@withContext Pair(false, uri.toString())
            }
        }

    suspend fun writeFile(
        destinationFolderUri: Uri,
        fileName: String,
        data: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Check if this is a DocumentFile TreeUri or MediaStore URI
                val uriString = destinationFolderUri.toString()

                if (uriString.startsWith("content://com.android.externalstorage.documents/tree/")) {
                    // SAF/DocumentFile approach for user-selected folders
                    val folder = DocumentFile.fromTreeUri(context, destinationFolderUri)
                    if (folder == null || !folder.isDirectory) {
                        Logger.e("LocalStorageClient", "Invalid destination folder URI")
                        return@withContext false
                    }

                    // Determine MIME type from file extension
                    val mimeType =
                        when {
                            fileName.endsWith(".jpg", ignoreCase = true) ||
                                fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
                            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                            fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                            else -> "application/octet-stream"
                        }

                    // Check if file already exists
                    val existingFile = folder.listFiles().find { it.name == fileName }
                    if (existingFile != null) {
                        Logger.w("LocalStorageClient", "File already exists: $fileName")
                        return@withContext false
                    }

                    // Create new file
                    val newFile = folder.createFile(mimeType, fileName)
                    if (newFile == null) {
                        Logger.e("LocalStorageClient", "Failed to create file")
                        return@withContext false
                    }

                    // Write data
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        output.write(data)
                        output.flush()
                    }

                    return@withContext true
                } else {
                    Logger.e("LocalStorageClient", "Unsupported URI format for write: $destinationFolderUri")
                    return@withContext false
                }
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Error writing file", e)
                return@withContext false
            }
        }

    suspend fun writeFileToStandardFolder(
        fileName: String,
        data: ByteArray,
        folderName: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Determine MIME type and collection
                val mimeType =
                    when {
                        fileName.endsWith(".jpg", ignoreCase = true) ||
                            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                        fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                        fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
                        fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                        fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                        else -> "application/octet-stream"
                    }

                val collection =
                    when {
                        mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else -> {
                            Logger.e("LocalStorageClient", "Unsupported MIME type: $mimeType")
                            return@withContext false
                        }
                    }

                // Map folder names to relative paths
                val relativePath =
                    when (folderName) {
                        "Camera" -> "DCIM/Camera/"
                        "Screenshots" -> "Pictures/Screenshots/"
                        "Pictures" -> "Pictures/"
                        "Download" -> "Download/"
                        else -> "Pictures/"
                    }

                // Prepare ContentValues
                val values =
                    android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                // Insert new file into MediaStore
                val newUri = context.contentResolver.insert(collection, values)
                if (newUri == null) {
                    Logger.e("LocalStorageClient", "Failed to create file in MediaStore")
                    return@withContext false
                }

                // Write data to the file
                try {
                    context.contentResolver.openOutputStream(newUri)?.use { output ->
                        output.write(data)
                        output.flush()
                    }

                    // Mark file as ready (not pending)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val readyValues =
                            android.content.ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }
                        context.contentResolver.update(newUri, readyValues, null, null)
                    }

                    return@withContext true
                } catch (e: Exception) {
                    // If write failed, delete the created file
                    context.contentResolver.delete(newUri, null, null)
                    Logger.e("LocalStorageClient", "Failed to write data, file deleted", e)
                    return@withContext false
                }
            } catch (e: Exception) {
                Logger.e("LocalStorageClient", "Error writing file to standard folder", e)
                return@withContext false
            }
        }
}
