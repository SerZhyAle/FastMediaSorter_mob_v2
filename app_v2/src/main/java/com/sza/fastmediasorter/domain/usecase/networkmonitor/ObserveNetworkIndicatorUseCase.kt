package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * S1440: the read seam for the six indicators that report by themselves.
 *
 * Every indicator routes through an existing S1433 flow rather than through a new measurement, because
 * strategic 2 non-goal 2 forbids background work for the sake of a widget and those flows are cold -
 * they observe only while collected and stop when the surface goes away.
 *
 * The two tap-driven indicators, EXTERNAL_ADDRESS and RESOURCE_REACHABILITY, emit [Awaiting] here and
 * nothing else; asking for them costs a network round trip and belongs to the on-demand use case.
 *
 * Collaborators are injected lazily so a widget showing one indicator does not build the read paths of
 * the other seven (CLAUDE.md Rule 18).
 */
class ObserveNetworkIndicatorUseCase @Inject constructor(
    private val monitorRepository: Lazy<NetworkMonitorRepository>,
    private val wifiSignal: Lazy<ObserveWifiSignalUseCase>,
    private val cellularSignal: Lazy<ObserveCellularSignalUseCase>,
    private val trafficRate: Lazy<ObserveTrafficRateUseCase>,
    private val gnssStatus: Lazy<ObserveGnssStatusUseCase>,
    private val measurementHistory: Lazy<NetworkMeasurementHistoryRepository>
) {

    operator fun invoke(
        indicator: NetworkMonitorIndicator,
        resourceId: Long?
    ): Flow<NetworkIndicatorReading> = when (indicator) {
        NetworkMonitorIndicator.LOCAL_ADDRESS -> observeSnapshot(::localAddress)
        NetworkMonitorIndicator.RADIO_STATUS -> observeSnapshot(::radioStatus)
        NetworkMonitorIndicator.SIGNAL_LEVEL -> observeSignalLevel()
        NetworkMonitorIndicator.LIVE_THROUGHPUT -> observeThroughput()
        NetworkMonitorIndicator.SATELLITE_COUNT -> observeSatellites()
        NetworkMonitorIndicator.LAST_MEASUREMENT -> observeLastMeasurement()
        // Both cost a round trip; ResolveNetworkIndicatorOnDemandUseCase answers them on a tap.
        NetworkMonitorIndicator.EXTERNAL_ADDRESS,
        NetworkMonitorIndicator.RESOURCE_REACHABILITY -> flowOf(NetworkIndicatorReading.Awaiting)
    }

    private fun observeSnapshot(
        read: (NetworkMonitorSnapshot) -> NetworkIndicatorReading
    ): Flow<NetworkIndicatorReading> = monitorRepository.get().observeSnapshot().map(read)

    private fun localAddress(snapshot: NetworkMonitorSnapshot): NetworkIndicatorReading {
        val address = snapshot.activeLink?.ipv4Addresses?.firstOrNull()
        return if (address == null) {
            NetworkIndicatorReading.Failed(NO_ACTIVE_LINK)
        } else {
            NetworkIndicatorReading.Value(
                primary = address,
                caption = snapshot.wifi.data?.ssid,
                levelBars = null
            )
        }
    }

    /**
     * Presence of a section's data is the honest "on": a radio the platform refuses to describe is
     * reported as absent by [MonitorSection.data] being null, and its availability says why.
     */
    private fun radioStatus(snapshot: NetworkMonitorSnapshot): NetworkIndicatorReading {
        val live = buildList {
            if (snapshot.wifi.data != null) add(WIFI_LABEL)
            if (snapshot.bluetooth.data != null) add(BLUETOOTH_LABEL)
            if (!snapshot.sims.data.isNullOrEmpty()) add(SIM_LABEL)
        }
        return NetworkIndicatorReading.Value(
            primary = if (live.isEmpty()) NONE_LIVE else live.joinToString(separator = RADIO_SEPARATOR),
            caption = snapshot.activeLink?.interfaceName,
            levelBars = null
        )
    }

    /**
     * Wi-Fi when it reports, cellular otherwise - the widget shows one number, and a connected Wi-Fi
     * link is the one the user is actually using.
     */
    private fun observeSignalLevel(): Flow<NetworkIndicatorReading> =
        combine(wifiSignal.get().invoke(), cellularSignal.get().invoke()) { wifi, cellular ->
            val wifiDbm = wifi.data?.value
            val cellularDbm = cellular.data?.firstOrNull()?.sample?.value
            val dbm = wifiDbm ?: cellularDbm
            when {
                dbm != null -> NetworkIndicatorReading.Value(
                    primary = "${dbm.roundToInt()} $DBM_UNIT",
                    caption = if (wifiDbm != null) WIFI_LABEL else SIM_LABEL,
                    levelBars = signalBars(dbm)
                )

                else -> unavailable(wifi)
            }
        }

    private fun observeThroughput(): Flow<NetworkIndicatorReading> =
        trafficRate.get().invoke().map { section ->
            val rate = section.data
            if (rate == null) {
                unavailable(section)
            } else {
                NetworkIndicatorReading.Value(
                    primary = megabitsPerSecond(rate.totalBytesPerSecond),
                    caption = "${megabitsPerSecond(rate.receivedBytesPerSecond)} $DOWN_LABEL",
                    levelBars = null
                )
            }
        }

    private fun observeSatellites(): Flow<NetworkIndicatorReading> =
        gnssStatus.get().invoke().map { section ->
            val snapshot = section.data
            if (snapshot == null) {
                unavailable(section)
            } else {
                NetworkIndicatorReading.Value(
                    primary = snapshot.satellites.size.toString(),
                    caption = null,
                    levelBars = null
                )
            }
        }

    private fun observeLastMeasurement(): Flow<NetworkIndicatorReading> =
        measurementHistory.get().observeHistory().map { history ->
            val latest = history.maxByOrNull { it.takenAtMillis }
            val down = latest?.downMbps
            when {
                latest == null -> NetworkIndicatorReading.Awaiting
                // A stored measurement need not be a speed test - a latency-only or failed run has no
                // rate to show, and its own result text says more than a blank cell would.
                down == null -> NetworkIndicatorReading.Failed(latest.resultText)
                else -> NetworkIndicatorReading.Value(
                    primary = formatMbps(down),
                    caption = latest.upMbps?.let(::formatMbps),
                    levelBars = null
                )
            }
        }

    /** A section without data is never swallowed - a denied permission stays distinct from absent hardware. */
    private fun unavailable(section: MonitorSection<*>): NetworkIndicatorReading =
        NetworkIndicatorReading.Unavailable(section.availability)

    private fun megabitsPerSecond(bytesPerSecond: Double): String =
        formatMbps(bytesPerSecond * BITS_PER_BYTE / BITS_PER_MEGABIT)

    /** The stored history already holds Mbit/s, so only the live traffic rate needs converting. */
    private fun formatMbps(megabitsPerSecond: Double): String =
        "${(megabitsPerSecond * ONE_DECIMAL).roundToInt() / ONE_DECIMAL} $MBIT_UNIT"

    /**
     * Maps dBm onto the five bars the launcher tray already draws.
     *
     * Both samplers report dBm - `WifiSignalSampler` reads `WifiInfo.rssi` and `CellularSignalSampler`
     * documents the 3GPP ASU mapping - and the boundaries below are the platform's own five-level RSSI
     * thresholds rather than a scale invented here.
     */
    private fun signalBars(dbm: Double): Int =
        BAR_THRESHOLDS_DBM.count { threshold -> dbm >= threshold }

    private companion object {
        const val NO_ACTIVE_LINK = "no-active-link"
        const val WIFI_LABEL = "Wi-Fi"
        const val BLUETOOTH_LABEL = "BT"
        const val SIM_LABEL = "SIM"
        const val NONE_LIVE = "-"
        const val RADIO_SEPARATOR = " . "
        const val DBM_UNIT = "dBm"
        const val MBIT_UNIT = "Mbit/s"
        const val DOWN_LABEL = "down"

        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val ONE_DECIMAL = 10.0

        /**
         * Lower bound of each bar, weakest first: a reading at or above N of them shows N bars, so the
         * list length is the bar count and no per-bar literal is repeated.
         */
        val BAR_THRESHOLDS_DBM = listOf(-88.0, -77.0, -66.0, -55.0)
    }
}
