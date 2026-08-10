package com.sza.fastmediasorter.data.networkmonitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.sza.fastmediasorter.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: which local protocol answered, kept so the history row can say how the address was learned. */
enum class RouterProbe { NAT_PMP, UPNP }

/** S1433: what the local router was willing to say about its own WAN address. */
sealed interface RouterWanAddress {

    data class Known(val address: String, val probe: RouterProbe) : RouterWanAddress

    /**
     * No router answered. This is the common case on consumer hardware with both protocols disabled, so
     * it is the absence of evidence and never evidence of CGNAT - see [ResolveExternalIpUseCase].
     */
    data object Unanswered : RouterWanAddress
}

/**
 * S1433: asks the default gateway for the address its WAN side holds, so the CGNAT comparison has a second
 * number to compare against.
 *
 * Two protocols, cheapest first, exactly as strategic §6.5 fixed the order. NAT-PMP is a single unicast UDP
 * exchange with no discovery stage; UPnP IGD needs an SSDP multicast round before it can even send its SOAP
 * call, so paying that cost first would tax every router that would have answered NAT-PMP immediately.
 *
 * No third-party library and no new permission: `CHANGE_WIFI_MULTICAST_STATE` is already declared, and the
 * two HTTP calls go through [HttpURLConnection] rather than the shared OkHttp client on purpose - that
 * client's debug interceptors persist response bodies, and these bodies carry the WAN address.
 *
 * Everything here is bounded by short timeouts: a diagnostic the user is waiting on must fail fast, and a
 * router that ignores us must not hold the screen.
 */
@Singleton
class RouterWanAddressDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun resolve(): RouterWanAddress = withContext(ioDispatcher) {
        val natPmp = defaultGateway()?.let { queryNatPmp(it) }
        val upnp = if (natPmp == null) queryUpnp() else null
        when {
            natPmp != null -> RouterWanAddress.Known(natPmp, RouterProbe.NAT_PMP)
            upnp != null -> RouterWanAddress.Known(upnp, RouterProbe.UPNP)
            else -> RouterWanAddress.Unanswered
        }
    }

    /** The gateway of the active network's default route, which is the only host worth asking. */
    private fun defaultGateway(): InetAddress? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager?.activeNetwork
            ?.let { manager.getLinkProperties(it) }
            ?.routes
            ?.firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.takeIf { it is Inet4Address }
    }

    /**
     * NAT-PMP public-address exchange (RFC 6886): a two-byte request, a twelve-byte reply whose last four
     * bytes are the external IPv4 address.
     */
    private fun queryNatPmp(gateway: InetAddress): String? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = NAT_PMP_TIMEOUT_MS
            socket.send(DatagramPacket(NAT_PMP_REQUEST, NAT_PMP_REQUEST.size, gateway, NAT_PMP_PORT))
            val buffer = ByteArray(NAT_PMP_RESPONSE_SIZE)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            buffer.readNatPmpAddress(reply.length)
        }
    } catch (e: IOException) {
        // Silence is the expected answer from a router with NAT-PMP off; the caller falls through to UPnP.
        Timber.d("Router WAN: NAT-PMP did not answer (%s)", e.javaClass.simpleName)
        null
    }

    /** SSDP discovery, then the device description, then one SOAP call - any step may come up empty. */
    private fun queryUpnp(): String? {
        val location = withMulticastLock { discoverIgdLocation() }
        val service = location?.let { fetchControlEndpoint(it) }
        return service?.let { requestExternalIpAddress(it) }
    }

    /**
     * Holds a Wi-Fi multicast lock across the SSDP exchange. Some ROMs drop multicast to sleeping Wi-Fi
     * without it, which would look exactly like a router that does not speak UPnP.
     */
    private fun <T> withMulticastLock(block: () -> T): T {
        val lock = (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock(MULTICAST_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
        lock?.acquire()
        return try {
            block()
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    /** Returns the `LOCATION` URL of the first Internet Gateway Device that answers the M-SEARCH. */
    private fun discoverIgdLocation(): String? = try {
        MulticastSocket().use { socket ->
            socket.soTimeout = SSDP_TIMEOUT_MS
            val payload = SSDP_SEARCH.toByteArray()
            val group = InetAddress.getByName(SSDP_ADDRESS)
            socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(group, SSDP_PORT)))
            val buffer = ByteArray(SSDP_REPLY_SIZE)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            String(reply.data, 0, reply.length).locationHeader()
        }
    } catch (e: IOException) {
        Timber.d("Router WAN: no UPnP gateway answered SSDP (%s)", e.javaClass.simpleName)
        null
    }

    /**
     * Reads the device description and pairs a WAN connection service with its control URL. The two are
     * taken from the same `<service>` block, because a description lists several services and pairing a
     * type with a stranger's control URL would send the SOAP call to the wrong endpoint.
     */
    private fun fetchControlEndpoint(location: String): ControlEndpoint? = try {
        val description = URL(location).readTextBounded()
        val block = SERVICE_BLOCK.findAll(description)
            .map { it.value }
            .firstOrNull { block -> WAN_SERVICE_TYPES.any { block.contains(it) } }
        block?.toControlEndpoint(location)
    } catch (e: IOException) {
        Timber.d("Router WAN: UPnP description unreadable (%s)", e.javaClass.simpleName)
        null
    }

    /** One SOAP `GetExternalIPAddress` call against the paired control URL. */
    private fun requestExternalIpAddress(service: ControlEndpoint): String? = try {
        val body = SOAP_BODY.format(service.type)
        val connection = (URL(service.controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = UPNP_TIMEOUT_MS
            readTimeout = UPNP_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"${service.type}#GetExternalIPAddress\"")
        }
        val reply = connection.use { open ->
            open.outputStream.write(body.toByteArray())
            open.readBodyBounded()
        }
        reply?.let { EXTERNAL_IP_ELEMENT.find(it)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.isNotEmpty() }
    } catch (e: IOException) {
        Timber.d("Router WAN: UPnP SOAP call failed (%s)", e.javaClass.simpleName)
        null
    }

    /** A WAN connection service and the control URL declared alongside it in the same block. */
    private data class ControlEndpoint(val type: String, val controlUrl: String)

    private companion object {

        val NAT_PMP_REQUEST = byteArrayOf(0, 0)
        const val NAT_PMP_PORT = 5351
        const val NAT_PMP_TIMEOUT_MS = 1_500
        const val NAT_PMP_RESPONSE_SIZE = 12
        const val NAT_PMP_VERSION: Byte = 0

        /** RFC 6886 answers a request opcode with the same opcode plus 128, which is -128 as a signed byte. */
        const val NAT_PMP_RESPONSE_OPCODE: Byte = -128
        const val RESULT_SUCCESS: Byte = 0
        const val OFFSET_VERSION = 0
        const val OFFSET_OPCODE = 1
        const val OFFSET_RESULT_HIGH = 2
        const val OFFSET_RESULT_LOW = 3
        const val NAT_PMP_ADDRESS_OFFSET = 8
        const val IPV4_BYTE_COUNT = 4
        const val BYTE_MASK = 0xFF

        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val SSDP_TIMEOUT_MS = 2_000
        const val SSDP_REPLY_SIZE = 2048
        const val UPNP_TIMEOUT_MS = 2_000
        const val MULTICAST_LOCK_TAG = "fms-network-monitor-igd"

        /** Body caps: a router answering with something enormous is a fault, not a payload to buffer. */
        const val MAX_DESCRIPTION_BYTES = 256 * 1024
        const val MAX_SOAP_BYTES = 16 * 1024

        val WAN_SERVICE_TYPES = listOf(
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1",
        )

        val SSDP_SEARCH = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n")
        }

        val SOAP_BODY = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
             s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body><u:GetExternalIPAddress xmlns:u="%s"/></s:Body></s:Envelope>
        """.trimIndent()

        val SERVICE_BLOCK = Regex("<service>.*?</service>", RegexOption.DOT_MATCHES_ALL)
        val SERVICE_TYPE_ELEMENT = Regex("<serviceType>(.*?)</serviceType>", RegexOption.DOT_MATCHES_ALL)
        val CONTROL_URL_ELEMENT = Regex("<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL)
        val EXTERNAL_IP_ELEMENT = Regex("<NewExternalIPAddress>(.*?)</NewExternalIPAddress>")
        val LOCATION_HEADER = Regex("(?im)^LOCATION:\\s*(\\S+)\\s*$")

        /**
         * The address bytes are only meaningful when the result code is zero; RFC 6886 leaves the field
         * undefined otherwise, so a failing router must not be reported as holding address 0.0.0.0.
         */
        fun ByteArray.readNatPmpAddress(length: Int): String? {
            val usable = length >= NAT_PMP_RESPONSE_SIZE &&
                this[OFFSET_VERSION] == NAT_PMP_VERSION &&
                this[OFFSET_OPCODE] == NAT_PMP_RESPONSE_OPCODE &&
                this[OFFSET_RESULT_HIGH] == RESULT_SUCCESS &&
                this[OFFSET_RESULT_LOW] == RESULT_SUCCESS
            return if (!usable) {
                null
            } else {
                (0 until IPV4_BYTE_COUNT).joinToString(".") {
                    (this[NAT_PMP_ADDRESS_OFFSET + it].toInt() and BYTE_MASK).toString()
                }
            }
        }

        fun String.locationHeader(): String? = LOCATION_HEADER.find(this)?.groupValues?.getOrNull(1)

        /** A relative `controlURL` is resolved against the description URL, as the UPnP spec requires. */
        fun String.toControlEndpoint(location: String): ControlEndpoint? {
            val type = SERVICE_TYPE_ELEMENT.find(this)?.groupValues?.getOrNull(1)?.trim()
            val control = CONTROL_URL_ELEMENT.find(this)?.groupValues?.getOrNull(1)?.trim()
            return if (type == null || control.isNullOrEmpty()) {
                null
            } else {
                ControlEndpoint(type, URL(URL(location), control).toString())
            }
        }

        fun URL.readTextBounded(): String {
            val connection = (openConnection() as HttpURLConnection).apply {
                connectTimeout = UPNP_TIMEOUT_MS
                readTimeout = UPNP_TIMEOUT_MS
            }
            return connection.use { it.readBodyBounded(MAX_DESCRIPTION_BYTES) }.orEmpty()
        }

        fun HttpURLConnection.readBodyBounded(limit: Int = MAX_SOAP_BYTES): String? =
            if (responseCode !in HTTP_OK_RANGE) null else inputStream.readNBytes(limit).decodeToString()

        val HTTP_OK_RANGE = HttpURLConnection.HTTP_OK..HttpURLConnection.HTTP_PARTIAL

        inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
            block(this)
        } finally {
            disconnect()
        }
    }
}
