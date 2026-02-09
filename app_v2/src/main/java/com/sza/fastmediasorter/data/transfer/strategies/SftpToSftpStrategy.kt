package com.sza.fastmediasorter.data.transfer.strategies

import android.net.Uri
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpToSftpStrategy @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        return sourceScheme == "sftp" && destScheme == "sftp"
    }
    
    override suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement SftpToSftp move when credentials resolution is available
        Timber.w("SftpToSftpStrategy.move not yet implemented")
        throw UnsupportedOperationException("SftpToSftp move not yet implemented")
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement SftpToSftp copy when credentials resolution is available
        Timber.w("SftpToSftpStrategy.copy not yet implemented")
        return false
    }
}
