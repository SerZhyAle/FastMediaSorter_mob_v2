package com.sza.fastmediasorter.wear.domain.netmonitor

data class WearSatelliteInfo(
    val svid: Int,
    val constellationName: String,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
    val azimuthDegrees: Float? = null,
    val elevationDegrees: Float? = null,
)

data class WearGnssDetails(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val satellites: List<WearSatelliteInfo> = emptyList(),
    val fixTimestampMillis: Long? = null,
)

data class WearTrafficRate(
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val sampledAtMillis: Long = 0L,
)

data class WearWifiDetails(
    val ssid: String? = null,
    val bssid: String? = null,
    val signalDbm: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val wifiStandard: String? = null,
    val ipAddress: String? = null,
    val visibleNetworks: List<String> = emptyList(),
)

data class WearIpDetails(
    val localIpv4: String? = null,
    val localIpv6: String? = null,
    val externalIp: String? = null,
    val isCgnat: Boolean? = null,
)

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
    val localIp: String? = null,
    val externalIp: String? = null,
    val wifiDetails: WearWifiDetails? = null,
    val gnssDetails: WearGnssDetails? = null,
    val trafficRate: WearTrafficRate? = null,
    val ipDetails: WearIpDetails? = null,
)

enum class WearNetworkTransport {
    Wifi,
    Cellular,
    Ethernet,
    Bluetooth,
    Vpn,
    Other,
}
