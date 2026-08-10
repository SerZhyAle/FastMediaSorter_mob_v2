package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: one signal reading of one SIM, tagged with the subscription it came from. */
data class CellularSignalReading(
    val takenAtMillis: Long,
    val subscriptionId: Int,
    val slotIndex: Int,
    val dbm: Int,
)

/**
 * S1433: samples mobile signal strength, one series per SIM.
 *
 * Per subscription rather than per device: research artifact 02 requires a separate callback and a separate
 * series for each SIM, because a dual-SIM phone can sit on a strong carrier and a dead one at once and a
 * single averaged line hides exactly the fault the user opened the Monitor to find.
 *
 * A SIM that reports nothing contributes no series at all. Nothing here fabricates a sample, because a
 * zero-filled line reads as "no signal" when the truth is "no data" - and the difference is the whole answer
 * on a screen whose job is diagnosis.
 *
 * Cold and symmetrical: every callback registered on collection is unregistered when collection ends, so a
 * closed Monitor leaves no telephony listener behind.
 */
@Singleton
class CellularSignalSampler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    /**
     * Emits the readings of every SIM that has reported one, newest per SIM, or the reason there are none.
     *
     * The emission carries the whole set rather than one changed SIM, so a consumer folding readings into a
     * chart never has to remember what the other SIM last said.
     */
    fun observe(): Flow<MonitorSection<List<CellularSignalReading>>> = callbackFlow {
        val latest = ConcurrentHashMap<Int, CellularSignalReading>()
        val registrations = when (val scan = scanSubscriptions()) {
            is SubscriptionScan.Unavailable -> {
                trySend(MonitorSection.absent(scan.reason))
                emptyList()
            }

            is SubscriptionScan.Found -> scan.subscriptions.map { subscription ->
                register(subscription) { reading ->
                    latest[reading.subscriptionId] = reading
                    trySend(MonitorSection.available(latest.values.sortedBy { it.slotIndex }))
                }
            }
        }

        awaitClose { registrations.forEach { it.unregister() } }
    }

    /**
     * Which SIMs can be sampled, or why none can.
     *
     * The permission gap is reported as [SectionAvailability.NoPermission] rather than as an empty list so
     * the UI can offer the one permission that would fill the section instead of leaving a blank chart.
     */
    private fun scanSubscriptions(): SubscriptionScan {
        val manager = subscriptionManager
        if (telephonyManager == null || manager == null) {
            return SubscriptionScan.Unavailable(SectionAvailability.NoHardware)
        }
        return if (hasReadPhoneState()) {
            scanGrantedSubscriptions(manager)
        } else {
            SubscriptionScan.Unavailable(
                SectionAvailability.NoPermission(Manifest.permission.READ_PHONE_STATE)
            )
        }
    }

    private fun scanGrantedSubscriptions(manager: SubscriptionManager): SubscriptionScan {
        val subscriptions = activeSubscriptions(manager)
        return if (subscriptions.isEmpty()) {
            SubscriptionScan.Unavailable(SectionAvailability.NoNetwork)
        } else {
            SubscriptionScan.Found(subscriptions)
        }
    }

    /**
     * Active subscriptions, or none when the platform refuses the read.
     *
     * A manufacturer build can throw despite a granted permission, and a diagnostic screen must report an
     * empty section rather than die inside it.
     */
    private fun activeSubscriptions(manager: SubscriptionManager): List<SimSubscription> = try {
        manager.activeSubscriptionInfoList
            .orEmpty()
            .map { info -> SimSubscription(info.subscriptionId, info.simSlotIndex) }
    } catch (security: SecurityException) {
        Timber.w(security, "Telephony subscriptions refused despite a granted READ_PHONE_STATE")
        emptyList()
    }

    private fun register(
        subscription: SimSubscription,
        onReading: (CellularSignalReading) -> Unit,
    ): SignalRegistration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        registerCallback(subscription, onReading)
    } else {
        registerLegacyListener(subscription, onReading)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallback(
        subscription: SimSubscription,
        onReading: (CellularSignalReading) -> Unit,
    ): SignalRegistration {
        val manager = managerFor(subscription)
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                report(subscription, signalStrength, onReading)
            }
        }
        return try {
            manager?.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            SignalRegistration { manager?.unregisterTelephonyCallback(callback) }
        } catch (security: SecurityException) {
            refusedRegistration(security, subscription)
        }
    }

    // TelephonyManager.listen and PhoneStateListener are deprecated from API 31 in favour of
    // TelephonyCallback, which does not exist below it; this branch never runs above API 30. The class is
    // named in full rather than imported because an import cannot carry the suppression this function does.
    @Suppress("DEPRECATION")
    private fun registerLegacyListener(
        subscription: SimSubscription,
        onReading: (CellularSignalReading) -> Unit,
    ): SignalRegistration {
        val manager = managerFor(subscription)
        val listener = object : android.telephony.PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                report(subscription, signalStrength, onReading)
            }
        }
        return try {
            manager?.listen(listener, android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            SignalRegistration {
                manager?.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
        } catch (security: SecurityException) {
            refusedRegistration(security, subscription)
        }
    }

    /**
     * What a refused registration contributes: nothing to unregister.
     *
     * One SIM being refused must not take the others down with it. Letting the exception escape the flow
     * builder would skip `awaitClose` entirely, leaving every callback registered before it live for the
     * rest of the process.
     */
    private fun refusedRegistration(
        security: SecurityException,
        subscription: SimSubscription,
    ): SignalRegistration {
        Timber.w(security, "Signal callback refused for subscription %d", subscription.subscriptionId)
        return SignalRegistration { }
    }

    private fun report(
        subscription: SimSubscription,
        signalStrength: SignalStrength,
        onReading: (CellularSignalReading) -> Unit,
    ) {
        val dbm = dbmOf(signalStrength) ?: return
        onReading(
            CellularSignalReading(
                takenAtMillis = System.currentTimeMillis(),
                subscriptionId = subscription.subscriptionId,
                slotIndex = subscription.slotIndex,
                dbm = dbm,
            )
        )
    }

    /**
     * The manager bound to one subscription, or the device-wide one below API 24.
     *
     * `createForSubscriptionId` arrived in API 24 while the `legacy` flavor still ships minSdk 23, so below
     * that the reading is the default subscription's - which on a single-SIM device is the same answer.
     */
    private fun managerFor(subscription: SimSubscription): TelephonyManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager?.createForSubscriptionId(subscription.subscriptionId)
        } else {
            telephonyManager
        }

    /** Strength in dBm, or null when this radio has nothing to report. */
    private fun dbmOf(signalStrength: SignalStrength): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            signalStrength.cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it != CellInfo.UNAVAILABLE }
        } else {
            legacyDbm(signalStrength)
        }

    /**
     * `SignalStrength.getDbm` is API 29, so below it the ASU reading is converted by the 3GPP mapping.
     *
     * The unknown ASU is a documented sentinel rather than a weak signal, and it maps to null so the series
     * skips the sample instead of drawing the floor of the scale.
     */
    @Suppress("DEPRECATION")
    private fun legacyDbm(signalStrength: SignalStrength): Int? {
        val asu = signalStrength.gsmSignalStrength
        return if (asu == UNKNOWN_ASU) null else ASU_BASE_DBM + ASU_STEP_DBM * asu
    }

    private fun hasReadPhoneState(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** One registered signal callback, closed the same way whichever API level opened it. */
    private fun interface SignalRegistration {
        fun unregister()
    }

    /** A SIM worth sampling: its subscription and the slot the UI names it by. */
    private data class SimSubscription(val subscriptionId: Int, val slotIndex: Int)

    private sealed interface SubscriptionScan {

        data class Found(val subscriptions: List<SimSubscription>) : SubscriptionScan

        data class Unavailable(val reason: SectionAvailability) : SubscriptionScan
    }

    private companion object {

        /** ASU value the platform reports when the strength is not known. */
        const val UNKNOWN_ASU = 99

        /** dBm of ASU 0, per the 3GPP mapping `dBm = -113 + 2 * asu`. */
        const val ASU_BASE_DBM = -113

        const val ASU_STEP_DBM = 2
    }
}
