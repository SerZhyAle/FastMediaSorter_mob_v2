package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.hash.FileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.utils.SftpPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class SftpFileHasher @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : FileHasher {

    override suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long
    ): String = withContext(Dispatchers.IO) {
        val pathInfo = SftpPathUtils.parseSftpPath(file.path)
            ?: throw IllegalArgumentException("Cannot parse SFTP path: ${file.path}")

        val creds = resource.credentialsId?.let {
            credentialsRepository.getByCredentialId(it)
        }

        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = pathInfo.host,
            port = pathInfo.port,
            username = creds?.username ?: "",
            password = creds?.password ?: "",
            privateKey = creds?.decryptedSshPrivateKey
        )

        val result = sftpClient.openInputStream(connectionInfo, pathInfo.remotePath)
        result.getOrElse { e ->
            Timber.w(e, "SftpFileHasher: openInputStream failed for ${file.path}")
            throw e
        }.use { stream ->
            md5Hex(stream, maxBytes)
        }
    }
}
