package com.sza.fastmediasorter.ui.launcher.tray

import android.Manifest
import android.annotation.SuppressLint
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
class LauncherTraySimSignalMonitor(context: Context) {

    // The platform keeps the subscriptions listener's binder stub alive in a native global ref after
    // removeOnSubscriptionsChangedListener, and the listener reaches this monitor, so an Activity
    // context here outlives the screen that created it. The managers below must be built from it too:
    // TelephonyRegistryManager keeps the ContextImpl it was created from.
    private val context: Context = context.applicationContext

    // `this.` because a bare name in an initializer resolves to the constructor parameter.
    private val subscriptionManager: SubscriptionManager? =
        ContextCompat.getSystemService(this.context, SubscriptionManager::class.java)

    private val telephonyManager: TelephonyManager? =
        ContextCompat.getSystemService(this.context, TelephonyManager::class.java)

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

        // A direct executor ran each callback on the binder thread that delivered it, so two SIM slots
        // could write one map at once. The lock stays because awaitClose runs on the collector's thread.
        val executor = ContextCompat.getMainExecutor(context)
        val lock = Any()
        val states = mutableMapOf<Int, LauncherTraySimState>()
        var registrations = emptyList<Registration>()
        var closed = false

        fun publish() {
            trySend(states.toMap())
        }

        fun onSlotState(slotIndex: Int, state: LauncherTraySimState) = synchronized(lock) {
            if (!closed) {
                states[slotIndex] = state
                publish()
            }
        }

        // A subscriptions change landing after close must not register callbacks nobody unregisters.
        fun resubscribe() = synchronized(lock) {
            registrations.forEach { it.unregister() }
            states.clear()
            registrations = if (closed) {
                emptyList()
            } else {
                activeSlots(manager).mapNotNull { (slotIndex, subscriptionId) ->
                    subscribe(subscriptionId, executor) { state -> onSlotState(slotIndex, state) }
                }
            }
            if (!closed) publish()
        }

        val subscriptionsListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                resubscribe()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { manager.addOnSubscriptionsChangedListener(executor, subscriptionsListener) }
                .onFailure {
                    Timber.w(it, "Launcher tray: SIM subscriptions unavailable, indicators hidden")
                    trySend(emptyMap())
                }
        } else {
            // The executor overload is API 30. Below it the indicators stay hidden, which is what the
            // unguarded call already produced there by failing with NoSuchMethodError.
            trySend(emptyMap())
        }

        awaitClose {
            synchronized(lock) {
                closed = true
                registrations.forEach { it.unregister() }
                registrations = emptyList()
            }
            runCatching { manager.removeOnSubscriptionsChangedListener(subscriptionsListener) }
        }
    }.distinctUntilChanged()

    // S3155: same runCatching guard as registerModernCallback below - a revoked READ_PHONE_STATE
    // yields an empty slot list and hidden indicators.
    @SuppressLint("MissingPermission")
    private fun activeSlots(manager: SubscriptionManager): List<Pair<Int, Int>> = runCatching {
        manager.activeSubscriptionInfoList.orEmpty()
            .filter { it.simSlotIndex >= 0 }
            .map { it.simSlotIndex to it.subscriptionId }
    }.onFailure {
        Timber.w(it, "Launcher tray: SIM list unreadable, indicators hidden")
    }.getOrDefault(emptyList())

    private fun subscribe(
        subscriptionId: Int,
        executor: Executor,
        onState: (LauncherTraySimState) -> Unit,
    ): Registration? {
        val manager = telephonyManager?.createForSubscriptionId(subscriptionId) ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback(manager, executor, onState)
            } else {
                registerLegacyListener(manager, onState)
            }
        }.onFailure {
            Timber.w(it, "Launcher tray: signal callback refused for subscription %d", subscriptionId)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    // S3155: the READ_PHONE_STATE reads below are each wrapped in runCatching, which absorbs the
    // SecurityException a revoked grant throws and falls back to a null or a default - the tray
    // simply shows less. Lint does not recognise runCatching as the handling it asks for, so it
    // reports MissingPermission on a call that already degrades safely.
    @SuppressLint("MissingPermission")
    private fun registerModernCallback(
        manager: TelephonyManager,
        executor: Executor,
        onState: (LauncherTraySimState) -> Unit
    ): Registration {
        var lastLevel = 0
        var lastDisplayInfo: TelephonyDisplayInfo? = null

        fun publish() {
            val roaming = runCatching { manager.isNetworkRoaming }.getOrDefault(false)
            val rawType = lastDisplayInfo?.networkType
                ?: runCatching { manager.dataNetworkType }.getOrNull()
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

        val callback = object :
            TelephonyCallback(),
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
        manager.registerTelephonyCallback(executor, callback)
        return Registration { runCatching { manager.unregisterTelephonyCallback(callback) } }
    }

    @Suppress("DEPRECATION")
    // S3155: same runCatching guard as registerModernCallback above.
    @SuppressLint("MissingPermission")
    private fun registerLegacyListener(
        manager: TelephonyManager,
        onState: (LauncherTraySimState) -> Unit
    ): Registration {
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
            android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                android.telephony.PhoneStateListener.LISTEN_SERVICE_STATE,
        )
        return Registration {
            runCatching { manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun interface Registration {
        fun unregister()
    }
}
