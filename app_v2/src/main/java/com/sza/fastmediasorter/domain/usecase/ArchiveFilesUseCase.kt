package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

sealed class ArchiveProgress {
    /** Archiving started - total number of files is known. */
    data class Started(val totalFiles: Int) : ArchiveProgress()
    /** One file has been added to the archive. */
    data class FileDone(val current: Int, val total: Int, val fileName: String) : ArchiveProgress()
    /** All files packed successfully. */
    data class Success(val archivePath: String, val archivedCount: Int) : ArchiveProgress()
    /** A non-fatal warning for one file (skipped). */
    data class FileWarning(val fileName: String, val reason: String) : ArchiveProgress()
    /** Fatal failure - archive could not be created. */
    data class Error(val message: String, val exception: Throwable? = null) : ArchiveProgress()
}

/**
 * Archives a list of local files into a single ZIP file.
 *
 * Only local file paths and content:// URIs are supported.
 * Network / cloud paths are skipped with a [ArchiveProgress.FileWarning].
 *
 * Usage:
 * ```
 * archiveFilesUseCase(filePaths, "photos_backup.zip", "/storage/emulated/0/Download")
 *     .collect { progress -> ... }
 * ```
 */
class ArchiveFilesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) {

    private val bufferSize = 64 * 1024 // 64 KB

    /**
     * @param filePaths  Absolute paths or content:// URI strings of files to archive.
     * @param archiveName Name of the output ZIP file (e.g. "archive.zip").
     *                    The ".zip" extension is appended if missing.
     * @param destinationDir Absolute path of the directory where the archive is created.
     */
    operator fun invoke(
        filePaths: List<String>,
        archiveName: String,
        destinationDir: String
    ): Flow<ArchiveProgress> = flow {
        if (filePaths.isEmpty()) {
            emit(ArchiveProgress.Error(context.getString(R.string.no_files_selected)))
            return@flow
        }

        val normalizedName = if (archiveName.endsWith(".zip", ignoreCase = true)) {
            archiveName
        } else {
            "$archiveName.zip"
        }

        val destDir = File(destinationDir)
        if (!destDir.exists() || !destDir.isDirectory) {
            emit(ArchiveProgress.Error(context.getString(R.string.archive_destination_not_found)))
            return@flow
        }

        val outputFile = generateUniqueFile(destDir, normalizedName)
        Timber.i("ArchiveFilesUseCase: creating archive ${outputFile.absolutePath} for ${filePaths.size} files")

        emit(ArchiveProgress.Started(filePaths.size))

        var archivedCount = 0
        var zipOutputStream: ZipOutputStream? = null
        val usedEntryNames = mutableSetOf<String>()

        try {
            zipOutputStream = ZipOutputStream(BufferedOutputStream(outputFile.outputStream(), bufferSize))

            filePaths.forEachIndexed { index, pathStr ->
                // Check for cancellation before each file
                if (!coroutineContext.isActive) throw CancellationException("Archive cancelled by user")

                val (name, inputProvider) = resolveFile(pathStr)
                    ?: run {
                        Timber.w("ArchiveFilesUseCase: skipping unsupported path: $pathStr")
                        emit(ArchiveProgress.FileWarning(pathStr, "Unsupported path (network/cloud files not supported)"))
                        return@forEachIndexed
                    }

                // Opened before putNextEntry: a source that cannot be read is skipped cleanly, while a
                // failure after the entry is open propagates and fails the whole archive, because a
                // truncated entry cannot be taken back out of a ZipOutputStream.
                val source = try {
                    inputProvider()
                } catch (e: IOException) {
                    Timber.e(e, "ArchiveFilesUseCase: failed to open file $pathStr")
                    emit(ArchiveProgress.FileWarning(pathStr, e.message ?: "I/O error"))
                    return@forEachIndexed
                }

                val entryName = uniqueEntryName(usedEntryNames, name)
                source.use { inputStream ->
                    zipOutputStream.putNextEntry(ZipEntry(entryName))
                    val buf = ByteArray(bufferSize)
                    var bytesRead: Int
                    val buffered = BufferedInputStream(inputStream, bufferSize)
                    while (buffered.read(buf).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) throw CancellationException("Archive cancelled by user")
                        zipOutputStream.write(buf, 0, bytesRead)
                    }
                }

                zipOutputStream.closeEntry()
                archivedCount++
                Timber.d("ArchiveFilesUseCase: [${index + 1}/${filePaths.size}] packed $entryName")
                emit(ArchiveProgress.FileDone(index + 1, filePaths.size, name))
            }

            zipOutputStream.finish()
            zipOutputStream.close()
            zipOutputStream = null

            Timber.i("ArchiveFilesUseCase: done - $archivedCount files → ${outputFile.absolutePath}")
            emit(ArchiveProgress.Success(outputFile.absolutePath, archivedCount))
            // S0473: archived-file count. Source files are heterogeneous; type is left OTHER per v1.
            statsSink.record(
                StatsEvent.FileOp(FileOpAction.ARCHIVE, StatsMediaType.OTHER, archivedCount.toLong(), 0L)
            )

        } catch (e: CancellationException) {
            Timber.w("ArchiveFilesUseCase: cancelled, cleaning up partial archive ${outputFile.absolutePath}")
            silentClose(zipOutputStream)
            deletePartialArchive(outputFile)
            throw e // Re-throw so coroutine machinery handles it properly

        } catch (e: IOException) {
            Timber.e(e, "ArchiveFilesUseCase: I/O failure")
            silentClose(zipOutputStream)
            deletePartialArchive(outputFile)
            emit(ArchiveProgress.Error(context.getString(R.string.archive_create_failed), e))

        } catch (e: Exception) {
            Timber.e(e, "ArchiveFilesUseCase: unexpected failure")
            silentClose(zipOutputStream)
            deletePartialArchive(outputFile)
            emit(ArchiveProgress.Error(context.getString(R.string.error_reason_unknown), e))
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a path string to (entryFileName, InputStreamProvider).
     * Returns null for paths that cannot be read locally (SMB/SFTP/FTP/cloud).
     */
    private fun resolveFile(pathStr: String): Pair<String, () -> java.io.InputStream>? {
        return when {
            pathStr.startsWith("smb://")
            || pathStr.startsWith("sftp://")
            || pathStr.startsWith("ftp://")
            || pathStr.startsWith("cloud://")
            || pathStr.startsWith("gdrive://")
            || pathStr.startsWith("onedrive://")
            || pathStr.startsWith("dropbox://") -> null // Remote - not supported

            pathStr.startsWith("content://") -> {
                val uri = Uri.parse(pathStr)
                val name = resolveContentUriName(uri) ?: uri.lastPathSegment ?: "file"
                name to { context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open content URI: $pathStr") }
            }

            else -> {
                val file = File(pathStr)
                if (!file.exists() || !file.isFile) {
                    Timber.w("ArchiveFilesUseCase: file not found or is directory: $pathStr")
                    return null
                }
                file.name to { FileInputStream(file) }
            }
        }
    }

    /** Tries to resolve the display name from a content URI via ContentResolver. */
    private fun resolveContentUriName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        Timber.w(e, "resolveContentUriName: failed for $uri")
        null
    }

    /**
     * Returns [baseName], or `name_1.ext`, `name_2.ext`, .. when that entry name is already taken,
     * and records the result in [used]. `ZipOutputStream.putNextEntry` throws on a duplicate name,
     * so two same-named files from different folders need distinct entries.
     */
    internal fun uniqueEntryName(used: MutableSet<String>, baseName: String): String {
        if (used.add(baseName)) return baseName
        val dotIndex = baseName.lastIndexOf('.')
        val (stem, ext) = if (dotIndex > 0) {
            baseName.substring(0, dotIndex) to baseName.substring(dotIndex)
        } else {
            baseName to ""
        }
        var counter = 1
        while (!used.add("${stem}_$counter$ext")) counter++
        return "${stem}_$counter$ext"
    }

    /**
     * Generates a unique output file name in [dir] by appending _1, _2, … before the extension
     * if a file with [baseName] already exists.
     */
    internal fun generateUniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, baseName)
        if (!candidate.exists()) return candidate

        val dotIndex = baseName.lastIndexOf('.')
        val (nameWithoutExt, ext) = if (dotIndex > 0) {
            baseName.substring(0, dotIndex) to baseName.substring(dotIndex)
        } else {
            baseName to ""
        }

        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${nameWithoutExt}_$counter$ext")
            counter++
        }
        return candidate
    }

    private fun silentClose(zos: ZipOutputStream?) {
        try { zos?.close() } catch (_: Exception) {}
    }

    private fun deletePartialArchive(file: File) {
        try {
            if (file.exists()) file.delete()
            Timber.d("ArchiveFilesUseCase: deleted partial archive ${file.name}")
        } catch (e: Exception) {
            Timber.w(e, "ArchiveFilesUseCase: could not delete partial archive ${file.absolutePath}")
        }
    }
}
