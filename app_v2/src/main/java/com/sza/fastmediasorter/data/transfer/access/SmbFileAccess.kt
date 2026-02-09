package com.sza.fastmediasorter.data.transfer.access

import android.net.Uri
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.FileAccess
import com.sza.fastmediasorter.utils.SmbPathUtils
import javax.inject.Inject

class SmbFileAccess @Inject constructor(
    private val smbClient: SmbClient
) : FileAccess {

    override fun supports(scheme: String?): Boolean {
        return scheme == "smb"
    }

    override suspend fun exists(uri: Uri): Boolean {
        val smbInfo = SmbPathUtils.parseSmbPath(uri.toString()) ?: return false
        val result = smbClient.exists(smbInfo.connectionInfo, smbInfo.remotePath)
        return result is SmbResult.Success && result.data
    }

    override suspend fun delete(uri: Uri): Boolean {
        val smbInfo = SmbPathUtils.parseSmbPath(uri.toString()) ?: return false
        val result = smbClient.deleteFile(smbInfo.connectionInfo, smbInfo.remotePath)
        return result is SmbResult.Success
    }
}
