package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Inet4Address
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local HTTP server broadcasting live audio with ICY metadata headers over LAN.
 */
@Suppress("MagicNumber")
class BroadcastHttpServer(private val context: Context) {

    companion object {
        private val CANDIDATE_PORTS = intArrayOf(8768, 8769, 8770)
        const val ENDPOINT = "/live-audio.aac"
        private const val DEFAULT_LAN_IP = "127.0.0.1"
        private const val BUFFER_SIZE_BYTES = 65536
    }

    private var server: InternalServer? = null
    private var activePort: Int = CANDIDATE_PORTS[0]

    private val outputStreams = CopyOnWriteArrayList<PipedOutputStream>()

    fun getBroadcastUrl(): String = "http://${getLanIp()}:$activePort$ENDPOINT"

    @Suppress("TooGenericExceptionCaught")
    fun start(): Int {
        for (port in CANDIDATE_PORTS) {
            try {
                val srv = InternalServer(port)
                srv.start()
                server = srv
                activePort = port
                Timber.d("BroadcastHttpServer: started on port $port")
                return port
            } catch (e: Exception) {
                Timber.w("BroadcastHttpServer: port $port unavailable - ${e.message}")
            }
        }
        Timber.e("BroadcastHttpServer: failed to bind candidate ports")
        return -1
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun writeFrame(buffer: ByteArray, offset: Int, length: Int) {
        outputStreams.forEach { pos ->
            try {
                pos.write(buffer, offset, length)
                pos.flush()
            } catch (e: Exception) {
                Timber.d(e, "BroadcastHttpServer: client disconnected")
                outputStreams.remove(pos)
                try {
                    pos.close()
                } catch (_: Exception) {}
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun stop() {
        outputStreams.forEach { pos ->
            try {
                pos.close()
            } catch (_: Exception) {}
        }
        outputStreams.clear()
        server?.stop()
        server = null
        Timber.d("BroadcastHttpServer: stopped")
    }

    val isAlive: Boolean get() = server?.isAlive == true

    private fun createClientStream(): InputStream {
        val pos = PipedOutputStream()
        val pis = PipedInputStream(pos, BUFFER_SIZE_BYTES)
        outputStreams.add(pos)
        return pis
    }

    private fun getLanIp(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLanIpApi31()
        } else {
            getLanIpLegacy()
        }
    }

    @Suppress("DEPRECATION", "TooGenericExceptionCaught")
    private fun getLanIpLegacy(): String {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: 0
            if (ip == 0) return DEFAULT_LAN_IP
            val octet1 = ip and 0xff
            val octet2 = (ip shr 8) and 0xff
            val octet3 = (ip shr 16) and 0xff
            val octet4 = (ip shr 24) and 0xff
            "$octet1.$octet2.$octet3.$octet4"
        } catch (e: Exception) {
            Timber.w("BroadcastHttpServer: getLanIpLegacy failed - ${e.message}")
            DEFAULT_LAN_IP
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun getLanIpApi31(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return DEFAULT_LAN_IP
            val props = cm.getLinkProperties(network) ?: return DEFAULT_LAN_IP
            props.linkAddresses
                .firstOrNull { la: LinkAddress ->
                    la.address is Inet4Address && !la.address.isLoopbackAddress
                }
                ?.address?.hostAddress ?: DEFAULT_LAN_IP
        } catch (e: Exception) {
            Timber.w("BroadcastHttpServer: getLanIpApi31 failed - ${e.message}")
            DEFAULT_LAN_IP
        }
    }

    private inner class InternalServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != ENDPOINT && session.uri != "/live-audio") {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }

            val clientStream = createClientStream()
            val response = newChunkedResponse(Response.Status.OK, "audio/aac", clientStream)
            response.addHeader("icy-name", "Phone Audio Broadcast")
            response.addHeader("icy-genre", "Live Audio")
            response.addHeader("icy-pub", "1")
            response.addHeader("icy-br", "128")
            response.addHeader("Accept-Ranges", "none")
            return response
        }
    }
}
