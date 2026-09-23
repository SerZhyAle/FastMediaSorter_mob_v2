package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.cloud.CloudDownloadUseCase
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * S3382: a readable local file for a FileDO container, wherever the scanner found it.
 *
 * The container format reads a `java.io.File` - the header, every chunk, then a full re-read against
 * the sealed digest - so a SAF document, a network file or a cloud file is copied into a directory the
 * caller owns first. The copy is still ciphertext and carries nothing the container does not. A local
 * path is returned as it is and never copied.
 */
class LocalizeFdSecContainerUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // Lazy: the common case is a local file, which needs neither the network clients nor the cloud stack.
    private val downloadNetworkFile: Lazy<DownloadNetworkFileUseCase>,
    private val cloudDownload: Lazy<CloudDownloadUseCase>,
) {

    /** Null when the container could not be read from where it is. A container is never empty. */
    suspend operator fun invoke(path: String, targetDirectory: File): File? =
        if (path.startsWith(LOCAL_ROOT)) {
            File(path)
        } else {
            fetchCopy(path, targetDirectory, COPY_NAME)?.takeIf { it.length() > 0L }
        }

    /**
     * S3408: a copy of a non-local file under [name], for an original about to be packed. The container
     * seals the original's name, so the copy must carry it; an empty original is valid input.
     */
    suspend fun copyOf(path: String, targetDirectory: File, name: String): File? =
        fetchCopy(path, targetDirectory, name)

    private suspend fun fetchCopy(path: String, targetDirectory: File, name: String): File? {
        if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) return null
        val target = File(targetDirectory, name)
        val written = when {
            path.startsWith(CONTENT_SCHEME) || path.startsWith(FILE_SCHEME) -> copyDocument(path, target)
            path.startsWith(CLOUD_SCHEME) -> downloadFromCloud(path, targetDirectory, name)
            NETWORK_SCHEMES.any { path.startsWith(it) } ->
                target.takeIf { downloadNetworkFile.get().execute(path, target) }
            else -> null
        }
        return written?.takeIf { it.isFile }
    }

    private suspend fun copyDocument(path: String, target: File): File? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                target
            }
        } catch (e: IOException) {
            Timber.w(e, "fdsec: the container document could not be copied")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "fdsec: the container document is no longer readable")
            null
        }
    }

    private suspend fun downloadFromCloud(path: String, directory: File, name: String): File? {
        val downloaded = cloudDownload.get().downloadToPublic(
            cloudPath = path,
            destPath = directory.absolutePath,
            fileName = name,
        )
        // The cloud layer may settle on a name from the provider's metadata instead of the one asked for.
        return if (downloaded) directory.listFiles()?.firstOrNull { it.isFile } else null
    }

    private companion object {
        const val LOCAL_ROOT = "/"
        const val CONTENT_SCHEME = "content://"
        const val FILE_SCHEME = "file://"
        const val CLOUD_SCHEME = "cloud:/"
        const val COPY_NAME = "container.fd-sec"
        val NETWORK_SCHEMES = listOf("smb:/", "sftp:/", "ftp:/")
    }
}
