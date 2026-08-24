package com.sza.fastmediasorter.wear.data.netmonitor

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkCapabilities
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Samples the watch's radios on demand.
 *
 * Holds no sampled state in a field: a snapshot exists only inside the running flow, so leaving the
 * screen leaves nothing behind. A permission the user declined yields a null field - never a crash
 * and never a zero, which would read as a measured value.
 */
class WearNetworkMonitorRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearNetworkMonitorRepository {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

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
        return !bluetoothPending && !nearbyPending
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
        }
    }

    private fun sample(): WearNetworkSnapshot {
        val active = connectivityManager?.activeNetwork
        val networkCapabilities = active?.let { connectivityManager.getNetworkCapabilities(it) }
        val wifiInfo = wifiInfo(networkCapabilities)
        return WearNetworkSnapshot(
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
            )
        )
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
        // S2013: below API 33 a scan list is only readable with ACCESS_FINE_LOCATION, which this
        // app no longer declares, so the list is simply absent there rather than empty.
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
        /** Slow enough that a watch screen costs little, fast enough that a signal bar still moves. */
        const val POLL_INTERVAL_MS = 5_000L
    }
}
