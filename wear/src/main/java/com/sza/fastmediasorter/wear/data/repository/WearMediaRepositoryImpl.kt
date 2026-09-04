package com.sza.fastmediasorter.wear.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val EXTERNAL_VOLUME = "external"
private const val UNKNOWN_NAME = "Unknown"

/** A column the query did not project answers with a negative index, never with a value. */
private const val COLUMN_ABSENT = -1

/** Not a real album id, so the cover lookup answers with no artwork rather than a broken uri. */
private const val NO_ALBUM_ID = -1L

/**
 * Newest first, on every listing this repository publishes.
 *
 * S2130 §6 (carried from S2134) settled that "recent" is not a time window: the sort plus the first
 * page is what makes a file copied onto the watch a moment ago the first thing the wearer sees.
 */
private val NEWEST_FIRST = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

private val BASE_PROJECTION = arrayOf(
    MediaStore.MediaColumns._ID,
    MediaStore.MediaColumns.DISPLAY_NAME,
    MediaStore.MediaColumns.MIME_TYPE,
    MediaStore.MediaColumns.SIZE,
    MediaStore.MediaColumns.DATE_MODIFIED
)

/**
 * The office mime types the phone side recognises as a document, restated rather than imported.
 *
 * `wear` declares no dependency on `app_v2`, so `data/common/MediaTypeUtils` is out of reach and its
 * office table has to be mirrored; a family added there has to be added here in the same change.
 */
private val OFFICE_DOCUMENT_MIME_TYPES = listOf(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/rtf",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.presentation"
)

/**
 * What `MediaStore.Files` is asked for when the wearer opens Documents.
 *
 * The four families are the ones the phone's own selection already covers in
 * `data/repository/MediaStoreRepositoryImpl.buildSelectionForAllowedTypes`: text, pdf, epub and
 * office. Epub is matched by file name as well as by mime because MediaStore indexes it as
 * `application/octet-stream`, or as nothing at all, on some devices - which is why the phone
 * selection carries that same clause.
 */
private val DOCUMENT_SELECTION = listOf(
    "LOWER(${MediaStore.MediaColumns.MIME_TYPE}) LIKE 'text/%'",
    "LOWER(${MediaStore.MediaColumns.MIME_TYPE}) = 'application/pdf'",
    "LOWER(${MediaStore.MediaColumns.MIME_TYPE}) = 'application/epub+zip'",
    "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE '%.epub'",
    "LOWER(${MediaStore.MediaColumns.MIME_TYPE}) IN " +
        OFFICE_DOCUMENT_MIME_TYPES.joinToString(prefix = "(", postfix = ")") { "'$it'" }
).joinToString(" OR ")

/**
 * Implementation of WearMediaRepository using MediaStore API.
 * Provides access to local media files on the device.
 *
 * Note: This class is provided via WearAppModule.provideWearMediaRepository
 * Do not add @Inject constructor or @Singleton as it would create duplicate bindings.
 * Singleton scope is managed by the @Provides method in the module.
 */
class WearMediaRepositoryImpl(
    private val contentResolver: ContentResolver,
    private val preferencesRepository: WearPreferencesRepository? = null
) : WearMediaRepository {

    override fun getMediaFiles(mediaType: MediaType): Flow<Result<List<WearMediaFile>>> =
        listing(mediaType.name) { queryMediaStore(mediaType) }

    override fun getDocumentFiles(): Flow<Result<List<WearMediaFile>>> =
        listing("documents") { queryDocuments() }

    override fun getAllMediaFiles(): Flow<Result<List<WearMediaFile>>> = listing("flat listing") {
        val isAudio = preferencesRepository?.isAudioEnabled?.firstOrNull() ?: true
        val isVideo = preferencesRepository?.isVideoEnabled?.firstOrNull() ?: true
        val isImages = preferencesRepository?.isImagesEnabled?.firstOrNull() ?: true
        val isDocs = preferencesRepository?.isDocumentsEnabled?.firstOrNull() ?: true

        Timber.d("S2492: getAllMediaFiles with audio=$isAudio video=$isVideo images=$isImages docs=$isDocs")

        val mediaFiles = mutableListOf<WearMediaFile>()
        if (isAudio) mediaFiles.addAll(queryMediaStore(MediaType.MUSIC))
        if (isVideo) mediaFiles.addAll(queryMediaStore(MediaType.VIDEO))
        if (isImages) mediaFiles.addAll(queryMediaStore(MediaType.PHOTO))
        if (isDocs) mediaFiles.addAll(queryDocuments())

        mediaFiles.sortedByDescending { it.dateModified }
    }

    override suspend fun getMediaFileById(id: Long, mediaType: MediaType): WearMediaFile? =
        withContext(Dispatchers.IO) {
            try {
                val files = queryMediaStore(mediaType, selectionId = id)
                files.firstOrNull()
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to fetch media file by id: $id")
                null
            }
        }

    /**
     * The one shape every listing takes: a store that refuses becomes a Result the screen can render,
     * never an exception crossing the flow boundary and killing the browse screen.
     */
    private fun listing(
        label: String,
        query: suspend () -> List<WearMediaFile>
    ): Flow<Result<List<WearMediaFile>>> = flow {
        try {
            val files = query()
            Timber.d("Found ${files.size} watch file(s) for $label")
            emit(Result.success(files))
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to fetch watch files for $label")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun queryMediaStore(
        mediaType: MediaType,
        selectionId: Long? = null
    ): List<WearMediaFile> {
        val (contentUri, projection, additionalColumns) = getMediaStoreConfig(mediaType)
        return readFiles(
            contentUri = contentUri,
            projection = projection + additionalColumns,
            selection = selectionId?.let { "${MediaStore.MediaColumns._ID} = ?" },
            selectionArgs = selectionId?.let { arrayOf(it.toString()) },
            mediaType = mediaType
        )
    }

    /**
     * Documents belong to no typed collection, so they are read straight off `MediaStore.Files` with
     * a mime selection instead of through [getMediaStoreConfig].
     */
    private fun queryDocuments(): List<WearMediaFile> = readFiles(
        contentUri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
        projection = BASE_PROJECTION,
        selection = DOCUMENT_SELECTION,
        selectionArgs = null,
        mediaType = null
    )

    /**
     * [mediaType] is null on the `MediaStore.Files` path: the four audio columns exist only on the
     * audio collection, and a cursor over any other one does not carry them.
     */
    private fun readFiles(
        contentUri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        mediaType: MediaType?
    ): List<WearMediaFile> {
        val files = mutableListOf<WearMediaFile>()
        contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            NEWEST_FIRST
        )?.use { cursor ->
            val columns = MediaCursorColumns(cursor, isMusic = mediaType == MediaType.MUSIC)
            while (cursor.moveToNext()) {
                files.add(cursor.readFile(columns, contentUri))
            }
        }
        return files
    }

    private fun Cursor.readFile(columns: MediaCursorColumns, contentUri: Uri): WearMediaFile {
        val id = getLong(columns.id)
        return WearMediaFile(
            id = id,
            name = getString(columns.name) ?: UNKNOWN_NAME,
            uri = ContentUris.withAppendedId(contentUri, id),
            mimeType = getStringOrNull(columns.mime),
            size = getLong(columns.size),
            dateModified = getLong(columns.dateModified),
            duration = getLongOrDefault(columns.duration, 0),
            albumArt = getAlbumArtUri(getLongOrDefault(columns.albumId, NO_ALBUM_ID)),
            artist = getStringOrNull(columns.artist),
            album = getStringOrNull(columns.album),
            title = getStringOrNull(columns.title)
        )
    }

    private fun getMediaStoreConfig(mediaType: MediaType): Triple<Uri, Array<String>, Array<String>> =
        when (mediaType) {
            MediaType.MUSIC -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                BASE_PROJECTION,
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
                BASE_PROJECTION,
                arrayOf(MediaStore.MediaColumns.DURATION)
            )
            MediaType.PHOTO -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                BASE_PROJECTION,
                emptyArray()
            )
        }

    private fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) {
            return null
        }
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    private fun Cursor.getStringOrNull(columnIndex: Int): String? =
        if (columnIndex >= 0 && !isNull(columnIndex)) {
            getString(columnIndex)
        } else {
            null
        }

    private fun Cursor.getLongOrDefault(columnIndex: Int, default: Long): Long {
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getLong(columnIndex)
        } else {
            default
        }
    }

    /**
     * The column indexes a listing needs, resolved once per cursor instead of once per row.
     *
     * The optional ones - duration and the four audio columns - answer [COLUMN_ABSENT] when the
     * query did not project them, which is what lets one reader serve both the typed collections
     * and the untyped `MediaStore.Files` path.
     *
     * Nested rather than top-level because this package admits only `*Repository` /
     * `*RepositoryImpl` names at file scope, and this is a cursor detail rather than a repository.
     */
    private class MediaCursorColumns(cursor: Cursor, isMusic: Boolean) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mime = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val duration = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val albumId = cursor.audioColumn(isMusic, MediaStore.Audio.AudioColumns.ALBUM_ID)
        val artist = cursor.audioColumn(isMusic, MediaStore.Audio.AudioColumns.ARTIST)
        val album = cursor.audioColumn(isMusic, MediaStore.Audio.AudioColumns.ALBUM)
        val title = cursor.audioColumn(isMusic, MediaStore.Audio.AudioColumns.TITLE)
    }
}

private fun Cursor.audioColumn(isMusic: Boolean, column: String): Int =
    if (isMusic) {
        getColumnIndex(column)
    } else {
        COLUMN_ABSENT
    }
