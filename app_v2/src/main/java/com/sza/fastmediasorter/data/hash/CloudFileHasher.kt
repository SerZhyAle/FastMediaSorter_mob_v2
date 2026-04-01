package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.domain.hash.FileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class CloudFileHasher @Inject constructor(
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient
) : FileHasher {

    override suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long
    ): String = withContext(Dispatchers.IO) {
        val fileId = file.cloudItemId
            ?: throw IllegalArgumentException("CloudFileHasher: cloudItemId is null for ${file.path}")

        val client = getClient(resource.cloudProvider)
            ?: throw IllegalArgumentException("CloudFileHasher: unknown cloud provider ${resource.cloudProvider}")

        val length = if (maxBytes < 0) -1L else maxBytes
        val result = client.getFileInputStream(fileId = fileId, position = 0L, length = length)
        when (result) {
            is CloudResult.Success -> result.data.use { stream ->
                md5Hex(stream, maxBytes)
            }
            is CloudResult.Error -> {
                Timber.w("CloudFileHasher: getFileInputStream failed for ${file.path}: ${result.message}")
                throw Exception(result.message)
            }
        }
    }

    private fun getClient(provider: CloudProvider?): CloudStorageClient? = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> googleDriveClient
        CloudProvider.DROPBOX -> dropboxClient
        CloudProvider.ONEDRIVE -> oneDriveClient
        null -> null
    }
}
