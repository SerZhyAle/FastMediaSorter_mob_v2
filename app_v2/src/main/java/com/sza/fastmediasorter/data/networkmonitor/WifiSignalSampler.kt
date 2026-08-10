package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: one reading of the connected Wi-Fi link. */
data class WifiSignalReading(
    val takenAtMillis: Long,
    val rssiDbm: Int,
    val linkSpeedMbps: Int?,
)

/**
 * S1433: samples the signal of the Wi-Fi network this device is connected to, for one chart.
 *
 * Connected network only, never a scan. Research artifact 02 rules out presenting scan results as a passive
 * signal monitor, so no scan is started here and no scan-results receiver is registered - which is also what
 * keeps the sampler clear of the scan-throttling rules and of the location prompt a scan drags in on every
 * level since API 28.
 *
 * Reads `WifiInfo` itself rather than borrowing the composed snapshot of
 * [ConnectivitySnapshotDataSource]: a chart needs two numbers, and the snapshot re-enumerates every visible
 * network and its link properties on each tick - a cost this sampler would otherwise pay once a second for
 * data it discards.
 *
 * Cold. The tick starts when a section collects and is cancelled when it stops, so nothing samples Wi-Fi
 * once the Monitor is closed.
 */
@Singleton
class WifiSignalSampler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Emits one reading per tick for as long as it is collected.
     *
     * The cadence matches `ConnectivitySnapshotDataSource.RESAMPLE_INTERVAL_MS`, which the composed snapshot
     * is already capped to: two sources of the same screen sampling at different rates would draw two
     * timelines of one moment.
     */
    fun observe(): Flow<MonitorSection<WifiSignalReading>> = callbackFlow {
        val ticker = launch {
            while (isActive) {
                trySend(sample())
                delay(SAMPLE_INTERVAL_MS)
            }
        }

        awaitClose { ticker.cancel() }
    }

    /**
     * The current reading, or why there is none.
     *
     * A redacted [WifiInfo] while connected to Wi-Fi is a missing location grant rather than a missing
     * network, and the two are told apart here because only one of them the user can fix.
     */
    private fun sample(): MonitorSection<WifiSignalReading> {
        val active = connectivityManager?.activeNetwork?.takeIf(::isWifi)
            ?: return MonitorSection.absent(SectionAvailability.NoNetwork)
        val info = wifiInfo(active)
        return if (info == null) {
            MonitorSection.absent(
                SectionAvailability.NoPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        } else {
            MonitorSection.available(reading(info))
        }
    }

    private fun reading(info: WifiInfo) = WifiSignalReading(
        takenAtMillis = System.currentTimeMillis(),
        rssiDbm = info.rssi,
        linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
    )

    private fun isWifi(network: Network): Boolean =
        connectivityManager
            ?.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    private fun wifiInfo(active: Network): WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager?.getNetworkCapabilities(active)?.transportInfo as? WifiInfo
        } else {
            legacyWifiInfo()
        }

    // WifiManager.connectionInfo is deprecated from API 31; below API 29 it is the only route to WifiInfo
    // at all, and this branch never runs above that level.
    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? =
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo

    private companion object {

        /** One reading per second, matching the cap the composed Monitor snapshot already applies. */
        const val SAMPLE_INTERVAL_MS = 1000L
    }
}
