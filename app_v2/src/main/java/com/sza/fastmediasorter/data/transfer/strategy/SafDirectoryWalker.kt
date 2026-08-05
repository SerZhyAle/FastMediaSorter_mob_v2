package com.sza.fastmediasorter.data.transfer.strategy

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.ensureActive
import java.io.FileNotFoundException
import kotlin.coroutines.coroutineContext

/**
 * S1378: depth-first traversal of a document tree. Traversal only - it never copies, deletes or
 * writes anything, so the operations built on it stay readable and testable apart from the walk.
 *
 * Two properties matter to every caller and are the reason this is not a plain recursive helper:
 *
 * - **One resolver query per directory level.** The children cursor already carries name, MIME type
 *   and size, so a level costs one round trip. Going through `DocumentFile.listFiles()` and then
 *   asking each child `isDirectory` costs one extra round trip *per file*, which is what puts the
 *   once-per-second progress budget of strategic §3.2 out of reach on a large folder.
 * - **Cancellation between entries.** `ensureActive()` runs before every yielded entry, so leaving
 *   the screen stops the walk within the current entry instead of after the whole tree.
 */
class SafDirectoryWalker(private val contentResolver: ContentResolver) {

    /** One node of the walk. [relativePath] is relative to the walk root, `/`-separated. */
    data class SafEntry(
        val uri: Uri,
        val documentId: String,
        val displayName: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    /** What a first pass found, so a caller can report progress against a known total. */
    data class TreeTotals(val entries: Int, val bytes: Long)

    /**
     * Children of [directoryUri], one query. [relativeBase] prefixes the children's relative paths;
     * pass an empty string at the walk root.
     */
    suspend fun listLevel(directoryUri: Uri, relativeBase: String = ""): List<SafEntry> {
        coroutineContext.ensureActive()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        val entries = mutableListOf<SafEntry>()
        contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(COLUMN_ID)
                val displayName = cursor.getString(COLUMN_NAME).orEmpty()
                val isDirectory = cursor.getString(COLUMN_MIME) == DocumentsContract.Document.MIME_TYPE_DIR
                entries += SafEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId),
                    documentId = documentId,
                    displayName = displayName,
                    relativePath = if (relativeBase.isEmpty()) displayName else "$relativeBase/$displayName",
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else cursor.getLong(COLUMN_SIZE),
                )
            }
        } ?: throw FileNotFoundException("Cannot list document tree level: $directoryUri")
        return entries
    }

    /**
     * Depth-first walk of everything under [rootUri]. A directory is handed to [onEntry] *before* its
     * children, which is what lets a copy create the destination subtree level by level, and is the
     * reverse of the order a recursive delete needs - see [walkDepthFirstLeavesFirst].
     */
    suspend fun walk(rootUri: Uri, onEntry: suspend (SafEntry) -> Unit) {
        for (entry in listLevel(rootUri)) {
            coroutineContext.ensureActive()
            onEntry(entry)
            if (entry.isDirectory) descend(entry, onEntry)
        }
    }

    /**
     * Same walk, children before their parent. A document provider refuses to delete a directory
     * that still has contents, so a recursive delete has to reach the leaves first.
     */
    suspend fun walkDepthFirstLeavesFirst(rootUri: Uri, onEntry: suspend (SafEntry) -> Unit) {
        for (entry in listLevel(rootUri)) {
            coroutineContext.ensureActive()
            if (entry.isDirectory) descendLeavesFirst(entry, onEntry)
            onEntry(entry)
        }
    }

    /** Entry count and total byte size under [rootUri], for progress that has a denominator. */
    suspend fun measure(rootUri: Uri): TreeTotals {
        var entries = 0
        var bytes = 0L
        walk(rootUri) { entry ->
            entries++
            bytes += entry.size
        }
        return TreeTotals(entries, bytes)
    }

    private suspend fun descend(parent: SafEntry, onEntry: suspend (SafEntry) -> Unit) {
        for (child in listLevel(parent.uri, parent.relativePath)) {
            coroutineContext.ensureActive()
            onEntry(child)
            if (child.isDirectory) descend(child, onEntry)
        }
    }

    private suspend fun descendLeavesFirst(parent: SafEntry, onEntry: suspend (SafEntry) -> Unit) {
        for (child in listLevel(parent.uri, parent.relativePath)) {
            coroutineContext.ensureActive()
            if (child.isDirectory) descendLeavesFirst(child, onEntry)
            onEntry(child)
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME = 2
        const val COLUMN_SIZE = 3
    }
}
