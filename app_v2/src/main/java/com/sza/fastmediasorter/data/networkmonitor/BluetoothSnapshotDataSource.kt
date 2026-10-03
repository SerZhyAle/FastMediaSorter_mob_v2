package com.sza.fastmediasorter.data.networkmonitor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: reads whether this device has a Bluetooth radio and whether it is switched on.
 *
 * Nothing here looks for other devices. The Monitor diagnoses the radio, not the room: no discovery is
 * started and no low-energy scanner is opened, which is what keeps this class clear of the location-class
 * permissions research artifacts 02 and 03 excluded from the first release.
 *
 * Paired and currently connected devices are counted without exposing names. Connection addresses are
 * deduplicated across profiles so a headset using two profiles still counts as one device.
 *
 * Sampled on demand, like its telephony sibling: no listener, no timer, nothing held once the Monitor
 * closes.
 */
@Singleton
class BluetoothSnapshotDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileConnections: BluetoothProfileConnectionReader,
) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Reads the current adapter state. Cheap enough to call on every Monitor tick. */
    suspend fun sample(): MonitorSection<BluetoothEntry> {
        val radio = adapter ?: return MonitorSection.absent(SectionAvailability.NoHardware)
        return when {
            !hasBluetoothAccess(context) -> {
                MonitorSection.absent(SectionAvailability.NoPermission(requiredBluetoothPermission()))
            }

            else -> entrySection(radio)
        }
    }

    /**
     * The adapter entry, or a permission gap when the platform refuses the read anyway.
     *
     * The grant check is not the last word: a manufacturer build can still refuse, and a diagnostic screen
     * must report that rather than die inside it.
     */
    private suspend fun entrySection(radio: BluetoothAdapter): MonitorSection<BluetoothEntry> = try {
        MonitorSection.available(
            BluetoothEntry(
                isEnabled = radio.isEnabled,
                bondedDeviceCount = radio.bondedDevices?.size,
                connectedDeviceCount = profileConnections.connectedAddresses().size,
            )
        )
    } catch (security: SecurityException) {
        Timber.w(security, "Bluetooth adapter state refused despite a granted permission")
        MonitorSection.absent(SectionAvailability.NoPermission(requiredBluetoothPermission()))
    }
}
