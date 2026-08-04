package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.LauncherTaskbarBinding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * S0404: the taskbar tray - clock, network transport, battery. Zero new permissions: the clock is a
 * self-driving [android.widget.TextClock], battery comes from the sticky ACTION_BATTERY_CHANGED
 * broadcast, and the transport read is covered by the already-declared ACCESS_NETWORK_STATE.
 *
 * Owns its lifecycle directly ([DefaultLifecycleObserver]) so the host stays logic-free (Rule 3).
 * Deliberately does NOT reuse `NetworkStateMonitor`: that one is app-scoped and debounces transitions
 * by 4s to avoid invalidating connections on a flap (S1040) - a tray must show exactly the flap it
 * hides, and it exposes no transport type at all.
 *
 * Signal strength is out of scope: it needs READ_PHONE_STATE, and the type alone answers the
 * question a tray asks (strategic §7).
 */
class LauncherTrayManager(
    private val lifecycleOwner: LifecycleOwner,
    private val binding: LauncherTaskbarBinding,
) : DefaultLifecycleObserver {

    private val context: Context = binding.root.context

    /**
     * S1087: the tray shows clock/network/battery only while the launcher owns the status area. With the
     * Android status bar left in place they would be a duplicate row, so they are hidden - and the
     * battery broadcast and network callback are dropped with them rather than feeding invisible views.
     */
    private var statusContentVisible = false

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { renderBattery(it) }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            binding.root.post { renderNetwork(transportOf(capabilities)) }
        }

        override fun onLost(network: Network) {
            // The default network went away; anything still up will re-announce itself immediately.
            binding.root.post { renderNetwork(Transport.NONE) }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * Follow the persisted replacement policy for as long as [lifecycleOwner] is started. Call once from
     * the host; the tray keeps itself in sync from there.
     */
    fun bind(replaceSystemStatusArea: Flow<Boolean>) {
        lifecycleOwner.collectOnLifecycle(replaceSystemStatusArea) { applyStatusContent(it) }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!statusContentVisible) return
        registerBattery()
        registerNetwork()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregisterBattery()
        unregisterNetwork()
    }

    private fun applyStatusContent(visible: Boolean) {
        Timber.d("S1087: tray status content visible=%s", visible)
        statusContentVisible = visible
        binding.trayClock.isVisible = visible
        binding.trayNetwork.isVisible = visible
        binding.trayBattery.isVisible = visible
        binding.trayBatteryPercent.isVisible = visible
        if (visible) {
            registerBattery()
            registerNetwork()
        } else {
            unregisterBattery()
            unregisterNetwork()
        }
    }

    private fun registerBattery() {
        if (batteryReceiverRegistered) return
        // A null receiver returns the sticky value without registering anything - seeds the first
        // render so the tray is never blank before the first real broadcast.
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        sticky?.let { renderBattery(it) }

        // ACTION_BATTERY_CHANGED is a protected system broadcast: a manifest receiver can never get
        // it, and from API 34 an unexported flag is mandatory on runtime registration.
        runCatching {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            batteryReceiverRegistered = true
        }.onFailure { Timber.w(it, "Launcher tray: battery updates unavailable") }
    }

    private fun unregisterBattery() {
        if (!batteryReceiverRegistered) return
        batteryReceiverRegistered = false
        runCatching { context.unregisterReceiver(batteryReceiver) }
            .onFailure { Timber.w(it, "Launcher tray: battery receiver was not registered") }
    }

    private fun registerNetwork() {
        val manager = connectivityManager ?: return
        if (networkCallbackRegistered) return
        renderNetwork(currentTransport())
        runCatching {
            manager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure { Timber.w(it, "Launcher tray: network updates unavailable") }
    }

    private fun unregisterNetwork() {
        val manager = connectivityManager ?: return
        if (!networkCallbackRegistered) return
        networkCallbackRegistered = false
        runCatching { manager.unregisterNetworkCallback(networkCallback) }
            .onFailure { Timber.w(it, "Launcher tray: network callback was not registered") }
    }

    private fun currentTransport(): Transport {
        val manager = connectivityManager ?: return Transport.NONE
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        return capabilities?.let { transportOf(it) } ?: Transport.NONE
    }

    private fun transportOf(capabilities: NetworkCapabilities): Transport = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
        else -> Transport.NONE
    }

    private fun renderNetwork(transport: Transport) {
        binding.trayNetwork.setImageResource(transport.iconRes)
        binding.trayNetwork.contentDescription = context.getString(transport.labelRes)
    }

    private fun renderBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * FULL_PERCENT / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        binding.trayBatteryPercent.text = context.getString(R.string.launcher_tray_battery_percent, percent)
        binding.trayBattery.setImageResource(
            if (charging) R.drawable.ic_battery_charging else R.drawable.ic_battery
        )
        // The icon is decorative; the percent text carries the value, so the pair is announced once.
        binding.trayBatteryPercent.contentDescription = context.getString(
            if (charging) R.string.launcher_tray_battery_charging else R.string.launcher_tray_battery_level,
            percent,
        )
    }

    private enum class Transport(
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int,
    ) {
        WIFI(R.drawable.ic_wifi, R.string.launcher_tray_network_wifi),
        CELLULAR(R.drawable.ic_signal_cellular, R.string.launcher_tray_network_cellular),
        ETHERNET(R.drawable.ic_ethernet, R.string.launcher_tray_network_ethernet),
        NONE(R.drawable.ic_network_off, R.string.launcher_tray_network_none),
    }

    private companion object {
        const val FULL_PERCENT = 100
    }
}
