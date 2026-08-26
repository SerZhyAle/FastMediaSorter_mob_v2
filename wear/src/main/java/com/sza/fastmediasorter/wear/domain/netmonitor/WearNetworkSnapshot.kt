package com.sza.fastmediasorter.wear.domain.netmonitor

data class WearNetworkSnapshot(
    val recordedAtMillis: Long,
    val activeTransport: WearNetworkTransport?,
    val wifiNetworkName: String?,
    val wifiSignalDbm: Int?,
    val wifiLinkSpeedMbps: Int?,
    val visibleWifiNetworks: List<String>?,
    val hasMobileData: Boolean?,
    val mobileOperator: String?,
    val isBluetoothEnabled: Boolean?,
    val hasLocationProvider: Boolean?,
    val hasInternet: Boolean?,
)

enum class WearNetworkTransport {
    Wifi,
    Cellular,
    Ethernet,
    Bluetooth,
    Vpn,
    Other,
}
