package com.sza.fastmediasorter.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.database.SQLException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber
import java.io.File

/**
 * S1897: resolves one MediaStore row by its id.
 *
 * Lives beside [MediaStoreRepositoryImpl] rather than inside it because that class already sits at
 * detekt's LargeClass ceiling - adding another cursor walk to it fails the gate.
 */
internal fun queryMediaStoreItemById(
    resolver: ContentResolver,
    id: Long,
    isTrashPath: (String) -> Boolean,
    resolveType: (String, String?, Int) -> MediaType?,
    resolveCreatedDate: (String, Long) -> Long
): MediaFile? {
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        projection.add(MediaStore.Files.FileColumns.DURATION)
    }

    return try {
        resolver.query(
            uri,
            projection.toTypedArray(),
            "${MediaStore.Files.FileColumns._ID} = ?",
            arrayOf(id.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toMediaFile(uri, id, isTrashPath, resolveType, resolveCreatedDate)
            } else {
                null
            }
        }
    } catch (e: SecurityException) {
        Timber.e(e, "Not permitted to read MediaStore id $id")
        null
    } catch (e: SQLException) {
        Timber.e(e, "MediaStore refused the query for id $id")
        null
    }
}

private fun Cursor.toMediaFile(
    uri: Uri,
    id: Long,
    isTrashPath: (String) -> Boolean,
    resolveType: (String, String?, Int) -> MediaType?,
    resolveCreatedDate: (String, Long) -> Long
): MediaFile? {
    val path = getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
    // A trashed row still answers a query by id, so the exclusion the folder listing applies has to
    // hold here too, or the watch could open a file the user already deleted.
    if (path == null || isTrashPath(path)) return null

    val name = getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
        ?: File(path).name
    val durCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getColumnIndex(MediaStore.Files.FileColumns.DURATION)
    } else {
        -1
    }
    val widthCol = getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
    val heightCol = getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
    val dateCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

    return resolveType(
        name,
        getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
        getInt(getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
    )?.let { type ->
        MediaFile(
            name = name,
            path = path,
            contentUri = ContentUris.withAppendedId(uri, id).toString(),
            size = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
            createdDate = resolveCreatedDate(path, getLong(dateCol)),
            type = type,
            duration = if (durCol != -1) getLong(durCol) else null,
            width = if (widthCol != -1) getInt(widthCol).takeIf { it != 0 } else null,
            height = if (heightCol != -1) getInt(heightCol).takeIf { it != 0 } else null
        )
    }
}
