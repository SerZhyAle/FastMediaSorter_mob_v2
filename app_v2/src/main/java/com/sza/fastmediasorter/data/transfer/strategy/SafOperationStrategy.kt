package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.transfer.DirectoryEntry
import com.sza.fastmediasorter.data.transfer.DirectoryInfo
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * S1378: [FileOperationStrategy] for Storage Access Framework document-tree addresses - the access
 * route a removable volume is reachable by without all-files access (strategic ADR-2).
 *
 * Before this strategy existed a `content://` path was dispatched to [LocalOperationStrategy], whose
 * own `supportsProtocol` explicitly excludes `content:/` - so the local strategy was being asked to
 * do exactly what it declares it cannot.
 *
 * **Path contract.** A path handled here is a `content:` document URI, or such a URI with
 * `/<displayName>` appended. The second form is what `UnifiedFileOperationHandler` produces for a
 * destination that does not exist yet, and SAF has no way to write at an arbitrary URI - a document
 * is always *created inside its parent* - so the appended form is resolved to (parent, name).
 *
 * **Both sides may be local.** `BaseFileOperationHandler` performs a cross-protocol transfer as
 * download-to-temp plus upload-from-temp, calling this strategy once with a local destination and
 * once with a local source. Stream opening therefore branches on each side independently instead of
 * assuming both ends are `content://`.
 */
class SafOperationStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileOperationStrategy {

    private val walker = SafDirectoryWalker(context.contentResolver)

    override fun supportsProtocol(path: String): Boolean = SafHelper.isContentUri(path)

    override fun getProtocolName(): String = "saf"

    // The broad catch is an operation-boundary guard: every SAF failure mode (SecurityException on a
    // revoked grant, FileNotFoundException, provider-specific IllegalArgumentException) must surface
    // as Result.failure with the cause attached, never as a crash inside a file operation.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val totalBytes = sizeOf(source)
            // Source first: opening the destination before it would leak that stream whenever the
            // source turns out to be unreadable, which is the single most common failure here.
            val written = openSource(source).use { input ->
                val sink = openDestination(destination, overwrite)
                    .getOrElse { return@withContext Result.failure(it) }
                sink.stream.use { output -> pump(input, output, totalBytes, progressCallback) }
                sink.path
            }
            Timber.d("SafOperationStrategy: copied %s -> %s", source, written)
            Result.success(written)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: copy failed - %s -> %s", source, destination)
            Result.failure(e)
        }
    }

    override suspend fun moveFile(source: String, destination: String): Result<Unit> {
        // Copy-then-delete rather than DocumentsContract.moveDocument: the latter only works when
        // both ends live under the same provider, which a cross-protocol transfer never guarantees.
        val copied = copyFile(source, destination, overwrite = true, progressCallback = null)
        copied.exceptionOrNull()?.let { return Result.failure(it) }
        // Not deleteFile(): the copy above accepts a local source (that is how a cross-protocol
        // transfer hands us the staged temp file), so the removal has to accept one too. Routing it
        // through the public, content-only deleteFile would report "copied but failed to delete" on
        // a move that in fact succeeded.
        return removeTransferred(source)
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SafHelper.isContentUri(path)) {
            return@withContext Result.failure(
                IllegalArgumentException("Not a document-tree path: $path")
            )
        }
        if (SafHelper.deleteContentUri(context, path, tag = "SafOperationStrategy")) {
            Result.success(Unit)
        } else {
            Result.failure(java.io.IOException("SAF delete refused for $path"))
        }
    }

    /**
     * Refuses a path outside this strategy's addressing domain instead of answering `false` for it.
     * A confident "no" about a local path that exists is how a misroute stays invisible; a failure
     * names it. [copyFile] accepts a local counterpart because a transfer has two ends - a *query*
     * about a local path has only one, and means the dispatcher sent it to the wrong strategy.
     */
    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!SafHelper.isContentUri(path)) {
            return@withContext Result.failure(
                IllegalArgumentException("Not a document-tree path: $path")
            )
        }
        Result.success(resolveExisting(path) != null)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (resolveExisting(path)?.isDirectory == true) return@withContext Result.success(Unit)
            val slot = resolveSlot(path)
                ?: return@withContext Result.failure(
                    FileNotFoundException("No writable parent for directory: $path")
                )
            val created = slot.parent.createDirectory(slot.displayName)
            if (created == null) {
                Result.failure(java.io.IOException("SAF refused to create directory: $path"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: createDirectory failed - %s", path)
            Result.failure(e)
        }
    }

    /**
     * Unlike [LocalOperationStrategy], which defers creation until the editor's first Save (S0189),
     * the document is created here and now: SAF has no stable "path that will exist later" to hand
     * back, since the provider - not the caller - assigns the document id.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun createTextFile(
        parentPath: String,
        fileName: String,
        content: String,
        resourceId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parent = resolveExisting(parentPath)?.takeIf { it.isDirectory }
                ?: return@withContext Result.failure(
                    FileNotFoundException("Parent is not a document-tree directory: $parentPath")
                )
            if (parent.findFile(fileName) != null) {
                return@withContext Result.failure(FileExistsException(fileName, parentPath, isMove = false))
            }
            val created = parent.createFile(SafHelper.guessMimeType(fileName), fileName)
                ?: return@withContext Result.failure(
                    java.io.IOException("SAF refused to create file: $fileName in $parentPath")
                )
            context.contentResolver.openOutputStream(created.uri, WRITE_TRUNCATE)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(
                java.io.IOException("Cannot open the created document for writing: ${created.uri}")
            )
            Result.success(created.uri.toString())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: createTextFile failed - %s/%s", parentPath, fileName)
            Result.failure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val target = resolveExisting(path)
                ?: return@withContext Result.failure(FileNotFoundException("Document not found: $path"))
            context.contentResolver.openOutputStream(target.uri, WRITE_TRUNCATE)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(
                java.io.IOException("Cannot open document for writing: $path")
            )
            Result.success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: writeFile failed - %s", path)
            Result.failure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = SafHelper.parseUri(path)
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(FileNotFoundException("Document not found: $path"))
            Result.success(text)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: readFile failed - %s", path)
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val directory = resolveExisting(path)?.takeIf { it.isDirectory }
            ?: return@withContext Result.failure(
                FileNotFoundException("Document-tree directory not found: $path")
            )
        Result.success(directory.listFiles().map { it.uri.toString() })
    }

    override suspend fun isDirectory(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val document = resolveExisting(path)
            ?: return@withContext Result.failure(FileNotFoundException("Document not found: $path"))
        Result.success(document.isDirectory)
    }

    // ==================== Directory operations ====================

    /**
     * Overridden rather than left to the interface default, which costs one extra resolver round
     * trip per child: the children cursor already carries the type and size, so a level is one query.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun listEntries(path: String): Result<List<DirectoryEntry>> = withContext(Dispatchers.IO) {
        try {
            val directory = requireDirectory(path)
            val entries = walker.listLevel(directory.uri).map { entry ->
                DirectoryEntry(
                    path = entry.uri.toString(),
                    name = entry.displayName,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: listEntries failed - %s", path)
            Result.failure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getDirectoryInfo(path: String): Result<DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            val directory = requireDirectory(path)
            val totals = walker.measure(directory.uri)
            Result.success(
                DirectoryInfo(
                    path = directory.uri.toString(),
                    name = directory.name.orEmpty(),
                    childCount = totals.entries,
                    totalSize = totals.bytes,
                    lastModified = directory.lastModified(),
                )
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: getDirectoryInfo failed - %s", path)
            Result.failure(e)
        }
    }

    /**
     * A name already taken inside the destination keeps the existing document and counts as skipped -
     * never overwritten. On a failure part-way through, the returned exception carries how many
     * entries did land, because strategic §6 item 3 requires an interrupted transfer to report a
     * partial result rather than roll back, and the caller can only say so if it is told the count.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        val progress = TreeProgress(progressCallback)
        try {
            val sourceRoot = requireDirectory(source)
            progress.total = walker.measure(sourceRoot.uri).entries
            val destinationRoot = openOrCreateDirectory(destination)
            val createdDirs = mutableMapOf("" to destinationRoot)
            walker.walk(sourceRoot.uri) { entry ->
                copyEntryWritten(entry, createdDirs, progress)
            }
            Result.success(progress.copied)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: copyDirectory failed after %d entries", progress.copied)
            Result.failure(PartialDirectoryTransferException(progress.copied, e))
        }
    }

    /**
     * Deletes each source entry only once its own destination write is confirmed, rather than copying
     * the whole tree and then deleting it. Strategic §7 makes that ordering a correctness requirement:
     * a card pulled mid-move must never have removed something that was not yet written.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun moveDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        val progress = TreeProgress(progressCallback)
        try {
            val sourceRoot = requireDirectory(source)
            progress.total = walker.measure(sourceRoot.uri).entries
            val destinationRoot = openOrCreateDirectory(destination)
            val createdDirs = mutableMapOf("" to destinationRoot)
            walker.walk(sourceRoot.uri) { entry ->
                val written = copyEntryWritten(entry, createdDirs, progress)
                // Only a confirmed write releases the source. A name clash in the destination is a
                // skip, not a write - deleting there would destroy the one copy that exists.
                if (written && !entry.isDirectory) {
                    DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
                }
            }
            // Directories only, leaves-first: a provider refuses to remove a non-empty document, and
            // anything still holding a file is a directory whose file was skipped and must survive.
            walker.walkDepthFirstLeavesFirst(sourceRoot.uri) { leftover ->
                if (leftover.isDirectory) {
                    DocumentsContract.deleteDocument(context.contentResolver, leftover.uri)
                }
            }
            // Fails harmlessly when a skipped file is still inside, which is the correct outcome.
            sourceRoot.delete()
            Result.success(progress.copied)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: moveDirectory failed after %d entries", progress.copied)
            Result.failure(PartialDirectoryTransferException(progress.copied, e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        val progress = TreeProgress(progressCallback)
        try {
            val root = requireDirectory(path)
            progress.total = walker.measure(root.uri).entries
            walker.walkDepthFirstLeavesFirst(root.uri) { entry ->
                DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
                progress.advance(entry.displayName, counted = true)
            }
            root.delete()
            Result.success(progress.copied)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SafOperationStrategy: deleteDirectory failed after %d entries", progress.copied)
            Result.failure(PartialDirectoryTransferException(progress.copied, e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun renameDirectory(oldPath: String, newPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val newName = newPath.substringAfterLast('/')
                val leavesParent = oldPath.substringBeforeLast('/') != newPath.substringBeforeLast('/')
                if (newName.isEmpty() || leavesParent) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Rename must stay in the same parent: $oldPath -> $newPath")
                    )
                }
                val target = requireDirectory(oldPath)
                val renamed = DocumentsContract.renameDocument(context.contentResolver, target.uri, newName)
                    ?: return@withContext Result.failure(
                        java.io.IOException("SAF refused to rename: $oldPath -> $newName")
                    )
                Result.success(renamed.toString())
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "SafOperationStrategy: renameDirectory failed - %s", oldPath)
                Result.failure(e)
            }
        }

    // ==================== Directory helpers ====================

    private fun requireDirectory(path: String): DocumentFile =
        resolveExisting(path)?.takeIf { it.isDirectory }
            ?: throw FileNotFoundException("Document-tree directory not found: $path")

    private fun openOrCreateDirectory(path: String): DocumentFile {
        resolveExisting(path)?.takeIf { it.isDirectory }?.let { return it }
        val slot = resolveSlot(path)
            ?: throw FileNotFoundException("No writable parent for directory: $path")
        return slot.parent.findFile(slot.displayName)?.takeIf { it.isDirectory }
            ?: slot.parent.createDirectory(slot.displayName)
            ?: throw java.io.IOException("SAF refused to create directory: $path")
    }

    /** @return true when this entry was actually written at the destination (false = name clash). */
    private fun copyEntryWritten(
        entry: SafDirectoryWalker.SafEntry,
        createdDirs: MutableMap<String, DocumentFile>,
        progress: TreeProgress,
    ): Boolean {
        val parentRelative = entry.relativePath.substringBeforeLast('/', "")
        val parent = createdDirs[parentRelative]
            ?: throw java.io.IOException("Destination parent missing for ${entry.relativePath}")
        val written = if (entry.isDirectory) {
            val made = parent.findFile(entry.displayName)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(entry.displayName)
                ?: throw java.io.IOException("SAF refused to create ${entry.relativePath}")
            createdDirs[entry.relativePath] = made
            true
        } else {
            copyFileInto(parent, entry)
        }
        progress.advance(entry.displayName, counted = written)
        return written
    }

    /** @return false when the name was already taken, which leaves the existing document untouched. */
    private fun copyFileInto(parent: DocumentFile, entry: SafDirectoryWalker.SafEntry): Boolean {
        if (parent.findFile(entry.displayName) != null) return false
        val created = parent.createFile(SafHelper.guessMimeType(entry.displayName), entry.displayName)
            ?: throw java.io.IOException("SAF refused to create ${entry.relativePath}")
        openDocumentInput(entry.uri, entry.relativePath).use { input ->
            openDocumentOutput(created.uri, entry.relativePath).use { output ->
                input.copyTo(output, COPY_BUFFER_BYTES)
            }
        }
        return true
    }

    private fun openDocumentInput(uri: Uri, label: String): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot read $label")

    private fun openDocumentOutput(uri: Uri, label: String): OutputStream =
        context.contentResolver.openOutputStream(uri, WRITE_TRUNCATE)
            ?: throw java.io.IOException("Cannot write $label")

    /**
     * Progress bookkeeping for a tree operation. The callback fires at most once a second plus once
     * on the final entry, which is the strategic §3.2 budget: often enough to look alive, rarely
     * enough not to spend the transfer's time on UI updates.
     */
    private class TreeProgress(private val callback: ((Int, Int, String) -> Unit)?) {
        var total: Int = 0
        var copied: Int = 0
            private set

        private var processed = 0
        private var lastReportAt = 0L

        fun advance(currentName: String, counted: Boolean) {
            processed++
            if (counted) copied++
            val now = System.currentTimeMillis()
            if (processed >= total || now - lastReportAt >= PROGRESS_INTERVAL_MS) {
                lastReportAt = now
                callback?.invoke(processed, total, currentName)
            }
        }
    }

    // ==================== Path resolution ====================

    /** The document at [path], or null when it does not exist or the path is not a content URI. */
    private fun resolveExisting(path: String): DocumentFile? {
        if (!SafHelper.isContentUri(path)) return null
        val uri = SafHelper.parseUri(path)
        return SafHelper.getDocumentFileFromUri(context, uri)?.takeIf { it.exists() }
    }

    /** A document that does not exist yet, expressed as its parent directory plus a display name. */
    private data class CreationSlot(val parent: DocumentFile, val displayName: String)

    private fun resolveSlot(path: String): CreationSlot? {
        val separator = path.lastIndexOf('/')
        val displayName = if (separator > 0) path.substring(separator + 1) else ""
        if (displayName.isEmpty()) return null
        return resolveExisting(path.substring(0, separator))
            ?.takeIf { it.isDirectory }
            ?.let { CreationSlot(it, displayName) }
    }

    // ==================== Streams ====================

    /** An opened destination plus the address the caller should report for it. */
    private class Sink(val stream: OutputStream, val path: String)

    private fun openSource(source: String): InputStream {
        if (!SafHelper.isContentUri(source)) return File(source).inputStream()
        return openDocumentInput(SafHelper.parseUri(source), source)
    }

    private fun openDestination(destination: String, overwrite: Boolean): Result<Sink> {
        if (!SafHelper.isContentUri(destination)) return openLocalDestination(destination, overwrite)
        val existing = resolveExisting(destination)
        return if (existing != null) {
            openExistingDocument(existing, destination, overwrite)
        } else {
            createDocumentInSlot(destination, overwrite)
        }
    }

    /** The far end of a cross-protocol transfer: a staged temp file this strategy writes into. */
    private fun openLocalDestination(destination: String, overwrite: Boolean): Result<Sink> {
        val file = File(destination)
        if (file.exists() && !overwrite) {
            return Result.failure(FileExistsException(file.name, destination, isMove = false))
        }
        file.parentFile?.mkdirs()
        return Result.success(Sink(file.outputStream(), destination))
    }

    private fun openExistingDocument(
        existing: DocumentFile,
        destination: String,
        overwrite: Boolean
    ): Result<Sink> {
        if (!overwrite) {
            return Result.failure(
                FileExistsException(existing.name.orEmpty(), destination, isMove = false)
            )
        }
        val stream = context.contentResolver.openOutputStream(existing.uri, WRITE_TRUNCATE)
        return stream?.let { Result.success(Sink(it, existing.uri.toString())) }
            ?: Result.failure(java.io.IOException("Cannot open document for writing: $destination"))
    }

    private fun createDocumentInSlot(destination: String, overwrite: Boolean): Result<Sink> {
        val slot = resolveSlot(destination)
            ?: return Result.failure(FileNotFoundException("No writable parent for: $destination"))
        val clash = slot.parent.findFile(slot.displayName)
        return if (clash != null && !overwrite) {
            Result.failure(FileExistsException(slot.displayName, destination, isMove = false))
        } else {
            // A stale child of the same name would otherwise make the provider mint "name (1)".
            clash?.delete()
            createAndOpen(slot, destination)
        }
    }

    private fun createAndOpen(slot: CreationSlot, destination: String): Result<Sink> {
        val created = slot.parent.createFile(SafHelper.guessMimeType(slot.displayName), slot.displayName)
            ?: return Result.failure(java.io.IOException("SAF refused to create: $destination"))
        val stream = context.contentResolver.openOutputStream(created.uri, WRITE_TRUNCATE)
        return stream?.let { Result.success(Sink(it, created.uri.toString())) }
            ?: Result.failure(java.io.IOException("Cannot open created document: ${created.uri}"))
    }

    /** Removes whichever end of a completed transfer the source happened to be. */
    private suspend fun removeTransferred(source: String): Result<Unit> {
        if (SafHelper.isContentUri(source)) return deleteFile(source)
        return withContext(Dispatchers.IO) {
            if (File(source).delete()) {
                Result.success(Unit)
            } else {
                Result.failure(java.io.IOException("Copied but failed to delete source: $source"))
            }
        }
    }

    private fun sizeOf(source: String): Long {
        if (!SafHelper.isContentUri(source)) return File(source).length()
        return SafHelper.getFileSize(context, source).coerceAtLeast(0L)
    }

    private suspend fun pump(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        progressCallback: ByteProgressCallback?
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            copied += read
            progressCallback?.onProgress(copied, totalBytes, 0L)
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 8 * 1024

        /** "wt" truncates; plain "w" leaves the tail of a longer previous document behind. */
        const val WRITE_TRUNCATE = "wt"

        /** Strategic §3.2: a tree operation reports at least once a second. */
        const val PROGRESS_INTERVAL_MS = 1000L
    }
}

/**
 * S1378: a tree operation that stopped part-way. [completed] is what actually landed, which strategic
 * §6 item 3 requires the caller to be able to state - an interrupted transfer reports a partial
 * result instead of rolling back, and it can only do that if the count survives the failure.
 */
class PartialDirectoryTransferException(
    val completed: Int,
    cause: Throwable,
) : Exception("Directory operation stopped after $completed entries: ${cause.message}", cause)
