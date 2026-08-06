package com.sza.fastmediasorter.ui.launcher.tray

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
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
 * S1415: signal level per SIM slot, keyed by [android.telephony.SubscriptionInfo.getSimSlotIndex] - slot 0 is
 * SIM1, slot 1 is SIM2. The value is [SignalStrength.getLevel], which the platform reports as 0..4.
 *
 * A slot the device cannot report is simply missing from the map, never present with a zero level: ADR-1
 * makes an unreadable indicator absent, and a fake zero would read as "no coverage" on a phone that has no
 * second SIM at all. The same rule covers a refused `READ_PHONE_STATE` - the map stays empty and both slots
 * disappear, which strategic §7 treats as a normal path rather than a failure.
 *
 * Every value arrives through a registered callback, so nothing here polls (strategic §3.2).
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

    fun levels(): Flow<Map<Int, Int>> = callbackFlow {
        val manager = subscriptionManager
        if (manager == null || !hasPermission()) {
            send(emptyMap())
            awaitClose { }
            return@callbackFlow
        }

        val executor = Executor { it.run() }
        val levels = mutableMapOf<Int, Int>()
        var registrations = emptyList<Registration>()

        fun publish() {
            trySend(levels.toMap())
        }

        fun resubscribe() {
            registrations.forEach { it.unregister() }
            levels.clear()
            registrations = activeSlots(manager).mapNotNull { (slotIndex, subscriptionId) ->
                subscribe(subscriptionId) { level ->
                    levels[slotIndex] = level
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
        // Registering already delivers the first callback, which is what seeds the initial subscription set.
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

    /** Slot index to subscription id for every active SIM; empty when the read is refused. */
    private fun activeSlots(manager: SubscriptionManager): List<Pair<Int, Int>> = runCatching {
        manager.activeSubscriptionInfoList.orEmpty()
            .filter { it.simSlotIndex >= 0 }
            .map { it.simSlotIndex to it.subscriptionId }
    }.onFailure {
        Timber.w(it, "Launcher tray: SIM list unreadable, indicators hidden")
    }.getOrDefault(emptyList())

    private fun subscribe(subscriptionId: Int, onLevel: (Int) -> Unit): Registration? {
        val manager = telephonyManager?.createForSubscriptionId(subscriptionId) ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback(manager, onLevel)
            } else {
                registerLegacyListener(manager, onLevel)
            }
        }.onFailure {
            Timber.w(it, "Launcher tray: signal callback refused for subscription %d", subscriptionId)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(manager: TelephonyManager, onLevel: (Int) -> Unit): Registration {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                onLevel(signalStrength.level)
            }
        }
        manager.registerTelephonyCallback(Executor { it.run() }, callback)
        return Registration { runCatching { manager.unregisterTelephonyCallback(callback) } }
    }

    // TelephonyCallback arrives in API 31; below it PhoneStateListener is the only route, so the deprecation
    // is the platform's own cutover rather than an outdated call. Named in full rather than imported, so the
    // suppression stays on this one function instead of covering the whole file.
    @Suppress("DEPRECATION")
    private fun registerLegacyListener(manager: TelephonyManager, onLevel: (Int) -> Unit): Registration {
        val listener = object : android.telephony.PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                onLevel(signalStrength.level)
            }
        }
        manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        return Registration {
            runCatching { manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun interface Registration {
        fun unregister()
    }
}
