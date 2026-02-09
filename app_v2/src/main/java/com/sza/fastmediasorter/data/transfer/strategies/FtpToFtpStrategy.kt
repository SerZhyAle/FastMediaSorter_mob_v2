package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpToFtpStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        return sourceScheme == "ftp" && destScheme == "ftp"
    }
    
    override suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement FtpToFtp move when credentials resolution is available
        Timber.w("FtpToFtpStrategy.move not yet implemented")
        throw UnsupportedOperationException("FtpToFtp move not yet implemented")
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement FtpToFtp copy when credentials resolution is available
        Timber.w("FtpToFtpStrategy.copy not yet implemented")
        return false
    }
}
