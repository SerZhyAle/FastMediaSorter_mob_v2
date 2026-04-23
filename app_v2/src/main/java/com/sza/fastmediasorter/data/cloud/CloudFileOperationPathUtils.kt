package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Pure path/scheme/MIME helpers for CloudFileOperationHandler:
 * - Normalize legacy `scheme:/foo` URIs to `scheme://foo` and strip leading slashes.
 * - Classify a normalized path into [ResourceType].
 * - Strip the `scheme://host[:port]` prefix from SFTP/FTP URIs to get a remote path.
 * - Guess a MIME type from the file extension.
 *
 * Extracted to keep CloudFileOperationHandler below the 1000-line cap.
 */
class CloudFileOperationPathUtils(private val cloudPathParser: CloudPathParser) {

    fun normalizeNetworkPath(path: String): String {
        val stripped = if (path.startsWith("/")) path.substring(1) else path
        return when {
            stripped.startsWith("cloud:/") && !stripped.startsWith("cloud://") -> stripped.replaceFirst("cloud:/", "cloud://")
            stripped.startsWith("smb:/") && !stripped.startsWith("smb://") -> stripped.replaceFirst("smb:/", "smb://")
            stripped.startsWith("sftp:/") && !stripped.startsWith("sftp://") -> stripped.replaceFirst("sftp:/", "sftp://")
            stripped.startsWith("ftp:/") && !stripped.startsWith("ftp://") -> stripped.replaceFirst("ftp:/", "ftp://")
            else -> stripped
        }
    }

    fun getResourceType(path: String): ResourceType {
        val normalized = normalizeNetworkPath(path)
        return when {
            cloudPathParser.isCloudPath(normalized) -> ResourceType.CLOUD
            normalized.startsWith("smb://") -> ResourceType.SMB
            normalized.startsWith("sftp://") -> ResourceType.SFTP
            normalized.startsWith("ftp://") -> ResourceType.FTP
            else -> ResourceType.LOCAL
        }
    }

    fun isNetworkPath(path: String): Boolean {
        val normalized = normalizeNetworkPath(path)
        return normalized.startsWith("smb://") || normalized.startsWith("sftp://") || normalized.startsWith("ftp://")
    }

    fun extractSftpRemotePath(path: String, credentials: NetworkCredentialsResolver.NetworkCredentials): String {
        val normalized = normalizeNetworkPath(path)
        val withPortPrefix = "sftp://${credentials.server}:${credentials.port}"
        val withoutPortPrefix = "sftp://${credentials.server}"
        return when {
            normalized.startsWith(withPortPrefix) -> normalized.removePrefix(withPortPrefix)
            normalized.startsWith(withoutPortPrefix) -> normalized.removePrefix(withoutPortPrefix)
            else -> normalized.substringAfter("sftp://")
        }
    }

    fun extractFtpRemotePath(path: String, credentials: NetworkCredentialsResolver.NetworkCredentials): String {
        val normalized = normalizeNetworkPath(path)
        val withPortPrefix = "ftp://${credentials.server}:${credentials.port}"
        val withoutPortPrefix = "ftp://${credentials.server}"
        return when {
            normalized.startsWith(withPortPrefix) -> normalized.removePrefix(withPortPrefix)
            normalized.startsWith(withoutPortPrefix) -> normalized.removePrefix(withoutPortPrefix)
            else -> normalized.substringAfter("ftp://")
        }
    }

    companion object {
        fun getMimeType(fileName: String): String =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
    }
}
