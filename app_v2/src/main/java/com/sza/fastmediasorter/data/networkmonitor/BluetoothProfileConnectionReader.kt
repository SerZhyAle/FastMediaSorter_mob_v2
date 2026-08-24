package com.sza.fastmediasorter.data.networkmonitor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1853: which devices the platform reports as connected right now, across every profile it will name.
 *
 * `BluetoothManager.getConnectedDevices` answers for GATT and GATT_SERVER only, so a headset, a speaker or
 * a car kit never appears there - research artifact 02 records that this is why no paired device was ever
 * marked connected. The classic profiles answer only through a profile proxy, which arrives on a listener
 * callback rather than as a return value; that asynchrony is the whole reason this class exists instead of
 * one more line inside [BluetoothDeviceDataSource].
 *
 * Nothing is held between reads. Every proxy obtained is released in the same call, including on the
 * timeout path, because a leaked proxy keeps a binding alive behind a screen the user has already left.
 *
 * A profile that has not answered within [PROFILE_TIMEOUT_MS] contributes nothing rather than delaying the
 * Monitor's one-second tick.
 */
@Singleton
class BluetoothProfileConnectionReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    /**
     * The addresses of every connected device, GATT and classic profiles alike.
     *
     * Addresses rather than devices: the caller already holds the bonded set and needs only to know which of
     * those links are live, and an address is the one key both sides share.
     */
    suspend fun connectedAddresses(): Set<String> {
        val adapter = bluetoothManager?.adapter ?: return emptySet()
        val gatt = gattAddresses()
        val addresses = gatt.toMutableSet()
        for (profile in supportedProfiles()) {
            addresses += profileAddresses(adapter, profile)
        }
        return addresses
    }

    /** GATT is the one profile the manager answers directly, with no proxy and no callback. */
    fun gattAddresses(): Set<String> = try {
        bluetoothManager
            ?.getConnectedDevices(BluetoothProfile.GATT)
            .orEmpty()
            .mapTo(mutableSetOf()) { device -> device.address }
    } catch (security: SecurityException) {
        Timber.w(security, "Connected GATT devices refused despite a granted permission")
        emptySet()
    }

    /**
     * One profile's connected devices, or nothing when the platform does not answer in time.
     *
     * `getProfileProxy` returns false on a profile this build does not support, in which case no callback
     * ever arrives - which is why the wait is bounded rather than trusting the return value alone.
     */
    private suspend fun profileAddresses(adapter: BluetoothAdapter, profile: Int): Set<String> {
        val proxy = obtainProxy(adapter, profile) ?: return emptySet()
        return try {
            proxy.connectedDevices.orEmpty().mapTo(mutableSetOf(), BluetoothDevice::getAddress)
        } catch (security: SecurityException) {
            Timber.w(security, "Connected devices of profile %d refused despite a granted permission", profile)
            emptySet()
        } finally {
            adapter.closeProfileProxy(profile, proxy)
        }
    }

    /**
     * The proxy for [profile], or null when the platform does not hand one over in time.
     *
     * The listener closes a proxy that arrives after the wait has been abandoned. `withTimeoutOrNull` only
     * cancels the waiting coroutine - the callback still fires afterwards, and a proxy nobody claims keeps a
     * service binding alive behind a screen the user has already left.
     */
    private suspend fun obtainProxy(adapter: BluetoothAdapter, profile: Int): BluetoothProfile? {
        val arrival = CompletableDeferred<BluetoothProfile?>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(connectedProfile: Int, proxy: BluetoothProfile?) {
                if (!arrival.complete(proxy) && proxy != null) {
                    adapter.closeProfileProxy(connectedProfile, proxy)
                }
            }

            override fun onServiceDisconnected(disconnectedProfile: Int) {
                arrival.complete(null)
            }
        }
        val requested = try {
            adapter.getProfileProxy(context, listener, profile)
        } catch (security: SecurityException) {
            Timber.w(security, "Profile proxy %d refused despite a granted permission", profile)
            false
        }
        if (!requested) {
            return null
        }
        val proxy = withTimeoutOrNull(PROFILE_TIMEOUT_MS) { arrival.await() }
        if (proxy == null) {
            // Marks the wait abandoned, so the listener above closes whatever arrives late.
            arrival.complete(null)
        }
        return proxy
    }

    /** The profile constants this API level knows; referencing a newer one below its level would not link. */
    private fun supportedProfiles(): List<Int> = buildList {
        add(BluetoothProfile.A2DP)
        add(BluetoothProfile.HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(BluetoothProfile.HEARING_AID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(BluetoothProfile.LE_AUDIO)
        }
    }

    private companion object {

        /**
         * How long one profile proxy is waited for.
         *
         * Four profiles timing out in series still return inside the Monitor's one-second tick, which is
         * the property that matters: a build that never answers for a profile must cost the screen nothing.
         * The observed callback latency is measured on device during the S1853 verification run and written
         * here once it is a number rather than an estimate.
         */
        const val PROFILE_TIMEOUT_MS = 200L
    }
}
