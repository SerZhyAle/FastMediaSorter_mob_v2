package com.sza.fastmediasorter.data.util

import java.net.URI
import java.util.Locale

/**
 * S1511: turns a channel address into the stable key the quality memory is filed under.
 *
 * The memory is keyed by address rather than by the catalog row id (strategic ADR-5) because the id is
 * reissued whenever the same channel is removed and re-imported, which the stream catalog does regularly -
 * an id-keyed record would silently reset exactly when it is worth the most.
 *
 * Only the parts a server treats case-insensitively are folded: scheme and host. Path, query and fragment
 * keep their case, because a stream path is frequently case-sensitive and two rungs of the same channel can
 * differ by nothing else. An address that cannot be parsed as a URI is returned trimmed, so an odd source
 * still gets a key of its own instead of colliding with every other unparsable address.
 */
object StreamUrlNormalizer {

    fun normalize(url: String): String {
        val trimmed = url.trim()
        val parsed = runCatching { URI(trimmed) }.getOrNull()
        val scheme = parsed?.scheme
        val host = parsed?.host
        return if (parsed == null || scheme == null || host == null) {
            dropTrailingSlash(trimmed)
        } else {
            rebuild(parsed, scheme, host)
        }
    }

    private fun rebuild(uri: URI, scheme: String, host: String): String {
        val lowerScheme = scheme.lowercase(Locale.ROOT)
        val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
        val port = uri.port
        val portPart = if (port < 0 || port == defaultPortOf(lowerScheme)) "" else ":$port"
        val path = dropTrailingSlash(uri.rawPath.orEmpty())
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return lowerScheme + "://" + userInfo + host.lowercase(Locale.ROOT) + portPart + path + query + fragment
    }

    private fun defaultPortOf(lowerScheme: String): Int = when (lowerScheme) {
        "http" -> HTTP_DEFAULT_PORT
        "https" -> HTTPS_DEFAULT_PORT
        "rtsp" -> RTSP_DEFAULT_PORT
        else -> NO_DEFAULT_PORT
    }

    private fun dropTrailingSlash(value: String): String = value.removeSuffix("/")

    private const val HTTP_DEFAULT_PORT = 80
    private const val HTTPS_DEFAULT_PORT = 443
    private const val RTSP_DEFAULT_PORT = 554
    private const val NO_DEFAULT_PORT = -1
}
