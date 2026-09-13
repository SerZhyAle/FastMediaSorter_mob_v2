package com.sza.fastmediasorter.core.network

/**
 * S0066: protocol-agnostic resource key extraction used by NetworkVideoFrameDecoder
 * to gate playback-arbitration and to filter the transient-failure cache. S3069 added
 * BrowseShutdownCoordinator as a second consumer, which cancels throttled operations on shutdown,
 * and moved this file out of `data.network.glide` into `core` so the UI-layer caller does not have
 * to import the data layer (the `ui-imports-data` rule of `assert-source-gates.ps1`).
 *
 * Returns a normalized "<scheme>://host:port" for SMB (default 445), SFTP (default 22),
 * FTP (default 21) - the exact form ConnectionThrottleManager registers operations under, so a
 * change to the output shape silently stops every cancellation from matching. Returns null for any
 * other input - local paths, empty strings, and any other scheme (cloud thumbnails go through a
 * separate Glide pipeline and are out of scope).
 *
 * Parses by string rather than java.net.URI on purpose: an unencoded space is legal in an SMB
 * share name and illegal in a URI, and callers include a ViewModel onCleared path (S3069).
 */
internal fun extractNetworkResourceKey(path: String): String? {
    val (scheme, defaultPort) = when {
        path.startsWith("smb://") -> "smb" to 445
        path.startsWith("sftp://") -> "sftp" to 22
        path.startsWith("ftp://") -> "ftp" to 21
        else -> return null
    }
    val hostPort = path.removePrefix("$scheme://").split("/").firstOrNull() ?: return null
    if (hostPort.isEmpty()) return null
    val host = hostPort.substringBefore(":")
    val port = hostPort.substringAfter(":", defaultPort.toString())
    return "$scheme://$host:$port"
}

/**
 * True iff [path] is a network path that resolves to [resourceKey] under
 * [extractNetworkResourceKey]. Used to filter transient-failure entries by resource. S0066.
 */
internal fun pathBelongsToResource(path: String, resourceKey: String): Boolean {
    return extractNetworkResourceKey(path) == resourceKey
}
