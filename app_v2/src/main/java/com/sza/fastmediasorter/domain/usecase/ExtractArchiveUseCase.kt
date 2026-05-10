package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.MalformedInputException
import java.nio.charset.Charset
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

class ExtractArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private class ExtractionAbort(val reason: String) : RuntimeException(reason)

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_UNCOMPRESSED_SIZE = 2L * 1024L * 1024L * 1024L
        private const val MAX_ENTRIES = 100_000
        private const val MAX_DEPTH = 10
    }

    fun invoke(
        archivePath: String,
        targetDirPath: String,
        onCancel: () -> Boolean
    ): Flow<ExtractProgress> = flow {
        try {
            val totalEntries = countEntries(archivePath)
            emit(ExtractProgress.Started(totalEntries))

            var extractedCount = 0
            var processedEntries = 0
            var totalUncompressed = 0L

            withZipInputStream(archivePath) { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (onCancel()) {
                        throw ExtractionAbort("cancelled")
                    }

                    processedEntries++
                    if (processedEntries > MAX_ENTRIES) {
                        throw ExtractionAbort("zip_bomb")
                    }

                    val sanitizedPath = sanitizeEntryPath(entry.name)
                    if (sanitizedPath == null) {
                        Timber.w("ExtractArchiveUseCase: skipped suspicious entry: %s", entry.name)
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                        continue
                    }

                    val depth = sanitizedPath.split('/').size
                    if (depth > MAX_DEPTH) {
                        throw ExtractionAbort("zip_bomb")
                    }

                    if (entry.isDirectory) {
                        ensureDirectory(targetDirPath, sanitizedPath)
                    } else {
                        val bytesWritten = writeEntry(zipInput, targetDirPath, sanitizedPath, onCancel)
                        totalUncompressed += bytesWritten
                        if (totalUncompressed > MAX_UNCOMPRESSED_SIZE) {
                            throw ExtractionAbort("zip_bomb")
                        }
                        extractedCount++
                    }

                    zipInput.closeEntry()
                    val totalSafe = totalEntries.coerceAtLeast(1)
                    val percent = ((processedEntries * 100f) / totalSafe).toInt().coerceIn(0, 100)
                    emit(
                        ExtractProgress.EntryDone(
                            entryName = File(sanitizedPath).name,
                            done = processedEntries,
                            total = totalSafe,
                            percent = percent
                        )
                    )

                    entry = zipInput.nextEntry
                }
            }

            emit(ExtractProgress.Success(extractedCount, targetDirPath))
        } catch (e: ExtractionAbort) {
            emit(ExtractProgress.Failure(e.reason))
        } catch (e: IOException) {
            val normalized = if (isNoSpaceError(e)) "no_space" else "extract_error"
            emit(ExtractProgress.Failure(normalized))
        } catch (e: Exception) {
            Timber.e(e, "ExtractArchiveUseCase failed")
            emit(ExtractProgress.Failure("extract_error"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun countEntries(archivePath: String): Int {
        var count = 0
        withZipInputStream(archivePath) { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                count++
                if (count > MAX_ENTRIES) break
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
        return count.coerceAtLeast(1)
    }

    private suspend fun withZipInputStream(
        archivePath: String,
        block: suspend (ZipInputStream) -> Unit
    ) {
        val charsets = listOf(Charsets.UTF_8, Charset.forName("CP866"))
        var lastError: Exception? = null

        for (charset in charsets) {
            try {
                openArchiveInputStream(archivePath).use { input ->
                    ZipInputStream(BufferedInputStream(input, BUFFER_SIZE), charset).use { zipInput ->
                        block(zipInput)
                    }
                }
                return
            } catch (e: ExtractionAbort) {
                throw e
            } catch (e: IOException) {
                if (isNoSpaceError(e) || e.message == "cancelled") {
                    throw e
                }
                if (!isCharsetRelatedError(e)) {
                    throw e
                }
                lastError = e
                Timber.w(e, "ExtractArchiveUseCase: charset fallback from %s", charset.name())
            } catch (e: Exception) {
                if (!isCharsetRelatedError(e)) {
                    throw e
                }
                lastError = e
                Timber.w(e, "ExtractArchiveUseCase: charset fallback from %s", charset.name())
            }
        }

        throw lastError ?: IllegalStateException("Failed to open zip stream")
    }

    private fun openArchiveInputStream(path: String) = when {
        path.startsWith("content:/") -> {
            val normalized = SafHelper.normalizeContentUri(path)
            context.contentResolver.openInputStream(Uri.parse(normalized))
                ?: throw IOException("Cannot open archive URI: $normalized")
        }
        else -> FileInputStream(File(path))
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
        zipInput: ZipInputStream,
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
        zipInput: ZipInputStream,
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
                if (onCancel()) throw ExtractionAbort("cancelled")
                output.write(buffer, 0, read)
                written += read
            }
            output.flush()
        }
        return written
    }

    private fun writeEntrySaf(
        zipInput: ZipInputStream,
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
                    if (onCancel()) throw ExtractionAbort("cancelled")
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
