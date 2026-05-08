package com.sza.fastmediasorter.wear.data.network.ftp

import android.net.Uri
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import javax.inject.Inject

class FtpDataSource @Inject constructor() {

    suspend fun listDirectory(source: NetworkSource, path: String): List<WearMediaFile> =
        withContext(Dispatchers.IO) {
            val client = FTPClient()
            try {
                client.connect(source.server, source.port)
                val replyCode = client.replyCode
                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    error("FTP server refused connection (code=$replyCode)")
                }
                if (!client.login(source.username, source.password)) {
                    error("FTP login failed for ${source.username}@${source.server}")
                }
                client.enterLocalPassiveMode()

                val files = client.listFiles(path)
                    ?: error("FTP listFiles returned null for path=$path")

                files.mapIndexed { index, ftpFile ->
                    val filePath = if (path.trimEnd('/').isEmpty()) {
                        "/${ftpFile.name}"
                    } else {
                        "${path.trimEnd('/')}/${ftpFile.name}"
                    }
                    WearMediaFile(
                        id = index.toLong(),
                        name = ftpFile.name,
                        uri = Uri.parse("ftp://${source.server}:${source.port}$filePath"),
                        mimeType = inferMimeType(ftpFile.name),
                        size = ftpFile.size,
                        dateModified = ftpFile.timestamp?.timeInMillis ?: 0L
                    )
                }
            } finally {
                runCatching { if (client.isConnected) client.disconnect() }
            }
        }

    private fun inferMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
