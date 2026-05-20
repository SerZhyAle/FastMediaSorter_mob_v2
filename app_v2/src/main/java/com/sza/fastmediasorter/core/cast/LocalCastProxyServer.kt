package com.sza.fastmediasorter.core.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File
import java.net.Inet4Address

/**
 * In-process HTTP server that serves a single file to the Chromecast receiver.
 *
 * Key design decisions:
 * - Binds to 0.0.0.0 (all interfaces) so the Chromecast can reach the phone over LAN.
 * - [castUrl] returns the phone's actual Wi-Fi LAN IP - NOT 127.0.0.1 (loopback is unreachable
 *   from a Chromecast device on the same network segment).
 * - RFC 1918 addresses are already permitted for cleartext in network_security_config.xml, so
 *   no XML change is needed.
 * - Tries port 8765, falls back to 8766 / 8767 if already in use.
 */
class LocalCastProxyServer(private val context: Context) {

    companion object {
        private val CANDIDATE_PORTS = intArrayOf(8765, 8766, 8767)
        private const val ENDPOINT = "/cast-media"
    }

    private var server: InternalServer? = null
    private var activePort: Int = CANDIDATE_PORTS[0]

    /** The file currently being served. Call [serveFile] before [castUrl]. */
    @Volatile
    private var currentFile: File? = null

    fun serveFile(file: File) {
        currentFile = file
    }

    /** Returns the full URL the Cast receiver should fetch (phone LAN IP). */
    fun castUrl(): String = "http://${getLanIp()}:$activePort$ENDPOINT"

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

    // ── LAN IP resolution ────────────────────────────────────────────────────

    private fun getLanIp(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLanIpApi31()
        } else {
            getLanIpLegacy()
        }
    }

    @Suppress("DEPRECATION")
    private fun getLanIpLegacy(): String {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return "127.0.0.1"
            // WifiManager returns little-endian int on all Android versions
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (e: Exception) {
            Timber.w("LocalCastProxyServer: getLanIpLegacy failed - ${e.message}")
            "127.0.0.1"
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getLanIpApi31(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "127.0.0.1"
            val props = cm.getLinkProperties(network) ?: return "127.0.0.1"
            props.linkAddresses
                .firstOrNull { la: LinkAddress ->
                    la.address is Inet4Address && !la.address.isLoopbackAddress
                }
                ?.address?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            Timber.w("LocalCastProxyServer: getLanIpApi31 failed - ${e.message}")
            "127.0.0.1"
        }
    }

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
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
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
            Timber.d("LocalCastProxyServer: serving ${file.name} ($mime, ${file.length()} bytes)")
            return newChunkedResponse(Response.Status.OK, mime, file.inputStream())
        }
    }
}
