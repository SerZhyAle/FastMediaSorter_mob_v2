package com.sza.fastmediasorter.core.power

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
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val PERCENT_SCALE = 100
private const val UNKNOWN_BATTERY_FIELD = -1

/**
 * S2536: folds the three signals that can put the app into a power-saving level into one answer.
 *
 * The signals are the user's own trigger choice, Android's battery saver and the charge itself. Any
 * one of them raising [PowerPolicyLevel.SAVING] is enough (ADR-5): a user who turned the system
 * saver on has already stated an intent, and the app's own threshold exists because the system's is
 * fixed at 15 percent and cannot be set from here.
 *
 * **Foreground only.** The receiver lives exactly as long as at least one activity is started. There
 * is no animation to govern and no screen to hold while the app is away, so an observer running in
 * the background would be a battery cost incurred by the feature meant to reduce one.
 *
 * **Never writes settings.** Automatic entry is runtime state (ADR-3). Persisting it would consume
 * the user's own choice: after one discharge their setting would read as whatever the battery did.
 */
@Singleton
class PowerStateObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    settingsRepository: SettingsRepository
) : Application.ActivityLifecycleCallbacks {

    private val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)

    private val mutableLevel = MutableStateFlow(PowerPolicyLevel.NORMAL)
    val level: StateFlow<PowerPolicyLevel> = mutableLevel.asStateFlow()

    /**
     * S2707: true only once the platform has answered about the battery and had nothing to report.
     *
     * The settings screen prints this as the reason its threshold options do nothing, so it must not
     * be inferred from [chargePercent] alone: that is null both on a device with no usable battery
     * level and in the window before the first reading arrives, and announcing a broken threshold on
     * a healthy phone for those milliseconds is worse than never announcing it.
     */
    private val mutableBatteryLevelUnavailable = MutableStateFlow(false)
    val batteryLevelUnavailable: StateFlow<Boolean> = mutableBatteryLevelUnavailable.asStateFlow()

    @Volatile
    private var trigger: PowerSavingTrigger = PowerSavingTrigger.DEFAULT

    @Volatile
    private var animationsDisabled: Boolean = false

    /** Null while the battery cannot be read, which leaves the threshold arm unsatisfied. */
    @Volatile
    private var chargePercent: Int? = null

    /** Separates "the platform reports no level" from "no reading has arrived yet". */
    @Volatile
    private var batteryIntentSeen: Boolean = false

    @Volatile
    private var osPowerSaveMode: Boolean = false

    private var startedActivities = 0
    private var registeredReceiver: BroadcastReceiver? = null

    init {
        scope.launch {
            settingsRepository.getSettings()
                .map { it.powerSavingTrigger to it.disableAnimations }
                .distinctUntilChanged()
                .collect { (chosenTrigger, disabled) ->
                    trigger = chosenTrigger
                    animationsDisabled = disabled
                    recompute()
                }
        }
    }

    private fun recompute() {
        mutableBatteryLevelUnavailable.value =
            resolveBatteryLevelUnavailable(batteryIntentSeen, chargePercent)
        mutableLevel.value = resolvePowerPolicyLevel(
            trigger = trigger,
            chargePercent = chargePercent,
            osPowerSaveMode = osPowerSaveMode,
            animationsDisabled = animationsDisabled
        )
        Timber.d("S2536: phone level=${mutableLevel.value} charge=$chargePercent osSaver=$osPowerSaveMode")
    }

    private fun startObserving() {
        if (registeredReceiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        batteryIntentSeen = true
                        chargePercent = readChargePercent(intent)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        osPowerSaveMode = powerManager?.isPowerSaveMode ?: false
                }
                recompute()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // ACTION_BATTERY_CHANGED is sticky, so this hands back the current charge immediately and the
        // level is right from the first frame rather than after the first battery tick, which on a
        // resting device can be minutes away.
        val sticky = ContextCompat.registerReceiver(
            context,
            listener,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registeredReceiver = listener
        if (sticky != null) batteryIntentSeen = true
        chargePercent = sticky?.let(::readChargePercent)
        osPowerSaveMode = powerManager?.isPowerSaveMode ?: false
        recompute()
    }

    private fun stopObserving() {
        val listener = registeredReceiver ?: return
        registeredReceiver = null
        try {
            context.unregisterReceiver(listener)
        } catch (e: IllegalArgumentException) {
            // The platform answers an already-gone registration with this. The app is leaving the
            // foreground either way, so the state to restore is the one already set below.
            Timber.w(e, "PowerStateObserver: receiver was not registered")
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        if (startedActivities == 1) startObserving()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) stopObserving()
    }

    // Only the started/stopped pair drives this observer; the rest of the interface is required by
    // the platform and has nothing to do here. Expression bodies rather than empty braces so the
    // emptiness reads as deliberate.
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Null when the platform reports no usable level or scale, rather than a fabricated percentage. */
private fun readChargePercent(intent: Intent): Int? {
    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_BATTERY_FIELD)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_BATTERY_FIELD)
    return if (rawLevel < 0 || scale <= 0) null else rawLevel * PERCENT_SCALE / scale
}

/**
 * S2536: the whole verdict, as a pure function so every arm is testable without a device.
 *
 * An unreadable [chargePercent] leaves the threshold arm unsatisfied rather than assuming a flat
 * battery - guessing the wrong way here would freeze the app on a device that simply does not report
 * its charge.
 */
/**
 * S2707: whether the device has ANSWERED that it has no usable battery level.
 *
 * A pure function for the same reason [resolvePowerPolicyLevel] is one: the difference between the
 * two null cases is invisible to the compiler and can only be proved by a test.
 */
internal fun resolveBatteryLevelUnavailable(batteryIntentSeen: Boolean, chargePercent: Int?): Boolean =
    batteryIntentSeen && chargePercent == null

internal fun resolvePowerPolicyLevel(
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
