package com.sza.fastmediasorter.data.local

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SafHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Storage Access Framework half of [LocalMediaScanner] - everything reachable only through a
 * `content://` tree uri. It shares no state with the MediaStore path, so it takes [context] and
 * nothing else.
 *
 * Failures are funnelled through `runCatching` + [warnUnlessCancellation] rather than a broad
 * `catch (e: Exception)`: the helper rethrows coroutine cancellation, so a cancelled scan does not
 * silently degrade into the slow fallback or into an empty result.
 */
internal class SafMediaScanner(private val context: Context) {

    /** Fast SAF scan via cursor query (10-20x faster than DocumentFile.listFiles); falls back on failure. */
    suspend fun scanFolderFast(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("S2401: SafMediaScanner.scanFolderFast entered")
        val request = ScanRequest(uriString, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
        runCatching { cursorScan(request, onProgress) }.getOrElse { error ->
            error.warnUnlessCancellation("SafMediaScanner: fast cursor scan failed, using DocumentFile walk")
            scanFolder(uriString, supportedTypes, sizeFilter, scanSubdirectories, onProgress)
        }
    }

    /** Slow DocumentFile-based scan; also the fallback target of [scanFolderFast]. */
    suspend fun scanFolder(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        onProgress: ScanProgressCallback? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        runCatching {
            documentFileScan(uriString, supportedTypes, sizeFilter, scanSubdirectories, onProgress)
        }.getOrElse { error ->
            error.warnUnlessCancellation("SafMediaScanner: DocumentFile scan failed for $uriString")
            emptyList()
        }
    }

    suspend fun getFileCount(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean
    ): Int = scanFolder(uriString, supportedTypes, sizeFilter, scanSubdirectories, null).size

    suspend fun isWritable(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, SafHelper.parseUri(uriString))
        folder != null && folder.exists() && folder.canWrite()
    }

    /** Directory listing for the browser: folders first, then supported files. */
    suspend fun listDirectoryContents(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        runCatching {
            listChildren(uriString, supportedTypes, sizeFilter, showHiddenFiles)
        }.getOrElse { error ->
            error.warnUnlessCancellation("SafMediaScanner: listing failed for $uriString")
            emptyList()
        }
    }

    private suspend fun cursorScan(request: ScanRequest, onProgress: ScanProgressCallback?): List<MediaFile> {
        val treeUri = SafHelper.parseUri(request.uriString)
        val sink = ScanSink(onProgress)
        if (hasReadPermission(treeUri)) {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            sink.pending.add(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId))
            while (sink.pending.isNotEmpty()) {
                readFolder(sink.pending.removeFirst(), treeUri, request, sink)
            }
            Timber.d("SafMediaScanner: cursor scan found ${sink.results.size} files")
        } else {
            Timber.w("SafMediaScanner: no persisted read permission for ${request.uriString}")
        }
        return sink.results
    }

    private suspend fun readFolder(childrenUri: Uri, treeUri: Uri, request: ScanRequest, sink: ScanSink) {
        val resolver = context.contentResolver
        resolver.query(childrenUri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
            val columns = DocumentColumns(cursor)
            while (cursor.moveToNext()) {
                readRow(DocumentRow(cursor, columns), treeUri, request, sink)
            }
        }
    }

    private suspend fun readRow(row: DocumentRow, treeUri: Uri, request: ScanRequest, sink: ScanSink) {
        val name = row.name
        when {
            name == null -> Unit
            !request.showHiddenFiles && name.startsWith(HIDDEN_PREFIX) -> Unit
            row.isDirectory -> queueSubFolder(name, row.docId, treeUri, request, sink)
            else -> cursorMediaFile(row, name, treeUri, request)?.let { sink.addFile(it) }
        }
    }

    private fun queueSubFolder(
        name: String,
        docId: String,
        treeUri: Uri,
        request: ScanRequest,
        sink: ScanSink
    ) {
        val skip = !request.scanSubdirectories || TrashFolderContract.matchesTrashSegment(name)
        if (!skip) {
            sink.pending.add(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId))
        }
    }

    private fun cursorMediaFile(
        row: DocumentRow,
        name: String,
        treeUri: Uri,
        request: ScanRequest
    ): MediaFile? = MediaTypeUtils.getMediaTypeFromMimeOrExtension(row.mime, name)
        ?.takeIf { it in request.supportedTypes && matchesSize(row.size, it, request.sizeFilter) }
        ?.let { type ->
            MediaFile(
                name = name,
                path = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.docId).toString(),
                size = row.size,
                createdDate = row.modified,
                type = type
            )
        }

    private suspend fun documentFileScan(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        val folder = readableTree(uriString) ?: return emptyList()
        val files = if (scanSubdirectories) {
            collectDocumentFilesRecursively(folder)
        } else {
            folder.listFiles().filter { it.isFile }
        }
        var processed = 0
        return files.mapNotNull { file ->
            processed++
            if (processed % SLOW_PROGRESS_STEP == 0) onProgress?.onProgress(processed, file.name)
            documentToMediaFile(file, supportedTypes, sizeFilter)
        }
    }

    private fun listChildren(
        uriString: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): List<MediaFile> {
        val folder = readableTree(uriString) ?: return emptyList()
        val children = folder.listFiles()
        val visible = if (showHiddenFiles) {
            children.toList()
        } else {
            children.filter { !it.name.orEmpty().startsWith(HIDDEN_PREFIX) }
        }
        return visible
            .mapNotNull { child -> directoryEntry(child, supportedTypes, sizeFilter) }
            .sortedWith(DIRECTORY_FIRST)
    }

    private fun directoryEntry(
        file: DocumentFile,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): MediaFile? = if (file.isDirectory) {
        folderEntry(file)
    } else {
        documentToMediaFile(file, supportedTypes, sizeFilter)
    }

    private fun folderEntry(file: DocumentFile): MediaFile? {
        val name = file.name ?: return null
        return if (TrashFolderContract.matchesTrashSegment(name)) {
            null
        } else {
            MediaFile(
                name = name,
                path = file.uri.toString(),
                size = 0L,
                createdDate = file.lastModified(),
                type = MediaType.IMAGE, // Placeholder type for folders
                isDirectory = true,
                childCount = file.listFiles().size
            )
        }
    }

    private fun documentToMediaFile(
        file: DocumentFile,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): MediaFile? {
        val name = file.name.orEmpty()
        return MediaTypeUtils.getMediaTypeFromMimeOrExtension(file.type, name)
            ?.takeIf { it in supportedTypes && matchesSize(file.length(), it, sizeFilter) }
            ?.let { type ->
                MediaFile(
                    name = name.ifEmpty { UNKNOWN_NAME },
                    path = file.uri.toString(),
                    size = file.length(),
                    createdDate = file.lastModified(),
                    type = type,
                    isDirectory = false
                )
            }
    }

    private fun readableTree(uriString: String): DocumentFile? {
        val uri = SafHelper.parseUri(uriString)
        val folder = if (hasReadPermission(uri)) DocumentFile.fromTreeUri(context, uri) else null
        return folder?.takeIf { it.exists() && it.isDirectory }
    }

    private fun hasReadPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }

    private fun matchesSize(size: Long, type: MediaType, sizeFilter: SizeFilter?): Boolean =
        sizeFilter == null || MediaTypeUtils.isFileSizeInRange(size, type, sizeFilter)

    private fun collectDocumentFilesRecursively(folder: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(folder)
        while (queue.isNotEmpty()) {
            queue.removeFirst().listFiles().forEach { child ->
                val trash = child.name?.let(TrashFolderContract::matchesTrashSegment) == true
                if (child.isDirectory && !trash) {
                    queue.add(child)
                } else if (child.isFile) {
                    result.add(child)
                }
            }
        }
        return result
    }

    private data class ScanRequest(
        val uriString: String,
        val supportedTypes: Set<MediaType>,
        val sizeFilter: SizeFilter?,
        val scanSubdirectories: Boolean,
        val showHiddenFiles: Boolean
    )

    /** Column indices resolved once per cursor instead of once per row. */
    private class DocumentColumns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
        val modified = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    }

    private class DocumentRow(cursor: Cursor, columns: DocumentColumns) {
        val docId: String = cursor.getString(columns.id)
        val name: String? = cursor.getString(columns.name)
        val mime: String? = cursor.getString(columns.mime)
        val size: Long = cursor.getLong(columns.size)
        val modified: Long = cursor.getLong(columns.modified)
        val isDirectory: Boolean = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /** Accumulates the walk so the folder queue and the progress counter travel as one argument. */
    private class ScanSink(private val onProgress: ScanProgressCallback?) {
        val results = mutableListOf<MediaFile>()
        val pending = ArrayDeque<Uri>()
        private var processed = 0

        suspend fun addFile(file: MediaFile) {
            results.add(file)
            processed++
            if (processed % FAST_PROGRESS_STEP == 0) onProgress?.onProgress(processed, file.name)
        }
    }

    private companion object {
        const val FAST_PROGRESS_STEP = 50
        const val SLOW_PROGRESS_STEP = 10
        const val HIDDEN_PREFIX = "."
        const val UNKNOWN_NAME = "unknown"

        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val DIRECTORY_FIRST: Comparator<MediaFile> =
            compareBy<MediaFile> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
