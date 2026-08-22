package com.sza.fastmediasorter.wear.util

/**
 * Strips a full network URI (ftp://host:port/path or sftp://host:port/path) down to the
 * server-relative path that the protocol library expects.
 *
 * Parsing by hand rather than through [android.net.Uri] keeps file names containing '?' or '#'
 * intact - those are legal in an FTP path and would be eaten as query/fragment by the URI parser.
 */
object NetworkUriParser {

    /**
     * Returns the path portion of [streamUri].
     *
     * - `ftp://192.168.1.1:21/photos/cat.jpg` -> `/photos/cat.jpg`
     * - `sftp://host:22/file.png`              -> `/file.png`
     * - `ftp://host:21`                        -> `/`
     * - a string with no `://`                 -> returned as-is (already a path)
     */
    fun remotePathOf(streamUri: String): String {
        val afterScheme = streamUri.substringAfter("://", missingDelimiterValue = "")
        return when {
            afterScheme.isEmpty() -> streamUri
            !afterScheme.contains('/') -> "/"
            else -> "/" + afterScheme.substringAfter('/')
        }
    }
}
