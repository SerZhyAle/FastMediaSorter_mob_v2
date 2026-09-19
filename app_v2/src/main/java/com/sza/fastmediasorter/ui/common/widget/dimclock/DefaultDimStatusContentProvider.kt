package com.sza.fastmediasorter.ui.common.widget.dimclock

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetBatteryStatusUseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3256: Default dim status content provider reading battery state, with empty chip set.
 */
@Singleton
class DefaultDimStatusContentProvider @Inject constructor(
    private val getBatteryStatusUseCase: GetBatteryStatusUseCase
) : DimStatusContentProvider {

    override fun observeStatus(): Flow<DimStatusSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            val battery = getBatteryStatusUseCase.read()
            val percent = when (val p = battery.percent) {
                is MetricValue.Known -> p.value
                else -> DEFAULT_BATTERY_PERCENT
            }
            emit(
                DimStatusSnapshot(
                    batteryPercent = percent,
                    isCharging = battery.isCharging,
                    chips = emptyList()
                )
            )
            delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val DEFAULT_BATTERY_PERCENT = 100
        private const val POLL_INTERVAL_MS = 15_000L
    }
}
