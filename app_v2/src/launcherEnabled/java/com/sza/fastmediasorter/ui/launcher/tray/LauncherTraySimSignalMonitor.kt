package com.sza.fastmediasorter.ui.launcher.tray

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import java.util.concurrent.Executor

/**
 * S1415/S2023: SIM state (signal level, roaming flag, and mobile data network type) per slot.
 */
class LauncherTraySimSignalMonitor(private val context: Context) {

    private val subscriptionManager: SubscriptionManager? =
        ContextCompat.getSystemService(context, SubscriptionManager::class.java)

    private val telephonyManager: TelephonyManager? =
        ContextCompat.getSystemService(context, TelephonyManager::class.java)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    fun states(): Flow<Map<Int, LauncherTraySimState>> = callbackFlow {
        val manager = subscriptionManager
        if (manager == null || !hasPermission()) {
            send(emptyMap())
            awaitClose { }
            return@callbackFlow
        }

        val executor = Executor { it.run() }
        val states = mutableMapOf<Int, LauncherTraySimState>()
        var registrations = emptyList<Registration>()

        fun publish() {
            trySend(states.toMap())
        }

        fun resubscribe() {
            registrations.forEach { it.unregister() }
            states.clear()
            registrations = activeSlots(manager).mapNotNull { (slotIndex, subscriptionId) ->
                subscribe(subscriptionId) { state ->
                    states[slotIndex] = state
                    publish()
                }
            }
            publish()
        }

        val subscriptionsListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                resubscribe()
            }
        }
        runCatching { manager.addOnSubscriptionsChangedListener(executor, subscriptionsListener) }
            .onFailure {
                Timber.w(it, "Launcher tray: SIM subscriptions unavailable, indicators hidden")
                trySend(emptyMap())
            }

        awaitClose {
            registrations.forEach { it.unregister() }
            runCatching { manager.removeOnSubscriptionsChangedListener(subscriptionsListener) }
        }
    }.distinctUntilChanged()

    private fun activeSlots(manager: SubscriptionManager): List<Pair<Int, Int>> = runCatching {
        manager.activeSubscriptionInfoList.orEmpty()
            .filter { it.simSlotIndex >= 0 }
            .map { it.simSlotIndex to it.subscriptionId }
    }.onFailure {
        Timber.w(it, "Launcher tray: SIM list unreadable, indicators hidden")
    }.getOrDefault(emptyList())

    private fun subscribe(subscriptionId: Int, onState: (LauncherTraySimState) -> Unit): Registration? {
        val manager = telephonyManager?.createForSubscriptionId(subscriptionId) ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback(manager, onState)
            } else {
                registerLegacyListener(manager, onState)
            }
        }.onFailure {
            Timber.w(it, "Launcher tray: signal callback refused for subscription %d", subscriptionId)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(manager: TelephonyManager, onState: (LauncherTraySimState) -> Unit): Registration {
        var lastLevel = 0
        var lastDisplayInfo: TelephonyDisplayInfo? = null

        fun publish() {
            val roaming = runCatching { manager.isNetworkRoaming }.getOrDefault(false)
            val rawType = if (lastDisplayInfo != null) {
                lastDisplayInfo!!.networkType
            } else {
                runCatching { manager.dataNetworkType }.getOrNull()
            }
            val nrAdvanced = when (lastDisplayInfo?.overrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> true
                else -> false
            }
            onState(
                LauncherTraySimState(
                    signalLevel = lastLevel,
                    roaming = roaming,
                    dataNetworkType = rawType,
                    nrAdvanced = nrAdvanced,
                )
            )
        }

        val callback = object : TelephonyCallback(),
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.ServiceStateListener {

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                lastLevel = signalStrength.level
                publish()
            }

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                lastDisplayInfo = telephonyDisplayInfo
                publish()
            }

            override fun onServiceStateChanged(serviceState: ServiceState) {
                publish()
            }
        }
        manager.registerTelephonyCallback(Executor { it.run() }, callback)
        return Registration { runCatching { manager.unregisterTelephonyCallback(callback) } }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener(manager: TelephonyManager, onState: (LauncherTraySimState) -> Unit): Registration {
        var lastLevel = 0

        fun publish() {
            val roaming = runCatching { manager.isNetworkRoaming }.getOrDefault(false)
            @Suppress("DEPRECATION")
            val networkType = runCatching { manager.networkType }.getOrNull()
            onState(
                LauncherTraySimState(
                    signalLevel = lastLevel,
                    roaming = roaming,
                    dataNetworkType = networkType,
                    nrAdvanced = false,
                )
            )
        }

        val listener = object : android.telephony.PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                lastLevel = signalStrength.level
                publish()
            }

            override fun onServiceStateChanged(serviceState: ServiceState?) {
                publish()
            }
        }
        manager.listen(
            listener,
            android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or android.telephony.PhoneStateListener.LISTEN_SERVICE_STATE
        )
        return Registration {
            runCatching { manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun interface Registration {
        fun unregister()
    }
}
