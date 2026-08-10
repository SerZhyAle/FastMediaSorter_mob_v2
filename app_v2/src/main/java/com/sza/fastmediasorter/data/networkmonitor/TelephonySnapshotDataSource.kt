package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SimEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: the telephony half of one Monitor sample. */
data class TelephonySample(
    val activeModemCount: Int?,
    val sims: MonitorSection<List<SimEntry>>,
)

/**
 * S1433: reads how many modems this device has, and - only with `READ_PHONE_STATE` - what each SIM is.
 *
 * The split between the two is the whole point of this class. The modem count is readable with no
 * permission at all, so a user who declined `READ_PHONE_STATE` still learns whether the device is dual-SIM;
 * folding the count into the permission-gated section would hide a fact they are entitled to behind a
 * decision they already made. Per-subscription detail is the gated half and reports
 * [SectionAvailability.NoPermission] rather than an empty list, so the UI can offer the one permission that
 * would fill it.
 *
 * Nothing here reads or returns subscriber identity. There is no call to `getDeviceId`, `getImei`,
 * `getLine1Number` or `getSimSerialNumber`, and [SimEntry] has nowhere to put one - strategic §3.2 forbids
 * them in UI, export and logs alike.
 *
 * Sampled on demand rather than observed: the class registers no listener and starts no timer, so it holds
 * nothing once the Monitor closes. The repository re-reads it on the connectivity flow's own tick, which is
 * already capped at one sample per second.
 *
 * Lives in `src/main` because the Monitor's data layer does, but it is only ever reached from the two
 * flavors that mount `src/networkMonitor` - and those are the same two that declare `READ_PHONE_STATE`.
 */
@Singleton
class TelephonySnapshotDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    /** Reads the current telephony state. Cheap enough to call on every Monitor tick. */
    fun sample(): TelephonySample = TelephonySample(
        activeModemCount = activeModemCount(),
        sims = simSection(),
    )

    /**
     * Modem count, or null when the device has no telephony stack at all.
     *
     * A device with no [TelephonyManager] and one whose modem count reads zero are different answers, and
     * only the first means "this is not a phone".
     */
    private fun activeModemCount(): Int? {
        val manager = telephonyManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.activeModemCount
        } else {
            legacyPhoneCount(manager)
        }
    }

    // getPhoneCount is deprecated from API 30 in favour of getActiveModemCount, which does not exist below
    // it, so this branch never runs above API 29.
    @Suppress("DEPRECATION")
    private fun legacyPhoneCount(manager: TelephonyManager): Int = manager.phoneCount

    private fun simSection(): MonitorSection<List<SimEntry>> {
        val manager = subscriptionManager
        val hasModem = manager != null && hasTelephonyHardware()
        val granted = hasModem && hasReadPhoneState()
        val subscriptions = if (granted) manager?.let { activeSubscriptions(it) } else null
        return when {
            !hasModem -> MonitorSection.absent(SectionAvailability.NoHardware)
            !granted || subscriptions == null ->
                MonitorSection.absent(SectionAvailability.NoPermission(Manifest.permission.READ_PHONE_STATE))

            subscriptions.isEmpty() -> MonitorSection.absent(SectionAvailability.NoNetwork)
            else -> {
                val defaultDataId = defaultDataSubscriptionId()
                MonitorSection.available(subscriptions.map { info -> simEntry(info, defaultDataId) })
            }
        }
    }

    private fun hasTelephonyHardware(): Boolean {
        val manager = telephonyManager ?: return false
        return manager.phoneTypeOrNone() != TelephonyManager.PHONE_TYPE_NONE
    }

    /**
     * Active subscriptions, or null when the platform refused the read.
     *
     * The grant check is not enough on its own: a manufacturer build can still throw [SecurityException]
     * here, and the honest report for that is the same "you cannot see this" the UI already draws for a
     * missing grant - not a crash inside a diagnostic screen.
     */
    private fun activeSubscriptions(manager: SubscriptionManager): List<SubscriptionInfo>? = try {
        manager.activeSubscriptionInfoList.orEmpty()
    } catch (security: SecurityException) {
        Timber.w(security, "Telephony subscriptions refused despite a granted READ_PHONE_STATE")
        null
    }

    private fun simEntry(info: SubscriptionInfo, defaultDataId: Int) = SimEntry(
        slotIndex = info.simSlotIndex,
        operatorName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
        isDataPreferred = info.subscriptionId == defaultDataId,
    )

    /**
     * Subscription id carrying mobile data, or a value no subscription can equal.
     *
     * `getDefaultDataSubscriptionId` arrived in API 24 and the `legacy` flavor still ships minSdk 23, so
     * below that the answer is "unknown" rather than a guess: no SIM is then marked as the data one.
     */
    private fun defaultDataSubscriptionId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultDataSubscriptionId()
        } else {
            NO_DATA_SUBSCRIPTION
        }

    private fun hasReadPhoneState(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Phone type, or [TelephonyManager.PHONE_TYPE_NONE] when the platform will not say.
     *
     * For the purpose of drawing a section, a refused read and an absent modem mean the same thing, and a
     * diagnostic screen must not die while reporting that nothing is there.
     */
    private fun TelephonyManager.phoneTypeOrNone(): Int = try {
        phoneType
    } catch (security: SecurityException) {
        Timber.w(security, "Telephony phone type refused; treating the device as having no modem")
        TelephonyManager.PHONE_TYPE_NONE
    }

    private companion object {

        /** Sentinel for "no known data subscription"; no real subscription id can equal it. */
        const val NO_DATA_SUBSCRIPTION = Int.MIN_VALUE
    }
}
