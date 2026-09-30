package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

sealed class ExtractProgress {
    data class Started(val totalEntries: Int) : ExtractProgress()
    data class EntryDone(
        val entryName: String,
        val done: Int,
        val total: Int,
        val percent: Int
    ) : ExtractProgress()
    data class Success(val extractedCount: Int, val targetPath: String) : ExtractProgress()
    data class Failure(val error: String) : ExtractProgress()
}

enum class ArchiveAccessResult(val failureReason: String?) {
    Accessible(null),
    PasswordRequired("password_required"),
    InvalidPassword("password_invalid"),
    Unreadable("extract_error")
}

class ExtractArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) {
    private class ExtractionAbort(val reason: String) : RuntimeException(reason)
    private data class ArchiveFileHandle(val file: File, val temporary: Boolean)
    private data class ArchiveProbe(val access: ArchiveAccessResult, val encrypted: Boolean, val entryCount: Int)

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_UNCOMPRESSED_SIZE = 2L * 1024L * 1024L * 1024L
        private const val MAX_ENTRIES = 100_000
        private const val MAX_DEPTH = 10
    }

    fun invoke(
        archivePath: String,
        targetDirPath: String,
        password: CharArray? = null,
        onCancel: () -> Boolean
    ): Flow<ExtractProgress> = flow {
        try {
            // One handle per run: for a content:// archive every handle is a full copy into cacheDir.
            val archiveHandle = createArchiveFileHandle(archivePath)
            try {
                val probe = probeArchive(archiveHandle.file, password)
                when {
                    probe.access != ArchiveAccessResult.Accessible ->
                        emit(ExtractProgress.Failure(probe.access.failureReason ?: "extract_error"))
                    probe.encrypted -> extractEncryptedArchive(
                        archiveFile = archiveHandle.file,
                        targetDirPath = targetDirPath,
                        password = requireNotNull(password),
                        onCancel = onCancel,
                        emitProgress = { emit(it) }
                    )
                    else -> extractPlainArchive(
                        archiveFile = archiveHandle.file,
                        totalEntries = probe.entryCount.coerceAtLeast(1),
                        targetDirPath = targetDirPath,
                        onCancel = onCancel,
                        emitProgress = { emit(it) }
                    )
                }
            } finally {
                cleanupArchiveFileHandle(archiveHandle)
            }
        } catch (e: ExtractionAbort) {
            emit(ExtractProgress.Failure(e.reason))
        } catch (e: ZipException) {
            emit(ExtractProgress.Failure(if (isPasswordError(e)) "password_invalid" else "extract_error"))
        } catch (e: IOException) {
            val normalized = if (isNoSpaceError(e)) "no_space" else "extract_error"
            emit(ExtractProgress.Failure(normalized))
        } catch (e: Exception) {
            // Leaving the screen cancels the flow; emitting a Failure here would show the user an
            // "extraction failed" result for an extraction they themselves abandoned.
            e.rethrowIfCancellation()
            Timber.e(e, "ExtractArchiveUseCase failed")
            emit(ExtractProgress.Failure("extract_error"))
        }
    }.flowOn(Dispatchers.IO)

    fun isPasswordRequired(archivePath: String): Boolean {
        return withArchiveFile(archivePath) { archiveFile ->
            try {
                ZipFile(archiveFile).use { it.isEncrypted }
            } catch (e: ZipException) {
                false
            }
        }
    }

    fun validateArchiveAccess(archivePath: String, password: CharArray?): ArchiveAccessResult =
        withArchiveFile(archivePath) { archiveFile -> probeArchive(archiveFile, password).access }

    /**
     * Access, encryption and entry count from one read of the central directory, so a run never
     * opens the archive once per question nor decompresses every entry just to count them.
     */
    private fun probeArchive(archiveFile: File, password: CharArray?): ArchiveProbe =
        try {
            ZipFile(archiveFile).use { zipFile ->
                val encrypted = zipFile.isEncrypted
                val access = if (encrypted) checkPassword(zipFile, password) else ArchiveAccessResult.Accessible
                ArchiveProbe(access, encrypted, zipFile.fileHeaders.size)
            }
        } catch (e: ZipException) {
            val access = if (isPasswordError(e)) ArchiveAccessResult.InvalidPassword else ArchiveAccessResult.Unreadable
            ArchiveProbe(access, encrypted = false, entryCount = 0)
        } catch (e: IOException) {
            Timber.w(e, "ExtractArchiveUseCase: archive unreadable")
            ArchiveProbe(ArchiveAccessResult.Unreadable, encrypted = false, entryCount = 0)
        }

    private fun checkPassword(zipFile: ZipFile, password: CharArray?): ArchiveAccessResult {
        if (password == null || password.isEmpty()) return ArchiveAccessResult.PasswordRequired
        zipFile.setPassword(password)
        // A wrong password surfaces only on the first decrypting read, as a ZipException.
        zipFile.fileHeaders.firstOrNull { !it.isDirectory }?.let { header ->
            zipFile.getInputStream(header).use { input -> input.read() }
        }
        return ArchiveAccessResult.Accessible
    }

    private suspend fun extractPlainArchive(
        archiveFile: File,
        totalEntries: Int,
        targetDirPath: String,
        onCancel: () -> Boolean,
        emitProgress: suspend (ExtractProgress) -> Unit
    ) {
        emitProgress(ExtractProgress.Started(totalEntries))

        var extractedCount = 0
        var processedEntries = 0
        var totalUncompressed = 0L

        withZipInputStream(archiveFile) { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                abortIf(onCancel(), "cancelled")

                processedEntries++
                abortIf(processedEntries > MAX_ENTRIES, "zip_bomb")

                val sanitizedPath = sanitizeEntryPath(entry.name)
                if (sanitizedPath == null) {
                    Timber.w("ExtractArchiveUseCase: skipped suspicious entry: %s", entry.name)
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                    continue
                }

                val depth = sanitizedPath.split('/').size
                abortIf(depth > MAX_DEPTH, "zip_bomb")

                if (entry.isDirectory) {
                    ensureDirectory(targetDirPath, sanitizedPath)
                } else {
                    val bytesWritten = writeEntry(zipInput, targetDirPath, sanitizedPath, onCancel)
                    totalUncompressed += bytesWritten
                    abortIf(totalUncompressed > MAX_UNCOMPRESSED_SIZE, "zip_bomb")
                    extractedCount++
                }

                zipInput.closeEntry()
                val percent = ((processedEntries * 100f) / totalEntries).toInt().coerceIn(0, 100)
                emitProgress(
                    ExtractProgress.EntryDone(
                        entryName = File(sanitizedPath).name,
                        done = processedEntries,
                        total = totalEntries,
                        percent = percent
                    )
                )

                entry = zipInput.nextEntry
            }
        }

        emitProgress(ExtractProgress.Success(extractedCount, targetDirPath))
        // S0473: extracted-file count (plain path). Heterogeneous output, type left OTHER per v1.
        statsSink.record(
            StatsEvent.FileOp(FileOpAction.EXTRACT, StatsMediaType.OTHER, extractedCount.toLong(), 0L)
        )
    }

    private suspend fun extractEncryptedArchive(
        archiveFile: File,
        targetDirPath: String,
        password: CharArray,
        onCancel: () -> Boolean,
        emitProgress: suspend (ExtractProgress) -> Unit
    ) {
        ZipFile(archiveFile).use { zipFile ->
            zipFile.setPassword(password)
            val headers = zipFile.fileHeaders
            val totalEntries = headers.size.coerceAtLeast(1)
            var extractedCount = 0
            var processedEntries = 0
            var totalUncompressed = 0L

            emitProgress(ExtractProgress.Started(totalEntries))
            for (header in headers) {
                abortIf(onCancel(), "cancelled")

                processedEntries++
                abortIf(processedEntries > MAX_ENTRIES, "zip_bomb")

                val sanitizedPath = sanitizeEntryPath(header.fileName)
                if (sanitizedPath == null) {
                    Timber.w("ExtractArchiveUseCase: skipped suspicious entry: %s", header.fileName)
                    continue
                }

                val depth = sanitizedPath.split('/').size
                abortIf(depth > MAX_DEPTH, "zip_bomb")

                if (header.isDirectory) {
                    ensureDirectory(targetDirPath, sanitizedPath)
                } else {
                    zipFile.getInputStream(header).use { input ->
                        val bytesWritten = writeEntry(input, targetDirPath, sanitizedPath, onCancel)
                        totalUncompressed += bytesWritten
                    }
                    abortIf(totalUncompressed > MAX_UNCOMPRESSED_SIZE, "zip_bomb")
                    extractedCount++
                }

                val percent = ((processedEntries * 100f) / totalEntries).toInt().coerceIn(0, 100)
                emitProgress(
                    ExtractProgress.EntryDone(
                        entryName = File(sanitizedPath).name,
                        done = processedEntries,
                        total = totalEntries,
                        percent = percent
                    )
                )
            }

            emitProgress(ExtractProgress.Success(extractedCount, targetDirPath))
            // S0473: extracted-file count (encrypted path). Heterogeneous output, type OTHER per v1.
            statsSink.record(
                StatsEvent.FileOp(FileOpAction.EXTRACT, StatsMediaType.OTHER, extractedCount.toLong(), 0L)
            )
        }
    }

    private suspend fun withZipInputStream(
        archiveFile: File,
        block: suspend (ZipInputStream) -> Unit
    ) {
        // The charset constructor of ZipInputStream is API 24; below it (legacy, API 23) the platform
        // reads entry names as UTF-8 only, so the CP866 fallback exists from API 24 up.
        val charsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listOf(Charsets.UTF_8, Charset.forName("CP866"))
        } else {
            listOf(Charsets.UTF_8)
        }
        var lastError: Exception? = null

        for (charset in charsets) {
            try {
                FileInputStream(archiveFile).use { input ->
                    openZipStream(BufferedInputStream(input, BUFFER_SIZE), charset).use { zipInput ->
                        block(zipInput)
                    }
                }
                return
            } catch (e: Exception) {
                // Propagation must not depend on isCharsetRelatedError's message heuristic: a
                // cancellation that ever looked charset-related would retry the next charset instead.
                e.rethrowIfCancellation()
                if (!isCharsetRetryable(e)) {
                    throw e
                }
                lastError = e
                Timber.w(e, "ExtractArchiveUseCase: charset fallback from %s", charset.name())
            }
        }

        throw lastError ?: IllegalStateException("Failed to open zip stream")
    }

    private fun openZipStream(input: InputStream, charset: Charset): ZipInputStream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ZipInputStream(input, charset) else ZipInputStream(input)

    private fun <T> withArchiveFile(archivePath: String, block: (File) -> T): T {
        val archiveHandle = createArchiveFileHandle(archivePath)
        return try {
            block(archiveHandle.file)
        } finally {
            cleanupArchiveFileHandle(archiveHandle)
        }
    }

    private fun createArchiveFileHandle(archivePath: String): ArchiveFileHandle {
        if (!archivePath.startsWith("content:/")) {
            return ArchiveFileHandle(File(archivePath), temporary = false)
        }

        val normalized = SafHelper.normalizeContentUri(archivePath)
        val tempFile = File.createTempFile("archive_", ".zip", context.cacheDir)
        // No handle exists until the copy completes, so cleanupArchiveFileHandle cannot reach a partial copy.
        var copied = false
        try {
            context.contentResolver.openInputStream(Uri.parse(normalized))?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            } ?: throw IOException("Cannot open archive URI: $normalized")
            copied = true
        } finally {
            if (!copied && !tempFile.delete()) {
                Timber.w("ExtractArchiveUseCase: failed to delete partial archive copy: %s", tempFile.name)
            }
        }
        return ArchiveFileHandle(tempFile, temporary = true)
    }

    private fun cleanupArchiveFileHandle(archiveHandle: ArchiveFileHandle) {
        if (archiveHandle.temporary && archiveHandle.file.exists() && !archiveHandle.file.delete()) {
            Timber.w("ExtractArchiveUseCase: failed to delete temporary archive: %s", archiveHandle.file.name)
        }
    }

    private fun sanitizeEntryPath(rawName: String): String? {
        val normalized = rawName.replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return null

        val parts = normalized
            .split('/')
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) return null
        if (parts.any { it == "." || it == ".." }) return null

        return parts.joinToString("/")
    }

    private fun ensureDirectory(targetDirPath: String, relativeDirPath: String) {
        if (targetDirPath.startsWith("content:/")) {
            val root = getTargetDirectoryDocument(targetDirPath)
            createDirectoriesSaf(root, relativeDirPath)
        } else {
            val root = File(targetDirPath)
            val dir = File(root, relativeDirPath)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Failed to create directory: ${dir.absolutePath}")
            }
            ensureInsideTarget(root, dir)
        }
    }

    private fun writeEntry(
        zipInput: InputStream,
        targetDirPath: String,
        relativeFilePath: String,
        onCancel: () -> Boolean
    ): Long {
        return if (targetDirPath.startsWith("content:/")) {
            writeEntrySaf(zipInput, targetDirPath, relativeFilePath, onCancel)
        } else {
            writeEntryLocal(zipInput, targetDirPath, relativeFilePath, onCancel)
        }
    }

    private fun writeEntryLocal(
        zipInput: InputStream,
        targetDirPath: String,
        relativeFilePath: String,
        onCancel: () -> Boolean
    ): Long {
        val root = File(targetDirPath)
        val outFile = File(root, relativeFilePath)
        outFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory: ${parent.absolutePath}")
            }
        }

        ensureInsideTarget(root, outFile)

        var written = 0L
        BufferedOutputStream(outFile.outputStream(), BUFFER_SIZE).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (zipInput.read(buffer).also { read = it } != -1) {
                abortIf(onCancel(), "cancelled")
                output.write(buffer, 0, read)
                written += read
            }
            output.flush()
        }
        return written
    }

    private fun writeEntrySaf(
        zipInput: InputStream,
        targetDirPath: String,
        relativeFilePath: String,
        onCancel: () -> Boolean
    ): Long {
        val root = getTargetDirectoryDocument(targetDirPath)
        val parts = relativeFilePath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0L

        val parent = if (parts.size > 1) {
            createDirectoriesSaf(root, parts.dropLast(1).joinToString("/"))
        } else {
            root
        }

        val fileName = parts.last()
        parent.findFile(fileName)?.delete()
        val outDoc = parent.createFile(detectMimeType(fileName), fileName)
            ?: throw IOException("Failed to create SAF file: $fileName")

        var written = 0L
        context.contentResolver.openOutputStream(outDoc.uri)?.use { output ->
            BufferedOutputStream(output, BUFFER_SIZE).use { buffered ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (zipInput.read(buffer).also { read = it } != -1) {
                    abortIf(onCancel(), "cancelled")
                    buffered.write(buffer, 0, read)
                    written += read
                }
                buffered.flush()
            }
        } ?: throw IOException("Cannot open output stream for ${outDoc.uri}")

        return written
    }

    private fun getTargetDirectoryDocument(targetDirPath: String): DocumentFile {
        val uri = SafHelper.parseUri(targetDirPath)
        val root = SafHelper.getDocumentFileFromUri(context, uri)
            ?: throw IOException("Cannot resolve SAF directory: $targetDirPath")
        if (!root.exists() || !root.isDirectory) {
            throw IOException("Target SAF path is not a directory: $targetDirPath")
        }
        return root
    }

    private fun createDirectoriesSaf(root: DocumentFile, relativeDirPath: String): DocumentFile {
        var current = root
        val parts = relativeDirPath.split('/').filter { it.isNotBlank() }
        for (part in parts) {
            val existing = current.findFile(part)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                current.createDirectory(part)
                    ?: throw IOException("Failed to create SAF directory: $part")
            }
        }
        return current
    }

    private fun ensureInsideTarget(root: File, child: File) {
        val rootCanonical = root.canonicalFile
        val childCanonical = child.canonicalFile
        val rootPath = rootCanonical.path
        val childPath = childCanonical.path
        if (!(childPath == rootPath || childPath.startsWith(rootPath + File.separator))) {
            throw SecurityException("Path traversal detected: $childPath")
        }
    }

    private fun detectMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "txt", "log", "md" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    private fun isNoSpaceError(error: Throwable): Boolean {
        val message = error.message?.lowercase(Locale.ROOT) ?: return false
        return message.contains("no space") || message.contains("enospc") || message.contains("not enough space")
    }

    private fun isPasswordError(error: Throwable): Boolean {
        val message = error.message?.lowercase(Locale.ROOT) ?: return false
        return message.contains("password") ||
            message.contains("wrong password") ||
            message.contains("invalid password") ||
            message.contains("mac")
    }

    private fun isCharsetRetryable(error: Exception): Boolean = when {
        error is ExtractionAbort -> false
        error is IOException && (isNoSpaceError(error) || error.message == "cancelled") -> false
        else -> isCharsetRelatedError(error)
    }

    private fun abortIf(condition: Boolean, reason: String) {
        if (condition) throw ExtractionAbort(reason)
    }

    private fun isCharsetRelatedError(error: Throwable): Boolean {
        if (error is MalformedInputException) return true
        val message = error.message?.lowercase(Locale.ROOT) ?: return false
        return message.contains("malformed") ||
            message.contains("charset") ||
            message.contains("utf") ||
            message.contains("input length") ||
            message.contains("unmappable")
    }
}
