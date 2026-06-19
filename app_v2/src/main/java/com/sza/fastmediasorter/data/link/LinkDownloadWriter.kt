package com.sza.fastmediasorter.data.link

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0003 - strategic §5.1 pillar E: writes a downloaded stream into the configured
 * destination resource (Local/SMB/SFTP/FTP/Cloud) via [FileOperationUseCase], or
 * falls back to MediaStore Downloads when the resource is missing/unavailable.
 *
 * Mirrors the pattern from [com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager].
 */
@Singleton
class LinkDownloadWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    // S0522: cheap transport pre-check so an unreachable network resource skips the doomed copy
    // and falls back to Downloads immediately, with the shared fallback-reason vocabulary.
    private val networkStateMonitor: NetworkStateMonitor,
    private val destinationWriter: LocalDestinationWriter,
) {

    sealed interface WriteResult {
        data class Saved(val resourceLabel: String, val fileName: String, val destinationUri: Uri?) : WriteResult
        data class FellBackToDownloads(val fileName: String, val reason: SaveFallbackReason, val destinationUri: Uri?) : WriteResult
        data class Failed(val cause: Throwable) : WriteResult

        /**
         * S0170 BUG-2: the bytes that arrived are not a usable media file (HTML error page,
         * JSON, truncated/garbage body). Surfaces an honest failure instead of saving the file
         * and opening the player on something that immediately errors out.
         */
        data class Corrupted(val bytesWritten: Long, val sniffedKind: String) : WriteResult
    }

    suspend fun writeFromStream(
        stream: InputStream,
        mime: String,
        suggestedFileName: String,
        resourceId: Long?,
        onBytesCopied: (Long) -> Unit,
    ): WriteResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "link_downloads").also { it.mkdirs() }
        val fileName = sanitiseFileName(suggestedFileName)
        val tempFile = uniqueFile(tempDir, fileName)

        try {
            var totalBytes = 0L
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    totalBytes += read
                    onBytesCopied(totalBytes)
                }
            }

            // S0170 BUG-2: validate the downloaded bytes before declaring success. A signed CDN
            // URL re-fetched without the WebView request context (Referer/UA) often yields a
            // 403-HTML page, a JSON error, or a truncated chunk - none of which are playable.
            val sniff = sniffMedia(tempFile)
            if (sniff != null) {
                Timber.w("LinkDownloadWriter: rejected corrupted download - kind=%s bytes=%d name=%s", sniff, totalBytes, fileName)
                return@withContext WriteResult.Corrupted(bytesWritten = totalBytes, sniffedKind = sniff)
            }

            // Step 2/3: try the configured resource, else fall through to Downloads.
            if (resourceId != null) {
                val destinations = getDestinationsUseCase.invoke().first()
                val resource = destinations.find { it.id == resourceId }
                if (resource == null) {
                    Timber.w("LinkDownloadWriter: resource id=%d not found, falling back to Downloads", resourceId)
                    val uri = saveToDownloads(tempFile, fileName, mime)
                    return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.ResourceUnavailable, uri)
                }
                // S0522: skip the copy when a network/cloud resource is unreachable on the current
                // transport and save locally instead, so the download is never lost.
                if (resource.type.isNetworkResource && !networkStateMonitor.canReach(resource.type)) {
                    Timber.w("LinkDownloadWriter: resource '%s' unreachable, falling back to Downloads", resource.name)
                    val uri = saveToDownloads(tempFile, fileName, mime)
                    return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.ResourceUnavailable, uri)
                }
                val operation = FileOperation.Copy(
                    sources = listOf(tempFile),
                    destination = File(resource.path),
                    overwrite = false,
                )
                when (val result = fileOperationUseCase.execute(operation)) {
                    is FileOperationResult.Success -> {
                        Timber.i("LinkDownloadWriter: saved '%s' to resource '%s'", fileName, resource.name)
                        return@withContext WriteResult.Saved(resource.name, fileName, destinationUri = null)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        Timber.w("LinkDownloadWriter: auth required for '%s' (%s)", resource.name, result.provider)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.ResourceUnavailable, uri)
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("LinkDownloadWriter: copy failed for '%s': %s", resource.name, result.error)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.ResourceWriteFailed, uri)
                    }
                    else -> {
                        Timber.w("LinkDownloadWriter: unexpected result %s for '%s'", result::class.simpleName, resource.name)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.ResourceWriteFailed, uri)
                    }
                }
            } else {
                val uri = saveToDownloads(tempFile, fileName, mime)
                return@withContext WriteResult.FellBackToDownloads(fileName, SaveFallbackReason.NoResourceConfigured, uri)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "LinkDownloadWriter: write failed")
            return@withContext WriteResult.Failed(t)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private suspend fun saveToDownloads(tempFile: File, fileName: String, mime: String): Uri? {
        return try {
            // Build the Downloads destination directly with the caller-supplied MIME so the
            // shared writer keeps the link's content type instead of re-deriving it from the
            // extension (strategic non-goal: MIME must not change).
            val category = LocalDestinationCategory.PublicCollection(
                collection = LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS,
                relativePath = Environment.DIRECTORY_DOWNLOADS + "/",
                displayName = fileName,
                mimeType = mime
            )
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                Timber.e(e, "LinkDownloadWriter: failed to open Downloads sink")
                return null
            }
            try {
                FileInputStream(tempFile).use { input -> input.copyTo(sink.outputStream) }
            } catch (e: Exception) {
                sink.abort()
                Timber.e(e, "LinkDownloadWriter: failed to write to Downloads")
                return null
            }
            val savedPath = sink.commit().getOrElse { e ->
                Timber.e(e, "LinkDownloadWriter: failed to commit Downloads sink")
                return null
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Pre-Q the writer lands a plain file the system has not indexed; the manual scan
                // makes it visible. On Q+ the MediaStore publish already indexes it.
                MediaStoreNotifier.notifyFile(context, savedPath, "link-download")
            }
            Timber.i("LinkDownloadWriter: saved '%s' to Downloads", fileName)
            if (savedPath.startsWith("content:")) Uri.parse(savedPath) else Uri.fromFile(File(savedPath))
        } catch (e: Exception) {
            Timber.e(e, "LinkDownloadWriter: failed to save to Downloads")
            null
        }
    }

    /**
     * S0170 BUG-2: lightweight content sniff. Returns `null` when the file looks like a usable
     * media container; otherwise a short reason string ("empty", "too-small", "html", "json").
     * Deliberately lenient - only rejects clearly-wrong content (error pages, truncated stubs),
     * never a real binary just because we don't enumerate its magic bytes.
     */
    private fun sniffMedia(file: File): String? {
        val length = file.length()
        if (length == 0L) return "empty"
        val head = ByteArray(16)
        val read = FileInputStream(file).use { it.read(head) }
        if (read <= 0) return "empty"

        fun startsWith(vararg bytes: Int): Boolean =
            read >= bytes.size && bytes.indices.all { (head[it].toInt() and 0xFF) == bytes[it] }
        fun asciiAt(offset: Int, text: String): Boolean {
            if (read < offset + text.length) return false
            return text.indices.all { (head[offset + it].toInt() and 0xFF) == text[it].code }
        }

        // Known media container signatures → accept.
        val isKnownMedia =
            asciiAt(4, "ftyp") ||                                   // MP4 / MOV / M4A / 3GP / HEIF
            startsWith(0x1A, 0x45, 0xDF, 0xA3) ||                   // Matroska / WebM (EBML)
            asciiAt(0, "RIFF") ||                                   // AVI / WAV / WebP
            asciiAt(0, "FLV") ||                                    // FLV
            asciiAt(0, "OggS") ||                                   // OGG
            asciiAt(0, "ID3") ||                                    // MP3 (ID3v2)
            startsWith(0xFF, 0xFB) || startsWith(0xFF, 0xF3) || startsWith(0xFF, 0xF2) || // MP3 frame
            startsWith(0xFF, 0xF1) || startsWith(0xFF, 0xF9) ||     // AAC/ADTS
            startsWith(0xFF, 0xD8, 0xFF) ||                         // JPEG
            startsWith(0x89, 0x50, 0x4E, 0x47) ||                   // PNG
            asciiAt(0, "GIF8") ||                                   // GIF
            startsWith(0x42, 0x4D)                                  // BMP
        if (isKnownMedia) return null

        // Obvious error-page / metadata content → reject.
        when (head[0].toInt() and 0xFF) {
            0x3C -> return "html"   // '<'  - HTML / XML
            0x7B, 0x5B -> return "json"  // '{' or '['
        }
        // Too small to be the media the user asked for (signed CDN error stubs are tiny).
        if (length < 1024L) return "too-small"
        return null
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        val suffix = System.currentTimeMillis()
        return if (ext.isBlank()) File(dir, "${base}_$suffix") else File(dir, "${base}_$suffix.$ext")
    }

    private fun sanitiseFileName(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(120).ifBlank { "download.bin" }
    }
}
