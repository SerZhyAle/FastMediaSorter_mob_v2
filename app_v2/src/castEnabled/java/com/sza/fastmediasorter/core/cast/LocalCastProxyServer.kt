package com.sza.fastmediasorter.core.cast

import android.content.Context
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.core.network.LanAddressResolver
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * In-process HTTP server that serves a single file to the Chromecast receiver.
 *
 * Key design decisions:
 * - Binds to 0.0.0.0 (all interfaces) so the Chromecast can reach the phone over LAN.
 * - [castUrl] returns the phone's actual Wi-Fi/LAN IP - NOT 127.0.0.1 (loopback is unreachable
 *   from a Chromecast device on the same network segment). Returns null when no LAN IP is resolved.
 * - RFC 1918 addresses are already permitted for cleartext in network_security_config.xml, so
 *   no XML change is needed.
 * - Tries port 8765, falls back to 8766 / 8767 if already in use.
 * - Every [serveFile] issues a fresh random token that [castUrl] carries in its path; any other path is
 *   404, so a LAN host that did not receive the URL cannot fetch the file.
 * - Honours a single `Range` request with 206: the Cast receiver seeks in a video by asking for a range,
 *   and a whole-file 200 left it unable to seek.
 */
class LocalCastProxyServer(
    private val context: Context,
    private val lanAddressProvider: () -> String? = { LanAddressResolver(context).resolve() },
) {

    companion object {
        private val CANDIDATE_PORTS = intArrayOf(8765, 8766, 8767)
        private const val ENDPOINT = "/cast-media"
        private const val BYTES_PREFIX = "bytes="

        private fun newToken(): String = UUID.randomUUID().toString().replace("-", "")

        /**
         * The span a `Range` header asks for within a file of [length] bytes: null when the header is not a
         * single well-formed byte range - RFC 9110 lets the server ignore it and send the whole file - and
         * an empty range when it is well-formed but cannot be satisfied (416).
         */
        @Suppress("ReturnCount")
        internal fun parseByteRange(header: String, length: Long): LongRange? {
            val spec = header.trim()
            if (!spec.startsWith(BYTES_PREFIX, ignoreCase = true) || ',' in spec) return null
            val bounds = spec.substring(BYTES_PREFIX.length).split('-', limit = 2)
            if (bounds.size != 2) return null
            val firstText = bounds[0].trim()
            val lastText = bounds[1].trim()
            if (firstText.isEmpty()) {
                val suffix = lastText.toLongOrNull() ?: return null
                return if (suffix <= 0L || length == 0L) LongRange.EMPTY else maxOf(0L, length - suffix) until length
            }
            val first = firstText.toLongOrNull() ?: return null
            if (lastText.isEmpty()) return if (first >= length) LongRange.EMPTY else first until length
            val last = lastText.toLongOrNull() ?: return null
            if (last < first) return null
            return if (first >= length) LongRange.EMPTY else first..minOf(last, length - 1)
        }

        /**
         * Resolves the MIME content type for [file] by extension. Shared with the Cast manager so
         * the [MediaInfo] content type and the bytes the proxy actually serves never diverge - the
         * default Cast receiver rejects a load whose content type is absent or mismatched.
         */
        fun mimeType(file: File): String {
            val ext = file.extension.lowercase()
            val fromMap = try {
                MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext)
            } catch (_: Exception) {
                null
            }
            return fromMap
                ?: when (ext) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    "wav" -> "audio/wav"
                    "aac" -> "audio/aac"
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    "avi" -> "video/x-msvideo"
                    "mov" -> "video/quicktime"
                    "gif" -> "image/gif"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
        }
    }

    private var server: InternalServer? = null
    private var activePort: Int = CANDIDATE_PORTS[0]

    /** The file currently being served. Call [serveFile] before [castUrl]. */
    @Volatile
    private var currentFile: File? = null

    @Volatile
    private var currentToken: String = newToken()

    fun serveFile(file: File) {
        currentToken = newToken()
        currentFile = file
    }

    /** Returns the full URL the Cast receiver should fetch (phone LAN IP), or null if unresolved. */
    fun castUrl(): String? = lanAddressProvider()?.let { ip -> "http://$ip:$activePort$ENDPOINT/$currentToken" }

    fun start() {
        for (port in CANDIDATE_PORTS) {
            try {
                val srv = InternalServer(port)
                srv.start()
                server = srv
                activePort = port
                Timber.d("LocalCastProxyServer: started on port $port")
                return
            } catch (e: Exception) {
                Timber.w("LocalCastProxyServer: port $port unavailable - ${e.message}")
            }
        }
        Timber.e("LocalCastProxyServer: all candidate ports are busy; Cast proxy not started")
    }

    fun stop() {
        server?.stop()
        server = null
        Timber.d("LocalCastProxyServer: stopped")
    }

    val isAlive: Boolean get() = server?.isAlive == true

    // ── NanoHTTPD inner server ────────────────────────────────────────────────

    private inner class InternalServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            val file = currentFile
            return when {
                session.uri != "$ENDPOINT/$currentToken" -> {
                    Timber.w("LocalCastProxyServer: refused a request for an unknown path")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
                }
                file == null || !file.exists() -> {
                    Timber.w("LocalCastProxyServer: serve called but no file set")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no file")
                }
                else -> fileResponse(file, session.headers["range"]).apply { addHeader("Accept-Ranges", "bytes") }
            }
        }

        private fun fileResponse(file: File, rangeHeader: String?): Response {
            val length = file.length()
            val mime = mimeType(file)
            val span = rangeHeader?.let { parseByteRange(it, length) }
            Timber.d("LocalCastProxyServer: serving ${file.name} ($mime, $length bytes, range $span)")
            return when {
                span == null -> newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), length)
                span.isEmpty() ->
                    newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                        .apply { addHeader("Content-Range", "bytes */$length") }
                else -> {
                    val stream = FileInputStream(file).apply { channel.position(span.first) }
                    newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        mime,
                        stream,
                        span.last - span.first + 1,
                    ).apply { addHeader("Content-Range", "bytes ${span.first}-${span.last}/$length") }
                }
            }
        }
    }
}
