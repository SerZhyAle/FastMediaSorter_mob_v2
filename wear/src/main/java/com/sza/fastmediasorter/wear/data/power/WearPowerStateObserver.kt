package com.sza.fastmediasorter.wear.data.power

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.ui.common.PowerPolicyLevel
import com.sza.fastmediasorter.wear.ui.common.WearPowerPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val PERCENT_SCALE = 100
private const val UNKNOWN_BATTERY_FIELD = -1

/**
 * S2536: the watch's own power verdict, mirroring the phone's [PowerStateObserver] arm for arm.
 *
 * The verdict is deliberately local. Only the trigger VALUE crosses the settings channel from the
 * phone, because the two devices have independent batteries - a phone at eighty percent says nothing
 * about a watch at twelve (ADR-4).
 *
 * Every platform read is wrapped, matching the health data source already in this module: a watch
 * that does not report its charge leaves the threshold arm unsatisfied rather than being treated as
 * flat, so the app does not freeze itself on a device that simply stays quiet.
 */
@Singleton
class WearPowerStateObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) : Application.ActivityLifecycleCallbacks {

    /**
     * Owned rather than injected: this module publishes no application-scoped `CoroutineScope`, and
     * this observer is a singleton that lives as long as the process, so there is nothing to cancel
     * it against. Never cancelled for the same reason the application's own scope is not.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)

    private val mutableLevel = MutableStateFlow(PowerPolicyLevel.NORMAL)
    val level: StateFlow<PowerPolicyLevel> = mutableLevel.asStateFlow()

    @Volatile
    private var trigger: PowerSavingTrigger = PowerSavingTrigger.DEFAULT

    @Volatile
    private var animationsDisabled: Boolean = false

    /** Null while the charge cannot be read, which leaves the threshold arm unsatisfied. */
    @Volatile
    private var chargePercent: Int? = null

    @Volatile
    private var osPowerSaveMode: Boolean = false

    private var startedActivities = 0
    private var registeredReceiver: BroadcastReceiver? = null

    init {
        scope.launch {
            combine(
                preferencesRepository.powerSavingTrigger,
                preferencesRepository.isAnimationsDisabled
            ) { chosenTrigger, disabled -> chosenTrigger to disabled }
                .distinctUntilChanged()
                .collect { (chosenTrigger, disabled) ->
                    trigger = chosenTrigger
                    animationsDisabled = disabled
                    recompute()
                }
        }
    }

    private fun recompute() {
        val next = resolveWearPowerPolicyLevel(
            trigger = trigger,
            chargePercent = chargePercent,
            osPowerSaveMode = osPowerSaveMode,
            animationsDisabled = animationsDisabled
        )
        mutableLevel.value = next
        WearPowerPolicy.update(next)
    }

    private fun startObserving() {
        if (registeredReceiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> chargePercent = readChargePercent(intent)
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> osPowerSaveMode = readPowerSaveMode()
                }
                recompute()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // ACTION_BATTERY_CHANGED is sticky, so this returns the current charge at once and the first
        // frame is drawn against the right level rather than against NORMAL.
        val sticky = runCatching {
            ContextCompat.registerReceiver(context, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.getOrElse { error ->
            Timber.w(error, "WearPowerStateObserver: could not register the power receiver")
            null
        }
        registeredReceiver = listener
        chargePercent = sticky?.let(::readChargePercent)
        osPowerSaveMode = readPowerSaveMode()
        recompute()
    }

    private fun stopObserving() {
        val listener = registeredReceiver ?: return
        registeredReceiver = null
        runCatching { context.unregisterReceiver(listener) }.onFailure { error ->
            Timber.w(error, "WearPowerStateObserver: receiver was not registered")
        }
    }

    private fun readPowerSaveMode(): Boolean =
        runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        if (startedActivities == 1) startObserving()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) stopObserving()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Null when the platform reports no usable level or scale, rather than a fabricated percentage. */
private fun readChargePercent(intent: Intent): Int? = runCatching {
    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_BATTERY_FIELD)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_BATTERY_FIELD)
    if (rawLevel < 0 || scale <= 0) null else rawLevel * PERCENT_SCALE / scale
}.getOrNull()

/**
 * S2536: the watch's verdict, as a pure function so every arm is testable - the module carries no
 * Compose instrumentation at all, so anything not asserted here can only be checked by hand on a
 * real watch.
 */
internal fun resolveWearPowerPolicyLevel(
    trigger: PowerSavingTrigger,
    chargePercent: Int?,
    osPowerSaveMode: Boolean,
    animationsDisabled: Boolean
): PowerPolicyLevel {
    val threshold = trigger.thresholdPercent
    val belowThreshold = threshold != null && chargePercent != null && chargePercent <= threshold
    val saving = trigger == PowerSavingTrigger.ALWAYS || osPowerSaveMode || belowThreshold
    return when {
        saving -> PowerPolicyLevel.SAVING
        animationsDisabled -> PowerPolicyLevel.REDUCED
        else -> PowerPolicyLevel.NORMAL
    }
}
