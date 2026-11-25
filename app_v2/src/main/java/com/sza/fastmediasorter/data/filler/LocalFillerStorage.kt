package com.sza.fastmediasorter.data.filler

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import com.sza.fastmediasorter.domain.filler.FillerFile
import com.sza.fastmediasorter.domain.filler.FillerPlace
import com.sza.fastmediasorter.domain.filler.FillerSession
import com.sza.fastmediasorter.domain.filler.FillerStorage
import com.sza.fastmediasorter.domain.filler.FillerTarget
import com.sza.fastmediasorter.domain.filler.FillerTargetResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * [FillerStorage] over a user-granted document tree on a mounted local volume.
 *
 * Ownership is never inferred from a name alone: a file is deleted only when the journal holds its
 * document id inside the session directory this run created, or when it is the single pending create
 * the journal precommitted. Anything else found there is preserved and keeps the session pending.
 */
@Singleton
class LocalFillerStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val volumeSource: StorageVolumeSource,
    private val journal: FillerSessionJournal,
    private val appStorage: AppFillerStorage,
) : FillerStorage, FillerTargetResolver {
    private val resolver: ContentResolver
        get() = context.contentResolver

    override suspend fun defaultLocation(): String? = appStorage.defaultLocation()

    override suspend fun grant(location: String): Boolean = io(false) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(Uri.parse(location), flags)
        true
    }

    /** Only the external-storage provider names a real mounted volume; cloud and network trees yield null. */
    override suspend fun place(location: String): FillerPlace? = if (appStorage.isLocation(location)) {
        appStorage.place()
    } else {
        io(null) {
            val tree = Uri.parse(location)
            val root = if (tree.authority == LOCAL_AUTHORITY) rootDocument(tree) else null
            val volumeId = root?.substringBefore(VOLUME_SEPARATOR, "")
            val volume = volumeId?.takeIf { it.isNotEmpty() }?.let { id ->
                volumeSource.listVolumes().firstOrNull { it.isMounted && it.id.equals(id, ignoreCase = true) }
            }
            volume?.let {
                val path = root.orEmpty().substringAfter(VOLUME_SEPARATOR)
                FillerPlace(
                    target = FillerTarget(location, it.id),
                    volumeName = it.displayName.ifEmpty { it.id },
                    folderName = path.substringAfterLast(PATH_SEPARATOR).ifEmpty { it.displayName.ifEmpty { it.id } },
                )
            }
        }
    }

    override suspend fun validate(target: FillerTarget): Boolean = if (appStorage.isLocation(target.location)) {
        appStorage.validate(target)
    } else {
        io(false) {
            val tree = Uri.parse(target.location)
            val root = rootDocument(tree)
            tree.authority == LOCAL_AUTHORITY &&
                root.substringBefore(VOLUME_SEPARATOR).equals(target.volumeId, ignoreCase = true) &&
                hasWriteGrant(tree) &&
                writableMount(target.volumeId) != null &&
                supportsCreate(tree, root)
        }
    }

    /** Measured on the volume itself; a failed read is null, never zero. */
    override suspend fun availableBytes(target: FillerTarget): Long? = if (appStorage.isLocation(target.location)) {
        appStorage.availableBytes(target)
    } else {
        io(null) {
            writableMount(target.volumeId)?.let { path ->
                try {
                    StatFs(path).availableBytes
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "LocalFillerStorage: free space unavailable")
                    null
                }
            }
        }
    }

    override suspend fun create(session: FillerSession, index: Int): FillerFile = withContext(Dispatchers.IO) {
        if (appStorage.isLocation(session.target.location)) return@withContext appStorage.create(session, index)
        val tree = Uri.parse(session.target.location)
        val directory = journal.entry(session).directory ?: createSessionDirectory(session, tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, directory)
        val uri = DocumentsContract.createDocument(resolver, parent, FILE_MIME, fileName(index))
            ?: throw IOException("Provider refused to create a filler file")
        FillerFile(uri.toString())
    }

    override suspend fun append(file: FillerFile, count: Int) = withContext(Dispatchers.IO) {
        if (appStorage.isFile(file)) return@withContext appStorage.append(file, count)
        // Fresh random bytes per chunk keep compressing or deduplicating storage from absorbing the fill.
        val bytes = Random.nextBytes(count)
        val output = resolver.openOutputStream(Uri.parse(file.id), APPEND_MODE)
            ?: throw IOException("Provider refused to open a filler file")
        output.use { it.write(bytes) }
    }

    override suspend fun cleanup(session: FillerSession): Boolean = withContext(Dispatchers.IO) {
        if (appStorage.isLocation(session.target.location)) return@withContext appStorage.cleanup(session)
        if (!validate(session.target)) {
            false
        } else {
            val tree = Uri.parse(session.target.location)
            guarded(false) {
                val filesGone = deleteRecordedFiles(session, tree)
                val pendingGone = deletePendingCreate(session, tree)
                filesGone && pendingGone && removeSessionDirectory(session, tree)
            }
        }
    }

    private suspend fun createSessionDirectory(session: FillerSession, tree: Uri): String {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocument(tree))
        val uri = DocumentsContract.createDocument(
            resolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            directoryName(session),
        ) ?: throw IOException("Provider refused to create the session directory")
        val id = DocumentsContract.getDocumentId(uri)
        journal.setDirectory(session, id)
        return id
    }

    private suspend fun deleteRecordedFiles(session: FillerSession, tree: Uri): Boolean {
        val entry = journal.entry(session)
        val directory = entry.directory
        var complete = true
        entry.files.forEach { fileId ->
            val uri = Uri.parse(fileId)
            val owned = directory != null && uri.authority == tree.authority &&
                DocumentsContract.getDocumentId(uri).startsWith(directory + PATH_SEPARATOR)
            if (owned && deleteIfPresent(uri)) {
                journal.forgetFile(session, fileId)
            } else {
                Timber.w("LocalFillerStorage: preserved an unverified or undeletable filler file")
                complete = false
            }
        }
        return complete
    }

    /** The precommitted create may or may not exist; only that one name in the owned directory is ours. */
    private suspend fun deletePendingCreate(session: FillerSession, tree: Uri): Boolean {
        val entry = journal.entry(session)
        val index = entry.pendingIndex ?: return true
        val directory = entry.directory ?: sessionDirectoryOnDisk(session, tree)
        val child = directory?.let { childNamed(tree, it, fileName(index)) }
        val gone = child == null || deleteIfPresent(DocumentsContract.buildDocumentUriUsingTree(tree, child))
        if (gone) journal.clearPending(session)
        return gone
    }

    /**
     * The directory may have been created without its id reaching the journal. Its name carries the
     * random session id, and it is removed only while empty, so no foreign content can be lost.
     */
    private suspend fun removeSessionDirectory(session: FillerSession, tree: Uri): Boolean {
        val directory = journal.entry(session).directory ?: sessionDirectoryOnDisk(session, tree)
        val removed = directory == null || removeIfEmpty(tree, directory)
        if (removed && journal.entry(session).directory != null) journal.setDirectory(session, null)
        return removed
    }

    private fun removeIfEmpty(tree: Uri, directory: String): Boolean {
        val children = childNames(tree, directory)
        if (children == null || children.isNotEmpty()) {
            Timber.w("LocalFillerStorage: session directory kept, entries=${children?.size}")
            return false
        }
        return deleteIfPresent(DocumentsContract.buildDocumentUriUsingTree(tree, directory))
    }

    private fun sessionDirectoryOnDisk(session: FillerSession, tree: Uri): String? =
        childNamed(tree, rootDocument(tree), directoryName(session))

    private fun childNamed(tree: Uri, parent: String, name: String): String? =
        children(tree, parent)?.firstOrNull { it.second == name }?.first

    private fun childNames(tree: Uri, parent: String): List<String>? = children(tree, parent)?.map { it.second }

    /** Document id and display name of each child, or null when the provider cannot list them. */
    private fun children(tree: Uri, parent: String): List<Pair<String, String>>? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1).orEmpty())
            }
        }
    }

    private fun deleteIfPresent(uri: Uri): Boolean =
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: FileNotFoundException) {
            // Already gone is the outcome cleanup wants.
            true
        }

    private fun hasWriteGrant(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun supportsCreate(tree: Uri, root: String): Boolean {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, root)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_FLAGS)
        val flags = resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
        return flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
    }

    /** Mount path of a mounted, writable volume; a read-only or ejected card yields null. */
    private fun writableMount(volumeId: String): String? =
        volumeSource.mountPathFor(volumeId)
            ?.takeIf { Environment.getExternalStorageState(File(it)) == Environment.MEDIA_MOUNTED }

    private fun rootDocument(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    private suspend fun <T> io(default: T, block: () -> T): T = withContext(Dispatchers.IO) { guarded(default, block) }

    // Providers surface a revoked grant, an ejected card or a malformed address through any of these;
    // each one means "not usable now", which the caller already handles as a refusal.
    private inline fun <T> guarded(default: T, block: () -> T): T =
        try {
            block()
        } catch (e: SecurityException) {
            Timber.w(e, "LocalFillerStorage: access lost")
            default
        } catch (e: IOException) {
            Timber.w(e, "LocalFillerStorage: provider I/O failed")
            default
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "LocalFillerStorage: provider rejected the address")
            default
        } catch (e: UnsupportedOperationException) {
            Timber.w(e, "LocalFillerStorage: provider operation unsupported")
            default
        }

    private fun directoryName(session: FillerSession) = "$DIRECTORY_PREFIX${session.id}"

    private fun fileName(index: Int) = "$FILE_PREFIX$index$FILE_SUFFIX"

    private companion object {
        const val LOCAL_AUTHORITY = "com.android.externalstorage.documents"
        const val VOLUME_SEPARATOR = ':'
        const val PATH_SEPARATOR = '/'
        const val APPEND_MODE = "wa"
        const val FILE_MIME = "application/octet-stream"
        const val DIRECTORY_PREFIX = ".fms-filler-"
        const val FILE_PREFIX = "fill-"
        const val FILE_SUFFIX = ".bin"
    }
}
