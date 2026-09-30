package com.sza.fastmediasorter.data.repository

import android.content.ContentResolver
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.model.MediaType
import java.io.File

/**
 * S3960: the folder-scoped MediaStore selection and the count-only walk of a folder's direct children.
 *
 * Lives beside [MediaStoreRepositoryImpl] rather than inside it because that class already sits at
 * detekt's LargeClass ceiling.
 */
internal fun mediaStoreFolderPathArg(folderPath: String): String =
    if (folderPath.endsWith("/")) folderPath else "$folderPath/"

/**
 * The DATA selection for rows under [pathArg]. A non-recursive listing also excludes `<folder>/%/%`, so
 * the provider returns direct children only instead of every descendant of DCIM or Download for the
 * caller to drop in memory.
 */
internal fun mediaStoreFolderSelection(pathArg: String, recursive: Boolean): Pair<String, Array<String>> {
    // Escape SQLite LIKE wildcards (% and _) in the path to prevent matching unrelated files
    val escaped = pathArg.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    val like = "${MediaStore.Files.FileColumns.DATA} LIKE ? ESCAPE '\\'"
    return if (recursive) {
        like to arrayOf("$escaped%")
    } else {
        "$like AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ? ESCAPE '\\'" to
            arrayOf("$escaped%", "$escaped%/%")
    }
}

/**
 * Row count and media types of the visible direct children of [folderPath], filtered as a non-recursive
 * listing with hidden files off filters them, without building a MediaFile per row.
 */
internal fun countMediaStoreDirectChildren(
    resolver: ContentResolver,
    folderPath: String,
    allowedTypes: Set<MediaType>,
    isTrashPath: (String) -> Boolean,
    resolveType: (String, String?, Int) -> MediaType?,
): Pair<Int, Set<MediaType>> {
    val pathArg = mediaStoreFolderPathArg(folderPath)
    val (selection, selectionArgs) = mediaStoreFolderSelection(pathArg, recursive = false)
    val projection = arrayOf(
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
    )
    var count = 0
    val types = mutableSetOf<MediaType>()
    val uri = MediaStore.Files.getContentUri("external")
    resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        while (cursor.moveToNext()) {
            val path = cursor.getString(dataCol).orEmpty()
            if (!isVisibleDirectChild(path, pathArg, isTrashPath)) continue
            val name = cursor.getString(nameCol) ?: File(path).name
            val type = resolveType(name, cursor.getString(mimeCol), cursor.getInt(typeCol))
            if (type != null && type in allowedTypes) {
                count++
                types += type
            }
        }
    }
    return count to types
}

private fun isVisibleDirectChild(path: String, pathArg: String, isTrashPath: (String) -> Boolean): Boolean {
    if (path.isEmpty() || isTrashPath(path)) return false
    val relative = path.removePrefix(pathArg)
    return !relative.contains('/') && !relative.startsWith(".")
}
