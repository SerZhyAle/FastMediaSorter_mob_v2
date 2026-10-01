package com.sza.fastmediasorter.data.transfer.strategy

/** Endpoint and remote path of an `sftp:` URI as the SFTP strategy and handler address it. */
internal data class SftpPathInfo(
    val host: String,
    val port: Int,
    val username: String,
    val remotePath: String
)

private const val DEFAULT_SFTP_PORT = 22

/**
 * Parses `sftp://host:port/path` and `sftp://username@host:port/path`. `java.io.File` folds the
 * scheme separator to `sftp:/`, which parses the same. An empty username means "use the stored
 * credentials", so two paths without one address the same account.
 */
internal fun parseSftpStrategyPath(path: String): SftpPathInfo? {
    if (!path.startsWith("sftp:", ignoreCase = true)) return null
    val withoutProtocol = path.substringAfter("sftp:", "").trimStart('/')
    val userHostPart = withoutProtocol.substringBefore("/")
    val hasUser = userHostPart.contains("@")
    val hostPortPart = if (hasUser) userHostPart.substringAfter("@") else userHostPart
    return SftpPathInfo(
        host = hostPortPart.substringBefore(":"),
        port = hostPortPart.substringAfter(":", "").toIntOrNull() ?: DEFAULT_SFTP_PORT,
        username = if (hasUser) userHostPart.substringBefore("@").trim() else "",
        remotePath = "/" + withoutProtocol.substringAfter("/", "")
    )
}
