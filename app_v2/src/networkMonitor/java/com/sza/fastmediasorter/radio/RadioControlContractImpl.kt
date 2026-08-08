package com.sza.fastmediasorter.radio

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * S1441: switches Wi-Fi and Bluetooth where the firmware still allows it.
 *
 * Success is decided by [RadioStateReader], never by what the platform call returned: Android 10+ firmwares
 * accept `setWifiEnabled` and ignore it, and Android 13+ does the same to the Bluetooth adapter, so trusting
 * the return value would break the button on exactly the old devices this is written for (strategic ADR-1).
 */
class RadioControlContractImpl(
    private val context: Context,
    private val reader: RadioStateReader,
) : RadioControlContract {

    override val isToggleSupported: Boolean = true

    override fun state(kind: RadioKind): Flow<Boolean?> = reader.state(kind)

    override suspend fun toggle(kind: RadioKind): Boolean {
        val current = reader.read(kind)
        // Unknown state or a permission the build never got: the caller opens the system screen instead,
        // which is cheaper than an attempt that cannot succeed.
        if (current == null || !hasPermissionFor(kind)) return false
        val requested = !current
        withContext(Dispatchers.IO) { attempt(kind, requested) }
        return withTimeoutOrNull(CONFIRM_WINDOW_MS) {
            reader.state(kind).first { it == requested }
        } != null
    }

    @Suppress("DEPRECATION")
    private fun attempt(kind: RadioKind, enable: Boolean) {
        runCatching {
            when (kind) {
                RadioKind.WIFI ->
                    (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled = enable
                // enable()/disable() are deprecated and are the only direct path that exists; on a firmware
                // that removed them the call is a no-op and the confirmation window reports the refusal.
                RadioKind.BLUETOOTH -> {
                    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    if (enable) adapter?.enable() else adapter?.disable()
                }
            }
        }.onFailure {
            // A refusal is the expected outcome on a modern device, so this is a note, not an error.
            Timber.w(it, "Radio toggle refused for %s", kind)
        }
    }

    private fun hasPermissionFor(kind: RadioKind): Boolean = when {
        kind != RadioKind.BLUETOOTH -> true
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> true
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /**
         * Upper bound on waiting for the state to flip. Strategic §6.1 chose "first change, capped" over a
         * fixed pause; §6.2 sets the cap: switching Bluetooth off on Android 13+ always exhausts it, so this
         * window is the visible delay before the system screen opens and must not feel like a hang.
         */
        const val CONFIRM_WINDOW_MS = 1200L
    }
}
