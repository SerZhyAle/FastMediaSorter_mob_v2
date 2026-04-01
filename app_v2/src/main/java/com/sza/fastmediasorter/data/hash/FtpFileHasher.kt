package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.domain.hash.FileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.utils.FtpPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class FtpFileHasher @Inject constructor(
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : FileHasher {

    override suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long
    ): String = withContext(Dispatchers.IO) {
        val pathInfo = FtpPathUtils.parseFtpPath(file.path)
            ?: throw IllegalArgumentException("Cannot parse FTP path: ${file.path}")

        val creds = resource.credentialsId?.let {
            credentialsRepository.getByCredentialId(it)
        }

        val result = ftpClient.openInputStream(
            host = pathInfo.host,
            port = pathInfo.port,
            username = creds?.username ?: "anonymous",
            password = creds?.password ?: "",
            remotePath = pathInfo.remotePath
        )
        result.getOrElse { e ->
            Timber.w(e, "FtpFileHasher: openInputStream failed for ${file.path}")
            throw e
        }.use { stream ->
            md5Hex(stream, maxBytes)
        }
    }
}
