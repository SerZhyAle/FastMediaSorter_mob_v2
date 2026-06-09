package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.NetworkFileDownloader
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of resolving a [MediaFile] to a local archive the VR classifier can read.
 *
 * S0388: a cloud copy is transient - [Available.cleanup] deletes it right after one read so no
 * permanent disk copy accumulates, while the in-memory result cache covers re-binds. [OutOfSpace]
 * tells the caller to stop classifying for the session instead of retrying a doomed download.
 */
sealed interface VrArchiveResolution {
    /** Archive ready to classify. Caller MUST invoke [cleanup] once it has read [file]. */
    class Available(val file: File, val cleanup: () -> Unit) : VrArchiveResolution

    /** Cache volume cannot hold the copy - stop classification for this session. */
    data object OutOfSpace : VrArchiveResolution

    /** No usable archive - degrade to NOT_VR for this item only. */
    data object Unavailable : VrArchiveResolution
}

@Singleton
class VrApkArchiveResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedFileCache: UnifiedFileCache,
    private val cloudFileOperationHandler: CloudFileOperationHandler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Captured at construction: files older than this in the classification dir are leftovers from a
    // previous session (or a legacy persistent-cache build) and are safe to purge exactly once.
    private val sessionStartMillis = System.currentTimeMillis()
    private val staleCopiesPurged = AtomicBoolean(false)

    suspend fun resolve(mediaFile: MediaFile): VrArchiveResolution = withContext(ioDispatcher) {
        when {
            PathUtils.isLocalPath(mediaFile.path) -> {
                val file = File(mediaFile.path).takeIf { it.exists() && it.isFile }
                // Local original: never delete it - cleanup is a no-op.
                if (file != null) VrArchiveResolution.Available(file) {} else VrArchiveResolution.Unavailable
            }

            mediaFile.path.isNetworkPath() -> {
                val temp = networkDownloader.downloadToTemp(
                    networkPath = mediaFile.path,
                    fileType = MediaType.BINARY_EXECUTABLE,
                    fileSize = mediaFile.size,
                )
                // Non-goal: network temp-file lifecycle is unchanged (no auto-delete here).
                if (temp != null) VrArchiveResolution.Available(temp) {} else VrArchiveResolution.Unavailable
            }

            mediaFile.path.isCloudPath() -> {
                resolveCloudArchive(mediaFile)
            }

            else -> VrArchiveResolution.Unavailable
        }
    }

    private suspend fun resolveCloudArchive(mediaFile: MediaFile): VrArchiveResolution {
        Timber.d("S0388: transient cloud APK copy resolve reached")
        return try {
            val cacheDir = File(context.cacheDir, CLOUD_CACHE_DIR_NAME).apply { mkdirs() }
            purgeStaleCopies(cacheDir)

            val needed = spaceNeeded(mediaFile.size)
            if (cacheDir.usableSpace < needed) {
                Timber.w("resolveCloudArchive: cache volume low on space before download (${cacheDir.usableSpace} < $needed)")
                return VrArchiveResolution.OutOfSpace
            }

            // Transient scratch copy keyed by path+size+name. No persistent reuse: the in-memory
            // result cache already memoizes the verdict, so a disk copy would only duplicate it.
            val scratch = File(
                cacheDir,
                "${mediaFile.path.hashCode()}_${mediaFile.size}_${mediaFile.name}",
            )

            val downloadOk = cloudFileOperationHandler.downloadFromCloudToPublic(
                cloudPath = mediaFile.path,
                destPath = cacheDir.absolutePath,
                fileName = scratch.name,
            )

            // Validate against the expected size, not just "> 0", so a truncated download (e.g. the
            // volume filled mid-transfer) is rejected instead of parsed as a non-VR APK.
            val sizeOk = if (mediaFile.size > 0L) {
                scratch.length() == mediaFile.size
            } else {
                scratch.length() > 0L
            }

            if (downloadOk && scratch.exists() && sizeOk) {
                VrArchiveResolution.Available(scratch) { scratch.delete() }
            } else {
                // Drop any partial/truncated copy so it cannot be mistaken for valid later.
                scratch.delete()
                if (cacheDir.usableSpace < needed) {
                    Timber.w("resolveCloudArchive: cloud APK copy failed, cache volume out of space")
                    VrArchiveResolution.OutOfSpace
                } else {
                    VrArchiveResolution.Unavailable
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "resolveCloudArchive: failed to copy cloud APK for classification")
            VrArchiveResolution.Unavailable
        }
    }

    private fun purgeStaleCopies(cacheDir: File) {
        if (!staleCopiesPurged.compareAndSet(false, true)) return
        // One-time, race-free: files created this session have lastModified >= sessionStartMillis
        // and are skipped, so an in-flight transient copy is never deleted from under a classifier.
        cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < sessionStartMillis) {
                f.delete()
            }
        }
    }

    private fun spaceNeeded(fileSize: Long): Long = fileSize.coerceAtLeast(MIN_FREE_SPACE_FLOOR_BYTES)

    private val networkDownloader by lazy {
        NetworkFileDownloader(
            context = context,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository,
            unifiedCache = unifiedFileCache,
        )
    }

    private companion object {
        private const val CLOUD_CACHE_DIR_NAME = "vr_apk_classification"
        private const val MIN_FREE_SPACE_FLOOR_BYTES = 32L * 1024L * 1024L
    }
}

private fun String.isNetworkPath(): Boolean {
    return startsWith("smb://") || startsWith("sftp://") || startsWith("ftp://")
}

private fun String.isCloudPath(): Boolean = startsWith("cloud://")
