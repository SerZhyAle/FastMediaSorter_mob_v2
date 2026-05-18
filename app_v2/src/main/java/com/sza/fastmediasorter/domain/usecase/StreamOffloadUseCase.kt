package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategies.FtpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SftpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SmbToLocalStrategy
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a streamable remote file to local storage so it can be played off-disk
 * when the network is too slow for progressive streaming (see spec §5.6).
 *
 * Strategy selection is protocol-driven (SMB / SFTP / FTP / Cloud). On success the
 * file is recorded in [StreamingCacheRepository]; on failure or cancellation the
 * partial download is removed and no DB row is created.
 *
 * Destination: `<externalFilesDir>/streaming_cache/<resourceHash>/<filename>`.
 * This is app-scoped external storage - no runtime permission needed and the
 * path is stable across API levels, which dodges the scoped-storage MediaStore
 * overhead described in the spec while still satisfying cleanup semantics.
 */
@Singleton
class StreamOffloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbToLocal: SmbToLocalStrategy,
    private val sftpToLocal: SftpToLocalStrategy,
    private val ftpToLocal: FtpToLocalStrategy,
    private val directoryStrategies: @JvmSuppressWildcards Map<String, FileOperationStrategy>,
    private val streamingCacheRepository: StreamingCacheRepository
) {

    /**
     * Runs the download and emits progress. The flow completes with either
     * [OffloadProgress.Completed] or [OffloadProgress.Failed]; cancellation of
     * the collecting coroutine triggers [OffloadProgress.Cancelled] + cleanup.
     */
    fun run(request: OffloadRequest): Flow<OffloadProgress> = channelFlow {
        val resourceHash = streamingCacheRepository.resolveHash(request.originalUri)
        val destFile = resolveDestinationFile(resourceHash, request.filename)

        if (!hasFreeSpace(request.sizeBytes)) {
            Timber.w("StreamOffload: insufficient free space for %d bytes", request.sizeBytes)
            trySend(OffloadProgress.Failed(FailureReason.INSUFFICIENT_SPACE))
            return@channelFlow
        }

        destFile.parentFile?.mkdirs()
        trySend(OffloadProgress.Starting(totalBytes = request.sizeBytes, destinationPath = destFile.absolutePath))

        val progressCallback = object : ByteProgressCallback {
            override suspend fun onProgress(
                bytesTransferred: Long,
                totalBytes: Long,
                speedBytesPerSecond: Long
            ) {
                // `totalBytes == 0` means the protocol did not report size; fall back to the request value.
                val effectiveTotal = if (totalBytes > 0) totalBytes else request.sizeBytes
                trySend(
                    OffloadProgress.Downloading(
                        bytesTransferred = bytesTransferred,
                        totalBytes = effectiveTotal,
                        speedBytesPerSecond = speedBytesPerSecond
                    )
                )
            }
        }

        try {
            val success = dispatchDownload(request, destFile, progressCallback)
            if (success) {
                val entry = StreamingCacheEntry(
                    resourceHash = resourceHash,
                    localPath = destFile.absolutePath,
                    originalUri = request.originalUri,
                    resourceKey = request.resourceKey,
                    sizeBytes = destFile.length(),
                    downloadedAt = System.currentTimeMillis(),
                    lastPlayedAt = System.currentTimeMillis(),
                    sourceProtocol = request.sourceProtocol.name
                )
                streamingCacheRepository.record(entry)
                trySend(OffloadProgress.Completed(entry))
            } else {
                cleanupPartial(destFile)
                trySend(OffloadProgress.Failed(FailureReason.DOWNLOAD_FAILED))
            }
        } catch (ce: CancellationException) {
            cleanupPartial(destFile)
            trySend(OffloadProgress.Cancelled)
            throw ce
        } catch (t: Throwable) {
            Timber.e(t, "StreamOffload: unexpected failure for %s", request.originalUri)
            cleanupPartial(destFile)
            trySend(OffloadProgress.Failed(FailureReason.DOWNLOAD_FAILED, t.message))
        }

        awaitClose {
            // awaitClose is called when the flow collector is cancelled; we already
            // handle the CancellationException branch above, but this guards against
            // a collector that exits without cancelling the download scope.
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Refuses offload if the free space would drop below a 10 % headroom after the
     * download. Mirrors the spec's `fileBytes × 1.1 > freeStorageBytes` rule.
     */
    fun hasFreeSpace(fileBytes: Long): Boolean {
        val dir = externalStreamingRoot()
        val usable = dir.usableSpace
        return (fileBytes * 11L / 10L) <= usable
    }

    /**
     * File-system path for the downloaded copy. Shape:
     * `<externalFilesDir>/streaming_cache/<resourceHash>/<filename>`.
     */
    fun resolveDestinationFile(resourceHash: String, filename: String): File {
        val safeName = sanitizeFilename(filename)
        return File(externalStreamingRoot(), "$resourceHash${File.separator}$safeName")
    }

    /** Estimated download time in whole seconds given file size and network speed. */
    fun estimateDownloadSec(fileBytes: Long, speedMbps: Double): Long {
        if (speedMbps <= 0.0) return Long.MAX_VALUE
        // bytes * 8 bits / bytes / (Mbps * 1_000_000 bits/sec) = sec
        val sec = (fileBytes.toDouble() * 8.0) / (speedMbps * 1_000_000.0)
        return sec.toLong().coerceAtLeast(1L)
    }

    // ── Strategy dispatch ───────────────────────────────────────────────────────

    private suspend fun dispatchDownload(
        request: OffloadRequest,
        destFile: File,
        progressCallback: ByteProgressCallback
    ): Boolean = when (request.sourceProtocol) {
        SourceProtocol.SMB -> smbToLocal.copy(
            source = Uri.parse(request.originalUri),
            destination = Uri.fromFile(destFile),
            overwrite = true,
            sourceCredentialsId = request.credentialsId,
            progressCallback = progressCallback
        )
        SourceProtocol.SFTP -> sftpToLocal.copy(
            source = Uri.parse(request.originalUri),
            destination = Uri.fromFile(destFile),
            overwrite = true,
            sourceCredentialsId = request.credentialsId,
            progressCallback = progressCallback
        )
        SourceProtocol.FTP -> ftpToLocal.copy(
            source = Uri.parse(request.originalUri),
            destination = Uri.fromFile(destFile),
            overwrite = true,
            sourceCredentialsId = request.credentialsId,
            progressCallback = progressCallback
        )
        SourceProtocol.CLOUD -> {
            val cloud = directoryStrategies["cloud"]
                ?: error("Cloud strategy not registered - FEATURE_CLOUD flavor required")
            val result = cloud.copyFile(
                source = request.originalUri,
                destination = destFile.absolutePath,
                overwrite = true,
                progressCallback = progressCallback
            )
            result.isSuccess
        }
    }

    // ── Destination helpers ─────────────────────────────────────────────────────

    private fun externalStreamingRoot(): File {
        // External-files dir (app-scoped) is writable on every supported API without
        // runtime permission; if it is unavailable (no external storage mounted) we
        // fall back to app-internal files dir.
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(base, "streaming_cache").apply { mkdirs() }
    }

    private fun cleanupPartial(destFile: File) {
        try {
            if (destFile.exists() && !destFile.delete()) {
                Timber.w("StreamOffload: failed to delete partial %s", destFile.absolutePath)
            }
            // Best-effort: also prune the per-hash parent directory if it's now empty.
            destFile.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
        } catch (t: Throwable) {
            Timber.w(t, "StreamOffload: cleanup failed for %s", destFile.absolutePath)
        }
    }

    private fun sanitizeFilename(raw: String): String {
        // Strip path separators and other filesystem-hostile characters so a hostile
        // server name can never escape the per-hash directory.
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
    }

    // ── DTOs ────────────────────────────────────────────────────────────────────

    enum class SourceProtocol { SMB, SFTP, FTP, CLOUD }

    data class OffloadRequest(
        val originalUri: String,
        val filename: String,
        val sizeBytes: Long,
        val resourceKey: String,
        val sourceProtocol: SourceProtocol,
        val credentialsId: String? = null
    )

    enum class FailureReason { INSUFFICIENT_SPACE, DOWNLOAD_FAILED }

    sealed interface OffloadProgress {
        data class Starting(val totalBytes: Long, val destinationPath: String) : OffloadProgress
        data class Downloading(
            val bytesTransferred: Long,
            val totalBytes: Long,
            val speedBytesPerSecond: Long
        ) : OffloadProgress
        data class Completed(val entry: StreamingCacheEntry) : OffloadProgress
        data class Failed(val reason: FailureReason, val message: String? = null) : OffloadProgress
        data object Cancelled : OffloadProgress
    }
}
