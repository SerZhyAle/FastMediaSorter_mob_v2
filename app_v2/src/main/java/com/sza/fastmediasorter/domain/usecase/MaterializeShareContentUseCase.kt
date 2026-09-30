package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.data.cloud.CloudDownloadUseCase
import com.sza.fastmediasorter.data.link.HttpFileDownloader
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Materializes a remote [ShareableContent] into a local cache copy and returns content localized to a
 * FileProvider Uri, so any file-consuming «Send to..» receiver can act on a remote file exactly as on
 * a local one (S0493).
 *
 * Local content (or content that does not require materialization) is returned unchanged. Network
 * sources (smb/sftp/ftp) are downloaded via [DownloadNetworkFileUseCase], cloud:// via
 * [CloudDownloadUseCase] and direct http(s):// via [HttpFileDownloader] (S0494). Sources without a
 * download primitive - streaming manifests above all - return [Result.failure] so the caller can
 * surface a "could not prepare" message instead of silently doing nothing.
 *
 * The copy keeps the original file name (unlike the hash-named [com.sza.fastmediasorter.core.cache.UnifiedFileCache])
 * so receivers that expose the file name - email attachment, messengers - show a readable name. Copies
 * of the same source are reused within a session (size-validated); the cache is bounded, and once it crosses
 * the cap every per-source copy not in use by a running share is deleted, since the copies are transient
 * share artifacts.
 */
class MaterializeShareContentUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    // Lazy: cloud download is only needed for cloud:// sources, so the common path (local / smb / sftp /
    // ftp) and flavors without cloud UI never construct the cloud singleton.
    private val cloudDownload: Lazy<CloudDownloadUseCase>,
    // Lazy for the same reason: flavors and flows that never share a web file must not construct the
    // OkHttp link-download stack.
    private val httpDownloader: Lazy<HttpFileDownloader>,
) {
    /**
     * @param onProgress receives 0..100 during download (best-effort - some protocols report none).
     * @return localized [ShareableContent] on success; failure on unsupported scheme or download error.
     */
    suspend fun execute(
        content: ShareableContent,
        onProgress: ((Int) -> Unit)? = null,
    ): Result<ShareableContent> = withContext(Dispatchers.IO) {
        if (!content.requiresMaterialization) return@withContext Result.success(content)
        val sourcePath = content.sourcePath
            ?: return@withContext Result.failure(IllegalStateException("No source path to materialize"))

        if (!isDownloadableScheme(sourcePath)) {
            Timber.i("Send-to materialize: no download primitive for %s", sourcePath)
            return@withContext Result.failure(UnsupportedOperationException("Unsupported scheme: $sourcePath"))
        }

        val expectedSize = content.mediaFile?.size ?: 0L
        val targetFile = cacheTargetFor(sourcePath, content.displayName ?: sourcePath.substringAfterLast('/'))
        try {
            materializeInto(content, sourcePath, targetFile, expectedSize, onProgress)
        } finally {
            // NonCancellable: a cancelled share must still leave the in-flight set, or its directory would be
            // exempt from pruning for the rest of the process.
            withContext(NonCancellable) { releaseInFlight(targetFile) }
        }
    }

    private suspend fun materializeInto(
        content: ShareableContent,
        sourcePath: String,
        targetFile: File,
        expectedSize: Long,
        onProgress: ((Int) -> Unit)?,
    ): Result<ShareableContent> {
        val localFile = resolveLocalFile(sourcePath, targetFile, expectedSize, onProgress)
            ?: return Result.failure(IOException("Download failed: $sourcePath"))
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        }.onFailure {
            Timber.w(it, "Send-to materialize: FileProvider uri failed for %s", localFile.absolutePath)
        }.map { uri -> content.materializedTo(uri, localFile.absolutePath) }
    }

    private suspend fun resolveLocalFile(
        sourcePath: String,
        targetFile: File,
        expectedSize: Long,
        onProgress: ((Int) -> Unit)?,
    ): File? {
        // Reuse an already-downloaded copy of the same source in this session (size-validated).
        val reusable = targetFile.exists() && expectedSize > 0L && targetFile.length() == expectedSize
        if (reusable) return targetFile
        val sub = targetFile.parentFile
        // Clear any prior/partial copy for this source so the freshly downloaded file is unambiguous
        // (cloud may write under a metadata-resolved name, not necessarily targetFile.name).
        sub?.listFiles()?.forEach { it.delete() }
        val resolved = downloadTo(sourcePath, targetFile, onProgress)
        if (resolved == null) sub?.listFiles()?.forEach { it.delete() }
        return resolved
    }

    // Route by scheme and return the local file actually written, or null on failure. smb/sftp/ftp and
    // http(s) write exactly to [targetFile] (byte progress on SMB, Content-Length progress on http).
    // cloud:// goes through the cloud downloader, which can resolve a different file name from provider
    // metadata, so the real written file is located in the per-source subdirectory rather than assumed
    // to be [targetFile] (S0494).
    private suspend fun downloadTo(sourcePath: String, targetFile: File, onProgress: ((Int) -> Unit)?): File? =
        when {
            isCloudScheme(sourcePath) -> downloadFromCloud(sourcePath, targetFile, onProgress)
            isHttpScheme(sourcePath) ->
                if (httpDownloader.get().download(sourcePath, targetFile, onProgress)) targetFile else null
            else ->
                if (downloadNetworkFile.execute(sourcePath, targetFile, onProgress)) targetFile else null
        }

    private suspend fun downloadFromCloud(
        sourcePath: String,
        targetFile: File,
        onProgress: ((Int) -> Unit)?,
    ): File? {
        val ok = cloudDownload.get().downloadToPublic(
            cloudPath = sourcePath,
            destPath = targetFile.parentFile?.absolutePath ?: targetFile.absolutePath,
            fileName = targetFile.name,
            progressCallback = percentProgressAdapter(onProgress),
        )
        return when {
            !ok -> null
            targetFile.exists() -> targetFile
            else -> targetFile.parentFile?.listFiles()?.firstOrNull { it.isFile }
        }
    }

    // The cloud layer reports transferred bytes; the share dialog's contract is 0..100, and a source that
    // never announces its total size must leave the dialog indeterminate rather than jump to a made-up
    // percentage, hence the totalBytes guard.
    private fun percentProgressAdapter(onProgress: ((Int) -> Unit)?): ByteProgressCallback? {
        if (onProgress == null) {
            return null
        }
        return object : ByteProgressCallback {
            override suspend fun onProgress(
                bytesTransferred: Long,
                totalBytes: Long,
                speedBytesPerSecond: Long
            ) {
                if (totalBytes > 0) {
                    onProgress(((bytesTransferred * PERCENT_SCALE) / totalBytes).toInt())
                }
            }
        }
    }

    // Per-source subdirectory (hash) keeps the original file name while avoiding same-name collisions
    // between different remote sources. FileProvider serves the whole cacheDir tree (file_provider_paths).
    // The subdirectory is registered as in-flight under the same lock that prunes, and stays registered until
    // execute() returns, so a concurrent share crossing the cap cannot delete a sibling's download mid-flight.
    private suspend fun cacheTargetFor(sourcePath: String, displayName: String): File = cacheLock.withLock {
        val root = File(context.cacheDir, SHARE_CACHE_DIR)
        if (!root.exists()) root.mkdirs()
        if (pruneOverCap(root, inFlightDirs.keys, MAX_SHARE_CACHE_BYTES)) {
            Timber.i("Send-to share cache exceeded %d MB - pruned", MAX_SHARE_CACHE_BYTES / 1024 / 1024)
        }
        val sub = File(root, sourcePath.hashCode().toString())
        if (!sub.exists()) sub.mkdirs()
        inFlightDirs[sub.name] = (inFlightDirs[sub.name] ?: 0) + 1
        File(sub, sanitizeFileName(displayName))
    }

    private suspend fun releaseInFlight(targetFile: File) = cacheLock.withLock {
        val name = targetFile.parentFile?.name ?: return@withLock
        val remaining = (inFlightDirs[name] ?: 0) - 1
        if (remaining > 0) inFlightDirs[name] = remaining else inFlightDirs.remove(name)
    }

    companion object {
        private const val SHARE_CACHE_DIR = "send_to_share"
        private const val PERCENT_SCALE = 100L
        private const val MAX_SHARE_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB - transient share copies
        private val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
        private val cacheLock = Mutex()

        // Subdirectory name -> number of shares currently using it (two shares of one source share a directory).
        // Mutated only under cacheLock.
        private val inFlightDirs = HashMap<String, Int>()

        /**
         * Deletes every child of [root] not named in [keep] once [root] exceeds [capBytes].
         * @return true when a prune ran.
         */
        internal fun pruneOverCap(root: File, keep: Set<String>, capBytes: Long): Boolean {
            if (directorySize(root) <= capBytes) return false
            (root.listFiles() ?: emptyArray())
                .filter { it.name !in keep }
                .forEach { it.deleteRecursively() }
            return true
        }

        private fun directorySize(file: File): Long =
            (file.listFiles() ?: emptyArray()).sumOf { if (it.isDirectory) directorySize(it) else it.length() }

        // A manifest describes a stream, not a finite file: materializing one would download an
        // unbounded body and still produce nothing a receiver could open, so HLS/DASH/Smooth stay on
        // the existing failure path (S0494 ADR-2).
        private val STREAMING_MANIFEST_EXTENSIONS = listOf(".m3u8", ".mpd", ".ism")

        /** Schemes with a local-download primitive: smb/sftp/ftp (S0493), cloud:// and direct http(s):// (S0494). */
        internal fun isDownloadableScheme(path: String): Boolean =
            path.startsWith("smb:/") || path.startsWith("sftp:/") || path.startsWith("ftp:/") ||
                isCloudScheme(path) || isHttpScheme(path)

        internal fun isCloudScheme(path: String): Boolean = path.startsWith("cloud:/")

        /** True for a direct http(s) file URL; a streaming manifest is excluded per ADR-2. */
        internal fun isHttpScheme(path: String): Boolean {
            val lower = path.lowercase()
            val isHttp = lower.startsWith("http:/") || lower.startsWith("https:/")
            // Query strings are stripped so a signed manifest url is still recognised as a manifest.
            val withoutQuery = lower.substringBefore('?').substringBefore('#')
            return isHttp && STREAMING_MANIFEST_EXTENSIONS.none { withoutQuery.endsWith(it) }
        }

        /** Readable share file name: strip any path, replace filesystem-unsafe chars, never blank. */
        internal fun sanitizeFileName(name: String): String =
            name.substringAfterLast('/').replace(UNSAFE_NAME_CHARS, "_").ifBlank { "shared_file" }
    }
}
