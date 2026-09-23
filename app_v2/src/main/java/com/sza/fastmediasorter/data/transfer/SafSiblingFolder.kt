package com.sza.fastmediasorter.data.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * S3408: a document-tree folder as a [SiblingFolder], addressed by a tree-based document URI.
 *
 * Written against `DocumentsContract` rather than `SafOperationStrategy`: that strategy renames by
 * copying and deleting, so the final document would be a fresh write nobody read back, and it creates
 * children through `DocumentFile.fromSingleUri`, which cannot create inside a subfolder. An address
 * here is a child's document URI.
 */
class SafSiblingFolder(
    private val resolver: ContentResolver,
    private val folderUri: Uri,
) : SiblingFolder {

    // Case-insensitive: emulated storage and removable FAT/exFAT volumes both treat "A.png" and
    // "a.png" as one name, so a case-only difference is a clash the provider would resolve by itself.
    override suspend fun contains(name: String): Boolean = withContext(Dispatchers.IO) {
        guarded { childNames().any { it.equals(name, ignoreCase = true) } }
    }

    override suspend fun write(source: File, name: String): String = withContext(Dispatchers.IO) {
        val created = guarded { DocumentsContract.createDocument(resolver, folderUri, BINARY_MIME, name) }
            ?: throw IOException("the folder refused a new document")
        val copied = runCatching { guarded { copyInto(source, created) } }
        if (copied.isFailure) {
            // The partial document is this call's alone: the caller never received its address.
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
        }
        copied.getOrThrow()
        created.toString()
    }

    override suspend fun read(address: String, target: File) {
        withContext(Dispatchers.IO) {
            guarded {
                val input = resolver.openInputStream(Uri.parse(address))
                    ?: throw IOException("the written document cannot be opened")
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            }
        }
    }

    override suspend fun rename(address: String, newName: String): String = withContext(Dispatchers.IO) {
        val renamed = guarded { DocumentsContract.renameDocument(resolver, Uri.parse(address), newName) }
        renamed?.toString() ?: throw IOException("the folder refused the rename")
    }

    override suspend fun delete(address: String) {
        val deleted = withContext(Dispatchers.IO) {
            guarded { DocumentsContract.deleteDocument(resolver, Uri.parse(address)) }
        }
        if (!deleted) throw IOException("the folder refused the delete")
    }

    private fun copyInto(source: File, document: Uri) {
        val output = resolver.openOutputStream(document, WRITE_TRUNCATE)
            ?: throw IOException("the new document cannot be opened for writing")
        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }

    private fun childNames(): List<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri),
        )
        val cursor = resolver.query(children, NAME_PROJECTION, null, null, null)
            ?: throw IOException("the folder cannot be listed")
        return cursor.use {
            buildList { while (it.moveToNext()) it.getString(0)?.let { name -> add(name) } }
        }
    }

    /**
     * Every refusal a document provider raises - a revoked grant, a name it will not take, an operation
     * it does not implement - becomes the one failure type the placement discipline handles.
     */
    private inline fun <T> guarded(block: () -> T): T = runCatching(block).getOrElse { cause ->
        throw when (cause) {
            is SecurityException,
            is IllegalStateException,
            is IllegalArgumentException,
            is UnsupportedOperationException,
            -> IOException("the document provider refused", cause)
            else -> cause
        }
    }

    private companion object {
        const val BINARY_MIME = "application/octet-stream"

        /** "wt" truncates; plain "w" leaves the tail of a longer previous document behind. */
        const val WRITE_TRUNCATE = "wt"

        val NAME_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    }
}
