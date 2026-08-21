package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: one immutable sample of everything the Monitor is allowed to know about the device's networking.
 *
 * Deliberately free of subscriber identity. No IMEI, ICCID, phone number, BSSID or radio configuration has
 * a field here, so no later layer can leak one by accident - strategic §3.2 forbids them in UI, export and
 * logs alike, and the cheapest way to keep that promise is to give them nowhere to live.
 *
 * [activeModemCount] sits outside [sims] on purpose: the count is readable with no permission at all, while
 * per-subscription detail needs `READ_PHONE_STATE`. Folding it into the section would hide a fact the user
 * is entitled to behind a permission they declined.
 */
data class NetworkMonitorSnapshot(
    val networks: List<VisibleNetwork>,
    val activeLink: ActiveLink?,
    val wifi: MonitorSection<WifiEntry>,
    val sims: MonitorSection<List<SimEntry>>,
    val activeModemCount: Int?,
    val bluetooth: MonitorSection<BluetoothEntry>,
    val sampledAtMillis: Long,
)

/** S1433: how a network reaches the outside world, as far as Android will say. */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    VPN,
    OTHER,
}

/**
 * S1433: one network Android currently exposes to this app.
 *
 * [hasValidatedInternet] is the platform's own verdict, not a reachability guess of ours: a network can be
 * connected, unmetered and useless, and only the validation flag distinguishes that from a working one.
 */
data class VisibleNetwork(
    val transport: NetworkTransport,
    val isActive: Boolean,
    val hasValidatedInternet: Boolean,
    val isCaptivePortal: Boolean,
    val isMetered: Boolean,
    val isVpn: Boolean,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
)

/**
 * S1433: link detail of the active network.
 *
 * [gateway] is inferred from the default route, which is as far as an ordinary app can honestly go -
 * research artifact 01 records that Android exposes no router or LAN topology, so nothing here may be
 * presented as a map of the network beyond this hop.
 */
data class ActiveLink(
    val interfaceName: String?,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val dnsServers: List<String>,
    val gateway: String?,
    val hasDefaultRoute: Boolean,
    val proxy: String?,
)

/**
 * S1433: the Wi-Fi network this device is connected to.
 *
 * No BSSID field, by decision rather than omission: it is a location-sensitive identifier, and research
 * artifact 02 scopes the first release to the connected network's own signal and link facts.
 *
 * [ssidAvailability] exists because the platform withholds the name alone while answering every other field
 * here (S1853). [ssid] is non-null if and only if this is [SectionAvailability.Available]; any other value
 * is the reason the name is missing, which the section states instead of writing "unknown".
 */
data class WifiEntry(
    val ssid: String?,
    val ssidAvailability: SectionAvailability,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val standard: String?,
)

/**
 * S1433: one visible SIM subscription.
 *
 * [slotIndex] identifies the slot for the UI; nothing here identifies the subscriber.
 */
data class SimEntry(
    val slotIndex: Int,
    val operatorName: String?,
    val isDataPreferred: Boolean,
)

/**
 * S1433: the Bluetooth adapter's own state.
 *
 * [bondedDeviceCount] is a count and never a device list: naming bonded devices needs `BLUETOOTH_CONNECT`
 * and says who the user is, which the Monitor has no reason to show to diagnose a radio.
 */
data class BluetoothEntry(
    val isEnabled: Boolean,
    val bondedDeviceCount: Int?,
)
