package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: how the device is attached to the outside world, as a domain fact.
 *
 * The enum carries no icon and no label: the taskbar tray, the gadget and any later surface each keep
 * their own mapping, so a display decision taken in one of them cannot leak into the others.
 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    NONE
}

/**
 * S1178: the network gadget's metric - transport, network name and whether the internet is reachable.
 *
 * @property networkName the Wi-Fi network or the carrier, and [MetricValue.Unknown] whenever it cannot be
 * read without asking the user for a permission. This set buys no permission for a label (ADR-4), so an
 * unknown name is the designed outcome rather than a failure - the transport alone still answers.
 */
data class NetworkStatus(
    val transport: NetworkTransport,
    val networkName: MetricValue<String>,
    val isOnline: Boolean
)
