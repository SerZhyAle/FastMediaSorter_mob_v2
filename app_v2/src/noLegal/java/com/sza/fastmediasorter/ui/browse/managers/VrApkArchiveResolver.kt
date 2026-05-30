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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun resolve(mediaFile: MediaFile): File? = withContext(ioDispatcher) {
        when {
            PathUtils.isLocalPath(mediaFile.path) -> {
                File(mediaFile.path).takeIf { it.exists() && it.isFile }
            }

            mediaFile.path.isNetworkPath() -> {
                networkDownloader.downloadToTemp(
                    networkPath = mediaFile.path,
                    fileType = MediaType.BINARY_EXECUTABLE,
                    fileSize = mediaFile.size,
                )
            }

            mediaFile.path.isCloudPath() -> {
                resolveCloudArchive(mediaFile)
            }

            else -> null
        }
    }

    private suspend fun resolveCloudArchive(mediaFile: MediaFile): File? {
        val cacheDir = File(context.cacheDir, CLOUD_CACHE_DIR_NAME).apply { mkdirs() }
        // Cloud Browse paths are not readable by PackageManager, so the classifier needs a
        // reusable on-disk APK copy keyed by the original cloud path and file size.
        val cacheFile = File(
            cacheDir,
            "${mediaFile.path.hashCode()}_${mediaFile.size}_${mediaFile.name}",
        )
        if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
            return cacheFile
        }

        val downloadOk = cloudFileOperationHandler.downloadFromCloudToPublic(
            cloudPath = mediaFile.path,
            destPath = cacheDir.absolutePath,
            fileName = cacheFile.name,
        )
        return cacheFile.takeIf { downloadOk && it.exists() && it.length() > 0L }
    }

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
    }
}

private fun String.isNetworkPath(): Boolean {
    return startsWith("smb://") || startsWith("sftp://") || startsWith("ftp://")
}

private fun String.isCloudPath(): Boolean = startsWith("cloud://")