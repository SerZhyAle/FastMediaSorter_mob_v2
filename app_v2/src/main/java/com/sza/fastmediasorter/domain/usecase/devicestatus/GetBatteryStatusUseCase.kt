package com.sza.fastmediasorter.domain.usecase.devicestatus

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.sza.fastmediasorter.domain.model.devicestatus.BatteryStatus
import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.repository.BatteryRuntimeEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1178: the battery gadget's metric - charge level, charging state and expected remaining runtime.
 *
 * The level is read from the sticky `ACTION_BATTERY_CHANGED` broadcast with a null receiver, so this
 * metric registers nothing and leaves nothing to unregister when a cell is detached.
 */
class GetBatteryStatusUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val estimator: BatteryRuntimeEstimator
) : DeviceStatusProvider<BatteryStatus> {

    override val refreshIntervalMs: Long = REFRESH_INTERVAL_MS

    override suspend fun read(): BatteryStatus = withContext(Dispatchers.IO) {
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val percentOrNull = readPercent(sticky)
        Timber.d("S4529: battery gadget estimate read")
        val charging = sticky?.let(::isCharging) ?: false
        val plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val estimate = when {
            percentOrNull == null -> MetricValue.Unknown
            else -> estimator.estimateRemainingMillis(
                percentOrNull,
                charging || plugged,
                SystemClock.elapsedRealtime(),
                readProperty(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.takeIf { it != 0 }
                    ?: readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            )
        }
        BatteryStatus(
            percent = percentOrNull?.let { MetricValue.Known(it) } ?: MetricValue.Unknown,
            isCharging = charging,
            remainingMillis = estimate,
            isEstimateApproximate = estimate is MetricValue.Known
        )
    }

    private fun readPercent(intent: Intent?): Int? {
        if (intent == null) return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, INVALID_EXTRA)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, INVALID_EXTRA)
        return if (level < 0 || scale <= 0) null else level * FULL_PERCENT / scale
    }

    private fun readProperty(manager: BatteryManager?, property: Int): Int? =
        runCatching { manager?.getIntProperty(property) }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private companion object {
        /** The charge level moves slowly; ten seconds is live enough and costs a binder call a minute. */
        const val REFRESH_INTERVAL_MS = 10_000L
        const val INVALID_EXTRA = -1
        const val FULL_PERCENT = 100
    }
}
