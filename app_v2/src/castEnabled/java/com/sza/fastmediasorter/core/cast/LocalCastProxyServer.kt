package com.sza.fastmediasorter.core.cast

import android.content.Context
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.core.network.LanAddressResolver
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File

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
 */
class LocalCastProxyServer(
    private val context: Context,
    private val lanAddressProvider: () -> String? = { LanAddressResolver(context).resolve() },
) {

    companion object {
        private val CANDIDATE_PORTS = intArrayOf(8765, 8766, 8767)
        private const val ENDPOINT = "/cast-media"

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

    fun serveFile(file: File) {
        currentFile = file
    }

    /** Returns the full URL the Cast receiver should fetch (phone LAN IP), or null if unresolved. */
    fun castUrl(): String? = lanAddressProvider()?.let { ip -> "http://$ip:$activePort$ENDPOINT" }

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
            if (file == null || !file.exists()) {
                Timber.w("LocalCastProxyServer: serve called but no file set")
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no file"
                )
            }
            val mime = mimeType(file)
            Timber.d("LocalCastProxyServer: serving ${file.name} ($mime, ${file.length()} bytes)")
            return newChunkedResponse(Response.Status.OK, mime, file.inputStream())
        }
    }
}
