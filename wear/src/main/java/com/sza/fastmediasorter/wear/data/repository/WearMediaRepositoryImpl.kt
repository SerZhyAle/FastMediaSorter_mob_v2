package com.sza.fastmediasorter.wear.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Implementation of WearMediaRepository using MediaStore API.
 * Provides access to local media files on the device.
 * 
 * Note: This class is provided via WearAppModule.provideWearMediaRepository
 * Do not add @Inject constructor or @Singleton as it would create duplicate bindings.
 * Singleton scope is managed by the @Provides method in the module.
 */
class WearMediaRepositoryImpl(
    private val contentResolver: ContentResolver
) : WearMediaRepository {

    override fun getMediaFiles(mediaType: MediaType): Flow<Result<List<WearMediaFile>>> = flow {
        try {
            Timber.d("Fetching media files for type: $mediaType")
            val files = queryMediaStore(mediaType)
            Timber.d("Found ${files.size} files for type: $mediaType")
            emit(Result.success(files))
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch media files for type: $mediaType")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getMediaFileById(id: Long, mediaType: MediaType): WearMediaFile? =
        withContext(Dispatchers.IO) {
            try {
                val files = queryMediaStore(mediaType, selectionId = id)
                files.firstOrNull()
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch media file by id: $id")
                null
            }
        }

    private fun queryMediaStore(
        mediaType: MediaType,
        selectionId: Long? = null
    ): List<WearMediaFile> {
        val (contentUri, projection, additionalColumns) = getMediaStoreConfig(mediaType)
        
        val selection = selectionId?.let {
            "${MediaStore.MediaColumns._ID} = ?"
        }
        val selectionArgs = selectionId?.let { arrayOf(it.toString()) }
        
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        
        val files = mutableListOf<WearMediaFile>()
        
        contentResolver.query(
            contentUri,
            projection + additionalColumns,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val albumIdColumn = if (mediaType == MediaType.MUSIC) {
                cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)
            } else -1
            val artistColumn = if (mediaType == MediaType.MUSIC) {
                cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)
            } else -1
            val albumColumn = if (mediaType == MediaType.MUSIC) {
                cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)
            } else -1
            val titleColumn = if (mediaType == MediaType.MUSIC) {
                cursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)
            } else -1
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn) else null
                val size = cursor.getLong(sizeColumn)
                val dateModified = cursor.getLong(dateColumn)
                val duration = if (durationColumn >= 0) cursor.getLongOrDefault(durationColumn, 0) else 0
                
                val fileUri = ContentUris.withAppendedId(contentUri, id)
                
                val albumArt = if (mediaType == MediaType.MUSIC && albumIdColumn >= 0) {
                    getAlbumArtUri(cursor.getLongOrDefault(albumIdColumn, -1))
                } else null
                
                files.add(
                    WearMediaFile(
                        id = id,
                        name = name,
                        uri = fileUri,
                        mimeType = mimeType,
                        size = size,
                        dateModified = dateModified,
                        duration = duration,
                        albumArt = albumArt,
                        artist = if (artistColumn >= 0) cursor.getString(artistColumn) else null,
                        album = if (albumColumn >= 0) cursor.getString(albumColumn) else null,
                        title = if (titleColumn >= 0) cursor.getString(titleColumn) else null
                    )
                )
            }
        }
        
        return files
    }
    
    private fun getMediaStoreConfig(mediaType: MediaType): Triple<Uri, Array<String>, Array<String>> {
        val baseProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        
        return when (mediaType) {
            MediaType.MUSIC -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                baseProjection,
                arrayOf(
                    MediaStore.MediaColumns.DURATION,
                    MediaStore.Audio.AudioColumns.ALBUM_ID,
                    // S1689: the network cover lookup asks by these two; nothing else reads them.
                    MediaStore.Audio.AudioColumns.ARTIST,
                    MediaStore.Audio.AudioColumns.ALBUM,
                    MediaStore.Audio.AudioColumns.TITLE
                )
            )
            MediaType.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                baseProjection,
                arrayOf(MediaStore.MediaColumns.DURATION)
            )
            MediaType.PHOTO -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                baseProjection,
                emptyArray()
            )
        }
    }
    
    private fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }
    
    private fun Cursor.getLongOrDefault(columnIndex: Int, default: Long): Long {
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getLong(columnIndex)
        } else {
            default
        }
    }
}
