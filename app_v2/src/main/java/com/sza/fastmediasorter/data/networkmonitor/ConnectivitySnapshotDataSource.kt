package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper
import com.sza.fastmediasorter.domain.model.networkmonitor.ActiveLink
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.VisibleNetwork
import com.sza.fastmediasorter.domain.model.networkmonitor.WifiEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: the connectivity half of one Monitor sample. */
data class ConnectivitySample(
    val networks: List<VisibleNetwork>,
    val activeLink: ActiveLink?,
    val wifi: MonitorSection<WifiEntry>,
)

/**
 * S1433: reads what Android will say about the networks this device can see.
 *
 * Borrows the process-wide observer in [NetworkStateMonitor] rather than registering a second
 * `ConnectivityManager.NetworkCallback`: research artifact 01 records a per-UID callback limit, and two
 * observers of one thing also produce two disagreeing views of it.
 *
 * Borrowing it costs two obligations, both discovered by reading that class rather than from the plan:
 *
 *  - It is started lazily. `NetworkStateMonitor.start()` runs inside [NetworkLifecycleBootstrapper], which
 *    S0195 defers to the first remote-flow entry boundary, so a Monitor opened on a fresh process would
 *    otherwise listen to a monitor that never started. Collection calls `ensureInitialized()`, which is a
 *    no-op after the first time.
 *  - It debounces on purpose. Only a move to a *different* network notifies; capability and link ticks on
 *    the same network are dropped, which is right for consumers asking "did my connection die" and wrong
 *    here, because RSSI, link speed and DNS all change while the network id does not. Hence the re-sample
 *    tick below, which lives and dies with the collection and so is not background work.
 */
@Singleton
class ConnectivitySnapshotDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkStateMonitor: NetworkStateMonitor,
    private val networkContextAnalyzer: NetworkContextAnalyzer,
    private val bootstrapper: dagger.Lazy<NetworkLifecycleBootstrapper>,
) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Emits a fresh sample on collection, on every network transition, and on a bounded tick.
     *
     * The callback is unregistered and the tick cancelled when collection ends, so nothing observes the
     * device once the section closes.
     */
    fun observe(): Flow<ConnectivitySample> = callbackFlow {
        bootstrapper.get().ensureInitialized()

        val callback = object : NetworkStateMonitor.NetworkChangeCallback {
            override fun onNetworkChanged() {
                trySend(sample())
            }

            override fun onNetworkLost() {
                trySend(sample())
            }
        }
        networkStateMonitor.registerCallback(callback)
        trySend(sample())

        val ticker = launch {
            while (isActive) {
                delay(RESAMPLE_INTERVAL_MS)
                trySend(sample())
            }
        }

        awaitClose {
            ticker.cancel()
            networkStateMonitor.unregisterCallback(callback)
        }
    }

    private fun sample(): ConnectivitySample {
        val active = connectivityManager?.activeNetwork
        return ConnectivitySample(
            networks = visibleNetworks(active),
            activeLink = active?.let(::activeLink),
            wifi = wifiSection(active),
        )
    }

    // getAllNetworks is deprecated from API 31, and its sanctioned replacement is a registered
    // NetworkCallback - the very thing this class must not add. It still returns the full set on every
    // supported level, so the deprecated read is the honest choice here.
    @Suppress("DEPRECATION")
    private fun visibleNetworks(active: Network?): List<VisibleNetwork> {
        val manager = connectivityManager ?: return emptyList()
        return manager.allNetworks.mapNotNull { network ->
            manager.getNetworkCapabilities(network)?.let { caps -> visibleNetwork(caps, network == active) }
        }
    }

    private fun visibleNetwork(caps: NetworkCapabilities, isActive: Boolean) = VisibleNetwork(
        transport = transportOf(caps),
        isActive = isActive,
        hasValidatedInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        downstreamKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
        upstreamKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
    )

    private fun transportOf(caps: NetworkCapabilities): NetworkTransport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkTransport.BLUETOOTH
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
        else -> NetworkTransport.OTHER
    }

    private fun activeLink(network: Network): ActiveLink? {
        val props = connectivityManager?.getLinkProperties(network) ?: return null
        val addresses = props.linkAddresses.map { it.address }
        val defaultRoute = props.routes.firstOrNull { it.isDefaultRoute }
        return ActiveLink(
            interfaceName = props.interfaceName,
            ipv4Addresses = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
            ipv6Addresses = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress },
            dnsServers = props.dnsServers.mapNotNull { it.hostAddress },
            gateway = defaultRoute?.gateway?.hostAddress,
            hasDefaultRoute = defaultRoute != null,
            proxy = props.httpProxy?.host,
        )
    }

    /**
     * Wi-Fi facts for the connected network only - never a scan.
     *
     * A null [WifiInfo] on a Wi-Fi network means the platform redacted it for want of location permission,
     * which is a different answer from "you are not on Wi-Fi" and is reported as such.
     */
    private fun wifiSection(active: Network?): MonitorSection<WifiEntry> {
        val onWifi = networkContextAnalyzer.hasWifi()
        val info = if (onWifi) wifiInfo(active) else null
        return when {
            !onWifi -> MonitorSection.absent(SectionAvailability.NoNetwork)
            info == null ->
                MonitorSection.absent(SectionAvailability.NoPermission(Manifest.permission.ACCESS_FINE_LOCATION))

            else -> MonitorSection.available(wifiEntry(info))
        }
    }

    private fun wifiEntry(info: WifiInfo) = WifiEntry(
        ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID },
        rssiDbm = info.rssi,
        frequencyMhz = info.frequency,
        linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
        standard = wifiStandardName(info),
    )

    private fun wifiInfo(active: Network?): WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            active?.let { connectivityManager?.getNetworkCapabilities(it)?.transportInfo as? WifiInfo }
        } else {
            legacyWifiInfo()
        }

    // WifiManager.connectionInfo is deprecated from API 31; below API 29 it is the only way to reach
    // WifiInfo at all, and this branch never runs above that.
    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? =
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo

    private fun wifiStandardName(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return when (info.wifiStandard) {
            ScanResultCompat.WIFI_STANDARD_LEGACY -> "802.11 a/b/g"
            ScanResultCompat.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
            ScanResultCompat.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
            ScanResultCompat.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
            ScanResultCompat.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
            else -> null
        }
    }

    /**
     * Wi-Fi standard constants, named locally.
     *
     * `ScanResult.WIFI_STANDARD_*` are plain compile-time ints inlined at build time, so naming them here
     * keeps the `when` above readable without a magic number and without importing a scan class into a
     * data source that must never scan.
     */
    private object ScanResultCompat {
        const val WIFI_STANDARD_LEGACY = 1
        const val WIFI_STANDARD_11N = 4
        const val WIFI_STANDARD_11AC = 5
        const val WIFI_STANDARD_11AX = 6
        const val WIFI_STANDARD_11BE = 8
    }

    private companion object {

        /**
         * How often a collected flow re-reads the device.
         *
         * One second matches the cap the repository applies to the composed snapshot, so the two cannot
         * fight: sampling faster would only produce emissions the repository throws away.
         */
        const val RESAMPLE_INTERVAL_MS = 1000L

        /** What the platform returns instead of an SSID when it will not disclose one. */
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
