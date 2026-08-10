package com.sza.fastmediasorter.data.networkmonitor

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: one signal reading of the Bluetooth device the user is watching. */
data class BluetoothRssiReading(
    val takenAtMillis: Long,
    val deviceAddress: String,
    val rssiDbm: Int,
)

/**
 * S1433: samples the signal of one connected Bluetooth device, chosen by the user.
 *
 * The selection is a precondition, not a filter applied afterwards: with no device chosen this returns a
 * flow that emits nothing, so no connection is opened and nothing is read. Research artifact 02 excludes
 * advertising-device RSSI because reaching it needs a scan, and a scan is what turns a passive diagnostic
 * into a location-class permission request - so no discovery is started and no low-energy scanner is opened
 * anywhere in this class.
 *
 * The reading comes from the device's own GATT connection. That is also why the chosen device must be in the
 * connected set: a bonded but absent device has no connection to read a signal from, and the honest report
 * for that is an empty section rather than a flat line.
 */
@Singleton
class BluetoothRssiSampler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    /**
     * Emits one reading per tick for the selected device, or nothing at all when none is selected.
     *
     * @param deviceAddress the address of a device the user picked from the connected set, or null.
     */
    fun observe(deviceAddress: String?): Flow<MonitorSection<BluetoothRssiReading>> {
        if (deviceAddress == null) {
            return emptyFlow()
        }
        return callbackFlow {
            val device = connectedDevice(deviceAddress)
            val refusal = refusalFor(device)
            if (refusal != null) {
                trySend(MonitorSection.absent(refusal))
            }
            val gatt = device?.takeIf { refusal == null }?.let { connected ->
                connect(connected, deviceAddress) { reading ->
                    trySend(MonitorSection.available(reading))
                }
            }
            val ticker = gatt?.let { connection ->
                launch {
                    while (isActive) {
                        delay(SAMPLE_INTERVAL_MS)
                        requestRssi(connection)
                    }
                }
            }

            awaitClose {
                ticker?.cancel()
                gatt?.let(::release)
            }
        }
    }

    /**
     * Why this device cannot be sampled, or null when it can.
     *
     * A missing grant is told apart from a missing radio and from a device that is simply not connected,
     * because only the first of the three is one tap away from being fixed.
     */
    private fun refusalFor(device: BluetoothDevice?): SectionAvailability? = when {
        bluetoothManager?.adapter == null -> SectionAvailability.NoHardware
        !hasBluetoothAccess(context) -> SectionAvailability.NoPermission(requiredBluetoothPermission())
        device == null -> SectionAvailability.NoNetwork
        else -> null
    }

    /**
     * The selected device, but only while it is actually connected.
     *
     * Matched against the connected set rather than resolved from the address directly: `getRemoteDevice`
     * answers for any well-formed address, including one that belongs to nothing in range, and connecting to
     * that would sit in a pending state instead of reporting the truth.
     */
    private fun connectedDevice(deviceAddress: String): BluetoothDevice? = try {
        bluetoothManager
            ?.getConnectedDevices(BluetoothProfile.GATT)
            ?.firstOrNull { device -> device.address == deviceAddress }
    } catch (security: SecurityException) {
        Timber.w(security, "Connected Bluetooth devices refused despite a granted permission")
        null
    }

    private fun connect(
        device: BluetoothDevice,
        deviceAddress: String,
        onReading: (BluetoothRssiReading) -> Unit,
    ): BluetoothGatt? {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    requestRssi(gatt)
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onReading(
                        BluetoothRssiReading(
                            takenAtMillis = System.currentTimeMillis(),
                            deviceAddress = deviceAddress,
                            rssiDbm = rssi,
                        )
                    )
                }
            }
        }
        return openGatt(device, callback)
    }

    /**
     * Opens the GATT client, or null when the platform refuses.
     *
     * The `autoConnect` argument is false: the device is connected already, and a pending background
     * connect would keep the section looking alive after the device walked away.
     */
    private fun openGatt(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? = try {
        device.connectGatt(context, false, callback)
    } catch (security: SecurityException) {
        Timber.w(security, "GATT connection refused despite a granted permission")
        null
    }

    private fun requestRssi(gatt: BluetoothGatt) {
        try {
            gatt.readRemoteRssi()
        } catch (security: SecurityException) {
            Timber.w(security, "Remote RSSI read refused despite a granted permission")
        }
    }

    /** Disconnects and releases the connection, so a closed section leaves no GATT client behind. */
    private fun release(gatt: BluetoothGatt) {
        try {
            gatt.disconnect()
            gatt.close()
        } catch (security: SecurityException) {
            Timber.w(security, "GATT teardown refused despite a granted permission")
        }
    }

    private companion object {

        /** One reading per second, matching the cap the composed Monitor snapshot already applies. */
        const val SAMPLE_INTERVAL_MS = 1000L
    }
}
