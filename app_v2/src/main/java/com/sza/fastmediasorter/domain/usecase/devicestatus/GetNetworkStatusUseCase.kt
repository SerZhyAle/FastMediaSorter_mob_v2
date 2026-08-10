package com.sza.fastmediasorter.domain.usecase.devicestatus

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkStatus
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1178: the network gadget's metric - transport, network name and whether the internet is reachable.
 *
 * This metric asks for no permission and requests none. The name is a label, and ADR-4 forbids buying a
 * permission for a label, so every refusal of the platform to name the network degrades to the transport
 * alone instead of raising a prompt.
 */
class GetNetworkStatusUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkStateMonitor: NetworkStateMonitor
) : DeviceStatusProvider<NetworkStatus> {

    override val refreshIntervalMs: Long = REFRESH_INTERVAL_MS

    override suspend fun read(): NetworkStatus = withContext(Dispatchers.IO) {
        val capabilities = activeCapabilities()
        val transport = classify(capabilities)
        NetworkStatus(
            transport = transport,
            networkName = readNetworkName(transport, capabilities),
            isOnline = networkStateMonitor.isInternetAvailable()
        )
    }

    private fun activeCapabilities(): NetworkCapabilities? = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
    }.getOrNull()

    private fun readNetworkName(
        transport: NetworkTransport,
        capabilities: NetworkCapabilities?
    ): MetricValue<String> = when (transport) {
        NetworkTransport.WIFI -> sanitize(wifiInfo(capabilities)?.ssid?.trim(SSID_QUOTE))
        NetworkTransport.CELLULAR -> sanitize(carrierName())
        NetworkTransport.ETHERNET, NetworkTransport.NONE -> MetricValue.Unknown
    }

    /**
     * From API 29 the live [WifiInfo] rides on the network capabilities; below it only the manager knows.
     * Both paths answer a placeholder rather than throwing when system location is off, which [sanitize]
     * turns into [MetricValue.Unknown] - the same outcome as a device with no Wi-Fi service at all.
     */
    private fun wifiInfo(capabilities: NetworkCapabilities?): WifiInfo? {
        val fromCapabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities?.transportInfo as? WifiInfo
        } else {
            null
        }
        return fromCapabilities ?: legacyWifiInfo()
    }

    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? = runCatching {
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
    }.getOrNull()

    private fun carrierName(): String? = runCatching {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkOperatorName
    }.getOrNull()

    private fun sanitize(name: String?): MetricValue<String> {
        val cleaned = name?.trim().orEmpty()
        val usable = cleaned.isNotEmpty() && !cleaned.equals(UNKNOWN_SSID, ignoreCase = true)
        return if (usable) MetricValue.Known(cleaned) else MetricValue.Unknown
    }

    companion object {
        /**
         * The one transport vocabulary in the tree, in the order the taskbar tray has always used, so the
         * tray and the gadget cannot name the same link differently.
         */
        fun classify(capabilities: NetworkCapabilities?): NetworkTransport = when {
            capabilities == null -> NetworkTransport.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.NONE
        }

        /** What the platform answers instead of an SSID when it will not name the network. */
        private const val UNKNOWN_SSID = "<unknown ssid>"

        /** The platform quotes an SSID; the quotes are transport formatting, not part of the name. */
        private const val SSID_QUOTE = '"'

        /** A link change arrives within seconds and costs two binder calls; nothing here is frame-paced. */
        private const val REFRESH_INTERVAL_MS = 5_000L
    }
}
