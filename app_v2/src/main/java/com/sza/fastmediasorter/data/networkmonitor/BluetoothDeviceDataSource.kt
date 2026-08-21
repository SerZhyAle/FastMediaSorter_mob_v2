package com.sza.fastmediasorter.data.networkmonitor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothDeviceEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: the Bluetooth section's own read path for paired and connected devices.
 *
 * Separate from [BluetoothSnapshotDataSource] rather than folded into it: the snapshot deliberately reports
 * a bonded count and no names, and it is read by the Summary tile as well, so widening it would carry device
 * names onto a screen that has no business showing them. Strategic §3.1.1 item 5 permits the names in this
 * one section and only after the grant, which is exactly the scope of this class.
 *
 * Nothing here scans. Discovery and low-energy scanning are excluded from the first release (strategic §2),
 * so the answer is limited to what the adapter already knows: what is paired and what is connected.
 *
 * Sampled on demand, like its siblings - no listener, no timer, nothing held once the Monitor closes.
 */
@Singleton
class BluetoothDeviceDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileConnections: BluetoothProfileConnectionReader,
) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    /** Reads the current device set, or why there is none to read. */
    suspend fun sample(): MonitorSection<List<BluetoothDeviceEntry>> {
        val adapter = bluetoothManager?.adapter
        return when {
            adapter == null -> MonitorSection.absent(SectionAvailability.NoHardware)
            !hasBluetoothAccess(context) ->
                MonitorSection.absent(SectionAvailability.NoPermission(requiredBluetoothPermission()))

            else -> readDevices(adapter)
        }
    }

    /**
     * The paired set unioned with the connected set, not the paired set alone.
     *
     * `BluetoothRssiSampler` can only chart a device that appears in the GATT connected set, and a
     * low-energy peer can be connected without ever having been paired. A picker built from bonded devices
     * alone would therefore hide exactly the device that is chartable.
     *
     * Connection is asked of every profile the platform will name, and chartability of GATT alone (S1853):
     * a headset holds a live A2DP link with no GATT connection to read an RSSI from, so one answer serving
     * both questions either hides the headset or offers a chart that stays empty.
     *
     * The grant check above is not the last word: a manufacturer build can still refuse, and a diagnostic
     * screen must report that rather than die inside it.
     */
    private suspend fun readDevices(adapter: BluetoothAdapter): MonitorSection<List<BluetoothDeviceEntry>> {
        val connectedAddresses = profileConnections.connectedAddresses()
        return try {
            val gattDevices = connectedDevices()
            val chartableAddresses = gattDevices.mapTo(mutableSetOf()) { it.address }
            val entries = (adapter.bondedDevices.orEmpty() + gattDevices)
                .distinctBy { it.address }
                .map { device ->
                    BluetoothDeviceEntry(
                        address = device.address,
                        name = device.name?.takeIf { it.isNotBlank() },
                        isConnected = device.address in connectedAddresses,
                        isChartable = device.address in chartableAddresses,
                    )
                }
            MonitorSection.available(entries.sortedWith(DISPLAY_ORDER))
        } catch (security: SecurityException) {
            Timber.w(security, "Bluetooth device list refused despite a granted permission")
            MonitorSection.absent(SectionAvailability.NoPermission(requiredBluetoothPermission()))
        }
    }

    private fun connectedDevices(): List<BluetoothDevice> =
        bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()

    private companion object {

        /** Connected first, then by name: the chartable devices are the ones the section is opened for. */
        val DISPLAY_ORDER: Comparator<BluetoothDeviceEntry> =
            compareByDescending<BluetoothDeviceEntry> { it.isConnected }
                .thenBy { it.name.orEmpty() }
    }
}
