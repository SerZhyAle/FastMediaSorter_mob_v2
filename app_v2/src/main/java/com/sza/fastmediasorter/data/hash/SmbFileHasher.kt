package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.network.SmbFileOperations
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.hash.FileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class SmbFileHasher @Inject constructor(
    private val smbFileOperations: SmbFileOperations,
    private val credentialsRepository: NetworkCredentialsRepository
) : FileHasher {

    override suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long
    ): String = withContext(Dispatchers.IO) {
        val creds = resource.credentialsId?.let {
            credentialsRepository.getByCredentialId(it)
        }
        val pathInfo = SmbPathUtils.parseSmbPath(
            path = file.path,
            username = creds?.username ?: "",
            password = creds?.password ?: "",
            domain = creds?.domain ?: ""
        ) ?: throw IllegalArgumentException("Cannot parse SMB path: ${file.path}")

        val result = smbFileOperations.openInputStream(pathInfo.connectionInfo, pathInfo.remotePath)
        when (result) {
            is SmbResult.Success -> result.data.use { stream ->
                md5Hex(stream, maxBytes)
            }
            is SmbResult.Error -> {
                Timber.w("SmbFileHasher: openInputStream failed for ${file.path}: ${result.message}")
                throw Exception(result.message)
            }
        }
    }
}
