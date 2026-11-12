package com.sza.fastmediasorter.data.filler

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import com.sza.fastmediasorter.domain.filler.FillerFile
import com.sza.fastmediasorter.domain.filler.FillerPlace
import com.sza.fastmediasorter.domain.filler.FillerSession
import com.sza.fastmediasorter.domain.filler.FillerTarget
import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Uses the app-owned Downloads directory without requiring a document-tree grant. */
@Singleton
class AppFillerStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val volumes: StorageVolumeSource,
    private val journal: FillerSessionJournal,
) {
    fun isLocation(location: String): Boolean = location == LOCATION
    fun isFile(file: FillerFile): Boolean = Uri.parse(file.id).scheme == "file"

    suspend fun defaultLocation(): String? = withContext(Dispatchers.IO) {
        if (root() != null) LOCATION else null
    }

    suspend fun place(): FillerPlace? = withContext(Dispatchers.IO) {
        val directory = root() ?: return@withContext null
        val volume = volumes.listVolumes().firstOrNull { it.isPrimary && it.isMounted }
        FillerPlace(target(), volume?.displayName?.ifEmpty { PRIMARY } ?: PRIMARY, directory.absolutePath)
    }

    suspend fun validate(target: FillerTarget): Boolean = withContext(Dispatchers.IO) {
        val directory = root()
        target == target() && directory != null && directory.isDirectory && directory.canWrite() &&
            Environment.getExternalStorageState(directory) == Environment.MEDIA_MOUNTED
    }

    suspend fun availableBytes(target: FillerTarget): Long? = withContext(Dispatchers.IO) {
        if (!validate(target)) return@withContext null
        try {
            root()?.let { StatFs(it.path).availableBytes }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun create(session: FillerSession, index: Int): FillerFile = withContext(Dispatchers.IO) {
        check(validate(session.target))
        val parent = root() ?: throw IOException("App storage unavailable")
        val directory = sessionDirectory(parent, session)
        if (journal.entry(session).directory == null) {
            if (!directory.mkdir()) throw IOException("Cannot create session directory")
            journal.setDirectory(session, directory.absolutePath)
        }
        check(directory.canonicalFile == directory.absoluteFile)
        val file = File(directory, fileName(index))
        if (!file.createNewFile()) throw IOException("Cannot create filler file")
        FillerFile(Uri.fromFile(file).toString())
    }

    suspend fun append(file: FillerFile, count: Int) = withContext(Dispatchers.IO) {
        val path = Uri.parse(file.id).path ?: throw IOException("Invalid filler file")
        FileOutputStream(File(path), true).use { it.write(Random.nextBytes(count)) }
    }

    suspend fun cleanup(session: FillerSession): Boolean = withContext(Dispatchers.IO) {
        if (!validate(session.target)) return@withContext false
        try {
            val parent = root() ?: return@withContext false
            val directory = sessionDirectory(parent, session)
            val entry = journal.entry(session)
            if (entry.directory != null && entry.directory != directory.absolutePath) return@withContext false
            if (directory.exists() && directory.canonicalFile != directory.absoluteFile) return@withContext false
            val filesGone = deleteRecordedFiles(session, entry, directory)
            val pendingGone = deletePendingFile(session, entry, directory)
            if (!filesGone || !pendingGone) return@withContext false
            if (directory.exists()) {
                val children = directory.list() ?: return@withContext false
                if (children.isNotEmpty() || !directory.delete()) return@withContext false
            }
            if (entry.directory != null) journal.setDirectory(session, null)
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private suspend fun deleteRecordedFiles(
        session: FillerSession,
        entry: FillerJournalEntry,
        directory: File,
    ): Boolean {
        var complete = true
        for (id in entry.files) {
            val uri = Uri.parse(id)
            val file = uri.path?.let(::File)
            val owned = uri.scheme == "file" && file != null && isOwnedFile(file, directory)
            val removed = owned && file != null && (!file.exists() || file.delete())
            if (owned && removed) {
                journal.forgetFile(session, id)
            } else {
                complete = false
            }
        }
        return complete
    }

    private suspend fun deletePendingFile(
        session: FillerSession,
        entry: FillerJournalEntry,
        directory: File,
    ): Boolean {
        val index = entry.pendingIndex
        if (index == null) return true
        val file = File(directory, fileName(index))
        val removed = file.canonicalFile == file.absoluteFile && (!file.exists() || file.delete())
        if (removed) journal.clearPending(session)
        return removed
    }

    private fun isOwnedFile(file: File, directory: File): Boolean =
        file.parentFile == directory && file.name.matches(FILE_NAME) && file.canonicalFile == file.absoluteFile

    private fun root(): File? = try {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.canonicalFile
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
    private fun target() = FillerTarget(LOCATION, PRIMARY)
    private fun sessionDirectory(parent: File, session: FillerSession): File {
        require(UUID.fromString(session.id).toString() == session.id)
        return File(parent, ".fms-filler-${session.id}")
    }
    private fun fileName(index: Int) = "fill-$index.bin"

    private companion object {
        const val LOCATION = "fms-filler://app-downloads"
        const val PRIMARY = StorageVolumeInfo.PRIMARY_VOLUME_ID
        val FILE_NAME = Regex("fill-[0-9]+\\.bin")
    }
}
