package com.sza.fastmediasorter.data.link

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
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
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
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
        val runDir = createRunDirectory(tempDir)
            ?: return@withContext WriteResult.Failed(IOException("link download cache unavailable: ${tempDir.path}"))
        // The copy names its destination after this file, so it keeps the display name; the private
        // run directory is what keeps two concurrent downloads of the same name apart.
        val tempFile = File(runDir, fileName)

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
                        // S0980: expose the real written path so the "open in player" setting can launch
                        // the saved file. Only local resources yield a playable file:// URI; a network/cloud
                        // copy has no local URI, so leave it null (the presenter then falls back to a toast).
                        val savedUri = result.copiedFilePaths.firstOrNull()
                            ?.takeIf { !resource.type.isNetworkResource }
                            ?.let { Uri.fromFile(File(it)) }
                        return@withContext WriteResult.Saved(resource.name, fileName, destinationUri = savedUri)
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
            runCatching { runDir.deleteRecursively() }
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
            e.errorUnlessCancellation("LinkDownloadWriter: failed to save to Downloads")
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
        if (isKnownMedia) return if (asciiAt(4, "ftyp") && isIsoBmffTruncated(file)) "truncated" else null

        // Obvious error-page / metadata content → reject.
        when (head[0].toInt() and 0xFF) {
            0x3C -> return "html"   // '<'  - HTML / XML
            0x7B, 0x5B -> return "json"  // '{' or '['
        }
        // Too small to be the media the user asked for (signed CDN error stubs are tiny).
        if (length < 1024L) return "too-small"
        return null
    }

    /**
     * S4550: a body cut off mid-transfer still opens with a valid `ftyp` box, so the header sniff
     * accepts it, yet the player freezes at the cut or fails with read-position-out-of-range
     * (journal 2026-11-11: Threads files stuck at 5575 of 22033 ms and 3014 of 10000 ms). Walks the
     * top-level ISO-BMFF boxes and reports true when one declares more bytes than the file holds.
     * Lenient on purpose: a malformed box header ends the walk as "unknown", never as truncated.
     */
    private fun isIsoBmffTruncated(file: File): Boolean {
        val fileLength = file.length()
        var truncated = false
        RandomAccessFile(file, "r").use { raf ->
            var offset = 0L
            var boxes = 0
            // Fewer than a box header left is trailing padding, not a cut box.
            while (!truncated && fileLength - offset >= BOX_HEADER_BYTES && boxes < MAX_BOXES_WALKED) {
                val remaining = fileLength - offset
                val size = readBoxSize(raf, offset, remaining)
                truncated = size > remaining
                offset = if (truncated || size == MALFORMED_BOX) fileLength else offset + size
                boxes++
            }
        }
        return truncated
    }

    // The 32-bit size field means: 0 = box runs to end of file, 1 = real size is the 64-bit field
    // after the type, 2..7 = impossible (smaller than its own header).
    private fun readBoxSize(raf: RandomAccessFile, offset: Long, remaining: Long): Long {
        raf.seek(offset)
        val size32 = raf.readInt().toLong() and UINT32_MASK
        return when {
            size32 == 0L -> remaining
            size32 == 1L && remaining < LARGE_BOX_HEADER_BYTES -> TRUNCATED_BOX
            size32 == 1L -> {
                raf.skipBytes(Int.SIZE_BYTES)
                raf.readLong().takeIf { it >= LARGE_BOX_HEADER_BYTES } ?: MALFORMED_BOX
            }
            size32 < BOX_HEADER_BYTES -> MALFORMED_BOX
            else -> size32
        }
    }

    // mkdir() either creates the directory or fails, atomically, so a directory this call created
    // belongs to this run alone.
    private fun createRunDirectory(parent: File): File? {
        repeat(RUN_DIRECTORY_ATTEMPTS) {
            val candidate = File(parent, "run_${UUID.randomUUID()}")
            if (candidate.mkdir()) return candidate
        }
        return null
    }

    private companion object {
        const val RUN_DIRECTORY_ATTEMPTS = 3
        const val BOX_HEADER_BYTES = 8L
        const val LARGE_BOX_HEADER_BYTES = 16L
        const val UINT32_MASK = 0xFFFFFFFFL
        const val MALFORMED_BOX = -1L
        const val TRUNCATED_BOX = Long.MAX_VALUE

        // A long fragmented file holds one moof+mdat pair per fragment; the cap bounds the walk and a
        // file that exhausts it is accepted, never rejected.
        const val MAX_BOXES_WALKED = 100_000
    }

    private fun sanitiseFileName(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(120).ifBlank { "download.bin" }
    }
}
