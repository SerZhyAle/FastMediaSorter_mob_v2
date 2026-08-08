package com.sza.fastmediasorter.radio

import android.bluetooth.BluetoothManager
import android.content.Context
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.sza.fastmediasorter.domain.radio.RadioKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * S1441: current on/off state of a radio, read without asking for a permission where possible.
 *
 * `Settings.Global.WIFI_ON` and `Settings.Global.BLUETOOTH_ON` are plain settings rows - readable by anyone
 * and observable through a [ContentObserver], which is what makes the source subscription-based rather than
 * polled. The system service is only the fallback for a device whose settings row cannot be read.
 *
 * A read that fails yields `null`, meaning unknown - never `false`, because drawing a radio as switched off
 * when the answer is simply unavailable is the failure mode S1415 already ruled out for the tray.
 *
 * Deliberately not shared with `LauncherTrayBluetoothMonitor`: that class lives in `src/launcherEnabled`,
 * a source set this one cannot see, and merging them would drag the tray into the network-monitor flavors.
 */
class RadioStateReader(private val context: Context) {

    /** Emits on every observed change, starting with the value at subscription time. */
    fun state(kind: RadioKind): Flow<Boolean?> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(read(kind))
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(settingsKey(kind)), false, observer)
        trySend(read(kind))
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    /** One-shot read, used before a toggle to know which way to flip. */
    fun read(kind: RadioKind): Boolean? = readFromSettings(kind) ?: readFromService(kind)

    private fun settingsKey(kind: RadioKind): String = when (kind) {
        RadioKind.WIFI -> Settings.Global.WIFI_ON
        RadioKind.BLUETOOTH -> Settings.Global.BLUETOOTH_ON
    }

    private fun readFromSettings(kind: RadioKind): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, settingsKey(kind))
    }.getOrNull()?.let { it != STATE_OFF }

    private fun readFromService(kind: RadioKind): Boolean? = runCatching {
        when (kind) {
            RadioKind.WIFI -> (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
            // getDefaultAdapter() is deprecated; the system service is the supported route.
            RadioKind.BLUETOOTH ->
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled
        }
    }.onFailure {
        Timber.w(it, "Radio state unreadable for %s", kind)
    }.getOrNull()

    private companion object {
        const val STATE_OFF = 0
    }
}
