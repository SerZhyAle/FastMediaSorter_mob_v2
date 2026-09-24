package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.networkmonitor.WifiGenerationMapper
import com.sza.fastmediasorter.data.networkmonitor.TrafficRateReading
import com.sza.fastmediasorter.data.networkmonitor.TrafficRateSampler
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.network.HotspotState
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetNetworkStatusUseCase
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusChip
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayBadgeMapper
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayBluetoothConnectionMonitor
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayBluetoothMonitor
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySimDescription
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySimSignalMonitor
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySimState
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySpeedFormatter
import com.sza.fastmediasorter.ui.launcher.tray.SpeedUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3475: every value the launcher tray draws, gathered for one dim-row snapshot. A source the tray
 * would not subscribe to (its switch is off) is carried as its absent value, never as "off".
 */
data class DimConnectivityInputs(
    val composition: LauncherTrayComposition,
    val bluetoothOn: Boolean? = null,
    val bluetoothConnected: Int? = null,
    val sims: Map<Int, LauncherTraySimState> = emptyMap(),
    val speed: TrafficRateReading? = null,
    val hotspotEnabled: Boolean = false,
)

/** S3475: the default network as the tray reads it - the transport and, on Wi-Fi, its generation. */
data class DimNetworkReading(
    val transport: NetworkTransport,
    val wifiGeneration: Int? = null,
)

/**
 * S3366 / S3475: the dim screen's status chips, built from the very monitors, composition switches and
 * wording the launcher tray uses, so the dim row and the tray cannot disagree. The owner's rule is that
 * the dim row copies the tray exactly: the same indicators, in the tray's order (Bluetooth, SIM 1,
 * SIM 2, speed down, speed up, tethering, network), with the same level glyph, badges and roaming mark.
 *
 * No permission is ever requested from here: the tray asks for it on its own first show, and a missing
 * grant leaves the indicator absent in both places. The monitors are built here rather than injected
 * because the tray builds its own the same way - each is a stateless-per-subscription reader.
 */
@Singleton
class ConnectivityDimStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val hotspotStateSource: HotspotStateSource,
    private val settingsRepository: SettingsRepository,
) {

    private val bluetoothMonitor = LauncherTrayBluetoothMonitor(context)
    private val bluetoothConnectionMonitor = LauncherTrayBluetoothConnectionMonitor(context)
    private val simSignalMonitor = LauncherTraySimSignalMonitor(context)
    private val trafficRateSampler = TrafficRateSampler()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Subscribes to exactly the sources the tray's composition keeps on, and re-subscribes when a
     * switch changes - a hidden indicator must not keep its receiver alive here either.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun inputs(): Flow<DimConnectivityInputs> = settingsRepository.getSettings()
        .map { LauncherTrayComposition.from(it) }
        .distinctUntilChanged()
        .flatMapLatest { composition ->
            combine(
                if (composition.bluetooth) bluetoothMonitor.state() else flowOf(null),
                if (composition.bluetooth) bluetoothConnectionMonitor.connectedCount() else flowOf(null),
                if (composition.sim1 || composition.sim2) simSignalMonitor.states() else flowOf(NO_SIMS),
                if (composition.speed) speed() else flowOf(null),
                if (composition.tethering) hotspotEnabled() else flowOf(false),
            ) { bluetoothOn, connected, sims, speed, hotspot ->
                DimConnectivityInputs(composition, bluetoothOn, connected, sims, speed, hotspot)
            }
        }

    private fun speed(): Flow<TrafficRateReading?> = trafficRateSampler.observe().map { section ->
        section.data.takeIf { section.availability == SectionAvailability.Available }
    }

    private fun hotspotEnabled(): Flow<Boolean> =
        hotspotStateSource.state().map { it == HotspotState.ENABLED }.distinctUntilChanged()

    /** The default network read the tray makes on its callback, taken here on the snapshot's own tick. */
    fun readNetwork(): DimNetworkReading {
        val manager = connectivityManager ?: return DimNetworkReading(NetworkTransport.NONE)
        val capabilities = runCatching { manager.activeNetwork?.let(manager::getNetworkCapabilities) }
            .getOrNull()
        val transport = GetNetworkStatusUseCase.classify(capabilities)
        return DimNetworkReading(transport, wifiGenerationOf(transport, capabilities))
    }

    private fun wifiGenerationOf(transport: NetworkTransport, capabilities: NetworkCapabilities?): Int? {
        if (transport != NetworkTransport.WIFI || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val wifiInfo = (capabilities?.transportInfo as? WifiInfo) ?: legacyConnectionInfo()
        return wifiInfo?.wifiStandard?.let { WifiGenerationMapper.generationOf(it) }
    }

    // API 31+ Wi-Fi capabilities carry their own WifiInfo, so only API 30 needs the manager's deprecated copy.
    @Suppress("DEPRECATION")
    private fun legacyConnectionInfo(): WifiInfo? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        } else {
            null
        }

    /** The chip row for one snapshot, in the tray's order and under the tray's switches. */
    fun chips(inputs: DimConnectivityInputs, network: DimNetworkReading): List<DimStatusChip> = buildList {
        val composition = inputs.composition
        if (composition.bluetooth) bluetoothChip(inputs)?.let(::add)
        if (composition.sim1) simChip(inputs.sims, SIM1_SLOT, ID_SIM1)?.let(::add)
        if (composition.sim2) simChip(inputs.sims, SIM2_SLOT, ID_SIM2)?.let(::add)
        if (composition.speed) addAll(speedChips(inputs.speed))
        if (composition.tethering && inputs.hotspotEnabled) {
            add(
                DimStatusChip(
                    id = ID_TETHERING,
                    iconResId = R.drawable.ic_wifi_tethering,
                    contentDescription = describe(context.getString(R.string.launcher_tray_tethering_on)),
                ),
            )
        }
        if (composition.network) add(networkChip(network))
    }

    /** Present only while the adapter is known to be on - the tray's unknown-is-absent contract. */
    private fun bluetoothChip(inputs: DimConnectivityInputs): DimStatusChip? {
        if (inputs.bluetoothOn != true) return null
        val connected = inputs.bluetoothConnected?.takeIf { it > 0 }
        val state = if (connected != null) {
            context.getString(R.string.launcher_tray_bluetooth_connected, connected)
        } else {
            context.getString(R.string.launcher_tray_bluetooth_on)
        }
        return DimStatusChip(
            id = ID_BLUETOOTH,
            iconResId = R.drawable.ic_bluetooth,
            badgeText = LauncherTrayBadgeMapper.bluetoothBadge(connected),
            highlighted = connected != null,
            contentDescription = describe(state),
        )
    }

    /** A slot the platform does not report is absent: level 0 there would read as "no coverage". */
    private fun simChip(sims: Map<Int, LauncherTraySimState>, slotIndex: Int, id: String): DimStatusChip? {
        val state = sims[slotIndex] ?: return null
        val dataBadge = LauncherTrayBadgeMapper.dataTypeBadge(state.dataNetworkType, state.nrAdvanced)
        return DimStatusChip(
            id = id,
            iconResId = R.drawable.launcher_tray_signal_level,
            iconLevel = state.signalLevel,
            badgeText = dataBadge,
            cornerMarkResId = if (state.roaming) R.drawable.launcher_tray_corner_marker else 0,
            contentDescription = describe(
                LauncherTraySimDescription.describe(context, slotIndex + 1, state, dataBadge),
            ),
        )
    }

    private fun speedChips(reading: TrafficRateReading?): List<DimStatusChip> {
        if (reading == null) return emptyList()
        return listOf(
            speedChip(ID_SPEED_RX, "↓ ", reading.rxBytesPerSecond, R.string.launcher_tray_speed_download),
            speedChip(ID_SPEED_TX, "↑ ", reading.txBytesPerSecond, R.string.launcher_tray_speed_upload),
        )
    }

    private fun speedChip(id: String, prefix: String, bytesPerSecond: Double, descriptionRes: Int): DimStatusChip {
        val readout = LauncherTraySpeedFormatter.format(bytesPerSecond)
        val unitText = when (readout.unit) {
            SpeedUnit.BYTES -> context.getString(R.string.launcher_tray_speed_unit_bytes, readout.value)
            SpeedUnit.KILOBYTES -> context.getString(R.string.launcher_tray_speed_unit_kilobytes, readout.value)
            SpeedUnit.MEGABYTES -> context.getString(R.string.launcher_tray_speed_unit_megabytes, readout.value)
            SpeedUnit.GIGABYTES -> context.getString(R.string.launcher_tray_speed_unit_gigabytes, readout.value)
        }
        return DimStatusChip(
            id = id,
            text = "$prefix$unitText",
            contentDescription = describe(context.getString(descriptionRes, unitText)),
        )
    }

    /** Drawn for every transport, "none" included, exactly as the tray keeps its network slot. */
    private fun networkChip(network: DimNetworkReading): DimStatusChip {
        val (iconRes, labelRes) = when (network.transport) {
            NetworkTransport.WIFI -> R.drawable.ic_wifi to R.string.launcher_tray_network_wifi
            NetworkTransport.CELLULAR -> R.drawable.ic_signal_cellular to R.string.launcher_tray_network_cellular
            NetworkTransport.ETHERNET -> R.drawable.ic_ethernet to R.string.launcher_tray_network_ethernet
            NetworkTransport.NONE -> R.drawable.ic_network_off to R.string.launcher_tray_network_none
        }
        val badge = network.wifiGeneration?.toString()
        val label = context.getString(labelRes)
        return DimStatusChip(
            id = if (network.transport == NetworkTransport.WIFI) ID_NETWORK_WIFI else ID_NETWORK_OTHER,
            iconResId = iconRes,
            badgeText = badge,
            contentDescription = describe(if (badge != null) "$label Wi-Fi $badge" else label),
        )
    }

    /** The tray's wording: the state first, then what a tap on it does. */
    private fun describe(state: CharSequence): String =
        context.getString(R.string.launcher_tray_indicator_action, state)

    companion object {
        /** Shared with the action router: the id IS the tap target's address. */
        const val ID_BLUETOOTH = "dim-status:bluetooth"
        const val ID_SIM1 = "dim-status:sim:1"
        const val ID_SIM2 = "dim-status:sim:2"
        const val ID_SPEED_RX = "dim-status:speed:rx"
        const val ID_SPEED_TX = "dim-status:speed:tx"
        const val ID_NETWORK_WIFI = "dim-status:network:wifi"
        const val ID_NETWORK_OTHER = "dim-status:network:other"
        const val ID_TETHERING = "dim-status:tethering"

        private const val SIM1_SLOT = 0
        private const val SIM2_SLOT = 1
        private val NO_SIMS: Map<Int, LauncherTraySimState> = emptyMap()
    }
}
