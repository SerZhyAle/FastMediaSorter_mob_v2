package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpToLocalStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceSftp = sourceScheme == "sftp"
        val isDestLocal = destScheme == "file" || destScheme == "content" || destScheme == null
        return isSourceSftp && isDestLocal
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement SftpToLocal copy when credentials resolution is available
        Timber.w("SftpToLocalStrategy.copy not yet implemented")
        return false
    }
}
