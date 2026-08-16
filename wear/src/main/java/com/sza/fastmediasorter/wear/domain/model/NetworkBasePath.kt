package com.sza.fastmediasorter.wear.domain.model

/**
 * The phone stores a network resource as a full URL ("smb://server/share/sub", "sftp://host:22/dir")
 * because that is what its own pickers produce. Every watch-side client instead needs the part below
 * the connection it has already opened: SMBJ lists paths relative to the connected share, and the
 * FTP/SFTP clients list absolute server paths without a scheme or a host. Normalising once at import
 * keeps that difference out of each data source (S1556).
 */
object NetworkBasePath {

    private const val SCHEME_SEPARATOR = "://"

    /**
     * @return share-relative path for SMB (empty means the share root), absolute server path
     *   otherwise. A value that is already in that form is returned unchanged.
     */
    fun normalize(rawPath: String, type: NetworkSourceType, shareName: String?): String {
        val trimmed = rawPath.trim()
        val schemeIndex = trimmed.indexOf(SCHEME_SEPARATOR)
        val withoutHost = when {
            trimmed.isEmpty() -> return defaultFor(type)
            schemeIndex < 0 -> trimmed
            else -> stripHost(trimmed, schemeIndex)
        }
        return normalizeRelative(withoutHost, type, shareName)
    }

    /** Drops "scheme://" and the "host[:port]" segment that follows it. */
    private fun stripHost(path: String, schemeIndex: Int): String = path
        .substring(schemeIndex + SCHEME_SEPARATOR.length)
        .substringAfter('/', missingDelimiterValue = "")

    private fun normalizeRelative(path: String, type: NetworkSourceType, shareName: String?): String {
        val segments = path.split('/', '\\').filter { it.isNotEmpty() }
        if (type != NetworkSourceType.SMB) {
            return if (segments.isEmpty()) "/" else segments.joinToString(separator = "/", prefix = "/")
        }
        val share = shareName?.trim()?.trim('/')
        val withoutShare = if (!share.isNullOrEmpty() && segments.firstOrNull().equals(share, ignoreCase = true)) {
            segments.drop(1)
        } else {
            segments
        }
        return withoutShare.joinToString(separator = "/")
    }

    private fun defaultFor(type: NetworkSourceType): String =
        if (type == NetworkSourceType.SMB) "" else "/"
}
