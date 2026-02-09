package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
class LocalToSftpStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceLocal = sourceScheme == "file" || sourceScheme == "content" || sourceScheme == null
        val isDestSftp = destScheme == "sftp"
        return isSourceLocal && isDestSftp
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // TODO: Implement LocalToSftp copy when credentials resolution is available
        Timber.w("LocalToSftpStrategy.copy not yet implemented")
        return false
    }
}
