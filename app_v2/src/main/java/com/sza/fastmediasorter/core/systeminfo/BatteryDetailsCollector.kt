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
        val current = runCatching { manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrNull()?.takeUnless { it == Int.MIN_VALUE }
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
        val discharging = status == BatteryManager.BATTERY_STATUS_DISCHARGING
        val remaining = if (percent != null) {
            estimator.estimateRemainingMillis(percent, !discharging, SystemClock.elapsedRealtime())
        } else {
            MetricValue.Unknown
        }
        val saver = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
        val capacity = estimateCapacityMilliampHours(manager, percent)
        return BatteryDetails(current, power, chargeTime, (remaining as? MetricValue.Known)?.value, saver, capacity)
    }

    /**
     * Full-charge capacity derived from the remaining charge counter and the charge level.
     * Android exposes no public rated (design) capacity, so this is a calculated value and is never
     * presented as one; a low level or an implausible result leaves it absent.
     */
    private fun estimateCapacityMilliampHours(manager: BatteryManager?, percent: Int?): Int? {
        val counterMicroampHours = runCatching {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrNull()?.takeIf { it > 0 }
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
    }
}

data class BatteryDetails(
    val currentMicroamps: Int?,
    val powerWatts: Double?,
    val chargeTimeMillis: Long?,
    val dischargeTimeMillis: Long?,
    val powerSaveMode: Boolean?,
    val estimatedCapacityMilliampHours: Int?
)
