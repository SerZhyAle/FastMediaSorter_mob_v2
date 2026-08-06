package com.sza.fastmediasorter.ui.launcher.tray

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * S1415: the tray's Bluetooth state, read without asking for a permission.
 *
 * `Settings.Global.BLUETOOTH_ON` is a plain settings row - readable by anyone and observable through a
 * [ContentObserver], which is what keeps the source subscription-based instead of polled (strategic §3.2).
 * [BluetoothAdapter] would answer the same question but is guarded by `BLUETOOTH_CONNECT` from API 31, so it
 * is only the fallback for a device whose settings row cannot be read; a `SecurityException` from it means
 * "unknown", not "off", so the indicator disappears per ADR-1 rather than claiming the adapter is idle.
 *
 * Emits `null` when the state cannot be established at all. See
 * `PLAN/S1415_launcher-taskbar-status-area-config/research/03__bluetooth-state-without-permission.md`.
 */
class LauncherTrayBluetoothMonitor(private val context: Context) {

    fun state(): Flow<Boolean?> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(readState())
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON),
            false,
            observer,
        )
        trySend(readState())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    private fun readState(): Boolean? = readFromSettings() ?: readFromAdapter()

    private fun readFromSettings(): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON)
    }.getOrNull()?.let { it != BLUETOOTH_OFF }

    private fun readFromAdapter(): Boolean? = runCatching {
        // BluetoothAdapter.getDefaultAdapter() is deprecated; the system service is the supported route.
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled
    }.onFailure {
        Timber.w(it, "Launcher tray: Bluetooth state unreadable, indicator hidden")
    }.getOrNull()

    private companion object {
        const val BLUETOOTH_OFF = 0
    }
}
