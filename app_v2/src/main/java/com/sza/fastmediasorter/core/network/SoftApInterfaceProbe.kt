package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.domain.model.network.HotspotState
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface descriptor representing a network interface name and operational state.
 */
data class NetworkInterfaceDescriptor(
    val name: String,
    val isUp: Boolean
)

/**
 * Probe evaluating network interfaces to detect Wi-Fi hotspot (soft-AP) state.
 *
 * Uses interface state (isUp) rather than presence, and an allowlist of soft-AP names, so Wi-Fi Direct
 * (p2p0), the Wi-Fi station (wlan0), VPN tunnels, USB tethering (rndis, usb) and Bluetooth tethering
 * (bt-pan) cannot be mistaken for a hotspot.
 */
@Singleton
class SoftApInterfaceProbe @Inject constructor() {

    fun probe(): HotspotState {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.map { iface ->
                NetworkInterfaceDescriptor(
                    name = iface.name,
                    isUp = runCatching { iface.isUp }.getOrDefault(false)
                )
            }
        }.getOrNull()

        return evaluateInterfaces(interfaces)
    }

    fun evaluateInterfaces(interfaces: List<NetworkInterfaceDescriptor>?): HotspotState {
        if (interfaces == null) return HotspotState.UNKNOWN

        // An allowlist, not a denylist: p2p0, wlan0, tun/vpn, rndis/usb and bt-pan are excluded by not
        // being named, so a new tunnel or tethering kind cannot read as a hotspot until it is added here.
        val candidateNames = setOf("ap0", "wlan1", "swlan0", "softap0")
        // State, not presence - research 03 found swlan0 present but down while the hotspot is off, so a
        // name match alone would report ENABLED forever on Samsung.
        val liveSoftAp = interfaces.any { iface -> iface.name.lowercase() in candidateNames && iface.isUp }
        return if (liveSoftAp) HotspotState.ENABLED else HotspotState.DISABLED
    }
}
