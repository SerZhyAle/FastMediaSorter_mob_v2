package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.network.HotspotState
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusChip
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayBluetoothMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the dim screen's radio-state chips - Bluetooth, network transport, tethering - fed by the
 * same monitors the launcher tray uses, so the dim row and the tray cannot disagree (strategic §7
 * risk 4). Deliberately contains no polling: Bluetooth and tethering are push flows, and the network
 * transport read rides the provider's existing battery tick, so dimming gains no new periodic task
 * (strategic §3.2).
 *
 * [LauncherTrayBluetoothMonitor] is built here rather than injected because the tray builds its own
 * instance the same way - the monitor is a stateless-per-subscription reader, safe to have two of.
 */
@Singleton
class ConnectivityDimStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val hotspotStateSource: HotspotStateSource,
) {

    private val bluetoothMonitor = LauncherTrayBluetoothMonitor(context)

    /** Emits the current Bluetooth state at subscription and on every change; null = unknown. */
    fun bluetoothState(): Flow<Boolean?> = bluetoothMonitor.state()

    /** Emits the current hotspot state at subscription and on every change. */
    fun hotspotState(): Flow<HotspotState> = hotspotStateSource.state()

    /**
     * The chip row for one poll snapshot: Bluetooth only while its state is known AND on (the
     * monitor's unknown contract), network only while a transport is up, tethering only while
     * enabled - a dim row that lists what is absent defeats the glance it exists for. The network
     * chip's id carries the transport, because the id is what the action router routes on and wifi
     * has its own settings screen while every other transport lands in wireless settings - the
     * tray's own pairing in [com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySectionRouting].
     */
    fun chips(bluetoothOn: Boolean?, hotspotEnabled: Boolean, transport: NetworkTransport): List<DimStatusChip> =
        buildList {
            if (bluetoothOn == true) {
                add(
                    chip(ID_BLUETOOTH, R.drawable.ic_bluetooth, R.string.launcher_tray_bluetooth_on)
                )
            }
            when (transport) {
                NetworkTransport.WIFI ->
                    add(chip(ID_NETWORK_WIFI, R.drawable.ic_wifi, R.string.launcher_tray_network_wifi))

                NetworkTransport.CELLULAR ->
                    add(chip(ID_NETWORK_OTHER, R.drawable.ic_signal_cellular, R.string.launcher_tray_network_cellular))

                NetworkTransport.ETHERNET ->
                    add(chip(ID_NETWORK_OTHER, R.drawable.ic_ethernet, R.string.launcher_tray_network_ethernet))

                NetworkTransport.NONE -> Unit
            }
            if (hotspotEnabled) {
                add(
                    chip(ID_TETHERING, R.drawable.ic_wifi_tethering, R.string.launcher_tray_tethering_on)
                )
            }
        }

    private fun chip(id: String, iconRes: Int, labelRes: Int) = DimStatusChip(
        id = id,
        iconResId = iconRes,
        isNotification = false,
        contentDescription = context.getString(labelRes),
    )

    companion object {
        /** Shared with the action router: the id IS the tap target's address. */
        const val ID_BLUETOOTH = "dim-status:bluetooth"
        const val ID_NETWORK_WIFI = "dim-status:network:wifi"
        const val ID_NETWORK_OTHER = "dim-status:network:other"
        const val ID_TETHERING = "dim-status:tethering"
    }
}
