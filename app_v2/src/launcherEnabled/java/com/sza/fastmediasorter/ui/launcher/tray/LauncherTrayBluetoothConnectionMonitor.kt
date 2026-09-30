package com.sza.fastmediasorter.ui.launcher.tray

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.networkmonitor.BluetoothProfileConnectionReader
import com.sza.fastmediasorter.data.networkmonitor.hasBluetoothAccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Delta-maintained monitor for connected Bluetooth device count.
 *
 * Seeded by a single sweep from [BluetoothProfileConnectionReader] on collection, then kept current via
 * ACL connect/disconnect events to avoid repeating expensive profile sweeps.
 */
class LauncherTrayBluetoothConnectionMonitor(
    private val context: Context,
    // The platform's profile connector keeps the context it was given in a native-rooted callback even
    // after closeProfileProxy, so an Activity passed here outlives its own destroy.
    private val connectionReader: BluetoothProfileConnectionReader =
        BluetoothProfileConnectionReader(context.applicationContext),
) {

    fun hasPermission(): Boolean = hasBluetoothAccess(context)

    fun connectedCount(): Flow<Int?> = callbackFlow {
        if (!hasPermission()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // The receiver is registered before the seed sweep so an ACL event inside the sweep is not lost;
        // events that arrive before the seed lands are buffered and replayed on top of it. Receiver
        // callbacks run on main while this body runs on the collector's thread, hence the lock.
        val lock = Any()
        val connectedAddresses = mutableSetOf<String>()
        val pendingEvents = mutableListOf<Pair<String, Boolean>>()
        var seeded = false

        fun applyEvent(address: String, connected: Boolean) {
            if (connected) connectedAddresses.add(address) else connectedAddresses.remove(address)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val connected = when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> true
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
                    else -> return
                }
                val address = intent.bluetoothDeviceAddress() ?: return
                synchronized(lock) {
                    if (seeded) {
                        applyEvent(address, connected)
                        trySend(connectedAddresses.size)
                    } else {
                        pendingEvents += address to connected
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        try {
            // warnUnlessCancellation rethrows a cancellation, which the finally below turns into an unregister.
            val initialResult = runCatching { connectionReader.connectedAddresses() }
                .onFailure { it.warnUnlessCancellation("Launcher tray: Bluetooth initial addresses read failed") }
                .getOrNull()

            synchronized(lock) {
                initialResult?.let { connectedAddresses.addAll(it) }
                pendingEvents.forEach { (address, connected) -> applyEvent(address, connected) }
                pendingEvents.clear()
                seeded = true
                trySend(if (initialResult != null) connectedAddresses.size else null)
            }

            awaitClose { }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

private fun Intent?.bluetoothDeviceAddress(): String? {
    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        this?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
    return device?.address
}
