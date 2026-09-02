package com.sza.fastmediasorter.wear.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

private const val EXTERNAL_VOLUME = "external"

/** S2201 ADR-5: the window applied inside one level, matching `ListPhoneResourcePageUseCase`. */
private const val PAGE_SIZE = 50

/** MediaStore stores a relative path with this separator and a trailing one. */
private const val PATH_SEPARATOR = '/'

private val FOLDER_PROJECTION = arrayOf(
    MediaStore.MediaColumns._ID,
    MediaStore.MediaColumns.DISPLAY_NAME,
    MediaStore.MediaColumns.MIME_TYPE,
    MediaStore.MediaColumns.SIZE,
    MediaStore.MediaColumns.DATE_MODIFIED,
    MediaStore.MediaColumns.RELATIVE_PATH
)

/**
 * The folder walk over the watch's own storage, across both halves of it (S2201 ADR-3).
 *
 * The two halves are read by different mechanisms because only one of them is reachable by a
 * filesystem walk:
 *
 * - The app's own roots are walked with `java.io.File`. No permission is involved at any API level,
 *   and this is the only place a file MediaStore never indexed can be found - which is what makes
 *   `browse` show something the `all` category cannot.
 * - Shared storage is not walkable at targetSdk 36 without special access this app does not declare,
 *   so its hierarchy is reconstructed by grouping rows on `RELATIVE_PATH`. A directory holding no
 *   file therefore cannot appear: MediaStore indexes files, not folders.
 *
 * Provided via `WearAppModule.provideWearLocalFolderRepository`. No `@Inject constructor` and no
 * `@Singleton` here, matching `WearMediaRepositoryImpl` - both would duplicate the module's binding.
 */
class WearLocalFolderRepositoryImpl(
    private val context: Context,
    private val contentResolver: ContentResolver
) : WearLocalFolderRepository {

    override suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(window(entriesOf(address), offset))
            } catch (e: CancellationException) {
                // A cancelled walk is the caller leaving the screen, not a level that failed to read.
                throw e
            } catch (e: IOException) {
                Timber.w(e, "Folder level unreadable: %s", address)
                Result.failure(e)
            } catch (e: SecurityException) {
                Timber.w(e, "Folder level refused: %s", address)
                Result.failure(e)
            }
        }

    private fun entriesOf(address: WearFolderAddress): List<WearFolderEntry> = when (address) {
        is WearFolderAddress.Root -> rootEntries()
        is WearFolderAddress.AppOwned -> appOwnedEntries(address.path)
        is WearFolderAddress.MediaStoreFolder -> mediaStoreEntries(address.relativePath)
    }

    /**
     * The entrance: app-owned roots that actually hold something, then the top of shared storage.
     *
     * An empty root is omitted for the same reason the parent keeps an empty category out of the tile
     * set - an entry that opens onto nothing is a promise the surface cannot keep.
     */
    private fun rootEntries(): List<WearFolderEntry> {
        val owned = appOwnedRoots()
            .filter { it.isDirectory && it.list()?.isNotEmpty() == true }
            .map { directoryEntry(it.name, WearFolderAddress.AppOwned(it.absolutePath)) }
        return owned + sharedStorageTopLevel()
    }

    /** The distinct first `RELATIVE_PATH` segments MediaStore holds, each becoming one folder row. */
    private fun sharedStorageTopLevel(): List<WearFolderEntry> =
        queryRelativePaths(selection = null, selectionArgs = null)
            .mapNotNull { it.substringBefore(PATH_SEPARATOR).takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
            .map { segment ->
                val path = "$segment$PATH_SEPARATOR"
                directoryEntry(segment, WearFolderAddress.MediaStoreFolder(path))
            }

    /**
     * One real directory of the app-owned tree.
     *
     * The containment check is not defensive coding: the address arrives as a navigation argument, so
     * without it any path the route could carry would be listed, app-owned or not.
     */
    private fun appOwnedEntries(path: String): List<WearFolderEntry> {
        val directory = File(path)
        if (!isAppOwned(directory)) {
            Timber.w("Refused a folder level outside the app-owned roots: %s", path)
            return emptyList()
        }
        return directory.listFiles().orEmpty()
            .map(::fileSystemEntry)
            .sortedWith(compareByDescending<WearFolderEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun fileSystemEntry(child: File): WearFolderEntry = WearFolderEntry(
        name = child.name,
        address = if (child.isDirectory) WearFolderAddress.AppOwned(child.absolutePath) else null,
        uri = if (child.isDirectory) null else Uri.fromFile(child),
        isDirectory = child.isDirectory,
        mimeType = null,
        sizeBytes = if (child.isDirectory) 0L else child.length(),
        dateModifiedEpochSeconds = child.lastModified() / MILLIS_PER_SECOND
    )

    private fun appOwnedRoots(): List<File> = listOfNotNull(
        context.filesDir,
        context.cacheDir,
        context.getExternalFilesDir(null)?.parentFile
    )

    private fun isAppOwned(directory: File): Boolean {
        val candidate = canonicalOrNull(directory) ?: return false
        return appOwnedRoots().mapNotNull(::canonicalOrNull).any { root ->
            candidate == root || candidate.startsWith(root + File.separator)
        }
    }

    private fun canonicalOrNull(file: File): String? = try {
        file.canonicalPath
    } catch (e: IOException) {
        Timber.w(e, "Canonical path unavailable: %s", file.path)
        null
    }

    /**
     * One level of shared storage, split into the files sitting here and the folders below.
     *
     * Both halves come from one query: a row whose `RELATIVE_PATH` equals this level is a file of it,
     * and a row deeper than it contributes its next segment as a folder.
     */
    private fun mediaStoreEntries(relativePath: String): List<WearFolderEntry> {
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$relativePath%")
        val folders = queryRelativePaths(selection, selectionArgs)
            .mapNotNull { nextSegmentBelow(it, relativePath) }
            .distinct()
            .sorted()
            .map { segment ->
                val path = "$relativePath$segment$PATH_SEPARATOR"
                directoryEntry(segment, WearFolderAddress.MediaStoreFolder(path))
            }
        return folders + mediaStoreFiles(relativePath)
    }

    /** The segment directly below [level] in [path], or null when [path] is [level] itself. */
    private fun nextSegmentBelow(path: String, level: String): String? =
        path.removePrefix(level).substringBefore(PATH_SEPARATOR).takeIf(String::isNotEmpty)

    private fun mediaStoreFiles(relativePath: String): List<WearFolderEntry> {
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val entries = mutableListOf<WearFolderEntry>()
        contentResolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            FOLDER_PROJECTION,
            selection,
            arrayOf(relativePath),
            "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                entries += WearFolderEntry(
                    name = cursor.getString(nameIndex).orEmpty(),
                    address = null,
                    uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(EXTERNAL_VOLUME), id),
                    isDirectory = false,
                    mimeType = cursor.getString(mimeIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    dateModifiedEpochSeconds = cursor.getLong(modifiedIndex)
                )
            }
        }
        return entries
    }

    /**
     * Every `RELATIVE_PATH` matching the selection.
     *
     * Only that one column is read, so this is an index lookup rather than the full storage walk
     * S2130 §3.2 forbids - nothing here opens a file or touches the filesystem.
     */
    private fun queryRelativePaths(selection: String?, selectionArgs: Array<String>?): List<String> {
        val paths = mutableListOf<String>()
        contentResolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                cursor.getString(pathIndex)?.takeIf(String::isNotEmpty)?.let(paths::add)
            }
        }
        return paths
    }

    private fun directoryEntry(name: String, address: WearFolderAddress): WearFolderEntry =
        WearFolderEntry(
            name = name,
            address = address,
            uri = null,
            isDirectory = true,
            mimeType = null,
            sizeBytes = 0L,
            dateModifiedEpochSeconds = 0L
        )

    private fun window(entries: List<WearFolderEntry>, offset: Int): WearFolderPage {
        val page = entries.drop(offset).take(PAGE_SIZE)
        val consumed = offset + page.size
        return WearFolderPage(entries = page, nextOffset = consumed.takeIf { it < entries.size })
    }

    private companion object {
        /** `File.lastModified` is milliseconds; MediaStore's `DATE_MODIFIED` is seconds. */
        const val MILLIS_PER_SECOND = 1000L
    }
}
