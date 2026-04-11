package com.sza.fastmediasorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.content.ContentUris
import android.provider.MediaStore
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

class MediaStoreRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MediaStoreRepository {

    private fun isTrashPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val segments = normalized.split('/')
        return segments.any { it.startsWith(".trash") }
    }

    private fun buildSelectionForAllowedTypes(allowedTypes: Set<MediaType>): String? {
        val selectionBuilder = StringBuilder()

        val mediaTypeConditions = mutableListOf<String>()
        if (allowedTypes.contains(MediaType.IMAGE)) mediaTypeConditions.add("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}")
        if (allowedTypes.contains(MediaType.VIDEO)) mediaTypeConditions.add("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}")
        if (allowedTypes.contains(MediaType.AUDIO)) mediaTypeConditions.add("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}")

        // Document types: filter via MIME_TYPE for database-level efficiency
        if (allowedTypes.contains(MediaType.TEXT)) mediaTypeConditions.add("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')")
        if (allowedTypes.contains(MediaType.PDF)) mediaTypeConditions.add("(LOWER(${MediaStore.Files.FileColumns.MIME_TYPE}) = 'application/pdf')")
        // EPUB: match by MIME type OR by filename extension OR by full path extension.
        // MediaStore indexes .epub inconsistently across devices/OEMs/API levels:
        // some use application/epub+zip, others application/octet-stream or NULL.
        // DATA covers files that have a row but no recognized MIME (e.g. placed via ADB).
        if (allowedTypes.contains(MediaType.EPUB)) mediaTypeConditions.add(
            "(LOWER(${MediaStore.Files.FileColumns.MIME_TYPE}) = 'application/epub+zip' " +
            "OR LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.epub' " +
            "OR LOWER(${MediaStore.Files.FileColumns.DATA}) LIKE '%.epub')"
        )

        if (mediaTypeConditions.isNotEmpty()) {
            selectionBuilder.append("(${mediaTypeConditions.joinToString(" OR ")})")
        }

        val otherTypes = allowedTypes.filter { it !in setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.TEXT, MediaType.PDF, MediaType.EPUB) }
        if (otherTypes.isNotEmpty()) {
            if (selectionBuilder.isNotEmpty()) selectionBuilder.append(" OR ")
            selectionBuilder.append("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})")
            if (allowedTypes.contains(MediaType.GIF) && !allowedTypes.contains(MediaType.IMAGE)) {
                selectionBuilder.append(" OR (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE})")
            }
        }

        return selectionBuilder.takeIf { it.isNotEmpty() }?.toString()
    }

    override suspend fun getFoldersWithMedia(allowedTypes: Set<MediaType>): List<MediaStoreRepository.FolderInfo> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, FolderBuilder>()
        
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        
        val selection = buildSelectionForAllowedTypes(allowedTypes)
        if (selection == null) {
            return@withContext emptyList()
        }
        
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    if (isTrashPath(path)) continue
                    val name = cursor.getString(nameColumn) ?: ""
                    val mimeType = cursor.getString(mimeColumn)
                    val mediaTypeInt = cursor.getInt(mediaTypeColumn)
                    
                    // Determine Type
                    val type = resolveType(name, mimeType, mediaTypeInt) ?: continue
                    
                    if (type in allowedTypes) {
                        val parentPath = File(path).parent ?: continue
                        val parentName = File(parentPath).name
                        
                        // Filter out hidden folders (starting with dot)
                        if (parentName.startsWith(".")) continue

                        // Use two-segment name when parent folder name is short (< 15 chars)
                        // to avoid ambiguity (e.g. "Telegram" inside both Movies and Pictures).
                        val grandParentName = File(parentPath).parentFile?.name.orEmpty()
                        val folderDisplayName = if (grandParentName.isNotEmpty()
                            && !grandParentName.startsWith(".")
                            && grandParentName.length < 15
                        ) {
                            "$grandParentName/$parentName"
                        } else {
                            parentName
                        }

                        val builder = folderMap.getOrPut(parentPath) { FolderBuilder(parentPath, folderDisplayName) }
                        builder.count++
                        builder.types.add(type)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying MediaStore")
        }
        
        folderMap.values.map { it.build() }
    }

    override suspend fun getRecentFiles(
        limit: Int,
        allowedTypes: Set<MediaType>
    ): List<com.sza.fastmediasorter.domain.model.MediaFile> = withContext(Dispatchers.IO) {
        val queryLimit = limit.coerceAtLeast(1)
        val selection = buildSelectionForAllowedTypes(allowedTypes) ?: return@withContext emptyList()
        val recentFiles = mutableListOf<com.sza.fastmediasorter.domain.model.MediaFile>()
        val uri = MediaStore.Files.getContentUri("external")

        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.DURATION)
        }

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(ContentResolver.QUERY_ARG_LIMIT, queryLimit)
                }
                context.contentResolver.query(uri, projection.toTypedArray(), queryArgs, null)
            } else {
                context.contentResolver.query(
                    uri,
                    projection.toTypedArray(),
                    selection,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $queryLimit"
                )
            }
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val widthCol = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val durCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                } else {
                    -1
                }

                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    if (isTrashPath(path)) continue
                    val name = c.getString(nameCol) ?: File(path).name
                    val mime = c.getString(mimeCol)
                    val mediaTypeInt = c.getInt(typeCol)
                    val type = resolveType(name, mime, mediaTypeInt) ?: continue

                    if (type !in allowedTypes) continue

                    val id = c.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val size = c.getLong(sizeCol)
                    val createdDate = c.getLong(dateCol) * 1000L
                    val duration = if (durCol != -1) c.getLong(durCol) else null
                    val width = if (widthCol != -1) c.getInt(widthCol).let { if (it == 0) null else it } else null
                    val height = if (heightCol != -1) c.getInt(heightCol).let { if (it == 0) null else it } else null

                    recentFiles.add(
                        com.sza.fastmediasorter.domain.model.MediaFile(
                            name = name,
                            path = path,
                            contentUri = contentUri,
                            size = size,
                            createdDate = createdDate,
                            type = type,
                            duration = duration,
                            width = width,
                            height = height,
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
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying recent files from MediaStore")
        }

        recentFiles
    }

    override suspend fun getAllFilesByTypes(
        allowedTypes: Set<MediaType>,
        limit: Int,
        showHiddenFiles: Boolean
    ): List<com.sza.fastmediasorter.domain.model.MediaFile> = withContext(Dispatchers.IO) {
        val queryLimit = limit.coerceAtLeast(1)
        val selection = buildSelectionForAllowedTypes(allowedTypes) ?: return@withContext emptyList()
        Timber.d("getAllFilesByTypes: types=$allowedTypes selection=$selection")
        val result = mutableListOf<com.sza.fastmediasorter.domain.model.MediaFile>()
        val uri = MediaStore.Files.getContentUri("external")

        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.DURATION)
        }

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, queryLimit)
                }
                context.contentResolver.query(uri, projection.toTypedArray(), queryArgs, null)
            } else {
                context.contentResolver.query(
                    uri,
                    projection.toTypedArray(),
                    selection,
                    null,
                    "_id ASC LIMIT $queryLimit"
                )
            }
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val widthCol = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val durCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                } else {
                    -1
                }

                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    if (isTrashPath(path)) continue
                    val name = c.getString(nameCol) ?: File(path).name
                    if (!showHiddenFiles && name.startsWith(".")) continue
                    val mime = c.getString(mimeCol)
                    val mediaTypeInt = c.getInt(typeCol)
                    val type = resolveType(name, mime, mediaTypeInt) ?: continue
                    if (type !in allowedTypes) continue

                    val id = c.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val size = c.getLong(sizeCol)
                    val createdDate = c.getLong(dateCol) * 1000L
                    val duration = if (durCol != -1) c.getLong(durCol) else null
                    val width = if (widthCol != -1) c.getInt(widthCol).let { if (it == 0) null else it } else null
                    val height = if (heightCol != -1) c.getInt(heightCol).let { if (it == 0) null else it } else null

                    result.add(
                        com.sza.fastmediasorter.domain.model.MediaFile(
                            name = name,
                            path = path,
                            contentUri = contentUri,
                            size = size,
                            createdDate = createdDate,
                            type = type,
                            duration = duration,
                            width = width,
                            height = height,
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
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying all files by types from MediaStore")
        }

        result
    }

    override suspend fun countAllFilesByTypes(
        allowedTypes: Set<MediaType>,
        limit: Int,
        showHiddenFiles: Boolean,
        sizeFilter: SizeFilter?
    ): Int = withContext(Dispatchers.IO) {
        val queryLimit = limit.coerceAtLeast(1)
        val selection = buildSelectionForAllowedTypes(allowedTypes) ?: return@withContext 0
        val uri = MediaStore.Files.getContentUri("external")
        var count = 0

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, queryLimit)
                }
                context.contentResolver.query(uri, projection, queryArgs, null)
            } else {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    "_id ASC LIMIT $queryLimit"
                )
            }

            cursor?.use { resultCursor ->
                val dataCol = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val typeCol = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (resultCursor.moveToNext()) {
                    val path = resultCursor.getString(dataCol) ?: continue
                    if (isTrashPath(path)) continue

                    val name = resultCursor.getString(nameCol) ?: File(path).name
                    if (!showHiddenFiles && name.startsWith(".")) continue

                    val mime = resultCursor.getString(mimeCol)
                    val mediaTypeInt = resultCursor.getInt(typeCol)
                    val type = resolveType(name, mime, mediaTypeInt) ?: continue
                    if (type !in allowedTypes) continue

                    val size = resultCursor.getLong(sizeCol)
                    if (sizeFilter != null && size > 0L && !MediaTypeUtils.isFileSizeInRange(size, type, sizeFilter)) {
                        continue
                    }

                    count++
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting all files by types from MediaStore")
        }

        count
    }
    
    private fun resolveType(name: String, mime: String?, mediaTypeInt: Int): MediaType? {
         // Special handling for GIF which is technically an IMAGE in MediaStore
         if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE && name.endsWith(".gif", ignoreCase = true)) {
             return MediaType.GIF
         }

         // Fast check via MediaType constant
         when (mediaTypeInt) {
             MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> return MediaType.IMAGE
             MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> return MediaType.VIDEO
             MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> {
                 // Only accept audio formats we can actually play; unsupported codecs (APE, WV, etc.)
                 // are treated as binary so they don't appear in audio lists or slideshow.
                 val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                 if (ext in MediaTypeUtils.AUDIO_EXTENSIONS) return MediaType.AUDIO
                 return MediaType.BINARY_OTHER
             }
             // Note: MEDIA_TYPE_DOCUMENT exists in API 30+, but we use fallback for broader compatibility
         }
         
         // Fallback to name/mime for everything else (PDF, Text, EPub, or if MediaStore didn't classify)
         return MediaTypeUtils.getMediaType(name) ?: MediaTypeUtils.getMediaTypeFromMime(mime)
    }

    private class FolderBuilder(val path: String, val name: String) {
        var count = 0
        val types = mutableSetOf<MediaType>()
        fun build() = MediaStoreRepository.FolderInfo(path, name, count, types)
    }

    override suspend fun getFilesInFolder(
        folderPath: String, 
        allowedTypes: Set<MediaType>, 
        recursive: Boolean,
        showHiddenFiles: Boolean
    ): List<com.sza.fastmediasorter.domain.model.MediaFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<com.sza.fastmediasorter.domain.model.MediaFile>()
        val uri = MediaStore.Files.getContentUri("external")
        
        // Define projection - be careful with columns available on API 28
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT
        )
        
        // DURATION is API 29+ in Files table, but might work via polymorphic query in some cases
        // We'll try to request it, but handle missing column gracefully
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.DURATION)
        }

        // Append slash if missing to match children
        val pathArg = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        // Escape SQLite LIKE wildcards (% and _) in the path to prevent matching unrelated files
        val escapedPathArg = pathArg.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ? ESCAPE '\\'"
        val selectionArgs = arrayOf("$escapedPathArg%")
        
        try {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                
                // Optional columns
                val durCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) 
                                cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION) else -1
                val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    if (isTrashPath(path)) {
                        continue
                    }
                    
                    // Filter direct children only if not recursive
                    val relative = path.removePrefix(pathArg)
                    if (!recursive && relative.contains('/')) continue
                    
                    // Filter out hidden files/folders if showHiddenFiles is false
                    val fileName = relative.substringBefore('/')
                    if (!showHiddenFiles && fileName.startsWith(".")) {
                        Timber.v("Filtering hidden file: $fileName")
                        continue
                    }
                    
                    val name = cursor.getString(nameCol) ?: File(path).name
                    val mime = cursor.getString(mimeCol)
                    val mediaTypeInt = cursor.getInt(typeCol)
                    val type = resolveType(name, mime, mediaTypeInt) ?: continue
                    
                    if (type in allowedTypes) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(uri, id).toString()
                        val size = cursor.getLong(sizeCol)
                        // date_modified is seconds
                        val date = cursor.getLong(dateCol) * 1000L 
                        val duration = if (durCol != -1) cursor.getLong(durCol) else null
                        val width = if (widthCol != -1) cursor.getInt(widthCol).let { if(it==0) null else it } else null
                        val height = if (heightCol != -1) cursor.getInt(heightCol).let { if(it==0) null else it } else null
                        
                        files.add(
                            com.sza.fastmediasorter.domain.model.MediaFile(
                                name = name,
                                path = path,
                                contentUri = contentUri,
                                size = size,
                                createdDate = date,
                                type = type,
                                duration = duration,
                                width = width,
                                height = height,
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
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing files in folder: $folderPath")
        }
        
        files
    }

    override suspend fun getStandardFolders(): List<MediaStoreRepository.FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<MediaStoreRepository.FolderInfo>()
        val standardPaths = listOf(
            android.os.Environment.DIRECTORY_DOWNLOADS to "Downloads",
            android.os.Environment.DIRECTORY_DCIM to "Camera",
            android.os.Environment.DIRECTORY_PICTURES to "Pictures",
            android.os.Environment.DIRECTORY_MUSIC to "Music",
            android.os.Environment.DIRECTORY_MOVIES to "Movies"
        )
        
        for ((directory, displayName) in standardPaths) {
            try {
                val path = android.os.Environment.getExternalStoragePublicDirectory(directory)
                if (path != null) {
                    val allTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF, MediaType.PDF, MediaType.TEXT)
                    val files = if (path.exists()) {
                        getFilesInFolder(path.absolutePath, allTypes, recursive = false, showHiddenFiles = false)
                    } else {
                        emptyList()
                    }
                    
                    val types = if (files.isNotEmpty()) files.map { it.type }.toSet() else emptySet()
                    folders.add(MediaStoreRepository.FolderInfo(
                        path = path.absolutePath,
                        name = displayName,
                        fileCount = files.size,
                        containedTypes = types
                    ))
                }
            } catch (e: Exception) {
                Timber.w(e, "Cannot access standard folder: $displayName")
            }
        }
        
        folders
    }

    override suspend fun findCameraFolderPath(): String? = withContext(Dispatchers.IO) {
        val fallbackPath = "/storage/emulated/0/DCIM/Camera"
        val uri = MediaStore.Images.Media.getContentUri("external")
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("Camera")

        try {
            val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                }
                context.contentResolver.query(uri, projection, queryArgs, null)
            } else {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 1"
                )
            }

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val filePath = c.getString(dataCol)
                    if (!filePath.isNullOrEmpty()) {
                        val parentFolder = File(filePath).parent
                        if (!parentFolder.isNullOrEmpty()) {
                            Timber.d("MediaStoreRepository: Found camera folder via MediaStore: $parentFolder")
                            return@withContext parentFolder
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error finding Camera folder path in MediaStore")
        }

        Timber.d("MediaStoreRepository: Camera folder not found in MediaStore. Using fallback: $fallbackPath")
        fallbackPath
    }
}
