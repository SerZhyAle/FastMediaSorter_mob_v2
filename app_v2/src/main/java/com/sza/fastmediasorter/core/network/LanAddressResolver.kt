package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Resolves the phone's current local network (LAN) IPv4 address.
 *
 * Checks active network capabilities (Wi-Fi, Ethernet, VPN) first and falls back
 * to scanning local network interfaces. Returns null when no valid LAN IPv4 address
 * is bound, preventing loopback (127.0.0.1) fallback that fails on remote LAN receivers
 * (such as Chromecast or DLNA / ICY listeners).
 */
class LanAddressResolver(private val context: Context) {

    fun resolve(): String? {
        val activeAddress = resolveActiveNetworkAddress()
        return activeAddress ?: resolveInterfaceAddress()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resolveActiveNetworkAddress(): String? {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork
            val capabilities = network?.let(manager::getNetworkCapabilities)
            val isLanTransport = capabilities != null && LAN_TRANSPORTS.any(capabilities::hasTransport)
            if (network == null || !isLanTransport) {
                null
            } else {
                manager.getLinkProperties(network)?.linkAddresses?.firstUsableAddress(allowPublic = true)
            }
        } catch (error: Exception) {
            Timber.w(error, "LanAddressResolver: active network address unavailable")
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resolveInterfaceAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            Collections.list(interfaces)
                .asSequence()
                .filter { networkInterface ->
                    networkInterface.isUp && !networkInterface.isLoopback && !isMobile(networkInterface.name)
                }
                .sortedBy { networkInterface -> interfacePriority(networkInterface.name) }
                .mapNotNull { networkInterface ->
                    Collections.list(networkInterface.inetAddresses)
                        .filterIsInstance<Inet4Address>()
                        .firstOrNull(::isFallbackAddress)
                        ?.hostAddress
                }
                .firstOrNull()
        } catch (error: Exception) {
            Timber.w(error, "LanAddressResolver: interface address unavailable")
            null
        }
    }

    private fun List<LinkAddress>.firstUsableAddress(allowPublic: Boolean): String? {
        return asSequence()
            .map(LinkAddress::getAddress)
            .filterIsInstance<Inet4Address>()
            .firstOrNull { address -> isUsable(address) && (allowPublic || isFallbackAddress(address)) }
            ?.hostAddress
    }

    private fun isFallbackAddress(address: Inet4Address): Boolean {
        return isUsable(address) && (address.isSiteLocalAddress || address.isLinkLocalAddress)
    }

    private fun isUsable(address: Inet4Address): Boolean {
        return !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress
    }

    private fun isMobile(interfaceName: String): Boolean {
        val normalized = interfaceName.lowercase()
        return MOBILE_INTERFACE_PREFIXES.any(normalized::startsWith)
    }

    private fun interfacePriority(interfaceName: String): Int {
        val normalized = interfaceName.lowercase()
        return when {
            HOTSPOT_WIFI_PREFIXES.any(normalized::startsWith) -> PRIORITY_WIFI
            ETHERNET_PREFIXES.any(normalized::startsWith) -> PRIORITY_ETHERNET
            VPN_PREFIXES.any(normalized::startsWith) -> PRIORITY_VPN
            else -> PRIORITY_OTHER
        }
    }

    companion object {
        private val LAN_TRANSPORTS = intArrayOf(
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_VPN,
        )
        private val MOBILE_INTERFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "wwan", "radio")
        private val HOTSPOT_WIFI_PREFIXES = listOf("wlan", "swlan", "ap")
        private val ETHERNET_PREFIXES = listOf("eth", "en")
        private val VPN_PREFIXES = listOf("tun", "tap", "wg", "ppp")
        private const val PRIORITY_WIFI = 0
        private const val PRIORITY_ETHERNET = 1
        private const val PRIORITY_VPN = 2
        private const val PRIORITY_OTHER = 3
    }
}
