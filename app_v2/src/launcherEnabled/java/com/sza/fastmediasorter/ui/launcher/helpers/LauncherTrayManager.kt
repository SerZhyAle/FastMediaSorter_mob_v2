package com.sza.fastmediasorter.ui.launcher.helpers

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.databinding.LauncherStatusClockBinding
import com.sza.fastmediasorter.databinding.LauncherStatusIndicatorsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetNetworkStatusUseCase
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayBluetoothMonitor
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayCallbacks
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayIconModel
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayIconView
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayIndicator
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySectionRouting
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySimSignalMonitor
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
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
 *
 * S1431: takes the two extracted layouts rather than the taskbar binding, so the same renderer - and
 * therefore one subscription, permission and ordering rule - serves the taskbar tray and the top status
 * strip alike (ADR-1). The two bindings are handed in separately because the strip puts them at opposite
 * edges of the same row.
 */
class LauncherTrayManager(
    private val lifecycleOwner: LifecycleOwner,
    private val clock: LauncherStatusClockBinding,
    private val indicators: LauncherStatusIndicatorsBinding,
    private val callbacks: LauncherTrayCallbacks = LauncherTrayCallbacks(),
) : DefaultLifecycleObserver {

    private val context: Context = indicators.root.context

    /**
     * S1087: the tray shows clock/network/battery only while the launcher owns the status area. With the
     * Android status bar left in place they would be a duplicate row, so they are hidden - and the
     * battery broadcast and network callback are dropped with them rather than feeding invisible views.
     */
    private var statusContentVisible = false

    /**
     * S1415: which indicators the user kept. Seeded from the settings defaults so the first render before
     * the settings flow emits looks like the stored state rather than an empty tray.
     */
    private var composition = LauncherTrayComposition.from(AppSettings())

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false

    /** The tray's only animation (strategic §3.2), so it is owned here and stopped with the tray. */
    private var lowBatteryBlink: ObjectAnimator? = null

    /** Captured before any threshold recolours the view, so "back to normal" needs no theme lookup. */
    private val defaultBatteryColor = indicators.trayBatteryLevel.currentTextColor

    private val bluetoothMonitor = LauncherTrayBluetoothMonitor(context)

    private val simSignalMonitor = LauncherTraySimSignalMonitor(context)

    /** Held so the collection can be cancelled when the switch goes off, not merely hidden (§5.2). */
    private var bluetoothJob: Job? = null

    private var simJob: Job? = null

    /** Asked once per activity instance: the system's own "don't ask again" governs everything after that. */
    private var phoneStatePermissionRequested = false

    /**
     * S1767: which settings screen the network indicator currently points at.
     *
     * Held from the last render rather than re-read on the tap: asking the connectivity manager again
     * would answer for a moment the user is not looking at, and the icon they tapped is the promise the
     * tap has to keep.
     */
    private var lastTransport: NetworkTransport = NetworkTransport.NONE

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { renderBattery(it) }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            indicators.root.post { renderNetwork(GetNetworkStatusUseCase.classify(capabilities)) }
        }

        override fun onLost(network: Network) {
            // The default network went away; anything still up will re-announce itself immediately.
            indicators.root.post { renderNetwork(NetworkTransport.NONE) }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        val openSystem = callbacks.onOpenSystemScreen
        val openNetwork = callbacks.onOpenNetworkSurface
        val routeTo = { indicator: LauncherTrayIndicator ->
            val (sectionKey, osShortcutKey) = LauncherTraySectionRouting.routeFor(indicator, lastTransport)
            openNetwork(sectionKey, osShortcutKey)
        }
        indicators.trayBluetooth.setOnClickListener { routeTo(LauncherTrayIndicator.BLUETOOTH) }
        indicators.traySim1.setOnClickListener { routeTo(LauncherTrayIndicator.SIM1) }
        indicators.traySim2.setOnClickListener { routeTo(LauncherTrayIndicator.SIM2) }
        indicators.trayNetwork.setOnClickListener { routeTo(LauncherTrayIndicator.NETWORK) }
        indicators.trayBatteryLevel.setOnClickListener { openSystem(OsShortcutCatalog.KEY_BATTERY) }
    }

    /**
     * S1767: the state description, plus what the tap does with it.
     *
     * Appended rather than replacing: the state is what the indicator is for, and a description saying
     * only "opens settings" would cost a screen-reader user the reading they came for.
     */
    private fun describe(state: CharSequence?): String =
        context.getString(R.string.launcher_tray_indicator_action, state)

    /**
     * Follow the persisted replacement policy for as long as [lifecycleOwner] is started. Call once from
     * the host; the tray keeps itself in sync from there.
     */
    fun bind(replaceSystemStatusArea: Flow<Boolean>, trayComposition: Flow<LauncherTrayComposition>) {
        lifecycleOwner.collectOnLifecycle(replaceSystemStatusArea) {
            statusContentVisible = it
            apply()
        }
        lifecycleOwner.collectOnLifecycle(trayComposition) {
            composition = it
            apply()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!statusContentVisible) return
        apply()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregisterBattery()
        unregisterNetwork()
        stopBlink()
    }

    /**
     * S1415: an indicator is shown only when the launcher owns the status area AND its own switch is on,
     * and its source is subscribed on exactly the same condition - a hidden indicator must not keep a
     * receiver alive. The Bluetooth and SIM slots have no source yet; they stay gone.
     */
    private fun apply() {
        val battery = statusContentVisible && composition.battery
        val network = statusContentVisible && composition.network
        clock.root.isVisible = statusContentVisible && composition.clock
        indicators.trayNetwork.isVisible = network
        indicators.trayBatteryLevel.isVisible = battery
        if (battery) registerBattery() else unregisterBattery()
        if (network) registerNetwork() else unregisterNetwork()
        if (!battery) stopBlink()
        applyBluetooth(statusContentVisible && composition.bluetooth)
        applySim(statusContentVisible && (composition.sim1 || composition.sim2))
    }

    private fun applyBluetooth(enabled: Boolean) {
        if (!enabled) {
            bluetoothJob?.cancel()
            bluetoothJob = null
            indicators.trayBluetooth.isVisible = false
            return
        }
        if (bluetoothJob?.isActive == true) return
        bluetoothJob = lifecycleOwner.collectOnLifecycle(bluetoothMonitor.state()) { renderBluetooth(it) }
    }

    /**
     * S1415: the icon is present only while the adapter is on, as in the Android status bar (strategic §3.4).
     * An unreadable state (`null`) hides it too - ADR-1 keeps "unknown" out of the tray rather than drawing it
     * as off.
     */
    private fun renderBluetooth(enabled: Boolean?) {
        if (enabled != true) {
            indicators.trayBluetooth.isVisible = false
            return
        }
        indicators.trayBluetooth.apply(
            LauncherTrayIconModel(
                iconRes = R.drawable.ic_bluetooth,
                contentDescription = describe(context.getString(R.string.launcher_tray_bluetooth_on)),
            ),
        )
        indicators.trayBluetooth.isVisible = true
    }

    /**
     * S1415: the permission is asked for at the tray's first show and only when a SIM indicator is actually
     * wanted (strategic §3.3). A refusal is a normal path - the map stays empty and both slots are absent.
     */
    private fun applySim(enabled: Boolean) {
        if (!enabled) {
            simJob?.cancel()
            simJob = null
            indicators.traySim1.isVisible = false
            indicators.traySim2.isVisible = false
            return
        }
        if (!simSignalMonitor.hasPermission() && !phoneStatePermissionRequested) {
            phoneStatePermissionRequested = true
            callbacks.onRequestPhoneStatePermission()
        }
        if (simJob?.isActive == true) return
        simJob = lifecycleOwner.collectOnLifecycle(simSignalMonitor.levels()) { renderSim(it) }
    }

    private fun renderSim(levels: Map<Int, Int>) {
        renderSimSlot(indicators.traySim1, SIM1_SLOT, composition.sim1, levels)
        renderSimSlot(indicators.traySim2, SIM2_SLOT, composition.sim2, levels)
    }

    /**
     * A slot missing from [levels] is absent whatever its switch says: on a single-SIM device there is no
     * SIM2 to report, and drawing level 0 there would read as "no coverage" (ADR-1, strategic §11.5).
     */
    private fun renderSimSlot(
        view: LauncherTrayIconView,
        slotIndex: Int,
        enabled: Boolean,
        levels: Map<Int, Int>,
    ) {
        val level = levels[slotIndex]
        if (!enabled || level == null) {
            view.isVisible = false
            return
        }
        val slotNumber = slotIndex + 1
        val state = if (level == NO_SERVICE_LEVEL) {
            context.getString(R.string.launcher_tray_sim_signal_none, slotNumber)
        } else {
            context.getString(R.string.launcher_tray_sim_signal, slotNumber, level)
        }
        view.apply(
            LauncherTrayIconModel(
                iconRes = R.drawable.launcher_tray_signal_level,
                contentDescription = describe(state),
            ),
        )
        view.setGlyphLevel(level)
        view.isVisible = true
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

    private fun currentTransport(): NetworkTransport {
        val manager = connectivityManager ?: return NetworkTransport.NONE
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        return GetNetworkStatusUseCase.classify(capabilities)
    }

    private fun renderNetwork(transport: NetworkTransport) {
        lastTransport = transport
        indicators.trayNetwork.apply(
            LauncherTrayIconModel(
                iconRes = iconOf(transport),
                contentDescription = describe(context.getString(labelOf(transport))),
            ),
        )
    }

    @DrawableRes
    private fun iconOf(transport: NetworkTransport): Int = when (transport) {
        NetworkTransport.WIFI -> R.drawable.ic_wifi
        NetworkTransport.CELLULAR -> R.drawable.ic_signal_cellular
        NetworkTransport.ETHERNET -> R.drawable.ic_ethernet
        NetworkTransport.NONE -> R.drawable.ic_network_off
    }

    @StringRes
    private fun labelOf(transport: NetworkTransport): Int = when (transport) {
        NetworkTransport.WIFI -> R.string.launcher_tray_network_wifi
        NetworkTransport.CELLULAR -> R.string.launcher_tray_network_cellular
        NetworkTransport.ETHERNET -> R.string.launcher_tray_network_ethernet
        NetworkTransport.NONE -> R.string.launcher_tray_network_none
    }

    private fun renderBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * FULL_PERCENT / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        indicators.trayBatteryLevel.text = context.getString(R.string.launcher_tray_battery_value, percent)
        // The number alone is what the owner asked for (strategic §2 goal 2), so the spoken description is
        // the only place left that says what the number means and whether the device is charging.
        indicators.trayBatteryLevel.contentDescription = describe(
            context.getString(
                if (charging) R.string.launcher_tray_battery_charging else R.string.launcher_tray_battery_level,
                percent,
            ),
        )
        applyBatteryLevelStyle(percent)
    }

    /**
     * Strategic §2 goal 3. Colour carries the warning and the number stays readable underneath it, so the
     * blink below [BATTERY_BLINK_PERCENT] repeats what red already said rather than being the only signal.
     */
    private fun applyBatteryLevelStyle(percent: Int) {
        val color = when {
            percent < BATTERY_CRITICAL_PERCENT -> ContextCompat.getColor(context, R.color.error_color)
            percent < BATTERY_WARNING_PERCENT -> ContextCompat.getColor(context, R.color.warning_color)
            else -> defaultBatteryColor
        }
        indicators.trayBatteryLevel.setTextColor(color)
        indicators.trayBatteryLevel.backgroundTintList = ColorStateList.valueOf(color)
        if (percent < BATTERY_BLINK_PERCENT) startBlink() else stopBlink()
    }

    private fun startBlink() {
        if (lowBatteryBlink?.isRunning == true) return
        lowBatteryBlink = ObjectAnimator.ofFloat(
            indicators.trayBatteryLevel,
            View.ALPHA,
            FULL_ALPHA,
            BLINK_MIN_ALPHA,
        ).apply {
            duration = BLINK_HALF_PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopBlink() {
        lowBatteryBlink?.cancel()
        lowBatteryBlink = null
        indicators.trayBatteryLevel.alpha = FULL_ALPHA
    }

    private companion object {
        const val FULL_PERCENT = 100

        // Strategic §2 goal 3: below 30 yellow, below 15 red, below 10 also blinking.
        const val BATTERY_WARNING_PERCENT = 30
        const val BATTERY_CRITICAL_PERCENT = 15
        const val BATTERY_BLINK_PERCENT = 10

        const val SIM1_SLOT = 0
        const val SIM2_SLOT = 1
        const val NO_SERVICE_LEVEL = 0

        const val FULL_ALPHA = 1f
        const val BLINK_MIN_ALPHA = 0.25f
        const val BLINK_HALF_PERIOD_MS = 700L
    }
}
