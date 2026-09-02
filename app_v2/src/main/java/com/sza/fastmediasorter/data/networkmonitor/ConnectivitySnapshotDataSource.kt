package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.core.networkmonitor.WifiGenerationMapper
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

    private fun wifiEntry(info: WifiInfo): WifiEntry {
        val name = networkName(info)
        return WifiEntry(
            ssid = name,
            ssidAvailability = if (name != null) SectionAvailability.Available else withheldSsidReason(),
            rssiDbm = info.rssi,
            frequencyMhz = info.frequency,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            standard = wifiStandardName(info),
        )
    }

    /**
     * The connected network's name, or null when the platform will not disclose it.
     *
     * Measured on emulator-5554 (Android 15, S1853): the `WifiInfo` reached through
     * `NetworkCapabilities.getTransportInfo()` answers `<unknown ssid>` **even with `ACCESS_FINE_LOCATION`
     * granted and location services on** - the capabilities returned by a direct query are redacted by the
     * platform, and only a registered `NetworkCallback` receives them unredacted. `WifiManager` answered
     * with the real name in the same second. So the name is read from there and every other Wi-Fi fact
     * still comes from the capabilities object, which is not redacted for them.
     */
    private fun networkName(info: WifiInfo): String? =
        usableSsid(info.ssid) ?: usableSsid(legacyWifiInfo()?.ssid)

    private fun usableSsid(raw: String?): String? =
        raw?.trim('"')?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }

    /**
     * Why the platform replaced the network name with its marker.
     *
     * The grant and the system location switch are separate gates and only one of them a permission dialog
     * can open, so the two are reported apart (S1853). Neither explaining it leaves the name simply absent -
     * which happens while a connection is still settling.
     */
    private fun withheldSsidReason(): SectionAvailability = when {
        !hasFineLocation() -> SectionAvailability.NoPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        !isLocationServiceOn() -> SectionAvailability.NoLocationService
        else -> SectionAvailability.NoNetwork
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationServiceOn(): Boolean =
        (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            ?.let(LocationManagerCompat::isLocationEnabled) == true

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
        return WifiGenerationMapper.displayName(info.wifiStandard)
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
