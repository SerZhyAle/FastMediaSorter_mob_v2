package com.sza.fastmediasorter.wear.data.network.ftp

import android.net.Uri
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

class FtpDataSource @Inject constructor() {

    suspend fun listDirectory(source: NetworkSource, path: String): List<WearMediaFile> =
        withContext(Dispatchers.IO) {
            val client = FTPClient()
            try {
                openSession(client, source)

                val files = client.listFiles(path)
                    ?: error("FTP listFiles returned null for path=$path")
                Timber.d("S1688: FTP listing decoded ${files.size} name(s), first=${files.firstOrNull()?.name}")

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

    /**
     * Open a file for reading. The caller owns the returned stream and closes it; the control
     * connection is torn down with it.
     */
    suspend fun getFileStream(source: NetworkSource, path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            val client = FTPClient()
            try {
                openSession(client, source)
                // S1687: without binary mode commons-net transfers in ASCII and silently corrupts
                // every media file it downloads.
                client.setFileType(FTP.BINARY_FILE_TYPE)
                Timber.d("Opening FTP file: $path")

                val stream = client.retrieveFileStream(path)
                    ?: error("FTP retrieveFileStream returned null for path=$path (code=${client.replyCode})")

                Result.success(streamClosingClient(stream, client, path))
            } catch (e: IOException) {
                failStream(e, client, path)
            } catch (e: IllegalStateException) {
                failStream(e, client, path)
            }
        }

    private fun failStream(
        cause: Exception,
        client: FTPClient,
        path: String
    ): Result<InputStream> {
        Timber.e(cause, "Failed to open FTP file stream for path=$path")
        runCatching { if (client.isConnected) client.disconnect() }
        return Result.failure(cause)
    }

    private fun openSession(client: FTPClient, source: NetworkSource) {
        // S1688: before connect, never after - the control-channel reader is built inside connect
        // from the encoding current at that moment.
        client.applyUtf8Encoding()
        client.connect(source.server, source.port)
        val replyCode = client.replyCode
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            error("FTP server refused connection (code=$replyCode)")
        }
        if (!client.login(source.username, source.password)) {
            error("FTP login failed for ${source.username}@${source.server}")
        }
        // S1688: RFC 2640 servers decode names as UTF-8 only after this; older ones refuse it and
        // keep working on the pre-connect encoding alone.
        client.enableUtf8Mode()
        client.enterLocalPassiveMode()
    }

    /**
     * The control connection has to outlive the data stream: commons-net only finishes the transfer
     * when completePendingCommand runs after the caller has closed the stream, and a client
     * disconnected any earlier truncates the download.
     */
    private fun streamClosingClient(
        stream: InputStream,
        client: FTPClient,
        path: String
    ): InputStream = object : FilterInputStream(stream) {
        override fun close() {
            try {
                super.close()
                if (!client.completePendingCommand()) {
                    Timber.w("FTP transfer did not complete cleanly for path=$path")
                }
            } finally {
                runCatching { client.logout() }
                runCatching { if (client.isConnected) client.disconnect() }
            }
        }
    }

    private fun inferMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
