package com.sza.fastmediasorter.core.util

import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
import timber.log.Timber

object MediaFilePathDescriptor {

    data class Decomposition(
        val scheme: String,
        val host: String?,
        val port: Int?,
        val share: String?,
        val directory: String?,
        val filename: String,
        val extension: String?,
        val displayPath: String,
        val canonicalPath: String
    )

    fun decompose(path: String, cloudDisplayPath: String? = null): Decomposition {
        return try {
            when {
                path.startsWith("smb://", ignoreCase = true) -> decomposeSmbPath(path)
                path.startsWith("sftp://", ignoreCase = true) -> decomposeSftpPath(path)
                path.startsWith("ftp://", ignoreCase = true) -> decomposeFtpPath(path)
                path.startsWith("cloud://", ignoreCase = true) || path.startsWith("cloud:/") ->
                    degenerate("cloud", path, displayPath = cloudDisplayPath ?: path)
                path.startsWith("content://", ignoreCase = true) ->
                    degenerate("content", path, displayPath = path)
                else -> decomposeLocalPath(path)
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaFilePathDescriptor: failed to decompose path")
            degenerate(path.substringBefore("://").ifEmpty { "local" }, path, displayPath = path)
        }
    }

    private fun decomposeSmbPath(path: String): Decomposition {
        val info = SmbPathUtils.parseSmbPath(path)
            ?: return degenerate("smb", path, displayPath = path)
        val remotePath = info.remotePath
        val filename = remotePath.substringAfterLast('/')
        val directory = remotePath.substringBeforeLast('/', "").ifEmpty { null }
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        val host = info.connectionInfo.server
        val port = info.connectionInfo.port
        val share = info.connectionInfo.shareName
        return Decomposition(
            scheme = "smb",
            host = host,
            port = port,
            share = share,
            directory = directory,
            filename = filename,
            extension = extension,
            displayPath = buildNetworkDisplayPath(host, port, share, remotePath),
            canonicalPath = path
        )
    }

    private fun decomposeSftpPath(path: String): Decomposition {
        val info = SftpPathUtils.parseSftpPath(path)
            ?: return degenerate("sftp", path, displayPath = path)
        val remotePath = info.remotePath
        val filename = remotePath.substringAfterLast('/')
        val directory = remotePath.substringBeforeLast('/', "").ifEmpty { null }
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        return Decomposition(
            scheme = "sftp",
            host = info.host,
            port = info.port,
            share = null,
            directory = directory,
            filename = filename,
            extension = extension,
            displayPath = buildNetworkDisplayPath(info.host, info.port, null, remotePath),
            canonicalPath = path
        )
    }

    private fun decomposeFtpPath(path: String): Decomposition {
        val info = FtpPathUtils.parseFtpPath(path)
            ?: return degenerate("ftp", path, displayPath = path)
        val remotePath = info.remotePath
        val filename = remotePath.substringAfterLast('/')
        val directory = remotePath.substringBeforeLast('/', "").ifEmpty { null }
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        return Decomposition(
            scheme = "ftp",
            host = info.host,
            port = info.port,
            share = null,
            directory = directory,
            filename = filename,
            extension = extension,
            displayPath = buildNetworkDisplayPath(info.host, info.port, null, remotePath),
            canonicalPath = path
        )
    }

    private fun decomposeLocalPath(path: String): Decomposition {
        val filename = path.substringAfterLast('/')
        val directory = path.substringBeforeLast('/', "").ifEmpty { null }
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        return Decomposition(
            scheme = "local",
            host = null,
            port = null,
            share = null,
            directory = directory,
            filename = filename,
            extension = extension,
            displayPath = path,
            canonicalPath = path
        )
    }

    private fun buildNetworkDisplayPath(host: String, port: Int?, share: String?, remotePath: String): String {
        val portPart = if (port != null) ":$port" else ""
        val sharePart = if (!share.isNullOrEmpty()) "/$share" else ""
        return "//$host$portPart$sharePart$remotePath"
    }

    private fun degenerate(scheme: String, path: String, displayPath: String): Decomposition {
        val filename = path.substringAfterLast('/').ifEmpty { path }
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        return Decomposition(
            scheme = scheme,
            host = null,
            port = null,
            share = null,
            directory = null,
            filename = filename,
            extension = extension,
            displayPath = displayPath,
            canonicalPath = path
        )
    }
}
