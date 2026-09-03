package com.sza.fastmediasorter.wear.data.netmonitor

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.netmonitor.WearGnssDetails
import com.sza.fastmediasorter.wear.domain.netmonitor.WearIpDetails
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkCapabilities
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.netmonitor.WearSatelliteInfo
import com.sza.fastmediasorter.wear.domain.netmonitor.WearTrafficRate
import com.sza.fastmediasorter.wear.domain.netmonitor.WearWifiDetails
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketException
import javax.inject.Inject

/**
 * Samples the watch's radios and network state on demand.
 *
 * Holds no sampled state in a field: a snapshot exists only inside the running flow, so leaving the
 * screen leaves nothing behind. A permission the user declined yields a null field - never a crash
 * and never a zero, which would read as a measured value.
 */
class WearNetworkMonitorRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : WearNetworkMonitorRepository {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    @Volatile
    private var lastRxBytes = -1L

    @Volatile
    private var lastTxBytes = -1L

    @Volatile
    private var lastTrafficSampleTime = 0L

    @Volatile
    private var latestGnssStatus: GnssStatus? = null

    override fun capabilities(): WearNetworkCapabilities {
        val packageManager = context.packageManager
        return WearNetworkCapabilities(
            hasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
            hasMobile = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            hasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
            hasLocation = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        )
    }

    override fun permissionsGranted(): Boolean {
        val bluetoothPending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isGranted(Manifest.permission.BLUETOOTH_CONNECT)
        val nearbyPending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
        val locationPending = !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return !bluetoothPending && !nearbyPending && !locationPending
    }

    override suspend fun probeReachability(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, DNS_PROBE_PORT), PROBE_TIMEOUT_MS)
                true
            }
        } catch (e: IOException) {
            // An unreachable host IS the answer here, so it is reported as false and not as a failure.
            Timber.d(e, "Probe reachability to %s failed", host)
            false
        } catch (e: SecurityException) {
            Timber.d(e, "Probe reachability to %s refused by policy", host)
            false
        }
    }

    /**
     * Asks the echo services in order and keeps the first answer that looks like a bare address.
     *
     * The budget is deliberately a third of the phone's: a watch on a tethered link pays for a stalled
     * socket in battery, and an address the user waited twelve seconds for is not worth the wait.
     */
    override suspend fun resolveExternalIp(): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(EXTERNAL_IP_BUDGET_MS) {
            ECHO_SERVICES.firstNotNullOfOrNull { service -> queryEchoService(service) }
        }
    }

    /**
     * A service behind a captive portal answers 200 with an HTML page, so the body is taken only when
     * it actually reads as an address.
     */
    private fun queryEchoService(service: String): String? = try {
        httpClient.newCall(Request.Builder().url(service).get().build()).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.string()?.trim()?.takeIf { it.isPlausibleAddress() }
            } else {
                null
            }
        }
    } catch (e: IOException) {
        // A blocked or dead echo service is the expected case, not an error: the caller falls through
        // to the next one. Only the service name is recorded - the address must never reach a log.
        Timber.d("External IP: %s did not answer (%s)", service, e.javaClass.simpleName)
        null
    }

    override fun snapshots(): Flow<WearNetworkSnapshot> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(sample())
            }

            override fun onLost(network: Network) {
                trySend(sample())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(sample())
            }
        }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                latestGnssStatus = status
            }
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val hasLocationPermission = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasLocationPermission && locationManager != null) {
            try {
                locationManager.registerGnssStatusCallback(mainHandler::post, gnssCallback)
            } catch (e: SecurityException) {
                Timber.w(e, "Location permission revoked during GNSS status registration")
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(callback)
        trySend(sample())

        val poller = launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                trySend(sample())
            }
        }

        awaitClose {
            poller.cancel()
            connectivityManager?.unregisterNetworkCallback(callback)
            if (locationManager != null) {
                try {
                    locationManager.unregisterGnssStatusCallback(gnssCallback)
                } catch (e: IllegalArgumentException) {
                    // The platform rejects a callback it never held; tearing down twice is not an error.
                    Timber.d(e, "Unregistering GNSS callback ignored")
                } catch (e: SecurityException) {
                    Timber.d(e, "Unregistering GNSS callback refused by policy")
                }
            }
            latestGnssStatus = null
        }
    }

    private fun sample(): WearNetworkSnapshot {
        val recordedAtMillis = System.currentTimeMillis()
        val active = connectivityManager?.activeNetwork
        val networkCapabilities = active?.let { connectivityManager.getNetworkCapabilities(it) }
        val wifiInfo = wifiInfo(networkCapabilities)
        val linkAddresses = getLocalIpAddresses()
        val ipv4 = linkAddresses.firstOrNull { it is Inet4Address }?.hostAddress
        val ipv6 = linkAddresses.firstOrNull { it is Inet6Address }?.hostAddress

        val ipDetails = WearIpDetails(
            localIpv4 = ipv4,
            localIpv6 = ipv6,
            externalIp = null,
            isCgnat = null
        )

        val wifiDetails = wifiInfo?.let { info ->
            WearWifiDetails(
                ssid = readableSsid(info),
                bssid = info.bssid,
                signalDbm = info.rssi,
                linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
                frequencyMhz = info.frequency.takeIf { it > 0 },
                wifiStandard = wifiStandardOf(info),
                ipAddress = ipv4,
                visibleNetworks = visibleWifiNetworks() ?: emptyList()
            )
        }

        val trafficRate = sampleTraffic(recordedAtMillis)
        val gnssDetails = sampleGnss(recordedAtMillis)

        return WearNetworkSnapshot(
            recordedAtMillis = recordedAtMillis,
            activeTransport = networkCapabilities?.let { transportOf(it) },
            wifiNetworkName = wifiInfo?.let { readableSsid(it) },
            wifiSignalDbm = wifiInfo?.rssi,
            wifiLinkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 },
            visibleWifiNetworks = visibleWifiNetworks(),
            hasMobileData = hasMobileData(),
            mobileOperator = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() },
            isBluetoothEnabled = bluetoothEnabled(),
            hasLocationProvider = locationManager?.allProviders?.isNotEmpty(),
            hasInternet = networkCapabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ),
            localIp = ipv4 ?: ipv6,
            externalIp = null,
            wifiDetails = wifiDetails,
            gnssDetails = gnssDetails,
            trafficRate = trafficRate,
            ipDetails = ipDetails
        )
    }

    private fun sampleTraffic(now: Long): WearTrafficRate {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val validRx = if (rx != TrafficStats.UNSUPPORTED.toLong()) rx else 0L
        val validTx = if (tx != TrafficStats.UNSUPPORTED.toLong()) tx else 0L

        var rxSpeed = 0L
        var txSpeed = 0L
        if (lastTrafficSampleTime > 0 && now > lastTrafficSampleTime) {
            val deltaSeconds = (now - lastTrafficSampleTime) / MILLIS_PER_SECOND
            if (deltaSeconds > 0 && lastRxBytes >= 0 && lastTxBytes >= 0) {
                rxSpeed = ((validRx - lastRxBytes) / deltaSeconds).toLong().coerceAtLeast(0L)
                txSpeed = ((validTx - lastTxBytes) / deltaSeconds).toLong().coerceAtLeast(0L)
            }
        }

        lastRxBytes = validRx
        lastTxBytes = validTx
        lastTrafficSampleTime = now

        return WearTrafficRate(
            rxBytesPerSec = rxSpeed,
            txBytesPerSec = txSpeed,
            totalRxBytes = validRx,
            totalTxBytes = validTx,
            sampledAtMillis = now
        )
    }

    private fun sampleGnss(now: Long): WearGnssDetails? {
        val hasPermission = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasPermission || locationManager == null) return null

        var location: Location? = null
        try {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: SecurityException) {
            Timber.w(e, "Security exception reading last known location")
        }

        val status = latestGnssStatus
        val satellitesList = mutableListOf<WearSatelliteInfo>()
        var visibleCount = 0
        var usedCount = 0

        if (status != null) {
            visibleCount = status.satelliteCount
            for (i in 0 until visibleCount) {
                val used = status.usedInFix(i)
                if (used) usedCount++
                satellitesList.add(
                    WearSatelliteInfo(
                        svid = status.getSvid(i),
                        constellationName = constellationNameOf(status.getConstellationType(i)),
                        cn0DbHz = status.getCn0DbHz(i),
                        usedInFix = used,
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        elevationDegrees = status.getElevationDegrees(i)
                    )
                )
            }
        }

        return WearGnssDetails(
            latitude = location?.latitude,
            longitude = location?.longitude,
            altitudeMeters = location?.altitude?.takeIf { location.hasAltitude() },
            accuracyMeters = location?.accuracy?.takeIf { location.hasAccuracy() },
            speedMps = location?.speed?.takeIf { location.hasSpeed() },
            bearingDegrees = location?.bearing?.takeIf { location.hasBearing() },
            satellitesVisible = visibleCount,
            satellitesUsed = usedCount,
            satellites = satellitesList,
            fixTimestampMillis = location?.time ?: now
        )
    }

    private fun constellationNameOf(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Unknown"
    }

    private fun getLocalIpAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress) {
                        result.add(addr)
                    }
                }
            }
        } catch (e: SocketException) {
            // An interface torn down mid-walk answers this; the addresses gathered so far still hold.
            Timber.d(e, "Error resolving local IP addresses")
        }
        return result
    }

    private fun wifiStandardOf(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy 802.11"
                else -> null
            }
        }
        return null
    }

    private fun transportOf(capabilities: NetworkCapabilities): WearNetworkTransport = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> WearNetworkTransport.Vpn
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WearNetworkTransport.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            WearNetworkTransport.Cellular
        }
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> {
            WearNetworkTransport.Bluetooth
        }
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
            WearNetworkTransport.Ethernet
        }
        else -> WearNetworkTransport.Other
    }

    /**
     * From API 31 the link details travel with the network's capabilities; below it the only source
     * is the manager's own last connection, which the platform deprecated but still answers.
     */
    @Suppress("DEPRECATION")
    private fun wifiInfo(capabilities: NetworkCapabilities?): WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities?.transportInfo as? WifiInfo
        } else {
            wifiManager?.connectionInfo
        }

    /** The platform answers an unavailable name with a quoted placeholder, which is not a name. */
    @Suppress("DEPRECATION")
    private fun readableSsid(info: WifiInfo): String? {
        val ssid = info.ssid?.trim('"')
        return ssid?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID.trim('"') }
    }

    @Suppress("DEPRECATION")
    private fun visibleWifiNetworks(): List<String>? {
        val scannable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (!scannable) {
            return null
        }
        return wifiManager?.scanResults
            ?.mapNotNull { it.SSID?.trim('"')?.takeIf(String::isNotBlank) }
            ?.distinct()
    }

    private fun hasMobileData(): Boolean? =
        telephonyManager?.let { it.phoneType != TelephonyManager.PHONE_TYPE_NONE }

    /** From API 31 the adapter state is behind a runtime permission the user may have declined. */
    private fun bluetoothEnabled(): Boolean? {
        val connectDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isGranted(Manifest.permission.BLUETOOTH_CONNECT)
        return if (connectDenied) null else bluetoothManager?.adapter?.isEnabled
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val PROBE_TIMEOUT_MS = 2_000
        const val DNS_PROBE_PORT = 53
        const val EXTERNAL_IP_BUDGET_MS = 4_000L
        const val MILLIS_PER_SECOND = 1_000.0

        /** Same order as the phone's chain, truncated to what one watch-sized budget can reach. */
        val ECHO_SERVICES = listOf(
            "https://checkip.amazonaws.com",
            "https://api.ipify.org",
            "https://icanhazip.com"
        )

        /** 45 chars is the longest possible IPv6 literal in its IPv4-mapped form. */
        const val MAX_ADDRESS_LENGTH = 45

        val IPV4_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        val IPV6_PATTERN = Regex("""[0-9a-fA-F:]{2,45}""")

        fun String.isPlausibleAddress(): Boolean = length <= MAX_ADDRESS_LENGTH &&
            (matches(IPV4_PATTERN) || (contains(':') && matches(IPV6_PATTERN)))
    }
}
