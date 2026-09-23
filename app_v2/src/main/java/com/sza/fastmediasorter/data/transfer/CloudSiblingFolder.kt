package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * S3409: a cloud folder (Google Drive, Dropbox, OneDrive) as a [SiblingFolder], addressed by the
 * provider's own id of each child.
 *
 * Written against [CloudStorageClient] rather than `CloudOperationStrategy`: the strategy reports the
 * path it was asked to write, not the id of what it created, and Google Drive keeps same-named files
 * apart only by id. [folderId] is blank for the Dropbox root, which the clients address as no parent.
 */
class CloudSiblingFolder(
    private val client: CloudStorageClient,
    private val folderId: String,
) : SiblingFolder {

    override suspend fun contains(name: String): Boolean = client.fileExists(name, folderId).orIoFailure()

    // No partial child to clean on failure: each provider commits an upload in one request, so a
    // refused or broken upload leaves nothing under the name.
    override suspend fun write(source: File, name: String): String {
        val uploaded = withContext(Dispatchers.IO) {
            source.inputStream().use { input ->
                client.uploadFile(
                    inputStream = input,
                    fileName = name,
                    mimeType = BINARY_MIME,
                    parentFolderId = folderId.ifBlank { null },
                    fileSize = source.length(),
                )
            }
        }
        return uploaded.orIoFailure().id.ifEmpty { throw IOException("the provider named no created file") }
    }

    override suspend fun read(address: String, target: File) {
        val downloaded = withContext(Dispatchers.IO) {
            target.outputStream().use { output -> client.downloadFile(address, output) }
        }.orIoFailure()
        if (!downloaded) throw IOException("the provider returned no content")
    }

    // A Dropbox id is the path itself, so the renamed child carries a new one.
    override suspend fun rename(address: String, newName: String): String =
        client.renameFile(address, newName).orIoFailure().id

    override suspend fun delete(address: String) {
        if (!client.deleteFile(address).orIoFailure()) throw IOException("the provider refused the delete")
    }

    private fun <T> CloudResult<T>.orIoFailure(): T = when (this) {
        is CloudResult.Success -> data
        is CloudResult.Error -> throw IOException(message, cause)
    }

    private companion object {
        const val BINARY_MIME = "application/octet-stream"
    }
}
