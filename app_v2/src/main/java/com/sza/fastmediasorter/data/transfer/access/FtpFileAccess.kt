package com.sza.fastmediasorter.data.transfer.access

import android.net.Uri
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.transfer.FileAccess
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpFileAccess @Inject constructor(
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : FileAccess {

    override fun supports(scheme: String?): Boolean {
        return scheme == "ftp"
    }

    override suspend fun exists(uri: Uri): Boolean {
        // TODO: Implement when credentials resolution is available
        return false
    }

    override suspend fun delete(uri: Uri): Boolean {
        // TODO: Implement when credentials resolution is available
        return false
    }
}
