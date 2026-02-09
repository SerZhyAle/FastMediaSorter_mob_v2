package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalToFtpStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceLocal = sourceScheme == "file" || sourceScheme == "content" || sourceScheme == null
        val isDestFtp = destScheme == "ftp"
        return isSourceLocal && isDestFtp
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement LocalToFtp copy when credentials resolution is available
        Timber.w("LocalToFtpStrategy.copy not yet implemented")
        return false
    }
}
