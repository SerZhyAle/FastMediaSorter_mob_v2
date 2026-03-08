package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.provider.MediaStore
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class MediaStoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
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

        if (mediaTypeConditions.isNotEmpty()) {
            selectionBuilder.append("(${mediaTypeConditions.joinToString(" OR ")})")
        }

        val otherTypes = allowedTypes.filter { it !in setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO) }
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
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $queryLimit"
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val durCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    if (isTrashPath(path)) continue
                    val name = cursor.getString(nameCol) ?: File(path).name
                    val mime = cursor.getString(mimeCol)
                    val mediaTypeInt = cursor.getInt(typeCol)
                    val type = resolveType(name, mime, mediaTypeInt) ?: continue

                    if (type !in allowedTypes) continue

                    val size = cursor.getLong(sizeCol)
                    val createdDate = cursor.getLong(dateCol) * 1000L
                    val duration = if (durCol != -1) cursor.getLong(durCol) else null
                    val width = if (widthCol != -1) cursor.getInt(widthCol).let { if (it == 0) null else it } else null
                    val height = if (heightCol != -1) cursor.getInt(heightCol).let { if (it == 0) null else it } else null

                    recentFiles.add(
                        com.sza.fastmediasorter.domain.model.MediaFile(
                            name = name,
                            path = path,
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
    
    private fun resolveType(name: String, mime: String?, mediaTypeInt: Int): MediaType? {
         // Special handling for GIF which is technically an IMAGE in MediaStore
         if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE && name.endsWith(".gif", ignoreCase = true)) {
             return MediaType.GIF
         }

         // Fast check via MediaType constant
         when (mediaTypeInt) {
             MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> return MediaType.IMAGE
             MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> return MediaType.VIDEO
             MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> return MediaType.AUDIO
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
        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("$pathArg%")
        
        try {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            )?.use { cursor ->
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
}
