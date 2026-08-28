package com.sza.fastmediasorter.ui.launcher.tray

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
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
    private val connectionReader: BluetoothProfileConnectionReader = BluetoothProfileConnectionReader(context),
) {

    fun hasPermission(): Boolean = hasBluetoothAccess(context)

    fun connectedCount(): Flow<Int?> = callbackFlow {
        if (!hasPermission()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val connectedAddresses = mutableSetOf<String>()

        val initialResult = runCatching { connectionReader.connectedAddresses() }
            .onFailure { timber.log.Timber.w(it, "Launcher tray: Bluetooth initial addresses read failed") }
            .getOrNull()

        if (initialResult != null) {
            connectedAddresses.addAll(initialResult)
            trySend(connectedAddresses.size)
        } else {
            trySend(null)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val address = device?.address ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        connectedAddresses.add(address)
                        trySend(connectedAddresses.size)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        connectedAddresses.remove(address)
                        trySend(connectedAddresses.size)
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

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
