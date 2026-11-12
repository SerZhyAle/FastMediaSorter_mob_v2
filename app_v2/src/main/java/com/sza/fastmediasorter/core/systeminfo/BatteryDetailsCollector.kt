package com.sza.fastmediasorter.core.systeminfo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.repository.BatteryRuntimeEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/** Reads only public battery data; unsupported measurements remain absent. */
class BatteryDetailsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val estimator: BatteryRuntimeEstimator
) {
    fun read(): BatteryDetails {
        Timber.d("S4529: battery details read")
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val current = readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, INVALID)?.takeIf { it > 0 }
        val power = if (current != null && voltage != null) current.toDouble() * voltage / POWER_DIVISOR else null
        val canEstimateCharge = status == BatteryManager.BATTERY_STATUS_CHARGING &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val chargeTime = if (canEstimateCharge) {
            runCatching { manager?.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it > 0L }
        } else {
            null
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, INVALID) ?: INVALID
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, INVALID) ?: INVALID
        val percent = if (level >= 0 && scale > 0 && level <= scale) level * FULL_PERCENT / scale else null
        // EXTRA_PLUGGED, not the status: a charge limiter reports "not charging" while the cable is in,
        // and that device is not running on its battery.
        val onExternalPower = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
        val counter = readProperty(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }
        val remaining = estimateRemaining(manager, percent, onExternalPower, counter, current)
        val saver = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
        val capacity = estimateCapacityMilliampHours(counter, percent)
        val cycleCount = intent?.getIntExtra(EXTRA_CYCLE_COUNT, INVALID)?.takeIf { it >= 0 }
        val chargingPolicy = chargingPolicyText(intent?.getIntExtra(EXTRA_CHARGING_STATUS, INVALID))
        val chargeCounterMah = counter?.let { (it / MICROAMPS_PER_MILLIAMP).toInt() }
        val averageCurrent = readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeIf { it != 0 }
        return BatteryDetails(
            currentMicroamps = current,
            powerWatts = power,
            chargeTimeMillis = chargeTime,
            dischargeTimeMillis = remaining,
            powerSaveMode = saver,
            estimatedCapacityMilliampHours = capacity,
            isOnExternalPower = onExternalPower,
            cycleCount = cycleCount,
            chargingPolicy = chargingPolicy,
            chargeCounterMilliampHours = chargeCounterMah,
            averageCurrentMicroamps = averageCurrent,
        )
    }

    private fun chargingPolicyText(status: Int?): String? = when (status) {
        CHARGING_POLICY_DEFAULT -> "Normal"
        CHARGING_POLICY_ADAPTIVE_AC, CHARGING_POLICY_ADAPTIVE_AON -> "Adaptive"
        CHARGING_POLICY_ADAPTIVE_LONGLIFE -> "Long life (80% limit)"
        else -> null
    }

    private fun estimateRemaining(
        manager: BatteryManager?,
        percent: Int?,
        onExternalPower: Boolean,
        counter: Int?,
        currentNow: Int?
    ): Long? {
        if (percent == null) return null
        // The average current, where the fuel gauge keeps one, is steadier than the instantaneous one.
        val draw = readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeIf { it != 0 } ?: currentNow
        val estimate = estimator.estimateRemainingMillis(
            percent,
            onExternalPower,
            SystemClock.elapsedRealtime(),
            counter,
            draw
        )
        return (estimate as? MetricValue.Known)?.value
    }

    private fun readProperty(manager: BatteryManager?, property: Int): Int? =
        runCatching { manager?.getIntProperty(property) }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }

    /**
     * Full-charge capacity derived from the remaining charge counter and the charge level.
     * Android exposes no public rated (design) capacity, so this is a calculated value and is never
     * presented as one; a low level or an implausible result leaves it absent.
     */
    private fun estimateCapacityMilliampHours(counterMicroampHours: Int?, percent: Int?): Int? {
        return if (percent != null && percent >= MIN_CAPACITY_PERCENT && counterMicroampHours != null) {
            val milliampHours = counterMicroampHours.toDouble() / MICROAMPS_PER_MILLIAMP * FULL_PERCENT / percent
            milliampHours.toInt().takeIf { it in PLAUSIBLE_CAPACITY_MAH }
        } else {
            null
        }
    }

    private companion object {
        const val INVALID = -1
        const val FULL_PERCENT = 100
        const val POWER_DIVISOR = 1_000_000_000.0
        const val MICROAMPS_PER_MILLIAMP = 1_000.0
        const val MIN_CAPACITY_PERCENT = 10
        val PLAUSIBLE_CAPACITY_MAH = 100..50_000
        const val EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"
        const val EXTRA_CHARGING_STATUS = "android.os.extra.CHARGING_STATUS"
        const val CHARGING_POLICY_DEFAULT = 1
        const val CHARGING_POLICY_ADAPTIVE_AC = 2
        const val CHARGING_POLICY_ADAPTIVE_AON = 3
        const val CHARGING_POLICY_ADAPTIVE_LONGLIFE = 4
    }
}

data class BatteryDetails(
    val currentMicroamps: Int?,
    val powerWatts: Double?,
    val chargeTimeMillis: Long?,
    val dischargeTimeMillis: Long?,
    val powerSaveMode: Boolean?,
    val estimatedCapacityMilliampHours: Int?,
    val isOnExternalPower: Boolean = false,
    val cycleCount: Int? = null,
    val chargingPolicy: String? = null,
    val chargeCounterMilliampHours: Int? = null,
    val averageCurrentMicroamps: Int? = null,
)
