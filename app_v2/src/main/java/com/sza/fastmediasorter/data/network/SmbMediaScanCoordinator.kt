package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.network.helpers.SmbDirectoryScanner
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbFileInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * SMB media-file scanning coordinator.
 * Owns the four "scan a folder for media files" entry points that share a single
 * pattern: open share via [SmbConnectionManager.withConnection], hand off to
 * [SmbDirectoryScanner] for the actual traversal, then convert internal scanner
 * results to the public [SmbFileInfo] model.
 *
 * Extracted from `SmbClient` (S0002 Wave 47) to keep that facade under the 700-LOC
 * stretch target. No behaviour change - every public method's signature, retry
 * shape, logging, and error mapping mirror the original SmbClient implementations.
 */
class SmbMediaScanCoordinator(
    private val connectionManager: SmbConnectionManager,
    private val directoryScanner: SmbDirectoryScanner
) {

    /**
     * S0248 Phase 4: defensive in-flight coalescer for full-scan listings.
     * Two callers that issue an identical [scanMediaFiles] request on the same
     * (server, share, port, remotePath, extensions, scanSubdirectories, includeDirectories)
     * key concurrently will share a single underlying directory listing. The entry
     * is evicted in `finally` so a future scan is never blocked by a stale Deferred.
     *
     * This is the SECONDARY layer. The primary root cause - namely the deliberate
     * two-phase pattern `scanFolderChunked` → `scanFolder` in GetMediaFilesUseCase
     * (progressive loading) - is by design: the chunked early-emit and the full
     * scan use different `maxFiles` budgets and therefore produce different
     * coalesce keys. The coalescer only merges genuinely identical requests.
     */
    private val inFlightFullScan = ConcurrentHashMap<String, CompletableDeferred<SmbResult<List<SmbFileInfo>>>>()

    private fun fullScanKey(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        extensions: Set<String>?,
        scanSubdirectories: Boolean,
        includeDirectories: Boolean
    ): String {
        val extKey = extensions?.sorted()?.joinToString(",") ?: "*"
        return "${connectionInfo.server}:${connectionInfo.port}/${connectionInfo.shareName}|$remotePath|$extKey|sub=$scanSubdirectories|dirs=$includeDirectories"
    }

    suspend fun scanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        scanSubdirectories: Boolean = true,
        progressCallback: ScanProgressCallback? = null,
        includeDirectories: Boolean = false
    ): SmbResult<List<SmbFileInfo>> {
        // S0248 Phase 4: coalesce identical concurrent listings.
        val key = fullScanKey(connectionInfo, remotePath, extensions, scanSubdirectories, includeDirectories)
        val mine = CompletableDeferred<SmbResult<List<SmbFileInfo>>>()
        val existing = inFlightFullScan.putIfAbsent(key, mine)
        if (existing != null) {
            Timber.d("SmbMediaScanCoordinator: dedup hit on $key")
            Timber.d("S0248: listing-dedup coalescer hit key=$key")
            return existing.await()
        }
        try {
            val result = runScanMediaFiles(connectionInfo, remotePath, extensions, scanSubdirectories, progressCallback, includeDirectories)
            mine.complete(result)
            return result
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlightFullScan.remove(key)
        }
    }

    private suspend fun runScanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        extensions: Set<String>?,
        scanSubdirectories: Boolean,
        progressCallback: ScanProgressCallback?,
        includeDirectories: Boolean
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()

            connectionManager.withConnection(connectionInfo) { share ->
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryRecursive(share, remotePath, extensions, scannerResults, progressCallback)
                } else {
                    directoryScanner.scanDirectoryNonRecursive(share, remotePath, extensions, scannerResults, Int.MAX_VALUE, progressCallback, includeDirectories)
                }
                mediaFiles.addAll(scannerResults.map { it.toPublic() })
                SmbResult.Success(mediaFiles)
            }.also {
                if (it is SmbResult.Success) {
                    val durationMs = System.currentTimeMillis() - startTime
                    progressCallback?.onComplete(it.data.size, durationMs)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Scan SMB folder with limit (for lazy loading). Returns early after finding maxFiles files.
     */
    suspend fun scanMediaFilesChunked(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxFiles: Int = 100,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            Timber.d("SmbClient.scanMediaFilesChunked: START - share=${connectionInfo.shareName}, remotePath=$remotePath, maxFiles=$maxFiles, scanSubdirectories=$scanSubdirectories")

            val mediaFiles = mutableListOf<SmbFileInfo>()

            connectionManager.withConnection(connectionInfo) { share ->
                Timber.d("SmbClient.scanMediaFilesChunked: Connection established, starting scan")
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryRecursiveWithLimit(share, remotePath, extensions, scannerResults, maxFiles)
                } else {
                    // Only scan root folder, no recursion
                    directoryScanner.scanDirectoryNonRecursive(share, remotePath, extensions, scannerResults, maxFiles, null)
                }
                mediaFiles.addAll(scannerResults.map { it.toPublic() })
                Timber.d("SmbClient.scanMediaFilesChunked: Scan completed, found ${mediaFiles.size} files")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files (chunked)")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Scan media files with pagination support (optimized for lazy loading).
     * Skips first 'offset' files, then collects up to 'limit' files.
     * Much faster than scanMediaFiles() for large folders with offset > 0.
     */
    suspend fun scanMediaFilesPaged(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        offset: Int = 0,
        limit: Int = 50,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()
            val skippedCount = 0

            connectionManager.withConnection(connectionInfo) { share ->
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryWithOffsetLimit(share, remotePath, extensions, scannerResults, offset, limit, skippedCount)
                } else {
                    directoryScanner.scanDirectoryNonRecursiveWithOffset(share, remotePath, extensions, scannerResults, offset, limit)
                }
                mediaFiles.addAll(scannerResults.map { it.toPublic() })
                Timber.d("SmbClient.scanMediaFilesPaged: offset=$offset, limit=$limit, scanSubdirs=$scanSubdirectories, returned=${mediaFiles.size}, took ${System.currentTimeMillis() - startTime}ms")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files (paged)")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Count media files in SMB folder (recursive, optimized).
     * Returns count without creating SmbFileInfo objects.
     */
    suspend fun countMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxCount: Int = 1000, // Fast initial scan: stop at 1000 to return quickly
        scanSubdirectories: Boolean = true
    ): SmbResult<Int> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val count = if (scanSubdirectories) {
                    directoryScanner.countDirectoryRecursive(share, remotePath, extensions, maxCount)
                } else {
                    directoryScanner.countDirectoryNonRecursive(share, remotePath, extensions, maxCount)
                }
                if (count >= maxCount) {
                    Timber.d("Fast count limit reached: $maxCount+ files")
                }
                SmbResult.Success(count)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to count SMB media files")
            SmbResult.Error("Failed to count media files: ${e.message}", e)
        }
    }

    private fun SmbDirectoryScanner.SmbFileInfo.toPublic(): SmbFileInfo = SmbFileInfo(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified
    )
}
